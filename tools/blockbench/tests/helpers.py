from __future__ import annotations

from PIL import Image


def coordinate_color(x: int, y: int, seed: int = 0) -> tuple[int, int, int, int]:
    return ((x + seed) % 256, (y * 3 + seed) % 256,
            (x * 17 + y * 31 + seed) % 256, 64 + (x * 7 + y * 11 + seed) % 192)


def coordinate_image(width: int, height: int, seed: int = 0) -> Image.Image:
    image = Image.new("RGBA", (width, height))
    image.putdata([coordinate_color(x, y, seed) for y in range(height) for x in range(width)])
    return image


def group_names(data: dict) -> list[str]:
    return [entry["name"] for entry in data["outliner"] if isinstance(entry, dict)]


def _elements_in_group(data: dict, name: str) -> list[dict]:
    by_uuid = {element["uuid"]: element for element in data["elements"]}
    group = next(entry for entry in data["outliner"] if isinstance(entry, dict) and entry["name"] == name)
    return [by_uuid[uuid] for uuid in group["children"]]


def stable_face_pairs(data: dict, slot: str) -> bool:
    destination = {
        (element["name"], face)
        for element in _elements_in_group(data, "CM_DEST")
        for face in element.get("faces", {})
    }
    source = {
        (element["name"], face)
        for element in _elements_in_group(data, f"CM_SOURCE[{slot}]")
        for face in element.get("faces", {})
    }
    return bool(source) and source <= destination
