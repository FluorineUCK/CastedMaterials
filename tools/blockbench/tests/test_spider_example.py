from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

from generate_spider_example import generate_example


PROJECT_ROOT = Path(__file__).resolve().parents[3]


def test_generates_self_contained_andesite_spider_blockbench_preview(tmp_path: Path) -> None:
    examples = tmp_path / "examples"
    build = tmp_path / "build"

    outputs = generate_example(PROJECT_ROOT, examples, build)

    assert outputs.authoring_model == examples / "spider_body.bbmodel"
    assert outputs.preview_model == examples / "andesite_spider_assembly_table.bbmodel"
    authoring = json.loads(outputs.authoring_model.read_text(encoding="utf-8"))
    preview = json.loads(outputs.preview_model.read_text(encoding="utf-8"))
    destination = next(group for group in preview["outliner"] if group["name"] == "CM_DEST")
    assert len(destination["children"]) == 11
    by_uuid = {element["uuid"]: element for element in preview["elements"]}
    assert sum(len(by_uuid[uuid]["faces"]) for uuid in destination["children"]) == 66
    assert preview["textures"][-1]["source"].startswith("data:image/png;base64,")
    assert authoring["casted_materials"]["target"] == "create_biotech:spider_assembly_table/spider"
    image = Image.open(outputs.preview_png).convert("RGBA")
    assert image.size == (64, 32)
    assert sum(pixel[3] != 0 for pixel in image.get_flattened_data()) == 1_344
    spider_path = PROJECT_ROOT / "src/test/resources/fixtures/create_biotech/spider_assembly_table/spider.png"
    spider = Image.open(spider_path).convert("RGBA")
    assert spider.size == (64, 32)
    assert hashlib.sha256(spider_path.read_bytes()).hexdigest() == (
        "93d330a24bda6316db42cd6198120f5a540f591305db671a599ca966888a4704")
    assert [pixel[3] != 0 for pixel in image.get_flattened_data()] == [
        pixel[3] != 0 for pixel in spider.get_flattened_data()]
    andesite_path = PROJECT_ROOT / "src/test/resources/fixtures/create/andesite_casing.png"
    andesite = Image.open(andesite_path).convert("RGBA")
    assert andesite.size == (16, 16)
    assert hashlib.sha256(andesite_path.read_bytes()).hexdigest() == (
        "c7da24aacfb6170ebd570e5a302a6c9d3f3c0b324314e3df79236209667f192a")
    andesite_colors = set(andesite.get_flattened_data())
    assert all(pixel[3] == 0 or pixel in andesite_colors for pixel in image.get_flattened_data())
    definition = json.loads(outputs.target_json.read_text(encoding="utf-8"))
    assert definition["size"] == [64, 32]
    assert definition["source_grids"] == {"all": [16, 16]}
    assert len(definition["regions"]) == 24
    assert definition["pixels"] == []
    assert definition["computed_cost"] == {"max_pieces_per_source_quad": 1, "additional_quads": 0}
