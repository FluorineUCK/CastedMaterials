# Casted Materials Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Minecraft 1.21.1 NeoForge library that discovers paired `#create:casing` item/block materials after datapack loading, synchronizes a stable material palette, renders material-mapped ordinary and custom models, and provides a non-packaged Python/Blockbench authoring tool.

**Architecture:** Common code owns material validation, palette authority, persistence, and payloads without loading client classes. Client code loads target/material definitions and routes ordinary models through cached `BakedQuad` UV remapping while custom renderers use an asynchronous pixel composer and 512×512 paged dynamic atlas. Development-only Python tooling converts paired-UV `.bbmodel` files into deterministic compressed target JSON and preview images.

**Tech Stack:** Java 21, Gradle 8.14.3, ModDevGradle 2.0.141, NeoForge 21.1.234, Parchment 2024.11.17, JUnit Jupiter 5.10.3, Python 3.11+, pytest, Pillow.

## Global Constraints

- Minecraft version is exactly `1.21.1`; Java toolchain is exactly `21`; NeoForge runtime is `21.1.x` with development pinned to `21.1.234`.
- Mod name is `Casted Materials`; Mod ID is `casted_materials`; Java package is `com.fluorineuck.castedmaterials`.
- The production mod has no Java dependency on Create; `create:casing` is referenced only as a Tag ID.
- A valid material must be present under the same ID in both Item and Block `#create:casing`, the item must be a `BlockItem`, and `BlockItem#getBlock()` must be that same-ID block.
- Invalid Tag entries are logged and skipped; they never fail datapack reload or disconnect a client.
- Item material storage uses `casted_materials:material` with a persistent and network-synchronized `ResourceLocation` value.
- Block entities compose `CastedMaterialState`; no ticking block entity is introduced.
- Disk persistence uses complete IDs; instance synchronization uses the current Palette VarInt index.
- Ordinary models use existing block-atlas sprites; custom renderers use 512×512 paged dynamic atlases and UV-transforming `VertexConsumer` wrappers.
- The ordinary-model fast-path threshold is 8 pieces per original Quad. The hard additional-Quad limits are 2,048 per block combination and 8,192 per item combination.
- Dynamic-atlas upload work is capped at 2 ms per frame; soft memory budget is 4 MiB and hard budget is 16 MiB.
- Dynamic output dimensions above 128 pixels on either axis warn; dimensions above 256 pixels on either axis fall back.
- `resolve()` never returns `null` and never leaks resource exceptions into a render loop.
- A client render failure is local to that client and combination; it cannot alter the server Palette or another client's rendering.
- Production JAR excludes `tools/**`, `examples/**`, `integration/**`, `.py`, `.bbmodel`, pytest data, Pillow, and preview images; compressed target size is at most 512 KiB.
- Python output is deterministic, uses integer pixel coordinates, performs no linear interpolation, and does not overwrite the last valid JSON on validation failure.
- Implement production behavior test-first. For Gradle scaffolding and metadata, create the minimal build harness before the first failing Java test.

---

## File Structure

### Build and metadata

- `settings.gradle` — plugin repositories and root name.
- `build.gradle` — NeoForge setup, JUnit, resource expansion, packaging exclusions, JAR audit, publishing for Jar-in-Jar fixtures.
- `gradle.properties` — pinned versions and mod metadata.
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — Gradle 8.14.3 wrapper copied from the local 1.21.1 reference project.
- `.gitignore`, `LICENSE`, `README.md` — repository boundaries and public usage summary.
- `src/main/resources/META-INF/neoforge.mods.toml`, `src/main/resources/pack.mcmeta` — runtime metadata.

### Common Java

- `src/main/java/com/fluorineuck/castedmaterials/CastedMaterials.java` — mod entry point and event registration.
- `src/main/java/com/fluorineuck/castedmaterials/CastedIds.java` — ResourceLocation and TagKey constants.
- `src/main/java/com/fluorineuck/castedmaterials/api/CastedMaterialsApi.java` — host-facing get/set facade.
- `src/main/java/com/fluorineuck/castedmaterials/api/CastedMaterialHolder.java` — compositional block-entity contract.
- `src/main/java/com/fluorineuck/castedmaterials/api/MaterialSetResult.java` — structured mutation result.
- `src/main/java/com/fluorineuck/castedmaterials/material/CastedMaterialState.java` — full-ID storage, NBT helpers, and change tracking.
- `src/main/java/com/fluorineuck/castedmaterials/material/MaterialPairValidator.java` — pure pairing validation.
- `src/main/java/com/fluorineuck/castedmaterials/material/MaterialValidationReport.java` — valid IDs and validation issues.
- `src/main/java/com/fluorineuck/castedmaterials/material/MaterialPalette.java` — sorted IDs, index lookup, and SHA-256 fingerprint.
- `src/main/java/com/fluorineuck/castedmaterials/material/MaterialPaletteStore.java` — atomic Palette holder.
- `src/main/java/com/fluorineuck/castedmaterials/material/TagSnapshot.java` — immutable registry/Tag input for pure synchronization tests.
- `src/main/java/com/fluorineuck/castedmaterials/material/ServerMaterialPalette.java` — server authority and Tag snapshot adapter.
- `src/main/java/com/fluorineuck/castedmaterials/registry/CastedDataComponents.java` — Data Component registration.
- `src/main/java/com/fluorineuck/castedmaterials/network/MaterialPalettePayload.java` — protocol payload and StreamCodec.
- `src/main/java/com/fluorineuck/castedmaterials/network/CastedNetwork.java` — payload registration.
- `src/main/java/com/fluorineuck/castedmaterials/network/ServerPaletteEvents.java` — datapack sync rebuild and send.
- `src/main/java/com/fluorineuck/castedmaterials/network/ServerPaletteSynchronizer.java` — fingerprint-aware rebuild and recipient selection.
- `src/main/java/com/fluorineuck/castedmaterials/network/ClientPaletteStore.java` — client-side Palette snapshot without Minecraft client references.

### Definitions and mapping core

- `src/main/java/com/fluorineuck/castedmaterials/definition/IntPoint.java`, `IntSize.java`, `IntRect.java` — validated integer geometry.
- `src/main/java/com/fluorineuck/castedmaterials/definition/MappingTransform.java` — six supported transforms.
- `src/main/java/com/fluorineuck/castedmaterials/definition/RegionMapping.java`, `PixelMapping.java`, `MaterialOverride.java`, `ComputedCost.java`, `TargetDefinition.java`, `MaterialDefinition.java` — immutable schema records.
- `src/main/java/com/fluorineuck/castedmaterials/definition/TargetDefinitionParser.java`, `MaterialDefinitionParser.java` — strict Gson parsers.
- `src/main/java/com/fluorineuck/castedmaterials/mapping/PixelImage.java` — compact ARGB image abstraction.
- `src/main/java/com/fluorineuck/castedmaterials/mapping/SourceSlotImage.java` — image, logical grid, and animation metadata.
- `src/main/java/com/fluorineuck/castedmaterials/mapping/CompositionResult.java`, `MappingComposer.java` — deterministic material composition.

### Client Java

- `src/main/java/com/fluorineuck/castedmaterials/client/CastedMaterialsClient.java` — stable custom-renderer API.
- `src/main/java/com/fluorineuck/castedmaterials/client/ClientRuntime.java` — facade implementation and generation owner.
- `src/main/java/com/fluorineuck/castedmaterials/client/CastedClientEvents.java` — client event registration.
- `src/main/java/com/fluorineuck/castedmaterials/client/definition/CastedResourceSnapshot.java` — immutable applied resource generation.
- `src/main/java/com/fluorineuck/castedmaterials/client/definition/CastedResourceReloadListener.java` — target/material JSON reload.
- `src/main/java/com/fluorineuck/castedmaterials/client/definition/SourceSlotResolver.java` — model parent/texture slot resolution and semantic fallback.
- `src/main/java/com/fluorineuck/castedmaterials/client/failure/RenderableState.java`, `RenderFailureCode.java`, `OncePerGenerationLogger.java` — local degradation/quarantine.
- `src/main/java/com/fluorineuck/castedmaterials/client/cache/RenderKey.java`, `RenderEntry.java`, `RenderRepository.java`, `UploadQueue.java`, `UploadResult.java` — generation-aware state machine and async queue.
- `src/main/java/com/fluorineuck/castedmaterials/client/atlas/AtlasAllocation.java`, `ShelfAtlasAllocator.java`, `TextureBackend.java`, `MinecraftTextureBackend.java`, `DynamicAtlasPage.java`, `DynamicAtlasManager.java` — page planning and GPU ownership.
- `src/main/java/com/fluorineuck/castedmaterials/client/render/UvTransform.java`, `UvTransformingVertexConsumer.java`, `CastedTextureHandle.java` — custom renderer handle.
- `src/main/java/com/fluorineuck/castedmaterials/client/model/CastedModelData.java`, `CastedGeometryLoader.java`, `CastedUnbakedGeometry.java`, `CastedBakedModel.java`, `CastedItemOverrides.java`, `QuadRemapper.java`, `QuadSplitBudget.java`, `BakedRenderCache.java` — ordinary model path.

### Tests, fixtures, and tools

- `src/test/java/com/fluorineuck/castedmaterials/**` — JUnit tests mirroring each production package.
- `src/test/java/com/fluorineuck/castedmaterials/TestIds.java` — shared deterministic test-ID helper.
- `src/gameTest/java/com/fluorineuck/castedmaterials/gametest/CastedMaterialsGameTests.java` — NeoForge server smoke tests in a non-production source set.
- `src/test/resources/fixtures/create/**` — minimal copied model/Tag fixtures for Create casing behavior.
- `src/test/resources/fixtures/definitions/**` — valid and invalid target/material JSON.
- `tools/casted_bb.py` — CLI entry point.
- `tools/casted_bb_lib/model.py`, `pairing.py`, `compress.py`, `preview.py`, `commands.py` — focused authoring units.
- `tools/requirements.txt` — `Pillow` and `pytest` development dependencies.
- `tools/tests/helpers.py`, `tools/tests/**` — declared `.bbmodel` assertion helpers, pytest suite, and minimal fixtures.
- `examples/spider_body.bbmodel` — generated paired-UV example, excluded from JAR.
- `integration/jarjar/**` — two-host Jar-in-Jar fixture build, excluded from JAR.

---

### Task 1: Bootstrap the NeoForge library and packaging guardrails

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `.gitignore`
- Create: `LICENSE`
- Create: `src/main/resources/META-INF/neoforge.mods.toml`
- Create: `src/main/resources/pack.mcmeta`
- Create: `src/main/java/com/fluorineuck/castedmaterials/CastedMaterials.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/CastedIds.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/ProjectMetadataTest.java`

**Interfaces:**
- Produces: `CastedMaterials.MOD_ID`, `CastedMaterials.id(String)`, `CastedIds.CASING_ITEMS`, and `CastedIds.CASING_BLOCKS`.
- Consumes: none.

- [ ] **Step 1: Create the build harness without production classes**

Copy the Gradle 8.14.3 wrapper from `D:\mymod\Create-Biotech`, pin `net.neoforged.moddev` to `2.0.141`, NeoForge to `21.1.234`, Parchment to `2024.11.17`, and JUnit BOM to `5.10.3`. Configure:

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '2.0.141'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

dependencies {
    testImplementation platform('org.junit:junit-bom:5.10.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

test { useJUnitPlatform() }

jar {
    exclude 'tools/**', 'examples/**', 'integration/**', '**/*.py', '**/*.bbmodel'
}
```

Use `rootProject.name = 'CastedMaterials'`, archive name `casted-materials-1.21.1`, group `com.fluorineuck`, version `0.1.0`, MIT license, and author `FluorineUCK`.

- [ ] **Step 2: Write the failing metadata test**

```java
class ProjectMetadataTest {
    @Test
    void exposesStableModIdentityAndCasingTags() {
        assertEquals("casted_materials", CastedMaterials.MOD_ID);
        assertEquals(ResourceLocation.fromNamespaceAndPath("create", "casing"), CastedIds.CASING_ITEMS.location());
        assertEquals(ResourceLocation.fromNamespaceAndPath("create", "casing"), CastedIds.CASING_BLOCKS.location());
    }
}
```

- [ ] **Step 3: Run the test and verify RED**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\mymod\.gradle-user-home'
.\gradlew.bat test --offline --tests '*ProjectMetadataTest'
```

Expected: compilation fails because `CastedMaterials` and `CastedIds` do not exist.

- [ ] **Step 4: Add the minimal mod entry point and constants**

```java
@Mod(CastedMaterials.MOD_ID)
public final class CastedMaterials {
    public static final String MOD_ID = "casted_materials";

    public CastedMaterials(IEventBus modBus) {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
```

```java
public final class CastedIds {
    public static final TagKey<Item> CASING_ITEMS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create", "casing"));
    public static final TagKey<Block> CASING_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create", "casing"));

    private CastedIds() {
    }
}
```

Create metadata with `side = "BOTH"`, exact Minecraft range `[1.21.1]`, NeoForge range `[21.1.234,)`, and no Create dependency stanza.

- [ ] **Step 5: Verify GREEN and the initial JAR**

Run:

```powershell
.\gradlew.bat test jar --offline
jar tf build\libs\casted-materials-1.21.1-0.1.0.jar
```

Expected: test passes; JAR contains the mod class and metadata and contains no `tools/`, `.py`, or `.bbmodel` entry.

- [ ] **Step 6: Commit**

```powershell
git add settings.gradle build.gradle gradle.properties gradlew gradlew.bat gradle .gitignore LICENSE src
git commit -m "build: bootstrap casted materials library"
```

---

### Task 2: Validate paired casing materials and build a stable Palette

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/MaterialPairValidator.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/MaterialValidationReport.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/MaterialPalette.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/MaterialPaletteStore.java`
- Create: `src/test/java/com/fluorineuck/castedmaterials/TestIds.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/material/MaterialPairValidatorTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/material/MaterialPaletteTest.java`

**Interfaces:**
- Produces: `MaterialPairValidator.validate(Map<ResourceLocation, Item>, Map<ResourceLocation, Block>, Set<ResourceLocation>, Set<ResourceLocation>)`.
- Produces: `MaterialPalette.from(Collection<ResourceLocation>)`, `indexOf(ResourceLocation)`, `idAt(int)`, `fingerprint()`, and `ids()`.
- Produces: `MaterialPaletteStore.current()` and `replace(MaterialPalette)`.
- Consumes: `CastedIds.CASING_ITEMS` and `CastedIds.CASING_BLOCKS` only in later registry adapter code.
- Test helper: `TestIds.id(String)` parses a fully-qualified ID and otherwise prefixes `test:`; tests use a static import instead of an undeclared local helper.

- [ ] **Step 1: Write validator tests before production types**

Create the shared helper first:

```java
public final class TestIds {
    public static ResourceLocation id(String value) {
        return value.indexOf(':') >= 0
                ? ResourceLocation.parse(value)
                : ResourceLocation.fromNamespaceAndPath("test", value);
    }

    private TestIds() {
    }
}
```

Create cases for valid pairing, item-only Tag, block-only Tag, non-`BlockItem`, mismatched `BlockItem#getBlock()`, and missing registry entry:

```java
@Test
void keepsOnlySameIdBlockItemsPointingAtTheSameBlock() {
    ResourceLocation good = id("good");
    ResourceLocation wrong = id("wrong");
    Block goodBlock = new Block(BlockBehaviour.Properties.of());
    Block otherBlock = new Block(BlockBehaviour.Properties.of());

    MaterialValidationReport report = MaterialPairValidator.validate(
            Map.of(good, new BlockItem(goodBlock, new Item.Properties()),
                    wrong, new BlockItem(otherBlock, new Item.Properties())),
            Map.of(good, goodBlock, wrong, goodBlock),
            Set.of(good, wrong),
            Set.of(good, wrong));

    assertEquals(List.of(good), report.validIds());
    assertEquals(MaterialPairValidator.ErrorCode.BLOCK_ITEM_POINTS_ELSEWHERE,
            report.issues().getFirst().code());
}
```

- [ ] **Step 2: Write Palette ordering and fingerprint tests**

```java
@Test
void sortsNamespaceBeforePathAndDeduplicates() {
    MaterialPalette palette = MaterialPalette.from(List.of(id("z:b"), id("a:z"), id("z:b"), id("a:a")));
    assertEquals(List.of(id("a:a"), id("a:z"), id("z:b")), palette.ids());
    assertEquals(2, palette.indexOf(id("z:b")).orElseThrow());
    assertEquals(id("a:z"), palette.idAt(1).orElseThrow());
    assertTrue(palette.idAt(3).isEmpty());
}

@Test
void fingerprintIsStableForEquivalentInputOrder() {
    assertArrayEquals(
            MaterialPalette.from(List.of(id("b:x"), id("a:y"))).fingerprint(),
            MaterialPalette.from(List.of(id("a:y"), id("b:x"))).fingerprint());
}
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```powershell
.\gradlew.bat test --offline --tests '*MaterialPairValidatorTest' --tests '*MaterialPaletteTest'
```

Expected: compilation fails because the material domain types do not exist.

- [ ] **Step 4: Implement the validator and immutable Palette**

Use the union of both Tag ID sets as candidates. Emit one issue per failed condition in deterministic ID order. Sort IDs with namespace first:

```java
private static final Comparator<ResourceLocation> ID_ORDER =
        Comparator.comparing(ResourceLocation::getNamespace)
                .thenComparing(ResourceLocation::getPath);
```

Compute SHA-256 by updating the digest with each UTF-8 ID followed by byte `0` to prevent concatenation ambiguity. Clone the fingerprint on input/output. Store an unmodifiable ID list and index map.

- [ ] **Step 5: Add atomic Palette replacement**

```java
public final class MaterialPaletteStore {
    private final AtomicReference<MaterialPalette> current =
            new AtomicReference<>(MaterialPalette.empty());

    public MaterialPalette current() {
        return current.get();
    }

    public MaterialPalette replace(MaterialPalette next) {
        return current.getAndSet(Objects.requireNonNull(next));
    }
}
```

- [ ] **Step 6: Verify GREEN**

Run the two focused tests, then all tests:

```powershell
.\gradlew.bat test --offline --tests '*MaterialPairValidatorTest' --tests '*MaterialPaletteTest'
.\gradlew.bat test --offline
```

Expected: all pass without warnings from application code.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/material src/test/java/com/fluorineuck/castedmaterials/material
git commit -m "feat: validate casing pairs and build palettes"
```

---

### Task 3: Add material persistence, Data Component, and host API

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/registry/CastedDataComponents.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/api/MaterialSetResult.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/api/CastedMaterialHolder.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/api/CastedMaterialsApi.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/CastedMaterialState.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/ServerMaterialPalette.java`
- Modify: `src/main/java/com/fluorineuck/castedmaterials/CastedMaterials.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/material/CastedMaterialStateTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/api/CastedMaterialsApiTest.java`

**Interfaces:**
- Consumes: `MaterialPalette`, `MaterialPaletteStore`.
- Produces: `CastedDataComponents.MATERIAL` as `DataComponentType<ResourceLocation>`.
- Produces: `CastedMaterialState#get()`, `set(ResourceLocation)`, `save(CompoundTag)`, `load(CompoundTag)`, `encodeIndex(MaterialPalette)`, and `decodeIndex(MaterialPalette, int)`.
- Produces: public `CastedMaterialsApi` signatures from the design specification.

- [ ] **Step 1: Write failing state persistence tests**

```java
@Test
void persistsFullIdAndUsesPaletteIndexOnlyForWireState() {
    ResourceLocation copper = id("create:copper_casing");
    MaterialPalette palette = MaterialPalette.from(List.of(copper));
    CastedMaterialState state = new CastedMaterialState();
    assertEquals(MaterialSetResult.CHANGED, state.set(copper));

    CompoundTag tag = new CompoundTag();
    state.save(tag);
    assertEquals("create:copper_casing", tag.getString("CastedMaterial"));
    assertEquals(0, state.encodeIndex(palette).orElseThrow());

    CastedMaterialState loaded = new CastedMaterialState();
    loaded.load(tag);
    assertEquals(copper, loaded.get().orElseThrow());
}
```

Also test invalid NBT clears no previously valid state, identical values return `UNCHANGED`, and unknown Palette indices return `Optional.empty()`.

- [ ] **Step 2: Write failing API mutation tests**

Use a fake `CastedMaterialHolder` with a change counter. Install a test Palette in `ServerMaterialPalette.store()` and verify `CHANGED`, `UNCHANGED`, `UNKNOWN_MATERIAL`, and `NO_ACTIVE_PALETTE`. For item tests, build an unregistered test `DataComponentType<ResourceLocation>` and exercise a package-private `setStackMaterial` helper injected with that component type; reserve the real registered holder for NeoForge integration tests.

- [ ] **Step 3: Run focused tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*CastedMaterialStateTest' --tests '*CastedMaterialsApiTest'
```

Expected: compilation fails because the state, API, and result types are missing.

- [ ] **Step 4: Register the component and implement state**

```java
public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CastedMaterials.MOD_ID);

public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> MATERIAL =
        COMPONENTS.registerComponentType("material", builder -> builder
                .persistent(ResourceLocation.CODEC)
                .networkSynchronized(ResourceLocation.STREAM_CODEC));
```

Register `COMPONENTS` from the mod constructor. `CastedMaterialState` stores an `Optional<ResourceLocation>` and writes exactly one `CastedMaterial` string key.

- [ ] **Step 5: Implement the host contract and API side effects**

```java
public interface CastedMaterialHolder {
    CastedMaterialState castedMaterialState();

    default void onCastedMaterialChanged() {
    }
}
```

When a holder changes, call its hook. If it is also a `BlockEntity`, call `setChanged()`, `requestModelDataUpdate()`, and on the logical server call `level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)`. Never create a tick callback.

- [ ] **Step 6: Verify GREEN**

```powershell
.\gradlew.bat test --offline --tests '*CastedMaterialStateTest' --tests '*CastedMaterialsApiTest'
.\gradlew.bat test --offline
```

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/api src/main/java/com/fluorineuck/castedmaterials/material src/main/java/com/fluorineuck/castedmaterials/registry src/main/java/com/fluorineuck/castedmaterials/CastedMaterials.java src/test
git commit -m "feat: persist and mutate casted materials"
```

---

### Task 4: Synchronize the authoritative Palette

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/network/MaterialPalettePayload.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/network/CastedNetwork.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/network/ServerPaletteEvents.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/network/ServerPaletteSynchronizer.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/network/ClientPaletteStore.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/material/TagSnapshot.java`
- Modify: `src/main/java/com/fluorineuck/castedmaterials/CastedMaterials.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/network/MaterialPalettePayloadTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/network/ServerPaletteEventsTest.java`

**Interfaces:**
- Consumes: `MaterialPairValidator`, `MaterialPalette`, `ServerMaterialPalette`.
- Produces: payload protocol string `"1"`, `MaterialPalettePayload.TYPE`, and `MaterialPalettePayload.STREAM_CODEC`.
- Produces: `ClientPaletteStore.current()` for rendering code.
- Produces: `TagSnapshot` and `ServerPaletteSynchronizer.SyncDecision` so event behavior stays independently testable.

- [ ] **Step 1: Write the failing payload round-trip and size tests**

```java
@Test
void roundTripsOrderedIdsAndFingerprint() {
    MaterialPalette palette = MaterialPalette.from(List.of(id("create:andesite_casing"), id("create:brass_casing")));
    MaterialPalettePayload original = MaterialPalettePayload.from(palette);
    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
            new FriendlyByteBuf(Unpooled.buffer()), RegistryAccess.EMPTY);
    MaterialPalettePayload.STREAM_CODEC.encode(buffer, original);
    MaterialPalettePayload decoded = MaterialPalettePayload.STREAM_CODEC.decode(buffer);
    assertEquals(original.ids(), decoded.ids());
    assertArrayEquals(original.fingerprint(), decoded.fingerprint());
}
```

Encode 32 maximum-length normal mod IDs and assert `readableBytes() <= 4096`.

- [ ] **Step 2: Write the failing synchronization scope test**

Extract the event behavior behind `ServerPaletteSynchronizer.rebuild(TagSnapshot, SyncCause)`, returning `SyncDecision(MaterialPalette palette, boolean broadcast)`. `TagSnapshot` contains registry item/block maps plus item/block Tag ID sets. Verify that the Palette is rebuilt once, invalid pairs are absent, and an unchanged fingerprint produces `broadcast=false` for `RELOAD`, while `PLAYER_JOIN` always returns the current snapshot to the single joining player.

- [ ] **Step 3: Run tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*MaterialPalettePayloadTest' --tests '*ServerPaletteEventsTest'
```

- [ ] **Step 4: Implement an explicit bounded StreamCodec**

Use `StreamCodec.of` with these limits:

```java
private static final int PROTOCOL_VERSION = 1;
private static final int FINGERPRINT_BYTES = 32;
private static final int MAX_MATERIALS = 16_384;
```

Write protocol as VarInt, exactly 32 fingerprint bytes, count as VarInt, then each `ResourceLocation.STREAM_CODEC`. Reject other protocol versions, fingerprint lengths, negative counts, and counts above `MAX_MATERIALS` during decode.

- [ ] **Step 5: Register and send the payload**

```java
event.registrar("1").playToClient(
        MaterialPalettePayload.TYPE,
        MaterialPalettePayload.STREAM_CODEC,
        (payload, context) -> ClientPaletteStore.replace(payload.toPalette()));
```

Subscribe `ServerPaletteEvents` to `OnDatapackSyncEvent`. Build the Tag snapshot from `BuiltInRegistries.ITEM.getTag(CastedIds.CASING_ITEMS)` and `BuiltInRegistries.BLOCK.getTag(CastedIds.CASING_BLOCKS)`, update `ServerMaterialPalette`, log validation issues once per rebuilt fingerprint, and use `event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload))`.

- [ ] **Step 6: Verify GREEN and dedicated-server class isolation at compile time**

```powershell
.\gradlew.bat test --offline --tests '*MaterialPalettePayloadTest' --tests '*ServerPaletteEventsTest'
.\gradlew.bat compileJava --offline
```

Inspect imports in `network` and `material`; none may reference `net.minecraft.client`.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/network src/main/java/com/fluorineuck/castedmaterials/CastedMaterials.java src/test/java/com/fluorineuck/castedmaterials/network
git commit -m "feat: synchronize casing material palettes"
```

---

### Task 5: Parse target and material resource definitions

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/IntPoint.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/IntSize.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/IntRect.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/MappingTransform.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/RegionMapping.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/PixelMapping.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/MaterialOverride.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/ComputedCost.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/TargetDefinition.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/MaterialDefinition.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/TargetDefinitionParser.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/definition/MaterialDefinitionParser.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/definition/CastedResourceSnapshot.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/definition/CastedResourceReloadListener.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/definition/TargetDefinitionParserTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/definition/MaterialDefinitionParserTest.java`
- Test fixtures: `src/test/resources/fixtures/definitions/**`

**Interfaces:**
- Consumes: target schema from the approved design.
- Produces: strict immutable `TargetDefinition` and `MaterialDefinition` values.
- Produces: `CastedResourceSnapshot(long generation, Map<ResourceLocation, TargetDefinition>, Map<ResourceLocation, MaterialDefinition>)`.

- [ ] **Step 1: Write valid-schema tests**

Parse a target containing `source_grids`, all six transforms, material-specific slots, overlays, regions, pixels, and `computed_cost`. Assert exact records and preserve declaration order only where composition order is meaningful.

```java
assertEquals(new IntSize(64, 32), target.size());
assertEquals(new IntSize(16, 16), target.sourceGrids().get("side"));
assertEquals(MappingTransform.ROTATE_90, target.regions().getFirst().transform());
```

- [ ] **Step 2: Write invalid-schema tests**

Each fixture must fail with a message containing its JSON field path: unsupported schema, non-positive size, output over 256, absent `source_grids` entry, out-of-bounds source/destination, mismatched transformed dimensions, duplicate destination writes, unsupported transform, invalid ResourceLocation, and empty mapping.

- [ ] **Step 3: Run tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*DefinitionParserTest'
```

- [ ] **Step 4: Implement strict parsers and validated geometry records**

`IntRect` rejects negative origins and non-positive width/height. `TargetDefinitionParser` validates overlaps using a `BitSet` sized to the output image. Parse `computed_cost` but document and expose it only as diagnostic input; no runtime budget decision may trust it.

- [ ] **Step 5: Implement generation-atomic resource reload**

`CastedResourceReloadListener` scans:

```text
casted_materials/targets/*.json
casted_materials/materials/*.json
```

Prepare a complete immutable snapshot on the reload worker. On apply, increment generation and atomically replace the previous snapshot. One bad file logs its resource ID and is omitted without discarding other files.

- [ ] **Step 6: Verify GREEN**

```powershell
.\gradlew.bat test --offline --tests '*DefinitionParserTest'
.\gradlew.bat test --offline
```

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/definition src/main/java/com/fluorineuck/castedmaterials/client/definition src/test
git commit -m "feat: load casted material definitions"
```

---

### Task 6: Compose mapped pixels deterministically

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/mapping/PixelImage.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/mapping/SourceSlotImage.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/mapping/CompositionResult.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/mapping/MappingComposer.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/failure/RenderableState.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/failure/RenderFailureCode.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/mapping/MappingComposerTest.java`

**Interfaces:**
- Consumes: `TargetDefinition`, resolved `Map<String, SourceSlotImage>`, optional overlay.
- Produces: `CompositionResult(PixelImage image, RenderableState state, List<RenderFailureCode> diagnostics)`.
- Produces: `PixelImage#get(int,int)`, `set(int,int,int)`, `copyRow(...)`, and `alphaOver(...)`.

- [ ] **Step 1: Write one test per transform**

Use a 2×3 source with six unique ARGB values. Assert exact output arrays for identity, 90/180/270-degree rotations, mirror X, and mirror Y. Name tests by transform rather than combining all failures into one assertion.

- [ ] **Step 2: Write rectangle, sparse-pixel, overlay, and degradation tests**

```java
@Test
void appliesRectangleThenSparsePixelThenOverlay() {
    CompositionResult result = MappingComposer.compose(target, slots, overlay);
    assertEquals(0xff112233, result.image().get(0, 0));
    assertEquals(0xffabcdef, result.image().get(3, 1));
    assertEquals(RenderableState.READY, result.state());
}
```

Also verify same-size sources remain `READY`, integer-scaled sources use center sampling and become `DEGRADED`, animated sources use frame zero and emit `ANIMATED_SOURCE_FIRST_FRAME`, missing semantic slots return a fallback result, and aspect mismatch never writes a partial output.

- [ ] **Step 3: Run tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*MappingComposerTest'
```

- [ ] **Step 4: Implement the minimal composer**

Use `System.arraycopy` for identity rows. Use integer coordinate functions for other transforms. Apply mapping in the validated order `regions`, then `pixels`, then alpha overlay. Do not allocate per output pixel; allocate one target `int[]` and reuse row buffers only when transform requires them.

- [ ] **Step 5: Verify GREEN and deterministic repeatability**

Run every composer test twice in the same JVM and assert content hashes are identical:

```powershell
.\gradlew.bat test --offline --tests '*MappingComposerTest'
.\gradlew.bat test --offline --tests '*MappingComposerTest'
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/mapping src/main/java/com/fluorineuck/castedmaterials/client/failure src/test/java/com/fluorineuck/castedmaterials/mapping
git commit -m "feat: compose pixel mapped textures"
```

---

### Task 7: Build the paged atlas planner and local failure state machine

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/atlas/AtlasAllocation.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/atlas/ShelfAtlasAllocator.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/cache/RenderKey.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/cache/RenderEntry.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/cache/RenderRepository.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/cache/UploadQueue.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/cache/UploadResult.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/failure/OncePerGenerationLogger.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/atlas/ShelfAtlasAllocatorTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/cache/RenderRepositoryTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/failure/OncePerGenerationLoggerTest.java`

**Interfaces:**
- Produces: `ShelfAtlasAllocator.allocate(int width, int height): Optional<AtlasAllocation>`.
- Produces: `RenderRepository.resolve(RenderKey, Supplier<CompositionResult>)` with states `MISSING`, `GENERATING`, `WAITING_UPLOAD`, `READY`, `FAILED`, and `QUARANTINED`.
- Consumes: `CompositionResult`, injected worker `Executor`, injected render-thread upload queue, generation token.

- [ ] **Step 1: Write atlas capacity and bounds tests**

```java
@Test
void packsExactlyOneHundredTwentyEight64By32EntriesOnOnePage() {
    ShelfAtlasAllocator allocator = new ShelfAtlasAllocator(512, 512);
    for (int i = 0; i < 128; i++) {
        assertTrue(allocator.allocate(64, 32).isPresent(), "allocation " + i);
    }
    assertTrue(allocator.allocate(64, 32).isEmpty());
}
```

Also test mixed row heights, width/height over 256 rejection, non-overlap, stable first-fit order, and reset on a new generation.

- [ ] **Step 2: Write one-to-many state isolation tests**

Create four repositories sharing the same Palette IDs but independent resource snapshots. Drive A to `READY`, B to `DEGRADED`, C to `FALLBACK_BASE`, and D to `QUARANTINED`. Assert the shared Palette remains byte-for-byte unchanged, D does not block a later A job, and a new generation permits D to retry.

- [ ] **Step 3: Write log-deduplication tests**

Assert the tuple `(generation, target, material, errorCode)` logs once, another material logs independently, and the same tuple logs again only after generation increments.

- [ ] **Step 4: Run tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*ShelfAtlasAllocatorTest' --tests '*RenderRepositoryTest' --tests '*OncePerGenerationLoggerTest'
```

- [ ] **Step 5: Implement shelf allocation and injected state transitions**

Use rows ordered by `y`; place into the first row whose height fits, otherwise open a new row below the previous one. The repository owns no Minecraft objects and accepts:

```java
public interface UploadQueue {
    void enqueue(RenderKey key, PixelImage image, Consumer<UploadResult> completion);
}

public record UploadResult(boolean uploaded, Optional<AtlasAllocation> allocation,
                           Optional<RenderFailureCode> failure) {
}
```

Catch each supplier/upload exception, transition only that key, and always release the worker to process the next queued job.

- [ ] **Step 6: Verify GREEN**

```powershell
.\gradlew.bat test --offline --tests '*ShelfAtlasAllocatorTest' --tests '*RenderRepositoryTest' --tests '*OncePerGenerationLoggerTest'
.\gradlew.bat test --offline
```

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/client/atlas src/main/java/com/fluorineuck/castedmaterials/client/cache src/main/java/com/fluorineuck/castedmaterials/client/failure src/test
git commit -m "feat: isolate dynamic atlas rendering state"
```

---

### Task 8: Implement the custom-renderer dynamic atlas API

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/render/UvTransform.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/render/UvTransformingVertexConsumer.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/render/CastedTextureHandle.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/atlas/DynamicAtlasPage.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/atlas/DynamicAtlasManager.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/atlas/TextureBackend.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/atlas/MinecraftTextureBackend.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/CastedMaterialsClient.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/ClientRuntime.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/CastedClientEvents.java`
- Modify: `src/main/java/com/fluorineuck/castedmaterials/client/definition/CastedResourceReloadListener.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/render/UvTransformingVertexConsumerTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/render/CastedTextureHandleTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/atlas/DynamicAtlasManagerTest.java`

**Interfaces:**
- Consumes: resource snapshot, client Palette, `MappingComposer`, `RenderRepository`, Minecraft `TextureManager`.
- Produces: `CastedMaterialsClient.resolve(ResourceLocation targetId, ResourceLocation materialId, ResourceLocation fallbackTexture)`.
- Produces: `CastedTextureHandle.texture()` and `wrap(VertexConsumer)`.
- Produces: `TextureBackend.createPage(ResourceLocation, int, int)`, `upload(...)`, and `closePage(...)` for deterministic tests without a Minecraft window.

- [ ] **Step 1: Write UV-wrapper tests**

With a recording delegate and `UvTransform(0.25f, 0.5f, 0.125f, 0.25f)`, verify input `(0.5, 0.75)` becomes `(0.375, 0.6875)` and all non-UV calls pass through unchanged.

- [ ] **Step 2: Write Handle fallback tests**

Assert `MISSING`, `GENERATING`, `WAITING_UPLOAD`, `FAILED`, and `QUARANTINED` Handles return the fallback texture and identity UV; `READY` returns the page texture and page transform. No state returns `null`.

- [ ] **Step 3: Write atlas budget and generation tests**

Inject a fake texture backend. Verify page creation at 1 MiB each, soft warning after four pages, refusal after sixteen pages, at most 2 ms worth of fake uploads in one drain call, old-generation completion discard, and page closure after atomic generation switch.

- [ ] **Step 4: Run tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*UvTransformingVertexConsumerTest' --tests '*CastedTextureHandleTest' --tests '*DynamicAtlasManagerTest'
```

- [ ] **Step 5: Implement GPU ownership behind a backend boundary**

`DynamicAtlasPage` owns one 512×512 `NativeImage` and `DynamicTexture`. Register page IDs as `casted_materials:dynamic/g<generation>/page_<index>`. Copy composed images into the page image, then upload on the render thread. `DynamicAtlasManager.closeGeneration(long)` closes both the `DynamicTexture` and its `NativeImage` only after the new generation is visible.

- [ ] **Step 6: Implement the stable client facade**

```java
public static CastedTextureHandle resolve(
        ResourceLocation targetId,
        ResourceLocation materialId,
        ResourceLocation fallbackTexture) {
    return runtime.resolve(targetId, materialId, fallbackTexture);
}
```

`CastedMaterialsClient` owns `private static volatile ClientRuntime runtime`, initialized from the client setup event and replaceable package-privately by tests. `ClientRuntime.resolve` is the only facade implementation.

Resolve semantic source slots as `requested → all → side → particle → dominant`; use the target's material-specific slots/overlay before global material definitions. Never throw for absent target, absent material, missing texture, composition failure, budget exhaustion, or upload failure.

- [ ] **Step 7: Register reload and shutdown lifecycle**

Register the reload listener through `RegisterClientReloadListenersEvent` and geometry registration later through `ModelEvent.RegisterGeometryLoaders`. Make `DynamicAtlasManager` `AutoCloseable`; resource-generation replacement closes old pages explicitly, while final client shutdown is owned by Minecraft's `TextureManager.close()` for registered `DynamicTexture` objects. Keep every registration entry point under a `Dist.CLIENT` subscriber.

- [ ] **Step 8: Verify GREEN**

```powershell
.\gradlew.bat test --offline --tests '*UvTransformingVertexConsumerTest' --tests '*CastedTextureHandleTest' --tests '*DynamicAtlasManagerTest'
.\gradlew.bat compileJava --offline
```

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/client src/test/java/com/fluorineuck/castedmaterials/client
git commit -m "feat: expose paged dynamic texture handles"
```

---

### Task 9: Implement the ordinary block/item model loader

**Files:**
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/CastedModelData.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/CastedGeometryLoader.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/CastedUnbakedGeometry.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/CastedBakedModel.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/CastedItemOverrides.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/QuadRemapper.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/QuadSplitBudget.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/model/BakedRenderCache.java`
- Create: `src/main/java/com/fluorineuck/castedmaterials/client/definition/SourceSlotResolver.java`
- Modify: `src/main/java/com/fluorineuck/castedmaterials/client/CastedClientEvents.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/model/QuadRemapperTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/model/QuadSplitBudgetTest.java`
- Test: `src/test/java/com/fluorineuck/castedmaterials/client/definition/SourceSlotResolverTest.java`
- Fixtures: `src/test/resources/fixtures/create/**`

**Interfaces:**
- Consumes: `CastedResourceSnapshot`, `ClientPaletteStore`, material component, `ModelData`, block atlas sprites.
- Produces: geometry loader ID `casted_materials:casted`.
- Produces: `CastedModelData.MATERIAL`, `QuadRemapper.remap(...)`, and generation-aware `BakedRenderCache`.

- [ ] **Step 1: Add real Create casing fixture tests**

Copy only the six small Tag/model JSON files needed to represent Create's andesite, brass, copper, and railway casing shapes. Verify automatic resolution yields `all` for the first three and `side/end` for railway. Verify fallback order `side → all → particle → dominant` and `end → all → side → particle → dominant`.

- [ ] **Step 2: Write Quad fast-path and split tests**

Build a four-vertex planar `BakedQuad` using `QuadBakingVertexConsumer`. Test:

- one full-face region returns one Quad with the source Sprite;
- two rectangular partitions return two non-overlapping Quads;
- rotation and mirror preserve winding and face direction;
- the original tint, shade, ambient occlusion, color, light, and normal data survive;
- eight pieces remain fast path;
- nine pieces are allowed but marked measured;
- 2,049 additional block Quads fall back;
- 8,193 additional item Quads fall back;
- sparse pixels do not expand without budget accounting.

- [ ] **Step 3: Write custom loader contract tests**

```json
{
  "loader": "casted_materials:casted",
  "base": "test:block/base_machine",
  "target": "test:machine_body"
}
```

Assert missing `base`, missing `target`, recursive self-base, and absent target definition produce a clear parse/fallback result rather than an exception during rendering.

- [ ] **Step 4: Run tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*QuadRemapperTest' --tests '*QuadSplitBudgetTest' --tests '*SourceSlotResolverTest'
```

- [ ] **Step 5: Implement geometry loading and model data**

`CastedGeometryLoader.read` parses the two ResourceLocations. `CastedUnbakedGeometry.resolveParents` resolves the base model; `bake` wraps `baker.bake(base, modelState)`. Register:

```java
event.register(CastedMaterials.id("casted"), CastedGeometryLoader.INSTANCE);
```

`CastedBakedModel#getQuads(..., ModelData, RenderType)` reads `CastedModelData.MATERIAL`. `CastedItemOverrides#resolve` reads `CastedDataComponents.MATERIAL` from the stack. Both use the same cache and return the original model on any failure.

- [ ] **Step 6: Implement planar rectangle clipping**

Decode BLOCK-format vertices, map target UV intersection corners to bilinear position/color/light/normal values, and rebuild with `QuadBakingVertexConsumer`. Use normalized source-grid coordinates with `TextureAtlasSprite#getU(float)` and `getV(float)`. Recompute actual cost and ignore JSON `computed_cost` for enforcement.

- [ ] **Step 7: Verify GREEN**

```powershell
.\gradlew.bat test --offline --tests '*QuadRemapperTest' --tests '*QuadSplitBudgetTest' --tests '*SourceSlotResolverTest'
.\gradlew.bat test --offline
```

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/fluorineuck/castedmaterials/client/model src/main/java/com/fluorineuck/castedmaterials/client/definition src/main/java/com/fluorineuck/castedmaterials/client/CastedClientEvents.java src/test
git commit -m "feat: remap casted block and item models"
```

---

### Task 10: Create and validate paired-UV Blockbench projects

**Files:**
- Create: `tools/casted_bb.py`
- Create: `tools/casted_bb_lib/__init__.py`
- Create: `tools/casted_bb_lib/model.py`
- Create: `tools/casted_bb_lib/pairing.py`
- Create: `tools/casted_bb_lib/commands.py`
- Create: `tools/requirements.txt`
- Create: `tools/tests/test_init.py`
- Create: `tools/tests/test_validate.py`
- Create: `tools/tests/helpers.py`
- Create: `tools/tests/fixtures/minimal_model.json`
- Create: `tools/tests/fixtures/paired_valid.bbmodel`
- Create: `tools/tests/fixtures/paired_invalid.bbmodel`

**Interfaces:**
- Produces: `python tools/casted_bb.py init ...` and `validate ...`.
- Produces: Python `BbModel`, `ElementFaceKey`, `PairedFace`, and `ValidationError` types.
- Consumes: standard `.bbmodel` JSON only.
- Test helper: `group_names(data: dict) -> list[str]` and `stable_face_pairs(data: dict, slot: str) -> bool`, imported from `tools.tests.helpers`.

- [ ] **Step 1: Create the Python test environment**

`tools/requirements.txt` contains exact compatible ranges:

```text
Pillow>=10.4,<13
pytest>=8.3,<10
```

Install into the active development Python environment and keep installed packages outside the repository.

- [ ] **Step 2: Write the failing `init` test**

Invoke `main(["init", "--model", str(model), "--target", "test:body", "--size", "64x32", "--slots", "side,end", "--output", str(output)])`, read the output JSON, and assert:

```python
assert data["meta"]["model_format"] == "java_block"
assert data["resolution"] == {"width": 64, "height": 32}
assert group_names(data) == ["CM_DEST", "CM_SOURCE[side]", "CM_SOURCE[end]", "CM_FIXED"]
assert stable_face_pairs(data, "side")
```

- [ ] **Step 3: Write failing validation tests**

Separate tests cover unmatched names, duplicate stable names, absent faces, fractional UV, negative UV, out-of-grid UV, mismatched transformed dimensions, overlapping destination writes, unsupported rotation, and empty mapping. Assert exit code `2` and an error containing group, element, and face.

- [ ] **Step 4: Run pytest and verify RED**

```powershell
python -m pytest tools\tests\test_init.py tools\tests\test_validate.py -q
```

Expected: import or command failures because the tool modules do not exist.

- [ ] **Step 5: Implement standard `.bbmodel` initialization and pairing**

Use `CM_DEST`, `CM_SOURCE[<slot>]`, and `CM_FIXED` exactly. Pair by `(element.name, faceDirection)`. Preserve standard element UUIDs but never use duplicated UUIDs as semantic keys. Export source texture `uv_width/uv_height` as the logical grid candidate.

- [ ] **Step 6: Verify GREEN**

```powershell
python -m pytest tools\tests\test_init.py tools\tests\test_validate.py -q
```

- [ ] **Step 7: Commit**

```powershell
git add tools
git commit -m "feat: create paired uv blockbench projects"
```

---

### Task 11: Export compressed mapping JSON and previews

**Files:**
- Create: `tools/casted_bb_lib/compress.py`
- Create: `tools/casted_bb_lib/preview.py`
- Modify: `tools/casted_bb_lib/commands.py`
- Create: `tools/tests/test_compress.py`
- Create: `tools/tests/test_export.py`
- Create: `tools/tests/fixtures/source_side.png`
- Create: `tools/tests/fixtures/source_end.png`
- Create: `examples/spider_body.bbmodel`

**Interfaces:**
- Consumes: validated `PairedFace` values.
- Produces: maximal same-transform rectangles, sparse pixels, deterministic schema-1 JSON, `computed_cost`, and two equal preview renderings.

- [ ] **Step 1: Write failing rectangle compression tests**

Test a full 16×16 region compresses to one record; two adjacent same-transform strips merge; differing slots/transforms do not merge; an L-shape becomes the deterministic minimum produced by row-runs followed by vertical merge; isolated corrections remain `pixels`.

- [ ] **Step 2: Write failing deterministic export tests**

Export the same `.bbmodel` twice to separate paths and assert byte-for-byte equality, sorted material override keys, fixed field order, no timestamp, valid JSON, exact `source_grids`, and recomputed cost.

- [ ] **Step 3: Write failing preview round-trip tests**

Render preview A directly from paired UV data and preview B by re-reading emitted target JSON. Assert `ImageChops.difference(a, b).getbbox() is None`. Verify validation failure leaves an existing sentinel target JSON unchanged.

- [ ] **Step 4: Run pytest and verify RED**

```powershell
python -m pytest tools\tests\test_compress.py tools\tests\test_export.py -q
```

- [ ] **Step 5: Implement deterministic compression and atomic output**

Represent mapped pixels as `(slot, sourceX, sourceY, transform)`. Build horizontal runs, merge vertically only when slot, source progression, transform, and width match, then sort regions by destination Y/X, slot, and source Y/X. Write to a sibling temporary file and use `Path.replace` only after validation and preview equality pass.

- [ ] **Step 6: Generate the spider example and verify GREEN**

```powershell
python tools\casted_bb.py init --model tools\tests\fixtures\minimal_model.json --target create_biotech:spider/body --size 64x32 --slots side,end --output examples\spider_body.bbmodel
python -m pytest tools\tests -q
```

- [ ] **Step 7: Audit JAR exclusion immediately**

```powershell
.\gradlew.bat jar --offline
jar tf build\libs\casted-materials-1.21.1-0.1.0.jar | Select-String -Pattern 'tools/|examples/|\.py$|\.bbmodel$'
```

Expected: no matches.

- [ ] **Step 8: Commit**

```powershell
git add tools examples
git commit -m "feat: export compressed casted mappings"
```

---

### Task 12: Add integration, dedicated-server, and Jar-in-Jar fixtures

**Files:**
- Create: `src/test/java/com/fluorineuck/castedmaterials/integration/CasingFixtureIntegrationTest.java`
- Create: `src/test/java/com/fluorineuck/castedmaterials/integration/MaterialComponentCodecTest.java`
- Create: `src/test/java/com/fluorineuck/castedmaterials/integration/ClientIsolationTest.java`
- Create: `src/test/java/com/fluorineuck/castedmaterials/integration/SpiderStyleRendererContractTest.java`
- Create: `src/gameTest/java/com/fluorineuck/castedmaterials/gametest/CastedMaterialsGameTests.java`
- Create: `integration/jarjar/settings.gradle`
- Create: `integration/jarjar/build.gradle`
- Create: `integration/jarjar/host-a/build.gradle`
- Create: `integration/jarjar/host-b/build.gradle`
- Create: `integration/jarjar/host-a/src/main/java/fixture/hosta/HostA.java`
- Create: `integration/jarjar/host-b/src/main/java/fixture/hostb/HostB.java`
- Create: `integration/jarjar/host-a/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `integration/jarjar/host-b/src/main/resources/META-INF/neoforge.mods.toml`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: every public API produced earlier.
- Produces: `integrationTest`, `verifyCommonClassIsolation`, `gameTestServer`, and `verifyJarJarFixtures` Gradle tasks.

- [ ] **Step 1: Write failing end-to-end JUnit contracts**

Verify the copied Create fixtures yield andesite/brass/copper valid materials, component Codec round-trips full IDs, a fake holder round-trips NBT and Palette index, an ordinary model mapping selects three source sprites, and the spider-style test resolves three dynamic Handles without changing recorded geometry vertex count.

- [ ] **Step 2: Write the client-isolation bytecode test**

Scan compiled `.class` bytes under common packages `api`, `material`, `network`, and `registry`. Fail if their constant pools contain `net/minecraft/client/`, `com/mojang/blaze3d/`, or any `com/fluorineuck/castedmaterials/client/` reference except the payload-neutral `ClientPaletteStore` class name.

- [ ] **Step 3: Run integration tests and verify RED**

```powershell
.\gradlew.bat test --offline --tests '*integration*'
```

Expected: at least the bytecode isolation and full API contract tests fail until integration wiring is complete.

- [ ] **Step 4: Complete registration and lifecycle wiring**

Register the component on the mod bus, payload handler on `RegisterPayloadHandlersEvent`, server Palette listener on `NeoForge.EVENT_BUS`, client reload listener, and geometry loader. Configure a dedicated `gameTest` source set, attach it only to the `gameTestServer` mod run, and add `CastedMaterialsGameTests` with an empty-template test that validates component registration and the initial Palette store. Set `neoforge.enabledGameTestNamespaces=casted_materials`; the `jar` task consumes only `sourceSets.main.output`, so GameTest fixture classes remain outside the production JAR.

- [ ] **Step 5: Build two Jar-in-Jar hosts**

Publish the library to `build/integration-repo`. Both hosts depend on:

```groovy
implementation('com.fluorineuck:casted-materials-1.21.1:0.1.0')
jarJar('com.fluorineuck:casted-materials-1.21.1:[0.1.0,0.2.0)')
```

Build both host JARs, inspect `META-INF/jarjar/metadata.json`, and assert each embeds the same library coordinates and compatible range. The fixture server run loads both host JARs together and checks the log contains exactly one successful `casted_materials` container construction.

- [ ] **Step 6: Verify GREEN and dedicated server startup**

```powershell
.\gradlew.bat test verifyCommonClassIsolation --offline
.\gradlew.bat publishAllPublicationsToIntegrationRepository --offline
.\gradlew.bat -p integration\jarjar build verifyJarJarFixtures --offline
.\gradlew.bat runGameTestServer --offline
```

Expected: JUnit and fixture checks pass; the server exits successfully after GameTests; no client-class loading exception appears.

- [ ] **Step 7: Commit**

```powershell
git add build.gradle src/test integration
git commit -m "test: verify multiplayer and jarjar integration"
```

---

### Task 13: Enforce performance, size, documentation, and release verification

**Files:**
- Create: `src/test/java/com/fluorineuck/castedmaterials/performance/MappingPerformanceTest.java`
- Create: `src/test/java/com/fluorineuck/castedmaterials/performance/PalettePayloadSizeTest.java`
- Create: `src/test/java/com/fluorineuck/castedmaterials/packaging/ProductionJarAuditTest.java`
- Modify: `build.gradle`
- Modify: `README.md`
- Create: `docs/host-integration.md`
- Create: `docs/blockbench-authoring.md`
- Create: `docs/performance-report.md`

**Interfaces:**
- Consumes: final library and tool APIs.
- Produces: `performanceTest`, `verifyProductionJar`, and documented host examples.

- [ ] **Step 1: Write failing budget tests and audit task**

Create repeatable warmed loops with fixed seeds. Separate correctness assertions from timings. Verify:

```text
32-entry payload <= 4096 bytes
64×32 composition P95 <= 1.00 ms
cached Handle resolution P95 <= 0.02 ms
24-Quad first remap P95 <= 0.30 ms
```

The JAR audit rejects entries matching `tools/`, `examples/`, `integration/`, `.py`, `.bbmodel`, `pytest`, `PIL`, and preview PNG paths, and rejects compressed size above 524,288 bytes.

- [ ] **Step 2: Run performance/audit and verify RED where wiring is absent**

```powershell
.\gradlew.bat performanceTest verifyProductionJar --offline
```

Expected: tasks fail until source sets, JAR input, and report output are configured.

- [ ] **Step 3: Configure reproducible reports without production timestamps**

Remove `Implementation-Timestamp` from the JAR manifest. Write benchmark hardware, JVM, warm-up count, sample count, median, and P95 to `build/reports/casted-materials-performance.json`. Do not make FPS a CI assertion.

- [ ] **Step 4: Document the exact host APIs**

`docs/host-integration.md` includes:

```java
CastedMaterialsApi.setMaterial(stack, casingId);
CastedMaterialsApi.setMaterial(blockEntity, casingId);

CastedTextureHandle handle = CastedMaterialsClient.resolve(
        TARGET_ID, casingId, ORIGINAL_TEXTURE);
VertexConsumer consumer = handle.wrap(
        buffer.getBuffer(RenderType.entityCutout(handle.texture())));
```

It also shows `CastedMaterialHolder`, NBT delegation, `ModelData`, custom loader JSON, and the Jar-in-Jar version range. `docs/blockbench-authoring.md` shows the three CLI commands and group convention.

- [ ] **Step 5: Run the full verification matrix**

```powershell
$env:GRADLE_USER_HOME='D:\mymod\.gradle-user-home'
.\gradlew.bat clean test performanceTest verifyProductionJar build --offline
python -m pytest tools\tests -q
.\gradlew.bat publishAllPublicationsToIntegrationRepository --offline
.\gradlew.bat -p integration\jarjar clean build verifyJarJarFixtures --offline
```

Then run the bounded NeoForge smoke checks:

```powershell
.\gradlew.bat runGameTestServer --offline
```

Expected: every command exits zero, no test output contains an unexpected warning/exception, and the final production JAR passes the content and size audit.

- [ ] **Step 6: Inspect final artifacts manually**

```powershell
Get-Item build\libs\casted-materials-1.21.1-0.1.0.jar | Select-Object FullName,Length
jar tf build\libs\casted-materials-1.21.1-0.1.0.jar
git status --short
```

Expected: JAR length is at most 524,288 bytes; only production classes/resources are present; Git status contains only intentional report or documentation changes.

- [ ] **Step 7: Commit**

```powershell
git add build.gradle README.md docs src/test
git commit -m "docs: finalize casted materials verification"
```

---

## Final Acceptance Checklist

- [ ] Create and Biotech casing fixtures validate the item/block one-to-one rule and named texture slots.
- [ ] Server Tag reload creates a stable namespaced Palette and sends it to every relevant player.
- [ ] Client-local missing resources degrade only the affected target/material/client combination.
- [ ] Item Data Component and composed block-entity state survive serialization and synchronization.
- [ ] Ordinary block and item paths select at least three materials without a resource reload.
- [ ] Spider-style custom rendering selects at least three materials without increasing geometry Quad count.
- [ ] `.bbmodel → compressed JSON → preview` is deterministic and pixel-identical.
- [ ] Two compatible Jar-in-Jar hosts resolve one `casted_materials` mod container.
- [ ] Dedicated server starts without client-class loading failures.
- [ ] Full Java, Python, integration, performance, and packaging verification passes.
- [ ] Production JAR is at most 512 KiB and contains no authoring tooling.
