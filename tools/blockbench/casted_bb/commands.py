from __future__ import annotations

import argparse
import base64
import copy
import json
import sys
import uuid
from pathlib import Path
from typing import Any, Sequence

from tools.material_mapping.cli_support import (
    _computed_cost,
    _json,
    _parse_grids,
    _parse_size,
    _parse_sources,
    _png_bytes,
    _publish_outputs,
)
from tools.material_mapping.compression import compress_pixels
from tools.material_mapping.sampling import (
    render_definition,
    render_pixels,
    validate_rgba_equal,
)

from .model import BbModel
from .pairing import ValidationError, validate_model
from .preview import expand_pairs


UUID_NAMESPACE = uuid.UUID("31e8cf5c-9b0a-4ee7-9f38-d808c1a01b67")


def main(argv: Sequence[str] | None = None) -> int:
    parser = _parser()
    try:
        args = parser.parse_args(list(argv) if argv is not None else None)
        if args.command == "init":
            _init(args)
        elif args.command == "validate":
            pairs = validate_model(BbModel.load(args.model))
            print(f"valid: {len(pairs)} paired faces")
        elif args.command == "export":
            _export(args)
        else:
            parser.error("missing command")
        return 0
    except (ValidationError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="cli.py")
    commands = parser.add_subparsers(dest="command")
    init = commands.add_parser("init", help="create a paired-UV Blockbench project")
    init.add_argument("--model", required=True, type=Path)
    init.add_argument("--target", required=True)
    init.add_argument("--size", required=True)
    init.add_argument("--slots", required=True)
    init.add_argument("--source-grid", action="append", default=[], metavar="SLOT=WIDTHxHEIGHT")
    init.add_argument("--output", required=True, type=Path)
    validate = commands.add_parser("validate", help="validate a paired-UV Blockbench project")
    validate.add_argument("model", type=Path)
    export = commands.add_parser("export", help="export target JSON and a material preview")
    export.add_argument("model", type=Path)
    export.add_argument("--target-json", required=True, type=Path)
    export.add_argument("--preview", required=True, type=Path)
    export.add_argument("--source", action="append", default=[], metavar="SLOT=PNG")
    export.add_argument("--blockbench-output", type=Path)
    return parser


def _export(args: argparse.Namespace) -> None:
    model = BbModel.load(args.model)
    pairs = validate_model(model)
    metadata = model.data["casted_materials"]
    grids = {
        slot: (int(grid[0]), int(grid[1]))
        for slot, grid in metadata["source_grids"].items()
    }
    source_paths = _parse_sources(args.source)
    declared_slots = set(metadata["slots"])
    missing = sorted(declared_slots - source_paths.keys())
    if missing:
        raise ValueError("missing --source for slots: " + ", ".join(missing))
    unknown = sorted(source_paths.keys() - declared_slots)
    if unknown:
        raise ValueError("undeclared --source slots: " + ", ".join(unknown))

    from PIL import Image

    sources = {
        slot: Image.open(source_paths[slot]).convert("RGBA")
        for slot in metadata["slots"]
    }
    mapped = expand_pairs(pairs)
    compressed = compress_pixels(mapped)
    definition = {
        "schema": 1,
        "size": [model.resolution[0], model.resolution[1]],
        "source_grids": {slot: list(grids[slot]) for slot in sorted(grids)},
        "regions": compressed.regions,
        "pixels": compressed.pixels,
        "computed_cost": _computed_cost(compressed.regions, compressed.pixels),
    }
    direct = render_pixels(mapped, model.resolution, sources, grids)
    round_trip = render_definition(definition, sources)
    validate_rgba_equal(direct, round_trip)

    target_bytes = _json(definition).encode("utf-8")
    preview_bytes = _png_bytes(direct)
    outputs = [(args.target_json, target_bytes), (args.preview, preview_bytes)]
    if args.blockbench_output is not None:
        project = _preview_project(model, args.preview.name, preview_bytes)
        outputs.append((args.blockbench_output, _json(project).encode("utf-8")))
    _publish_outputs(outputs)
    print(
        f"exported: {len(compressed.regions)} regions, {len(compressed.pixels)} pixels, "
        f"preview {model.resolution[0]}x{model.resolution[1]}")


def _init(args: argparse.Namespace) -> None:
    source = BbModel.load(args.model).data
    target_width, target_height = _parse_size(args.size, target=True)
    slots = [slot.strip() for slot in args.slots.split(",") if slot.strip()]
    if not slots or len(slots) != len(set(slots)):
        raise ValueError("--slots must contain unique, non-empty names")
    textures = source.get("textures", [])
    slot_by_texture = _slot_by_texture(textures, slots)
    grids = {slot: _grid_for_slot(textures, slot_by_texture, slot, source) for slot in slots}
    grids.update(_parse_grids(args.source_grid, slots))

    output: dict[str, Any] = {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": source.get("name", args.output.stem),
        "resolution": {"width": target_width, "height": target_height},
        "elements": [],
        "outliner": [],
        "textures": copy.deepcopy(textures),
        "casted_materials": {
            "schema": 1,
            "target": args.target,
            "slots": slots,
            "source_grids": {slot: list(grids[slot]) for slot in slots},
        },
    }
    elements = [element for element in source.get("elements", []) if isinstance(element, dict)]
    groups: list[tuple[str, list[dict[str, Any]]]] = [("CM_DEST", [])]
    groups.extend((f"CM_SOURCE[{slot}]", []) for slot in slots)
    groups.append(("CM_FIXED", []))

    for element in elements:
        mapped_faces: dict[str, dict[str, Any]] = {}
        fixed_faces: dict[str, dict[str, Any]] = {}
        faces_by_slot: dict[str, dict[str, dict[str, Any]]] = {slot: {} for slot in slots}
        for direction, face in element.get("faces", {}).items():
            slot = _face_slot(face, textures, slot_by_texture, slots)
            if slot is None:
                fixed_faces[direction] = copy.deepcopy(face)
            else:
                mapped_faces[direction] = copy.deepcopy(face)
                faces_by_slot[slot][direction] = copy.deepcopy(face)
        if mapped_faces:
            groups[0][1].append(_copy_element(element, "CM_DEST", mapped_faces, 0))
        for index, slot in enumerate(slots):
            if faces_by_slot[slot]:
                groups[index + 1][1].append(
                    _copy_element(
                        element, f"CM_SOURCE[{slot}]",
                        _source_faces(faces_by_slot[slot], grids[slot], slot),
                        24 * (index + 1)))
        if fixed_faces:
            groups[-1][1].append(_copy_element(element, "CM_FIXED", fixed_faces, 0))

    for group_name, group_elements in groups:
        output["elements"].extend(group_elements)
        output["outliner"].append({
            "name": group_name,
            "origin": [0, 0, 0],
            "rotation": [0, 0, 0],
            "uuid": _uuid(f"group:{group_name}"),
            "export": True,
            "visibility": group_name in {"CM_DEST", "CM_FIXED"},
            "isOpen": group_name in {"CM_DEST", "CM_FIXED"},
            "children": [element["uuid"] for element in group_elements],
        })

    model = BbModel(output)
    validate_model(model)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(_json(output), encoding="utf-8")


def _source_faces(
        faces: dict[str, dict[str, Any]], grid: tuple[int, int], slot: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for direction, face in faces.items():
        copied = copy.deepcopy(face)
        uv = copied.get("uv")
        if not isinstance(uv, list) or len(uv) != 4:
            raise ValueError(f"source slot {slot} face {direction} has no rectangular UV")
        width, height = abs(int(uv[2]) - int(uv[0])), abs(int(uv[3]) - int(uv[1]))
        if width > grid[0] or height > grid[1]:
            raise ValueError(
                f"source slot {slot} face {direction} size {width}x{height} exceeds grid {grid[0]}x{grid[1]}")
        copied["uv"] = [
            width if uv[0] > uv[2] else 0,
            height if uv[1] > uv[3] else 0,
            0 if uv[0] > uv[2] else width,
            0 if uv[1] > uv[3] else height,
        ]
        result[direction] = copied
    return result


def _preview_project(model: BbModel, texture_name: str, png: bytes) -> dict[str, Any]:
    project = model.clone_data()
    texture_index = len(project.setdefault("textures", []))
    width, height = model.resolution
    project["textures"].append({
        "name": texture_name,
        "id": str(texture_index),
        "width": width,
        "height": height,
        "uv_width": width,
        "uv_height": height,
        "particle": False,
        "use_as_default": True,
        "visible": True,
        "internal": True,
        "saved": True,
        "source": "data:image/png;base64," + base64.b64encode(png).decode("ascii"),
    })
    destination = model.group("CM_DEST")
    if destination is None:
        raise ValidationError("CM_DEST", "<none>", "<none>", "missing group")
    by_uuid = {element["uuid"]: element for element in project["elements"]}
    for uuid_value in _child_uuids(destination.get("children", [])):
        if uuid_value in by_uuid:
            for face in by_uuid[uuid_value].get("faces", {}).values():
                face["texture"] = texture_index
    for group in project.get("outliner", []):
        if isinstance(group, dict):
            group["visibility"] = group.get("name") in {"CM_DEST", "CM_FIXED"}
    project["name"] = f"{project.get('name', 'casted_model')} material preview"
    return project


def _child_uuids(children: list[Any]) -> list[str]:
    result: list[str] = []
    for child in children:
        if isinstance(child, str):
            result.append(child)
        elif isinstance(child, dict):
            result.extend(_child_uuids(child.get("children", [])))
    return result


def _copy_element(
        element: dict[str, Any], group: str, faces: dict[str, dict[str, Any]], offset_x: int) -> dict[str, Any]:
    result = copy.deepcopy(element)
    original_uuid = str(element.get("uuid", element.get("name", "element")))
    stable_name = str(element.get("name", "element"))
    result["uuid"] = _uuid(f"element:{group}:{stable_name}:{original_uuid}")
    result["faces"] = faces
    if offset_x:
        for key in ("from", "to", "origin"):
            if isinstance(result.get(key), list) and len(result[key]) == 3:
                result[key][0] += offset_x
    return result


def _face_slot(
        face: dict[str, Any], textures: list[dict[str, Any]],
        slot_by_texture: dict[int, str], slots: list[str]) -> str | None:
    index = _texture_index(face.get("texture"), textures)
    if index is not None and index in slot_by_texture:
        return slot_by_texture[index]
    return slots[0] if len(slots) == 1 else None


def _slot_by_texture(textures: list[dict[str, Any]], slots: list[str]) -> dict[int, str]:
    result: dict[int, str] = {}
    for index, texture in enumerate(textures):
        name = Path(str(texture.get("name", ""))).stem.lower()
        for slot in slots:
            if name == slot.lower() or name.endswith("_" + slot.lower()):
                result[index] = slot
                break
    return result


def _grid_for_slot(
        textures: list[dict[str, Any]], slot_by_texture: dict[int, str],
        slot: str, source: dict[str, Any]) -> tuple[int, int]:
    for index, mapped in slot_by_texture.items():
        if mapped == slot:
            texture = textures[index]
            return int(texture.get("uv_width", texture.get("width", 0))), int(
                texture.get("uv_height", texture.get("height", 0)))
    resolution = source.get("resolution", {})
    return int(resolution.get("width", 0)), int(resolution.get("height", 0))


def _texture_index(value: Any, textures: list[dict[str, Any]]) -> int | None:
    if isinstance(value, int) and 0 <= value < len(textures):
        return value
    if isinstance(value, str):
        for index, texture in enumerate(textures):
            if value in {str(index), str(texture.get("id")), str(texture.get("uuid"))}:
                return index
    return None


def _uuid(key: str) -> str:
    return str(uuid.uuid5(UUID_NAMESPACE, key))
