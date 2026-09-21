from __future__ import annotations

import json
from pathlib import Path

import pytest
from PIL import Image

from casted_bb.commands import main
from tests.helpers import coordinate_color, coordinate_image


# Source is the non-square, non-origin tile A B C / D E F at (300,400).
# Expected destination orders are hand-derived, not computed by mapping helpers.
TRANSFORMS = [
    ("identity", [300, 400, 303, 402], 0, ["ABC", "DEF"]),
    ("rotate_90", [300, 400, 303, 402], 90, ["DA", "EB", "FC"]),
    ("rotate_180", [300, 400, 303, 402], 180, ["FED", "CBA"]),
    ("rotate_270", [300, 400, 303, 402], 270, ["CF", "BE", "AD"]),
    ("mirror_x", [303, 400, 300, 402], 0, ["CBA", "FED"]),
    ("mirror_y", [300, 402, 303, 400], 0, ["DEF", "ABC"]),
]
GOLDEN = (Path(__file__).resolve().parents[2]
          / "test-resources/fixtures/definitions/python_export/asymmetric_target.json")


def authoring_project(destination_rotation: int = 0) -> dict:
    elements = []
    dest_ids, source_ids = [], []
    for index, (name, source_uv, rotation, rows) in enumerate(TRANSFORMS):
        x, y = 1 + index * 6, 1
        width, height = len(rows[0]), len(rows)
        for prefix, uv, angle, ids in [
                ("dest", [x, y, x + width, y + height], destination_rotation, dest_ids),
                ("src", source_uv, (rotation + destination_rotation) % 360, source_ids)]:
            uid = f"{prefix}-{name}"
            elements.append({"uuid": uid, "name": name, "type": "cube",
                             "faces": {"north": {"uv": uv, "rotation": angle, "texture": 0}}})
            ids.append(uid)
    for prefix, uv, ids in [("dest", [255, 255, 256, 256], dest_ids),
                            ("src", [511, 511, 512, 512], source_ids)]:
        uid = f"{prefix}-sparse"
        elements.append({"uuid": uid, "name": "sparse", "type": "cube",
                         "faces": {"north": {"uv": uv, "texture": 0}}})
        ids.append(uid)
    return {
        "resolution": {"width": 256, "height": 256},
        "casted_materials": {"schema": 1, "target": "test:asymmetric", "slots": ["all"],
                             "source_grids": {"all": [512, 512]}},
        "elements": elements,
        "outliner": [{"name": "CM_DEST", "children": dest_ids},
                     {"name": "CM_SOURCE[all]", "children": source_ids},
                     {"name": "CM_FIXED", "children": []}],
        "textures": [{"name": "all.png", "id": "0", "width": 512, "height": 512}],
    }


@pytest.mark.parametrize("destination_rotation", [0, 90, 180, 270])
@pytest.mark.parametrize("seed,scale", [(0, 1), (79, 1), (0, 2)])
def test_export_transforms_compression_and_sparse_pixels_match_independent_coordinates(
        tmp_path: Path, destination_rotation: int, seed: int, scale: int) -> None:
    model, source = tmp_path / "paired.bbmodel", tmp_path / "material.png"
    model.write_text(json.dumps(authoring_project(destination_rotation)), encoding="utf-8")
    coordinate_image(512 * scale, 512 * scale, seed).save(source)
    target, preview = tmp_path / "target.json", tmp_path / "preview.png"

    assert main([
        "export", str(model), "--source", f"all={source}",
        "--target-json", str(target), "--preview", str(preview),
    ]) == 0
    # This exact schema fixture is also parsed and composed by the Java integration test.
    assert json.loads(target.read_text(encoding="utf-8")) == json.loads(GOLDEN.read_text(encoding="utf-8"))
    expected = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for index, (_, _, _, rows) in enumerate(TRANSFORMS):
        for y, row in enumerate(rows):
            for x, letter in enumerate(row):
                cell = ord(letter) - ord("A")
                color = coordinate_color((300 + cell % 3) * scale + scale // 2,
                                         (400 + cell // 3) * scale + scale // 2, seed)
                expected.putpixel((1 + index * 6 + x, 1 + y), color)
    expected.putpixel((255, 255), coordinate_color(511 * scale + scale // 2, 511 * scale + scale // 2, seed))
    with Image.open(preview) as image:
        assert image.mode == "RGBA" and image.size == expected.size
        assert image.tobytes() == expected.tobytes()
