from __future__ import annotations

import copy
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True, order=True)
class ElementFaceKey:
    element: str
    face: str


@dataclass
class BbModel:
    data: dict[str, Any]
    path: Path | None = None

    @classmethod
    def load(cls, path: str | Path) -> "BbModel":
        source = Path(path)
        data = json.loads(source.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise ValueError("Blockbench project root must be an object")
        return cls(data, source)

    def clone_data(self) -> dict[str, Any]:
        return copy.deepcopy(self.data)

    @property
    def resolution(self) -> tuple[int, int]:
        value = self.data.get("resolution", {})
        return int(value.get("width", 0)), int(value.get("height", 0))

    @property
    def elements_by_uuid(self) -> dict[str, dict[str, Any]]:
        return {
            element["uuid"]: element
            for element in self.data.get("elements", [])
            if isinstance(element, dict) and isinstance(element.get("uuid"), str)
        }

    def group(self, name: str) -> dict[str, Any] | None:
        for entry in self.data.get("outliner", []):
            if isinstance(entry, dict) and entry.get("name") == name:
                return entry
        return None

    def group_elements(self, name: str) -> list[dict[str, Any]]:
        group = self.group(name)
        if group is None:
            return []
        by_uuid = self.elements_by_uuid
        return [by_uuid[uuid] for uuid in _child_uuids(group.get("children", [])) if uuid in by_uuid]


def _child_uuids(children: list[Any]) -> list[str]:
    result: list[str] = []
    for child in children:
        if isinstance(child, str):
            result.append(child)
        elif isinstance(child, dict):
            result.extend(_child_uuids(child.get("children", [])))
    return result
