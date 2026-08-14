from __future__ import annotations

import base64
import json
import uuid
from dataclasses import dataclass
from pathlib import Path

from casted_bb.commands import main


UUID_NAMESPACE = uuid.UUID("d79bf190-8954-43d4-99e5-3c17c2d43bb6")


@dataclass(frozen=True)
class ExampleOutputs:
    authoring_model: Path
    preview_model: Path
    target_json: Path
    preview_png: Path


def generate_example(project_root: Path, examples: Path, build: Path) -> ExampleOutputs:
    spider_png = project_root / "src/test/resources/fixtures/create_biotech/spider_assembly_table/spider.png"
    andesite_png = project_root / "src/test/resources/fixtures/create/andesite_casing.png"
    if not spider_png.is_file() or not andesite_png.is_file():
        raise FileNotFoundError("real spider and andesite test fixtures are required")
    examples.mkdir(parents=True, exist_ok=True)
    build.mkdir(parents=True, exist_ok=True)
    base_model = build / "spider_base.json"
    authoring = examples / "spider_body.bbmodel"
    preview_model = examples / "andesite_spider_assembly_table.bbmodel"
    target_json = build / "spider_body_andesite.json"
    preview_png = build / "andesite_spider_blockbench.png"

    base_model.write_text(_json(_spider_model(spider_png)), encoding="utf-8")
    code = main([
        "init", "--model", str(base_model),
        "--target", "create_biotech:spider_assembly_table/spider",
        "--size", "64x32", "--slots", "all", "--source-grid", "all=16x16",
        "--output", str(authoring),
    ])
    if code != 0:
        raise RuntimeError("failed to initialize spider authoring project")
    _embed_source_texture(authoring, andesite_png)
    code = main([
        "export", str(authoring), "--target-json", str(target_json),
        "--preview", str(preview_png), "--source", f"all={andesite_png}",
        "--blockbench-output", str(preview_model),
    ])
    if code != 0:
        raise RuntimeError("failed to export spider material preview")
    return ExampleOutputs(authoring, preview_model, target_json, preview_png)


def _spider_model(texture: Path) -> dict:
    cubes = [
        _cube("head", (-4, 11, -11), (4, 19, -3), (0, 15, -3), (32, 4), (8, 8, 8)),
        _cube("body_front", (-3, 12, -3), (3, 18, 3), (0, 15, 0), (0, 0), (6, 6, 6)),
        _cube("body_rear", (-5, 11, 3), (5, 19, 15), (0, 15, 9), (0, 12), (10, 8, 12),
              south_extension=2),
        _leg("right_hind_leg", -4, 2, -1, (0, 45, -45)),
        _leg("left_hind_leg", 4, 2, 1, (0, -45, 45)),
        _leg("right_middle_hind_leg", -4, 1, -1, (0, 22.5, -33.333333)),
        _leg("left_middle_hind_leg", 4, 1, 1, (0, -22.5, 33.333333)),
        _leg("right_middle_front_leg", -4, 0, -1, (0, -22.5, -33.333333)),
        _leg("left_middle_front_leg", 4, 0, 1, (0, 22.5, 33.333333)),
        _leg("right_front_leg", -4, -1, -1, (0, -45, -45)),
        _leg("left_front_leg", 4, -1, 1, (0, 45, 45)),
    ]
    encoded = base64.b64encode(texture.read_bytes()).decode("ascii")
    return {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": "Create Biotech Spider Assembly Table Spider",
        "resolution": {"width": 64, "height": 32},
        "elements": cubes,
        "outliner": [cube["uuid"] for cube in cubes],
        "textures": [{
            "name": "all.png", "id": "0", "width": 64, "height": 32,
            "uv_width": 64, "uv_height": 32, "particle": False,
            "use_as_default": True, "visible": True, "internal": True, "saved": True,
            "source": "data:image/png;base64," + encoded,
        }],
    }


def _leg(name: str, origin_x: int, origin_z: int, side: int, rotation: tuple[float, float, float]) -> dict:
    if side < 0:
        start, end = origin_x - 15, origin_x + 1
    else:
        start, end = origin_x - 1, origin_x + 15
    return _cube(
        name, (start, 14, origin_z - 1), (end, 16, origin_z + 1),
        (origin_x, 15, origin_z), (18, 0), (16, 2, 2), rotation=rotation)


def _cube(
        name: str, start: tuple[float, float, float], end: tuple[float, float, float],
        origin: tuple[float, float, float], texture_offset: tuple[int, int],
        dimensions: tuple[int, int, int], rotation: tuple[float, float, float] = (0, 0, 0),
        south_extension: int = 0) -> dict:
    faces = _cube_faces(*texture_offset, *dimensions)
    if south_extension:
        faces["south"]["uv"][2] += south_extension
    return {
        "name": name,
        "box_uv": False,
        "from": list(start),
        "to": list(end),
        "origin": list(origin),
        "rotation": list(rotation),
        "faces": faces,
        "type": "cube",
        "uuid": str(uuid.uuid5(UUID_NAMESPACE, name)),
    }


def _cube_faces(u: int, v: int, width: int, height: int, depth: int) -> dict:
    rectangles = {
        "west": [u, v + depth, u + depth, v + depth + height],
        "north": [u + depth, v + depth, u + depth + width, v + depth + height],
        "east": [u + depth + width, v + depth, u + depth * 2 + width, v + depth + height],
        "south": [u + depth * 2 + width, v + depth, u + depth * 2 + width * 2, v + depth + height],
        "up": [u + depth, v, u + depth + width, v + depth],
        "down": [u + depth + width, v, u + depth + width * 2, v + depth],
    }
    return {direction: {"uv": uv, "texture": 0} for direction, uv in rectangles.items()}


def _embed_source_texture(model_path: Path, source_path: Path) -> None:
    data = json.loads(model_path.read_text(encoding="utf-8"))
    texture_index = len(data["textures"])
    encoded = base64.b64encode(source_path.read_bytes()).decode("ascii")
    data["textures"].append({
        "name": "andesite_casing.png", "id": str(texture_index),
        "width": 16, "height": 16, "uv_width": 16, "uv_height": 16,
        "particle": False, "use_as_default": False, "visible": True,
        "internal": True, "saved": True,
        "source": "data:image/png;base64," + encoded,
    })
    by_uuid = {element["uuid"]: element for element in data["elements"]}
    group = next(item for item in data["outliner"] if item["name"] == "CM_SOURCE[all]")
    for uuid_value in group["children"]:
        for face in by_uuid[uuid_value]["faces"].values():
            face["texture"] = texture_index
    model_path.write_text(_json(data), encoding="utf-8")


def _json(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, separators=(",", ": ")) + "\n"


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    generate_example(root, root / "examples", root / "build/casted-materials-preview")
