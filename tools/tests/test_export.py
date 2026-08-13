from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from casted_bb_lib.commands import main


FIXTURES = Path(__file__).parent / "fixtures"


def create_source(path: Path, color: tuple[int, int, int, int]) -> None:
    image = Image.new("RGBA", (16, 16), color)
    image.save(path)


def paired_project(tmp_path: Path) -> Path:
    output = tmp_path / "paired.bbmodel"
    assert main([
        "init", "--model", str(FIXTURES / "minimal_model.json"),
        "--target", "test:body", "--size", "64x32",
        "--slots", "side,end", "--output", str(output),
    ]) == 0
    return output


def export(
        model: Path, target: Path, preview: Path, side: Path, end: Path,
        blockbench: Path | None = None) -> int:
    args = [
        "export", str(model), "--target-json", str(target), "--preview", str(preview),
        "--source", f"side={side}", "--source", f"end={end}",
    ]
    if blockbench is not None:
        args.extend(["--blockbench-output", str(blockbench)])
    return main(args)


def test_export_is_deterministic_and_uses_fixed_schema_order(tmp_path: Path) -> None:
    model = paired_project(tmp_path)
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    create_source(side, (172, 124, 75, 255))
    create_source(end, (91, 150, 73, 255))
    targets = [tmp_path / "a.json", tmp_path / "b.json"]

    for index, target in enumerate(targets):
        assert export(model, target, tmp_path / f"preview-{index}.png", side, end) == 0

    assert targets[0].read_bytes() == targets[1].read_bytes()
    raw = targets[0].read_text(encoding="utf-8")
    data = json.loads(raw)
    assert list(data) == ["schema", "size", "source_grids", "regions", "pixels", "computed_cost"]
    assert "timestamp" not in raw
    assert data["source_grids"] == {"end": [16, 16], "side": [16, 16]}
    assert data["regions"] == [
        {
            "source_slot": "side", "source": [0, 0, 8, 8],
            "destination": [0, 0, 8, 8], "transform": "identity",
        },
        {
            "source_slot": "end", "source": [0, 0, 8, 8],
            "destination": [8, 0, 8, 8], "transform": "identity",
        },
    ]
    assert data["computed_cost"] == {"max_pieces_per_source_quad": 1, "additional_quads": 0}


def test_export_preview_round_trip_and_blockbench_output_are_self_contained(tmp_path: Path) -> None:
    model = paired_project(tmp_path)
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    create_source(side, (172, 124, 75, 255))
    create_source(end, (91, 150, 73, 255))
    target, preview = tmp_path / "target.json", tmp_path / "preview.png"
    blockbench = tmp_path / "preview.bbmodel"

    assert export(model, target, preview, side, end, blockbench) == 0

    image = Image.open(preview).convert("RGBA")
    assert image.size == (64, 32)
    assert image.getpixel((2, 2)) == (172, 124, 75, 255)
    assert image.getpixel((10, 2)) == (91, 150, 73, 255)
    assert image.getpixel((40, 20)) == (0, 0, 0, 0)
    project = json.loads(blockbench.read_text(encoding="utf-8"))
    texture = project["textures"][-1]
    assert texture["name"] == "preview.png"
    assert texture["source"].startswith("data:image/png;base64,")
    assert texture["width"] == 64 and texture["height"] == 32
    destination = next(group for group in project["outliner"] if group["name"] == "CM_DEST")
    by_uuid = {element["uuid"]: element for element in project["elements"]}
    assert {
        face["texture"]
        for uuid in destination["children"]
        for face in by_uuid[uuid]["faces"].values()
    } == {len(project["textures"]) - 1}


def test_invalid_project_does_not_overwrite_last_valid_target(tmp_path: Path) -> None:
    model = paired_project(tmp_path)
    data = json.loads(model.read_text(encoding="utf-8"))
    by_uuid = {element["uuid"]: element for element in data["elements"]}
    source_group = next(group for group in data["outliner"] if group["name"] == "CM_SOURCE[side]")
    by_uuid[source_group["children"][0]]["faces"] = {}
    model.write_text(json.dumps(data), encoding="utf-8")
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    create_source(side, (1, 2, 3, 255))
    create_source(end, (4, 5, 6, 255))
    target = tmp_path / "target.json"
    target.write_bytes(b"last-valid-target")

    assert export(model, target, tmp_path / "preview.png", side, end) == 2

    assert target.read_bytes() == b"last-valid-target"


def test_preview_retextures_destination_elements_nested_in_child_groups(tmp_path: Path) -> None:
    model = paired_project(tmp_path)
    data = json.loads(model.read_text(encoding="utf-8"))
    destination = next(group for group in data["outliner"] if group["name"] == "CM_DEST")
    nested = {
        "name": "nested",
        "uuid": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "children": destination["children"],
    }
    destination["children"] = [nested]
    model.write_text(json.dumps(data), encoding="utf-8")
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    create_source(side, (172, 124, 75, 255))
    create_source(end, (91, 150, 73, 255))
    blockbench = tmp_path / "preview.bbmodel"

    assert export(
        model, tmp_path / "target.json", tmp_path / "preview.png", side, end, blockbench) == 0

    project = json.loads(blockbench.read_text(encoding="utf-8"))
    texture_index = len(project["textures"]) - 1
    by_uuid = {element["uuid"]: element for element in project["elements"]}
    assert {
        face["texture"]
        for uuid in nested["children"]
        for face in by_uuid[uuid]["faces"].values()
    } == {texture_index}


def test_export_rejects_undeclared_source_slot(tmp_path: Path) -> None:
    model = paired_project(tmp_path)
    side, end, typo = tmp_path / "side.png", tmp_path / "end.png", tmp_path / "typo.png"
    for path in (side, end, typo):
        create_source(path, (1, 2, 3, 255))

    code = main([
        "export", str(model), "--target-json", str(tmp_path / "target.json"),
        "--preview", str(tmp_path / "preview.png"),
        "--source", f"side={side}", "--source", f"end={end}", "--source", f"typo={typo}",
    ])

    assert code == 2
