from __future__ import annotations

import argparse
import copy
import json
import sys
import uuid
from pathlib import Path
from typing import Any, Sequence

from .model import BbModel
from .pairing import ValidationError, validate_model


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
        else:
            parser.error("missing command")
        return 0
    except (ValidationError, OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="casted_bb.py")
    commands = parser.add_subparsers(dest="command")
    init = commands.add_parser("init", help="create a paired-UV Blockbench project")
    init.add_argument("--model", required=True, type=Path)
    init.add_argument("--target", required=True)
    init.add_argument("--size", required=True)
    init.add_argument("--slots", required=True)
    init.add_argument("--output", required=True, type=Path)
    validate = commands.add_parser("validate", help="validate a paired-UV Blockbench project")
    validate.add_argument("model", type=Path)
    return parser


def _init(args: argparse.Namespace) -> None:
    source = BbModel.load(args.model).data
    target_width, target_height = _parse_size(args.size)
    slots = [slot.strip() for slot in args.slots.split(",") if slot.strip()]
    if not slots or len(slots) != len(set(slots)):
        raise ValueError("--slots must contain unique, non-empty names")
    textures = source.get("textures", [])
    slot_by_texture = _slot_by_texture(textures, slots)
    grids = {slot: _grid_for_slot(textures, slot_by_texture, slot, source) for slot in slots}

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
                    _copy_element(element, f"CM_SOURCE[{slot}]", faces_by_slot[slot], 24 * (index + 1)))
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


def _copy_element(
        element: dict[str, Any], group: str, faces: dict[str, dict[str, Any]], offset_x: int) -> dict[str, Any]:
    result = copy.deepcopy(element)
    original_uuid = str(element.get("uuid", element.get("name", "element")))
    result["uuid"] = _uuid(f"element:{group}:{original_uuid}")
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


def _parse_size(value: str) -> tuple[int, int]:
    parts = value.lower().split("x", 1)
    if len(parts) != 2:
        raise ValueError("--size must be WIDTHxHEIGHT")
    width, height = int(parts[0]), int(parts[1])
    if width <= 0 or height <= 0:
        raise ValueError("--size must be positive")
    return width, height


def _uuid(key: str) -> str:
    return str(uuid.uuid5(UUID_NAMESPACE, key))


def _json(data: dict[str, Any]) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, separators=(",", ": ")) + "\n"
