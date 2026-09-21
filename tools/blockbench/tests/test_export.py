from __future__ import annotations

import json
from pathlib import Path

import pytest
from PIL import Image

from casted_bb import commands
from casted_bb.commands import main
from tests.helpers import coordinate_color, coordinate_image


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
    coordinate_image(16, 16, 0).save(side)
    coordinate_image(16, 16, 79).save(end)
    targets = [tmp_path / "a.json", tmp_path / "b.json"]

    for index, target in enumerate(targets):
        assert export(model, target, tmp_path / f"preview-{index}.png", side, end) == 0

    assert targets[0].read_bytes() == targets[1].read_bytes()
    assert (tmp_path / "preview-0.png").read_bytes() == (tmp_path / "preview-1.png").read_bytes()
    raw = targets[0].read_text(encoding="utf-8")
    data = json.loads(raw)
    assert list(data) == ["schema", "size", "source_grids", "regions", "pixels", "computed_cost"]
    assert "timestamp" not in raw
    assert str(side) not in raw and str(end) not in raw
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


@pytest.mark.parametrize("corruption", ["red", "green", "blue", "alpha", "transparent_rgb", "mode", "size"])
def test_round_trip_mismatch_preserves_all_previous_outputs(
        tmp_path: Path, monkeypatch: pytest.MonkeyPatch,
        capsys: pytest.CaptureFixture[str], corruption: str) -> None:
    model = paired_project(tmp_path)
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    create_source(side, (10, 20, 30, 255))
    create_source(end, (40, 50, 60, 255))
    target, preview, blockbench = (tmp_path / name for name in ("target.json", "preview.png", "preview.bbmodel"))
    assert export(model, target, preview, side, end, blockbench) == 0
    previous = {path: path.read_bytes() for path in (target, preview, blockbench)}
    # Make every prospective output different, so publishing before a late error
    # cannot accidentally pass the preservation check by writing identical bytes.
    data = json.loads(model.read_text(encoding="utf-8"))
    group = next(group for group in data["outliner"] if group["name"] == "CM_SOURCE[side]")
    source_element = next(item for item in data["elements"] if item["uuid"] == group["children"][0])
    source_element["faces"]["north"]["uv"] = [1, 2, 9, 10]
    model.write_text(json.dumps(data), encoding="utf-8")
    create_source(side, (110, 120, 130, 255))
    render = commands.render_definition

    def corrupt_round_trip(definition, sources):
        # Exercise real export/publication, injecting only a defective round-trip renderer result.
        image = render(definition, sources)
        if corruption == "mode":
            return image.convert("RGB")
        if corruption == "size":
            return image.crop((0, 0, image.width - 1, image.height))
        position = (40, 20) if corruption == "transparent_rgb" else (2, 2)
        rgba = list(image.getpixel(position))
        channel = {"red": 0, "green": 1, "blue": 2, "alpha": 3, "transparent_rgb": 0}[corruption]
        rgba[channel] ^= 1
        image.putpixel(position, tuple(rgba))
        return image

    monkeypatch.setattr(commands, "render_definition", corrupt_round_trip)
    assert export(model, target, preview, side, end, blockbench) == 2
    assert "preview round-trip" in capsys.readouterr().err
    assert {path: path.read_bytes() for path in previous} == previous
    assert not list(tmp_path.glob("*.tmp"))


@pytest.mark.parametrize("axis", ["width", "height"])
@pytest.mark.parametrize("value", [257, 512, 0, -1])
def test_export_rejects_invalid_target_size_without_publishing(
        tmp_path: Path, capsys: pytest.CaptureFixture[str], axis: str, value: int) -> None:
    model = paired_project(tmp_path)
    data = json.loads(model.read_text(encoding="utf-8"))
    data["resolution"][axis] = value
    model.write_text(json.dumps(data), encoding="utf-8")
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    create_source(side, (1, 2, 3, 255))
    create_source(end, (4, 5, 6, 255))
    target, preview = tmp_path / "target.json", tmp_path / "preview.png"
    target.write_bytes(b"previous target")
    preview.write_bytes(b"previous preview")

    assert export(model, target, preview, side, end) == 2
    error = capsys.readouterr().err
    size = data["resolution"]
    assert f"{size['width']}x{size['height']}" in error
    assert "1" in error and "256" in error
    assert target.read_bytes() == b"previous target"
    assert preview.read_bytes() == b"previous preview"


@pytest.mark.parametrize("size", [1, 256])
def test_target_size_boundaries_allow_larger_source_grids_and_images(tmp_path: Path, size: int) -> None:
    data = json.loads((FIXTURES / "minimal_model.json").read_text(encoding="utf-8"))
    data["elements"] = [data["elements"][0]]
    data["elements"][0]["faces"] = {"north": {"uv": [0, 0, 1, 1], "texture": 0}}
    source_model = tmp_path / "input.bbmodel"
    source_model.write_text(json.dumps(data), encoding="utf-8")
    model = tmp_path / "paired.bbmodel"
    assert main([
        "init", "--model", str(source_model), "--target", "test:boundary",
        "--size", f"{size}x{size}", "--slots", "all", "--source-grid", "all=512x512",
        "--output", str(model),
    ]) == 0
    assert main(["validate", str(model)]) == 0
    source = tmp_path / "source.png"
    coordinate_image(512, 512).save(source)
    target, preview = tmp_path / "target.json", tmp_path / "preview.png"
    assert main([
        "export", str(model), "--source", f"all={source}",
        "--target-json", str(target), "--preview", str(preview),
    ]) == 0
    definition = json.loads(target.read_text(encoding="utf-8"))
    assert definition["size"] == [size, size]
    assert definition["source_grids"] == {"all": [512, 512]}
    with Image.open(preview) as image:
        assert image.size == (size, size)
        assert image.getpixel((0, 0)) == coordinate_color(0, 0)


def test_fixed_faces_and_textures_are_preserved_only_in_blockbench_project(tmp_path: Path) -> None:
    model = paired_project(tmp_path)
    data = json.loads(model.read_text(encoding="utf-8"))
    fixed = {"uuid": "fixed-element", "name": "fixed-eye", "type": "cube",
             "from": [0, 0, 0], "to": [2, 2, 2],
             "faces": {"north": {"uv": [12, 12, 14, 14], "texture": 0}}}
    data["elements"].append(fixed)
    next(group for group in data["outliner"] if group["name"] == "CM_FIXED")["children"] = [fixed["uuid"]]
    model.write_text(json.dumps(data), encoding="utf-8")
    side, end = tmp_path / "side.png", tmp_path / "end.png"
    coordinate_image(16, 16).save(side)
    coordinate_image(16, 16, 79).save(end)
    target, preview, blockbench = (tmp_path / name for name in ("target.json", "preview.png", "preview.bbmodel"))

    assert export(model, target, preview, side, end, blockbench) == 0
    definition = json.loads(target.read_text(encoding="utf-8"))
    assert "overlay" not in definition
    assert len(definition["regions"]) == 2 and definition["pixels"] == []
    with Image.open(preview) as image:
        assert image.getpixel((12, 12)) == (0, 0, 0, 0)
    project = json.loads(blockbench.read_text(encoding="utf-8"))
    assert next(item for item in project["elements"] if item["uuid"] == fixed["uuid"]) == fixed
    assert project["textures"][:-1] == data["textures"]
    assert next(group for group in project["outliner"] if group["name"] == "CM_FIXED")["visibility"] is True
