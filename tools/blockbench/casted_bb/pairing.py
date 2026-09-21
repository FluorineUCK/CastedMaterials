from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from tools.material_mapping.sampling import inverse_transform, validate_target_size

from .model import BbModel, ElementFaceKey


VALID_ROTATIONS = {0, 90, 180, 270}


class ValidationError(ValueError):
    def __init__(self, group: str, element: str, face: str, message: str):
        self.group = group
        self.element = element
        self.face = face
        super().__init__(f"{group}/{element}/{face}: {message}")


@dataclass(frozen=True)
class PairedFace:
    key: ElementFaceKey
    slot: str
    destination_uv: tuple[int, int, int, int]
    source_uv: tuple[int, int, int, int]
    transform: str


def validate_model(model: BbModel) -> list[PairedFace]:
    metadata = model.data.get("casted_materials")
    if not isinstance(metadata, dict):
        raise ValidationError("CM_DEST", "<none>", "<none>", "missing casted_materials metadata")
    slots = metadata.get("slots")
    grids = metadata.get("source_grids")
    if not isinstance(slots, list) or not slots or not isinstance(grids, dict):
        raise ValidationError("CM_DEST", "<none>", "<none>", "missing source slots or grids")

    destination = _named_elements(model, "CM_DEST")
    sources = {slot: _named_elements(model, f"CM_SOURCE[{slot}]") for slot in slots}
    face_count = sum(len(element.get("faces", {})) for element in destination.values())
    if face_count == 0:
        raise ValidationError("CM_DEST", "<none>", "<none>", "empty mapping")

    target_width, target_height = model.resolution
    try:
        validate_target_size((target_width, target_height))
    except ValueError as error:
        raise ValidationError("CM_DEST", "<none>", "<none>", str(error)) from error

    occupied: dict[tuple[int, int], tuple[str, int, int, ElementFaceKey]] = {}
    pairs: list[PairedFace] = []
    for element_name in sorted(destination):
        dest_element = destination[element_name]
        faces = dest_element.get("faces", {})
        for direction in sorted(faces):
            key = ElementFaceKey(element_name, direction)
            dest_face = faces[direction]
            destination_uv = _validated_face(
                dest_face, "CM_DEST", element_name, direction, target_width, target_height)
            candidates: list[tuple[str, dict[str, Any]]] = []
            for slot in slots:
                source_element = sources[slot].get(element_name)
                if source_element is not None and direction in source_element.get("faces", {}):
                    candidates.append((slot, source_element["faces"][direction]))
            if len(candidates) != 1:
                raise ValidationError(
                    "CM_DEST", element_name, direction,
                    f"expected exactly one matching source face, found {len(candidates)}")
            slot, source_face = candidates[0]
            grid = grids.get(slot)
            if not (isinstance(grid, list) and len(grid) == 2):
                raise ValidationError(f"CM_SOURCE[{slot}]", element_name, direction, "invalid source grid")
            source_uv = _validated_face(
                source_face, f"CM_SOURCE[{slot}]", element_name, direction, int(grid[0]), int(grid[1]))
            transform = _relative_transform(
                dest_face, source_face, destination_uv, source_uv,
                f"CM_SOURCE[{slot}]", element_name, direction)

            dx, dy, dw, dh = _rect(destination_uv)
            sx, sy, sw, sh = _rect(source_uv)
            for local_y in range(dh):
                for local_x in range(dw):
                    source_local_x, source_local_y = inverse_transform(
                        transform, local_x, local_y, sw, sh)
                    pixel = dx + local_x, dy + local_y
                    signature = slot, sx + source_local_x, sy + source_local_y
                    previous = occupied.get(pixel)
                    if previous is not None and previous[:3] != signature:
                        previous_key = previous[3]
                        raise ValidationError(
                            "CM_DEST", element_name, direction,
                            f"overlaps destination pixel {pixel[0]},{pixel[1]} written by "
                            f"{previous_key.element}/{previous_key.face}")
                    occupied[pixel] = (*signature, key)
            pairs.append(PairedFace(key, slot, destination_uv, source_uv, transform))
    return pairs


def _named_elements(model: BbModel, group_name: str) -> dict[str, dict[str, Any]]:
    group = model.group(group_name)
    if group is None:
        raise ValidationError(group_name, "<none>", "<none>", "missing group")
    result: dict[str, dict[str, Any]] = {}
    for element in model.group_elements(group_name):
        name = element.get("name")
        if not isinstance(name, str) or not name:
            raise ValidationError(group_name, "<unnamed>", "<none>", "element needs a stable name")
        if name in result:
            raise ValidationError(group_name, name, "<none>", "duplicate stable element name")
        result[name] = element
    return result


def _validated_face(
        face: dict[str, Any], group: str, element: str, direction: str,
        width: int, height: int) -> tuple[int, int, int, int]:
    rotation = face.get("rotation", 0)
    if rotation not in VALID_ROTATIONS:
        raise ValidationError(group, element, direction, f"unsupported face rotation {rotation}")
    uv = face.get("uv")
    if not isinstance(uv, list) or len(uv) != 4 or any(not _is_integer(value) for value in uv):
        raise ValidationError(group, element, direction, "face requires four integer UV coordinates")
    result = tuple(int(value) for value in uv)
    min_u, max_u = sorted((result[0], result[2]))
    min_v, max_v = sorted((result[1], result[3]))
    if min_u < 0 or min_v < 0 or max_u > width or max_v > height:
        raise ValidationError(group, element, direction, f"UV {list(result)} is outside {width}x{height}")
    if min_u == max_u or min_v == max_v:
        raise ValidationError(group, element, direction, "face UV has zero area")
    return result


def _relative_transform(
        destination: dict[str, Any], source: dict[str, Any],
        destination_uv: tuple[int, int, int, int], source_uv: tuple[int, int, int, int],
        group: str, element: str, direction: str) -> str:
    dw, dh = _dimensions(destination_uv)
    sw, sh = _dimensions(source_uv)
    relative_rotation = (int(source.get("rotation", 0)) - int(destination.get("rotation", 0))) % 360
    if relative_rotation in (90, 270):
        dimensions_match = (dw, dh) == (sh, sw)
    else:
        dimensions_match = (dw, dh) == (sw, sh)
    if not dimensions_match:
        raise ValidationError(group, element, direction,
                              f"source dimensions {sw}x{sh} do not match destination {dw}x{dh}")

    destination_flip_x = destination_uv[0] > destination_uv[2]
    destination_flip_y = destination_uv[1] > destination_uv[3]
    source_flip_x = source_uv[0] > source_uv[2]
    source_flip_y = source_uv[1] > source_uv[3]
    flip_x = destination_flip_x != source_flip_x
    flip_y = destination_flip_y != source_flip_y
    if flip_x and flip_y:
        relative_rotation = (relative_rotation + 180) % 360
        flip_x = flip_y = False
    if (flip_x or flip_y) and relative_rotation != 0:
        raise ValidationError(group, element, direction,
                              "combined rotated mirror transform is unsupported")
    if flip_x:
        return "mirror_x"
    if flip_y:
        return "mirror_y"
    return {0: "identity", 90: "rotate_90", 180: "rotate_180", 270: "rotate_270"}[relative_rotation]


def _dimensions(uv: tuple[int, int, int, int]) -> tuple[int, int]:
    return abs(uv[2] - uv[0]), abs(uv[3] - uv[1])


def _rect(uv: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = uv
    return min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1)


def _is_integer(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) or (
        isinstance(value, float) and value.is_integer())
