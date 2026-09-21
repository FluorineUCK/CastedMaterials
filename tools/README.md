# Authoring and verification tools

Everything under `tools/` is development-time input, code or evidence and is excluded from the production
JAR. Run commands from the repository root with Python 3.10+ and dependencies from `tools/requirements.txt`.

## Choose an entry point

| Goal | Entry point | Blockbench required |
| --- | --- | --- |
| Infer a mapping from sample/source PNGs | `python -B -m tools.material_mapping infer ...` | No |
| Render a flat preview of an existing definition | `python -B -m tools.material_mapping preview ...` | No |
| Create/validate/export a paired-UV project | `python -B tools/blockbench/cli.py init\|validate\|export ...` | The file format is used; the CLI itself is plain Python |

The generic mapping package is independent of `tools.blockbench`. The retained paired workflow imports the
shared sampling, compression and mapping algorithms in `tools.material_mapping`; it does not keep a second
inference implementation.

- [Generic mapping and inference](material_mapping/README.md)
- [Paired Blockbench workflow](blockbench/README.md)
- [Connected-spider fixture provenance](test-resources/fixtures/connected_spider/README.md)
- [Fixture license details](test-resources/FIXTURE_PROVENANCE.md)
- [Explicit test resource pack](resourcepacks/connected_spider/README.md)

## Tests

```powershell
python -m pip install -r tools/requirements.txt
python -B -m pytest -q -p no:cacheprovider tools
```

Write generated JSON, previews and reports under `build/`; do not overwrite authoritative fixtures.
