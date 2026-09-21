#!/usr/bin/env python3
"""Verify that a release JAR contains only the standalone Casted Materials mod."""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import re
import sys
from zipfile import BadZipFile, ZipFile


MOD_METADATA = "META-INF/neoforge.mods.toml"
LICENSE_ENTRY = "META-INF/LICENSE_casted_materials.txt"
MIGRATED_SOURCE_LICENSE_ENTRY = "META-INF/LICENSE_Nobodiiiii.txt"

REQUIRED_ENTRIES = {
    MOD_METADATA,
    LICENSE_ENTRY,
    MIGRATED_SOURCE_LICENSE_ENTRY,
    "com/fluorineuck/castedmaterials/CastedMaterials.class",
    "com/fluorineuck/castedmaterials/CastedMaterialsApi.class",
    "com/fluorineuck/castedmaterials/CastedMaterialState.class",
    "com/fluorineuck/castedmaterials/palette/MaterialPalette.class",
    "com/fluorineuck/castedmaterials/mapping/UvPlanCompiler.class",
    "com/fluorineuck/castedmaterials/client/CastedMaterialsClient.class",
    "com/fluorineuck/castedmaterials/client/CastedModelHandle.class",
    "com/fluorineuck/castedmaterials/client/ComposedTextureCache.class",
    "com/fluorineuck/castedmaterials/client/CreateCasingTextures.class",
}

FORBIDDEN_PREFIXES = (
    "com/nobodiiiii/",
    "com/simibubi/",
    "META-INF/jarjar/",
    "assets/create_biotech/",
    "tools/",
    "fixtures/",
    "com/fluorineuck/castedmaterials/client/atlas/",
    "com/fluorineuck/castedmaterials/client/cache/",
    "com/fluorineuck/castedmaterials/client/render/CastedTextureHandle",
)

FORBIDDEN_SUFFIXES = (".py", ".pyc", ".bbmodel", ".jar")
FORBIDDEN_EXACT_OR_INNER_CLASS_PREFIXES = (
    "com/fluorineuck/castedmaterials/client/ClientRuntime",
    "com/fluorineuck/castedmaterials/client/DefaultClientRuntime",
)

TABLE = re.compile(r"^\s*\[\[\s*([^]]+?)\s*]]\s*(?:#.*)?$")
STRING_ASSIGNMENT = re.compile(r'^\s*([A-Za-z0-9_.-]+)\s*=\s*"([^"\r\n]*)"\s*(?:#.*)?$')


class VerificationError(ValueError):
    """Raised when a release artifact violates one or more packaging rules."""


@dataclass(frozen=True)
class VerificationResult:
    jar: Path
    mod_id: str
    entry_count: int


def _tables(text: str) -> list[tuple[str, dict[str, str]]]:
    tables: list[tuple[str, dict[str, str]]] = []
    current: tuple[str, dict[str, str]] | None = None
    for line in text.splitlines():
        header = TABLE.match(line)
        if header:
            current = (header.group(1).strip(), {})
            tables.append(current)
            continue
        assignment = STRING_ASSIGNMENT.match(line)
        if current is not None and assignment:
            current[1][assignment.group(1)] = assignment.group(2)
    return tables


def _metadata_errors(text: str) -> list[str]:
    errors: list[str] = []
    if "${" in text:
        errors.append("mod metadata contains an unexpanded placeholder")

    tables = _tables(text)
    mods = [values for name, values in tables if name == "mods"]
    if len(mods) != 1:
        errors.append(f"expected exactly one [[mods]] table, found {len(mods)}")
    elif mods[0].get("modId") != "casted_materials":
        errors.append("expected exactly one casted_materials mod")

    dependencies = [values for name, values in tables
                    if name == "dependencies.casted_materials"]
    create = [values for values in dependencies if values.get("modId") == "create"]
    if len(create) != 1 or create[0].get("type") != "optional" or create[0].get("side") != "CLIENT":
        errors.append("Create must be exactly one optional CLIENT dependency")
    return errors


def _entry_errors(names: list[str]) -> list[str]:
    errors: list[str] = []
    duplicates = sorted(name for name, count in Counter(names).items() if count > 1)
    if duplicates:
        errors.append("duplicate JAR entries: " + ", ".join(duplicates))

    unsafe = sorted(name for name in names if (
        "\\" in name
        or name.startswith("/")
        or ".." in PurePosixPath(name).parts
    ))
    if unsafe:
        errors.append("unsafe JAR entry paths: " + ", ".join(unsafe))

    forbidden = sorted(name for name in names if (
        name.startswith(FORBIDDEN_PREFIXES)
        or name.startswith(FORBIDDEN_EXACT_OR_INNER_CLASS_PREFIXES)
        or name.lower().endswith(FORBIDDEN_SUFFIXES)
        or "/fixtures/" in name
        or name.endswith("Test.class")
    ))
    if forbidden:
        errors.append("forbidden JAR entries: " + ", ".join(forbidden))

    missing = sorted(REQUIRED_ENTRIES.difference(names))
    if missing:
        errors.append("missing required JAR entries: " + ", ".join(missing))
    return errors


def verify_jar(jar_path: str | Path) -> VerificationResult:
    path = Path(jar_path)
    if not path.is_file():
        raise VerificationError(f"JAR does not exist or is not a file: {path}")

    try:
        with ZipFile(path) as jar:
            names = jar.namelist()
            errors = _entry_errors(names)
            corrupt = jar.testzip()
            if corrupt is not None:
                errors.append(f"corrupt JAR entry: {corrupt}")

            if MOD_METADATA in names:
                try:
                    metadata = jar.read(MOD_METADATA).decode("utf-8-sig", errors="strict")
                except UnicodeDecodeError as error:
                    errors.append(f"mod metadata is not valid UTF-8: {error}")
                else:
                    errors.extend(_metadata_errors(metadata))
            if LICENSE_ENTRY in names and not jar.read(LICENSE_ENTRY).strip():
                errors.append(f"license entry is empty: {LICENSE_ENTRY}")
            if MIGRATED_SOURCE_LICENSE_ENTRY in names:
                source_license = jar.read(MIGRATED_SOURCE_LICENSE_ENTRY)
                required_notice = (
                    b"MIT License",
                    b"Copyright (c) Nobodiiiii",
                    b"Permission is hereby granted",
                    b'THE SOFTWARE IS PROVIDED "AS IS"',
                )
                missing_notice = [part.decode("ascii") for part in required_notice
                                  if part not in source_license]
                if missing_notice:
                    errors.append("migrated source license is incomplete; missing: "
                                  + ", ".join(missing_notice))
    except (BadZipFile, OSError) as error:
        raise VerificationError(f"cannot read JAR {path}: {error}") from error

    if errors:
        raise VerificationError("standalone JAR verification failed:\n- " + "\n- ".join(errors))
    return VerificationResult(path.resolve(), "casted_materials", len(names))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="release JAR to inspect")
    args = parser.parse_args(argv)
    try:
        result = verify_jar(args.jar)
    except VerificationError as error:
        print(error, file=sys.stderr)
        return 1
    print(f"verified standalone {result.mod_id} JAR: {result.jar} ({result.entry_count} entries)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
