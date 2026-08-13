# Andesite Spider Fixture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic integration test that maps the real Create andesite casing texture over every used UV island of Create Biotech's spider assembly table texture.

**Architecture:** Test-only PNG fixtures and a schema-1 target definition feed the existing strict parser and `MappingComposer`. Assertions verify provenance, exact UV-mask coverage, source-pixel correspondence, output previews, and unchanged custom-renderer vertex count.

**Tech Stack:** Java 21, JUnit Jupiter 5.10.3, Java ImageIO, existing Casted Materials mapping/runtime APIs.

## Global Constraints

- No Create or Create Biotech Java dependency is added.
- Third-party PNG fixtures remain under `src/test/resources` and never enter the production JAR.
- The mapping uses fixed rectangles; it does not expand the texture into sparse per-pixel records.
- The emissive eyes layer remains a separate renderer layer.

---

### Task 1: Add the real-resource integration fixture

**Files:**
- Create: `src/test/resources/fixtures/create/andesite_casing.png`
- Create: `src/test/resources/fixtures/create_biotech/spider_assembly_table/spider.png`
- Create: `src/test/resources/fixtures/create_biotech/spider_assembly_table/spider_eyes.png`
- Create: `src/test/resources/fixtures/definitions/create_biotech/spider_assembly_table_andesite.json`
- Create: `src/test/java/com/fluorineuck/castedmaterials/integration/AndesiteSpiderAssemblyTableTest.java`

**Interfaces:**
- Consumes: `TargetDefinitionParser.parse(ResourceLocation, String)`, `MappingComposer.compose(TargetDefinition, Map<String, SourceSlotImage>, PixelImage)`, and `CastedTextureHandle.wrap(VertexConsumer)`.
- Produces: test previews under `build/casted-materials-preview/` only.

- [ ] **Step 1: Write the failing integration test**

Load the three classpath PNGs, verify dimensions and SHA-256, parse the target definition, compose the `all` source slot, compare the exact alpha mask and mapped pixels, write both previews, and pass 264 vertices through a wrapped counting consumer.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*AndesiteSpiderAssemblyTableTest'
```

Expected: the test fails because `spider_assembly_table_andesite.json` is absent.

- [ ] **Step 3: Add the three provenance-checked PNG fixtures and fixed rectangle JSON**

The JSON declares output `[64,32]`, logical source grid `[16,16]`, and 20 non-overlapping rectangles matching the real spider texture alpha runs. Each source rectangle starts at `[0,0]` and has exactly the destination rectangle's width and height.

- [ ] **Step 4: Run focused and full verification**

```powershell
.\gradlew.bat test --offline --tests '*AndesiteSpiderAssemblyTableTest'
.\gradlew.bat test jar --offline
```

Expected: both commands exit zero, the two preview PNGs exist, and the JAR has no `fixtures/` or preview entries.

- [ ] **Step 5: Commit**

```powershell
git add docs src/test
git commit -m "test: cover biotech spider with andesite casing"
```

