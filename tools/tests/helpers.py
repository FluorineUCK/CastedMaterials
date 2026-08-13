from __future__ import annotations


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
