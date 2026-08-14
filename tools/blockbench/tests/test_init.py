from __future__ import annotations

import json
from pathlib import Path

import pytest

from casted_bb.commands import main
from tests.helpers import group_names, stable_face_pairs


FIXTURES = Path(__file__).parent / "fixtures"


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
