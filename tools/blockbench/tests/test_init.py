from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys

import pytest

from casted_bb.commands import main
from tests.helpers import group_names, stable_face_pairs


FIXTURES = Path(__file__).parent / "fixtures"


def test_cli_direct_script_runs_from_arbitrary_working_directory(tmp_path: Path) -> None:
    script = Path(__file__).resolve().parents[1] / "cli.py"
    result = subprocess.run(
        [sys.executable, "-B", str(script), "--help"],
        cwd=tmp_path, capture_output=True, text=True,
    )

    assert result.returncode == 0, result.stdout + result.stderr
    assert "init" in result.stdout and "export" in result.stdout


def test_help_uses_canonical_cli_filename(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit) as exit_info:
        main(["--help"])

    assert exit_info.value.code == 0
    help_text = capsys.readouterr().out
    assert "usage: cli.py" in help_text
    assert "casted_bb.py" not in help_text


def test_init_builds_standard_paired_uv_blockbench_project(tmp_path: Path) -> None:
    output = tmp_path / "paired.bbmodel"

    code = main([
        "init",
        "--model", str(FIXTURES / "minimal_model.json"),
        "--target", "test:body",
        "--size", "64x32",
        "--slots", "side,end",
        "--output", str(output),
    ])

    assert code == 0
    data = json.loads(output.read_text(encoding="utf-8"))
    assert data["meta"]["model_format"] == "java_block"
    assert data["resolution"] == {"width": 64, "height": 32}
    assert group_names(data) == ["CM_DEST", "CM_SOURCE[side]", "CM_SOURCE[end]", "CM_FIXED"]
    assert stable_face_pairs(data, "side")
    assert stable_face_pairs(data, "end")
    assert data["casted_materials"] == {
        "schema": 1,
        "target": "test:body",
        "slots": ["side", "end"],
        "source_grids": {"side": [16, 16], "end": [16, 16]},
    }


def test_init_is_byte_deterministic(tmp_path: Path) -> None:
    outputs = [tmp_path / "a.bbmodel", tmp_path / "b.bbmodel"]
    for output in outputs:
        assert main([
            "init", "--model", str(FIXTURES / "minimal_model.json"),
            "--target", "test:body", "--size", "64x32",
            "--slots", "side,end", "--output", str(output),
        ]) == 0

    assert outputs[0].read_bytes() == outputs[1].read_bytes()


def test_init_uses_stable_names_when_input_uuids_are_duplicated(tmp_path: Path) -> None:
    source = json.loads((FIXTURES / "minimal_model.json").read_text(encoding="utf-8"))
    source["elements"][1]["uuid"] = source["elements"][0]["uuid"]
    model = tmp_path / "duplicate-uuid.json"
    model.write_text(json.dumps(source), encoding="utf-8")
    output = tmp_path / "paired.bbmodel"

    assert main([
        "init", "--model", str(model), "--target", "test:body",
        "--size", "64x32", "--slots", "side,end", "--output", str(output),
    ]) == 0
    data = json.loads(output.read_text(encoding="utf-8"))
    destination = next(group for group in data["outliner"] if group["name"] == "CM_DEST")
    assert len(destination["children"]) == len(set(destination["children"])) == 2


@pytest.mark.parametrize("size", ["257x32", "32x257", "512x32", "32x512", "0x32", "32x0", "-1x32", "32x-1"])
def test_init_rejects_invalid_target_size_before_writing(
        tmp_path: Path, capsys: pytest.CaptureFixture[str], size: str) -> None:
    output = tmp_path / "paired.bbmodel"
    output.write_bytes(b"previous model")

    assert main([
        "init", "--model", str(FIXTURES / "minimal_model.json"),
        "--target", "test:body", f"--size={size}",
        "--slots", "side,end", "--output", str(output),
    ]) == 2
    error = capsys.readouterr().err
    assert size in error and "1" in error and "256" in error
    assert output.read_bytes() == b"previous model"


def test_init_keeps_unmatched_texture_faces_in_fixed_group_with_multiple_slots(tmp_path: Path) -> None:
    data = json.loads((FIXTURES / "minimal_model.json").read_text(encoding="utf-8"))
    data["textures"].append({"id": "2", "name": "eyes.png", "uv_width": 16, "uv_height": 16})
    fixed_face = {"uv": [12, 12, 14, 14], "rotation": 180, "texture": 2}
    data["elements"][0]["faces"]["up"] = fixed_face
    model, output = tmp_path / "input.bbmodel", tmp_path / "paired.bbmodel"
    model.write_text(json.dumps(data), encoding="utf-8")

    assert main([
        "init", "--model", str(model), "--target", "test:body", "--size", "64x32",
        "--slots", "side,end", "--output", str(output),
    ]) == 0
    project = json.loads(output.read_text(encoding="utf-8"))
    group = next(group for group in project["outliner"] if group["name"] == "CM_FIXED")
    fixed = next(item for item in project["elements"] if item["uuid"] == group["children"][0])
    assert fixed["faces"] == {"up": fixed_face}
    assert project["textures"] == data["textures"]
