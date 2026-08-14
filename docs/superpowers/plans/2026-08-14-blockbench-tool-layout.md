# Blockbench Development Tool Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move every zero-install Blockbench authoring artifact into one self-contained `tools/blockbench` Module without changing mapping behavior or production JAR contents.

**Architecture:** `tools/blockbench/cli.py` and `generate_spider_example.py` are the only command interfaces. The `casted_bb` package hides model parsing, pairing, compression, preview, and publication implementation; examples, Python tests, and shared Java/Python fixtures live beside it. Gradle adds only `tools/blockbench/test-resources` to the test resource set.

**Tech Stack:** Python 3.11+, pytest 8.3–9.x, Pillow 10.4–12.x, Java 21, Gradle 8.14.3, NeoForge 21.1.234.

## Global Constraints

- Preserve all `.bbmodel`, JSON, PNG, Java runtime, API, network, and mapping behavior.
- Break old development-tool paths intentionally; do not add forwarding scripts.
- Keep the Python workflow zero-install.
- Keep production JAR exclusions for `tools/**`, `.py`, `.bbmodel`, test fixtures, and previews.
- Do not modify or add the untracked `.vscode/` directory.

---

### Task 1: Deepen the Python tool Module under `tools/blockbench`

**Files:**
- Move: `tools/casted_bb.py` → `tools/blockbench/cli.py`
- Move: `tools/generate_spider_example.py` → `tools/blockbench/generate_spider_example.py`
- Move: `tools/requirements.txt` → `tools/blockbench/requirements.txt`
- Move: `tools/casted_bb_lib/__init__.py` → `tools/blockbench/casted_bb/__init__.py`
- Move: `tools/casted_bb_lib/commands.py` → `tools/blockbench/casted_bb/commands.py`
- Move: `tools/casted_bb_lib/model.py` → `tools/blockbench/casted_bb/model.py`
- Move: `tools/casted_bb_lib/pairing.py` → `tools/blockbench/casted_bb/pairing.py`
- Move: `tools/casted_bb_lib/preview.py` → `tools/blockbench/casted_bb/preview.py`
- Move: `tools/casted_bb_lib/compress.py` → `tools/blockbench/casted_bb/compression.py`
- Move: `tools/tests/**` → `tools/blockbench/tests/**`
- Modify: moved Python imports and `tools/blockbench/tests/conftest.py`

**Interfaces:**
- Produces: `python tools/blockbench/cli.py {init,validate,export}`.
- Produces: `python tools/blockbench/generate_spider_example.py`.
- Internal package: `casted_bb`, importable because the entry and test roots add `tools/blockbench` to `sys.path` naturally.

- [ ] **Step 1: Record the green refactor baseline**

```powershell
python -m pytest tools\tests -q
```

Expected: 25 tests pass before any move.

- [ ] **Step 2: Move entry points, package, requirements, and tests with Git-aware moves**

Create `tools/blockbench`, move the declared files, rename `casted_bb_lib` to `casted_bb`, and rename `compress.py` to `compression.py`. Do not copy files or leave duplicate old paths.

- [ ] **Step 3: Update imports and tool-root discovery**

Apply these exact import changes:

```python
# tools/blockbench/cli.py and generate_spider_example.py
from casted_bb.commands import main

# tools/blockbench/casted_bb/commands.py
from .compression import compress_pixels

# tests
from casted_bb.commands import main
from casted_bb.compression import MappedPixel, compress_pixels
from tests.helpers import group_names, stable_face_pairs
```

Keep `tools/blockbench/tests/conftest.py` inserting `Path(__file__).resolve().parents[1]`, which now resolves directly to `tools/blockbench`.

- [ ] **Step 4: Run Python tests from both supported working directories**

```powershell
python -m pytest tools\blockbench\tests -q
Push-Location tools\blockbench
python -m pytest tests -q
Pop-Location
```

Expected: 25 tests pass in each invocation.

- [ ] **Step 5: Commit the Python Module move**

```powershell
git add tools
git commit -m "refactor: group blockbench python tooling"
```

---

### Task 2: Co-locate examples, documentation, and shared fixtures

**Files:**
- Move: `examples/spider_body.bbmodel` → `tools/blockbench/examples/spider_body.bbmodel`
- Move: `examples/andesite_spider_assembly_table.bbmodel` → `tools/blockbench/examples/andesite_spider_assembly_table.bbmodel`
- Move: `docs/blockbench-authoring.md` → `tools/blockbench/README.md`
- Move: `src/test/resources/fixtures/**` → `tools/blockbench/test-resources/fixtures/**`
- Modify: `tools/blockbench/generate_spider_example.py`
- Modify: `tools/blockbench/tests/test_spider_example.py`
- Modify: `tools/blockbench/README.md`
- Modify: root `README.md`
- Modify: `build.gradle`

**Interfaces:**
- Shared resource root: `tools/blockbench/test-resources`, exposed unchanged to Java tests as classpath `/fixtures/**`.
- Example output root: `tools/blockbench/examples`.
- Generated preview root remains project `build/casted-materials-preview`.

- [ ] **Step 1: Move examples, authoring documentation, and shared fixtures**

Use Git-aware moves. Remove the empty root `examples/` and `src/test/resources/` directories after their tracked contents move. Keep Python-only fixtures under `tools/blockbench/tests/fixtures`.

- [ ] **Step 2: Update Gradle test resource locality**

Add the following source-set declaration next to the existing test classpath configuration:

```groovy
sourceSets.test.resources.srcDir('tools/blockbench/test-resources')
```

Do not add `tools/blockbench` as a whole source directory.

- [ ] **Step 3: Update the generator to use its local resource root**

Change the generator interface to:

```python
def generate_example(tool_root: Path, examples: Path, build: Path) -> ExampleOutputs:
    spider_png = tool_root / "test-resources/fixtures/create_biotech/spider_assembly_table/spider.png"
    andesite_png = tool_root / "test-resources/fixtures/create/andesite_casing.png"
```

At the command entry:

```python
tool_root = Path(__file__).resolve().parent
project_root = tool_root.parents[1]
generate_example(
    tool_root,
    tool_root / "examples",
    project_root / "build/casted-materials-preview",
)
```

Update `test_spider_example.py` to define `TOOL_ROOT = Path(__file__).resolve().parents[1]`, pass it to `generate_example`, and read provenance fixtures below `TOOL_ROOT / "test-resources"`.

- [ ] **Step 4: Update documentation paths without compatibility aliases**

In `tools/blockbench/README.md`, replace every old command and example path with:

```powershell
python tools/blockbench/cli.py ...
python tools/blockbench/generate_spider_example.py
```

and `tools/blockbench/examples/*.bbmodel`.

Add this compact section to root `README.md`:

```markdown
## Blockbench authoring tools

The zero-install development workflow, examples, and tests live in
[`tools/blockbench`](tools/blockbench/README.md). They are excluded from the production JAR.
```

- [ ] **Step 5: Regenerate and validate the moved examples**

```powershell
python tools\blockbench\generate_spider_example.py
python tools\blockbench\cli.py validate tools\blockbench\examples\spider_body.bbmodel
python tools\blockbench\cli.py validate tools\blockbench\examples\andesite_spider_assembly_table.bbmodel
```

Expected: generation reports 24 regions and 0 sparse pixels; both validations report 66 paired faces.

- [ ] **Step 6: Run focused Python and Java resource tests**

```powershell
python -m pytest tools\blockbench\tests\test_spider_example.py -q
.\gradlew.bat test --offline --tests '*AndesiteSpiderAssemblyTableTest'
```

Expected: both commands exit zero and Java resolves `/fixtures/**` from the new shared resource root.

- [ ] **Step 7: Commit the co-located artifacts**

```powershell
git add build.gradle README.md docs examples src/test/resources tools/blockbench
git commit -m "refactor: co-locate blockbench authoring assets"
```

---

### Task 3: Verify structure, behavior, and packaging

**Files:**
- Verify only; no planned production changes.

**Interfaces:**
- Confirms both Python working-directory interfaces, Java classpath resources, generated examples, and production JAR exclusion.

- [ ] **Step 1: Run complete Python verification**

```powershell
python -m pytest tools\blockbench\tests -q
Push-Location tools\blockbench
python -m pytest tests -q
Pop-Location
python -m compileall -q tools\blockbench
```

Expected: 25 tests pass twice and bytecode compilation exits zero.

- [ ] **Step 2: Run a clean Java build**

```powershell
.\gradlew.bat clean test jar --offline
```

Expected: 34 Java tests pass and the production JAR builds.

- [ ] **Step 3: Audit production JAR contents**

```powershell
$jar = Get-ChildItem build\libs\casted-materials-1.21.1-0.1.0.jar
$entries = & jar tf $jar.FullName
$entries | Select-String -Pattern '(^|/)(tools|examples|fixtures|pytest|PIL)(/|$)|\.py$|\.bbmodel$|casted-materials-preview'
```

Expected: zero matches.

- [ ] **Step 4: Verify final repository shape and cleanliness**

```powershell
rg --files tools\blockbench | Sort-Object
Test-Path tools\casted_bb.py
Test-Path tools\casted_bb_lib
Test-Path tools\tests
Test-Path examples
Test-Path docs\blockbench-authoring.md
git diff --check
git status --short
```

Expected: only the new self-contained tree is listed; all five old paths return `False`; `git diff --check` is clean; the pre-existing untracked `.vscode/` remains untouched.
