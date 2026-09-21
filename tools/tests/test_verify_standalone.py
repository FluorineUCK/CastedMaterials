"""Behavior tests for the release-JAR boundary verifier."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import subprocess
import sys
from zipfile import ZipFile

import pytest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/verify_standalone.py"

REQUIRED_CLASSES = [
    "com/fluorineuck/castedmaterials/CastedMaterials.class",
    "com/fluorineuck/castedmaterials/CastedMaterialsApi.class",
    "com/fluorineuck/castedmaterials/CastedMaterialState.class",
    "com/fluorineuck/castedmaterials/palette/MaterialPalette.class",
    "com/fluorineuck/castedmaterials/mapping/UvPlanCompiler.class",
    "com/fluorineuck/castedmaterials/client/CastedMaterialsClient.class",
    "com/fluorineuck/castedmaterials/client/CastedModelHandle.class",
    "com/fluorineuck/castedmaterials/client/ComposedTextureCache.class",
    "com/fluorineuck/castedmaterials/client/CreateCasingTextures.class",
]

SOURCE_LICENSE = b'''MIT License

Copyright (c) Nobodiiiii

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
'''

MODS_TOML = b'''modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "casted_materials"
version = "0.1.0"
displayName = "Casted Materials"

[[dependencies.casted_materials]]
modId = "minecraft"
type = "required"
side = "BOTH"

[[dependencies.casted_materials]]
modId = "create"
type = "optional"
side = "CLIENT"
'''


def write_jar(path: Path, extra: dict[str, bytes] | None = None,
              omitted: set[str] | None = None) -> Path:
    entries = {
        "META-INF/neoforge.mods.toml": MODS_TOML,
        "META-INF/LICENSE_casted_materials.txt": b"MIT License\n",
        "META-INF/LICENSE_Nobodiiiii.txt": SOURCE_LICENSE,
        **{name: b"class" for name in REQUIRED_CLASSES},
    }
    entries.update(extra or {})
    for name in omitted or set():
        entries.pop(name, None)
    with ZipFile(path, "w") as jar:
        for name, content in entries.items():
            jar.writestr(name, content)
    return path


def load_verifier():
    spec = importlib.util.spec_from_file_location("verify_standalone", SCRIPT)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot import {SCRIPT}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def test_accepts_one_standalone_mod_with_optional_client_create(tmp_path):
    verifier = load_verifier()
    result = verifier.verify_jar(write_jar(tmp_path / "valid.jar"))
    assert result.mod_id == "casted_materials"
    assert result.entry_count == len(REQUIRED_CLASSES) + 3


@pytest.mark.parametrize("forbidden", [
    "com/nobodiiiii/createbiotech/Host.class",
    "com/simibubi/create/Create.class",
    "META-INF/jarjar/metadata.json",
    "assets/create_biotech/lang/en_us.json",
    "tools/material_mapping/inference.py",
    "fixtures/SpiderTestModel.class",
    "com/fluorineuck/castedmaterials/client/atlas/DynamicAtlasManager.class",
    "com/fluorineuck/castedmaterials/client/cache/RenderRepository.class",
    "com/fluorineuck/castedmaterials/client/render/CastedTextureHandle.class",
    "nested/dependency.jar",
    "model/example.bbmodel",
])
def test_rejects_non_standalone_or_development_entries(tmp_path, forbidden):
    verifier = load_verifier()
    jar = write_jar(tmp_path / "forbidden.jar", {forbidden: b"forbidden"})
    with pytest.raises(verifier.VerificationError, match="forbidden"):
        verifier.verify_jar(jar)


def test_reports_all_missing_release_entries(tmp_path):
    verifier = load_verifier()
    jar = write_jar(tmp_path / "missing.jar", omitted={
        "META-INF/LICENSE_casted_materials.txt",
        "com/fluorineuck/castedmaterials/client/ComposedTextureCache.class",
    })
    with pytest.raises(verifier.VerificationError) as raised:
        verifier.verify_jar(jar)
    message = str(raised.value)
    assert "META-INF/LICENSE_casted_materials.txt" in message
    assert "ComposedTextureCache.class" in message


def test_rejects_missing_optional_create_bridge_class(tmp_path):
    verifier = load_verifier()
    entry = "com/fluorineuck/castedmaterials/client/CreateCasingTextures.class"
    jar = write_jar(tmp_path / "missing-bridge.jar", omitted={entry})
    with pytest.raises(verifier.VerificationError, match="CreateCasingTextures.class"):
        verifier.verify_jar(jar)


def test_rejects_missing_migrated_source_license(tmp_path):
    verifier = load_verifier()
    jar = write_jar(tmp_path / "missing-source-license.jar", omitted={
        "META-INF/LICENSE_Nobodiiiii.txt",
    })
    with pytest.raises(verifier.VerificationError, match="LICENSE_Nobodiiiii.txt"):
        verifier.verify_jar(jar)


@pytest.mark.parametrize("metadata, expected", [
    (MODS_TOML.replace(b'modId = "casted_materials"', b'modId = "host_mod"', 1),
     "exactly one casted_materials mod"),
    (MODS_TOML.replace(b'[[dependencies.casted_materials]]\nmodId = "create"\n'
                       b'type = "optional"\nside = "CLIENT"',
                       b'[[dependencies.casted_materials]]\nmodId = "create"\n'
                       b'type = "required"\nside = "BOTH"'),
     "optional CLIENT dependency"),
    (MODS_TOML + b'\n[[mods]]\nmodId = "second_mod"\nversion = "1"\n',
     "exactly one [[mods]] table"),
    (MODS_TOML.replace(b'0.1.0', b'${mod_version}'), "unexpanded placeholder"),
])
def test_rejects_wrong_or_unexpanded_mod_metadata(tmp_path, metadata, expected):
    verifier = load_verifier()
    jar = write_jar(tmp_path / "metadata.jar", {"META-INF/neoforge.mods.toml": metadata})
    with pytest.raises(verifier.VerificationError, match=re.escape(expected)):
        verifier.verify_jar(jar)


def test_optimized_python_cannot_disable_validation(tmp_path):
    jar = write_jar(tmp_path / "optimized.jar", {
        "com/simibubi/create/Create.class": b"must not be bundled",
    })
    result = subprocess.run(
        [sys.executable, "-O", "-B", str(SCRIPT), str(jar)],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    assert result.returncode != 0
    assert "com/simibubi/create/Create.class" in result.stderr
