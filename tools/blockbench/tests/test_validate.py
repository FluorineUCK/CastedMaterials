from __future__ import annotations

import copy
import json
from pathlib import Path

import pytest

from casted_bb.commands import main


FIXTURES = Path(__file__).parent / "fixtures"


def initialized(tmp_path: Path) -> dict:
    output = tmp_path / "valid.bbmodel"
    assert main([
        "init", "--model", str(FIXTURES / "minimal_model.json"),
        "--target", "test:body", "--size", "64x32",
        "--slots", "side,end", "--output", str(output),
    ]) == 0
    return json.loads(output.read_text(encoding="utf-8"))


def element(data: dict, group_name: str, name: str) -> dict:
    by_uuid = {item["uuid"]: item for item in data["elements"]}
    group = next(item for item in data["outliner"] if item["name"] == group_name)
    return next(by_uuid[uuid] for uuid in group["children"] if by_uuid[uuid]["name"] == name)


def duplicate_element(data: dict, group_name: str, name: str) -> None:
    duplicate = copy.deepcopy(element(data, group_name, name))
    duplicate["uuid"] = "ffffffff-ffff-ffff-ffff-ffffffffffff"
    data["elements"].append(duplicate)
    group = next(item for item in data["outliner"] if item["name"] == group_name)
    group["children"].append(duplicate["uuid"])


def validate_mutation(tmp_path: Path, capsys: pytest.CaptureFixture[str], mutation) -> str:
    data = initialized(tmp_path)
    mutation(data)
    path = tmp_path / "mutated.bbmodel"
    path.write_text(json.dumps(data), encoding="utf-8")

    assert main(["validate", str(path)]) == 2
    return capsys.readouterr().err


def test_validate_accepts_complete_integer_paired_faces(tmp_path: Path) -> None:
    path = tmp_path / "valid.bbmodel"
    data = initialized(tmp_path)
    path.write_text(json.dumps(data), encoding="utf-8")

    assert main(["validate", str(path)]) == 0


@pytest.mark.parametrize(
    ("mutation", "location", "message"),
    [
        (
            lambda data: element(data, "CM_SOURCE[side]", "body").update(name="renamed"),
            "CM_DEST/body/north", "matching source face",
        ),
        (
            lambda data: duplicate_element(data, "CM_SOURCE[side]", "body"),
            "CM_SOURCE[side]/body/<none>", "duplicate stable element name",
        ),
        (
            lambda data: element(data, "CM_SOURCE[side]", "body")["faces"].pop("north"),
            "CM_DEST/body/north", "matching source face",
        ),
        (
            lambda data: element(data, "CM_SOURCE[side]", "body")["faces"]["north"].update(uv=[0.5, 0, 8, 8]),
            "CM_SOURCE[side]/body/north", "integer UV",
        ),
        (
            lambda data: element(data, "CM_SOURCE[side]", "body")["faces"]["north"].update(uv=[-1, 0, 7, 8]),
            "CM_SOURCE[side]/body/north", "outside 16x16",
        ),
        (
            lambda data: element(data, "CM_SOURCE[side]", "body")["faces"]["north"].update(uv=[9, 0, 17, 8]),
            "CM_SOURCE[side]/body/north", "outside 16x16",
        ),
        (
            lambda data: element(data, "CM_SOURCE[side]", "body")["faces"]["north"].update(uv=[0, 0, 4, 8]),
            "CM_SOURCE[side]/body/north", "dimensions",
        ),
        (
            lambda data: element(data, "CM_DEST", "leg")["faces"]["north"].update(uv=[0, 0, 8, 8]),
            "CM_DEST/leg/north", "overlaps destination",
        ),
        (
            lambda data: element(data, "CM_SOURCE[side]", "body")["faces"]["north"].update(rotation=45),
            "CM_SOURCE[side]/body/north", "rotation",
        ),
        (
            lambda data: [item.update(faces={}) for item in data["elements"]],
            "CM_DEST/<none>/<none>", "empty mapping",
        ),
    ],
)
def test_validate_rejects_invalid_pairing(
        tmp_path: Path,
        capsys: pytest.CaptureFixture[str],
        mutation,
        location: str,
        message: str) -> None:
    error = validate_mutation(tmp_path, capsys, mutation)

    assert location in error
    assert message in error
