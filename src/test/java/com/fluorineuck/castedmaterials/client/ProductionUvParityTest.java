package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.mapping.UvPlan.*;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fluorineuck.castedmaterials.fixtures.SpiderTestModel;
import com.fluorineuck.castedmaterials.client.CastedResources;
import com.fluorineuck.castedmaterials.client.CapturedModel;
import com.fluorineuck.castedmaterials.client.CastedModelHandle;
import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import com.fluorineuck.castedmaterials.mapping.DefinitionParser;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import com.fluorineuck.castedmaterials.mapping.UvMapping;
import com.fluorineuck.castedmaterials.mapping.PixelReference;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.Layer;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.TextureLayer;
import com.fluorineuck.castedmaterials.palette.MaterialPalette;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class ProductionUvParityTest {
    private static final Path PROJECT = Path.of(System.getProperty("castedMaterials.projectDir"));
    private static final double EPSILON = 1.0e-6;
    private static final Map<String, Map<String, Object>> COSTS = new ConcurrentSkipListMap<>();

    @Test
    void spiderFixturePreservesSourcePartNamesAndGeometry() {
        var root = SpiderTestModel.create();
        var names = List.of("head", "body0", "body1", "right_hind_leg", "left_hind_leg",
                "right_middle_hind_leg", "left_middle_hind_leg", "right_middle_front_leg",
                "left_middle_front_leg", "right_front_leg", "left_front_leg");
        names.forEach(name -> assertDoesNotThrow(() -> root.getChild(name), name));
        assertEquals(12, root.getAllParts().count(), "Root plus eleven named single-cube parts");
        assertEquals(66, CapturedModel.capture(root)
                .quads(new com.fluorineuck.castedmaterials.mapping.IntSize(64,32)).size());
    }

    @Test
    void andesiteDedicatedBodyIsSelectedInNormalUvPathWithoutConnectedTexture() throws IOException {
        var target=DefinitionParser.parseTarget(ResourceLocation.parse("create_biotech:spider_assembly_table/spider"),
                Files.readString(PROJECT.resolve("tools/test-resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json")));
        var material=ResourceLocation.parse("create:andesite_casing");
        var body=ResourceLocation.parse("create_biotech:entity/spider_assembly_table/body_andesite_casing");
        var eye=ResourceLocation.parse("create_biotech:entity/spider_assembly_table/eye_andesite_casing");
        var loads=new java.util.ArrayList<ResourceLocation>();
        var runtime=new DefaultModelRuntime(() -> new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()),
                () -> MaterialPalette.from(List.of(material)),(t,m,g,required) -> {
                    assertTrue(required.isEmpty(),"A dedicated body must not require generated casing sprites: "+required);
                    return Optional.of(Map.of());
                },16,(sprite,grid) -> {
                    loads.add(sprite);
                    if (!sprite.equals(body) && !sprite.equals(eye)) return Optional.empty();
                    try { return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite,readTexture(sprite))); }
                    catch(IOException error) { throw new UncheckedIOException(error); }
                });
        var root=SpiderTestModel.create();
        var handle=runtime.resolve(root,target.id(),material,id("unrelated_fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend(),handle.reason());
        assertTrue(loads.contains(body),"The dedicated body is not just a renderer fallback");
        var rendered=new RecordingBuffers();
        handle.render(new PoseStack(),rendered,RenderType::entityCutoutNoCull,0,0,-1);
        assertTrue(loads.contains(eye), "The transparent dedicated eye must win instead of falling through to generic eyes");
        assertFalse(loads.contains(ResourceLocation.parse("create_biotech:entity/spider_assembly_table/eye_default")));
        assertFalse(loads.contains(ResourceLocation.parse("create_biotech:entity/spider_assembly_table/eye_00")));
        assertEquals(Set.of(RenderType.entityCutoutNoCull(MaterialTextures.textureId(body))),rendered.vertices.keySet(),
                "The authored andesite body uses its own face artwork; transparent dedicated eyes add no geometry");
        var glow=new RecordingBuffers();
        handle.renderEmissive(new PoseStack(),glow,15728640,0,-1);
        assertTrue(glow.vertices.isEmpty(),"Transparent dedicated eyes must not emit default-eye glow");
        assertSame(handle,runtime.resolve(root,target.id(),material,id("unrelated_fallback")));
    }

    @Test
    void asurineUsesDefaultEyeWhenNumberedPoolIsAbsentAndAutomaticallyFindsConnectedCasing() throws Exception {
        var target = DefinitionParser.parseTarget(ResourceLocation.parse("create_biotech:spider_assembly_table/spider"),
                Files.readString(PROJECT.resolve("tools/test-resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json")));
        var material = ResourceLocation.parse("create_biotech:asurine_casing");
        var tile = ResourceLocation.parse("create_biotech:block/asurine_casing");
        var sourceRequests = new java.util.ArrayList<ResourceLocation>();
        var eyeRequests = new java.util.ArrayList<ResourceLocation>();
        var runtime = new DefaultModelRuntime(() -> new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()),
                () -> MaterialPalette.from(List.of(material)),
                (t,m,global,required) -> com.fluorineuck.castedmaterials.mapping.SourceSlotResolver.resolveSlots(
                        t,m,global,ignored -> Optional.of(tile.withSuffix("_connected")),(sprite,grid) -> {
                            sourceRequests.add(sprite);
                            return availableProjectImage(sprite,grid).map(image ->
                                    new SourceSprite(MaterialTextures.textureId(sprite),grid,0,0,1,1));
                        },required),16,(sprite,grid) -> {
                    eyeRequests.add(sprite);
                    return availableProjectImage(sprite,grid).map(image -> new DefaultModelRuntime.FixedOverlay(sprite,image));
                }, model -> { fail("Spider must no longer request any hat model: " + model); return null; });
        var handle = runtime.resolve(SpiderTestModel.create(),target.id(),material,id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend(),handle.reason());
        assertEquals(List.of(tile.withSuffix("_connected")),sourceRequests);
        assertEquals(ResourceLocation.parse("create_biotech:entity/spider_assembly_table/eye_default"),eyeRequests.getLast());
        assertEquals(2,handle.textureBatches());
        assertEquals(66,handle.plan().orElseThrow().originalQuads());
    }

    private static Optional<PixelImage> availableProjectImage(ResourceLocation sprite,
            com.fluorineuck.castedmaterials.mapping.IntSize grid) {
        Path path = textureFixture(sprite);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            PixelImage image = readPng(path);
            return grid.integerScaleOf(image.width(),image.height()).isPresent() ? Optional.of(image) : Optional.empty();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    @Test
    void unchangedProductionSpiderUsesUvWithoutRequestingComposition() throws Exception {
        Path project = Path.of(System.getProperty("castedMaterials.projectDir"));
        var target = DefinitionParser.parseTarget(id("target"), Files.readString(project.resolve(
                "tools/test-resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json")));
        var snapshot = new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of());
        var runtime = new DefaultModelRuntime(() -> snapshot, () -> MaterialPalette.from(List.of(id("material"))),
                (t, m, global, required) -> Optional.of(Map.of("all",
                        new SourceSprite(id("source"), target.sourceGrids().get("all"), 0,0,1,1))),
                16, (sprite, grid) -> {
                    try {
                        Path file = textureFixture(sprite);
                        if (!Files.isRegularFile(file)) return Optional.empty();
                        var image = javax.imageio.ImageIO.read(file.toFile());
                        return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite, new PixelImage(image.getWidth(),image.getHeight(),
                                image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()))));
                    } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                });
        var handle = runtime.resolve(SpiderTestModel.create(), target.id(), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV, handle.backend(), handle.reason());
        assertEquals(66, handle.plan().orElseThrow().originalQuads(), "Use the actual machine-owned spider geometry");
    }

    @Test
    void standaloneApiUsesMaterialThenRestoresOnlyBaseForUnavailableOrClearedMaterial() throws Exception {
        var target = DefinitionParser.parseTarget(ResourceLocation.parse("create_biotech:spider_assembly_table/spider"),
                Files.readString(PROJECT.resolve(
                        "tools/test-resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json")));
        ResourceLocation material = ResourceLocation.parse("create:brass_casing");
        ResourceLocation sourceTexture = ResourceLocation.parse("create:textures/block/brass_casing_connected.png");
        ResourceLocation baseTexture = ResourceLocation.parse("create_biotech:textures/entity/spider_assembly_table/spider.png");
        ResourceLocation baseEyes = ResourceLocation.parse("create_biotech:textures/entity/spider_assembly_table/eye_00.png");
        TextureLayer eyeLayer = (TextureLayer) target.layers().getFirst();
        ResourceLocation overlayId = eyeLayer.texture();
        ResourceLocation fixedEyes = MaterialTextures.textureId(overlayId);
        PixelImage overlay = readTexture(overlayId);
        var snapshot = new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of());
        var runtime = new DefaultModelRuntime(() -> snapshot, () -> MaterialPalette.from(List.of(material)),
                (requestedTarget, requestedMaterial, global, required) -> {
                    assertEquals(target.id(), requestedTarget.id());
                    assertEquals(material, requestedMaterial);
                    return Optional.of(Map.of("all", new SourceSprite(sourceTexture,
                            target.sourceGrids().get("all"), 0, 0, 1, 1)));
                }, 16, (sprite, grid) -> sprite.equals(overlayId)
                        ? Optional.of(new DefaultModelRuntime.FixedOverlay(sprite, overlay)) : Optional.empty());

        var runtimeField = CastedMaterialsClient.class.getDeclaredField("modelRuntime");
        runtimeField.setAccessible(true);
        Object previousRuntime = runtimeField.get(null);
        try {
            runtimeField.set(null, runtime);
            var root = SpiderTestModel.create();
            var encased = new RecordingBuffers();
            var encasedHandle = CastedMaterialsClient.resolveModel(root,target.id(),material,baseTexture);
            encasedHandle.render(new PoseStack(),encased,RenderType::entityCutoutNoCull,15728640,0,-1);
            encasedHandle.renderEmissive(new PoseStack(),encased,15728640,0,-1);

            RenderType casingType = RenderType.entityCutoutNoCull(sourceTexture);
            RenderType overlayType = RenderType.entityCutoutNoCull(fixedEyes);
            RenderType fixedEyeType = RenderType.eyes(fixedEyes);
            assertTrue(encased.vertexCount(casingType) > 0,
                    "The standalone API must draw the material source, not silently use its base texture");
            assertTrue(encased.vertexCount(overlayType) > 0, "Fixed eyes remain part of the UV body overlay");
            assertEquals(encased.vertexCount(overlayType), encased.vertexCount(fixedEyeType),
                    "Only surviving emissive eye fragments may be drawn in the glow pass");
            assertEquals(Set.of(casingType, overlayType, fixedEyeType), encased.vertices.keySet(),
                    "A material handle must not also draw the fallback skin or fallback eyes");

            // An installed but unavailable material belongs to the same base-only FALLBACK path.
            var failedMaterial = new RecordingBuffers();
            var failedHandle = CastedMaterialsClient.resolveModel(root,target.id(),
                    ResourceLocation.parse("example:unavailable_casing"),baseTexture);
            failedHandle.render(new PoseStack(),failedMaterial,RenderType::entityCutoutNoCull,15728640,0,-1);
            failedHandle.renderEmissive(new PoseStack(),failedMaterial,15728640,0,-1);
            assertEquals(CastedModelHandle.Backend.FALLBACK,failedHandle.backend());
            assertEquals(Set.of(RenderType.entityCutoutNoCull(baseTexture)),failedMaterial.vertices.keySet());
            assertEquals(66 * 4,failedMaterial.vertexCount(RenderType.entityCutoutNoCull(baseTexture)));

            var plain = new RecordingBuffers();
            var plainHandle = CastedMaterialsClient.resolveModel(root,target.id(),null,baseTexture);
            plainHandle.render(new PoseStack(),plain,RenderType::entityCutoutNoCull,15728640,0,-1);
            plainHandle.renderEmissive(new PoseStack(),plain,15728640,0,-1);

            RenderType baseType = RenderType.entityCutoutNoCull(baseTexture);
            RenderType baseEyeType = RenderType.eyes(baseEyes);
            assertEquals(CastedModelHandle.Backend.FALLBACK,plainHandle.backend());
            assertEquals(Set.of(baseType), plain.vertices.keySet(), "Clearing material must also remove its eye pass");
            assertEquals(66 * 4, plain.vertexCount(baseType), "Fallback retains all 66 supplied model faces");
            assertEquals(0, plain.vertexCount(baseEyeType), "Fallback uses only the supplied base skin");
        } finally {
            runtimeField.set(null, previousRuntime);
        }
    }

    // Catches missing/wrong fragments, shifted or mirrored UVs, transparent-hole fills,
    // duplicate coverage, recolored eyes, and a default path silently using composition.
    // The oracle is an offline pixel reference, not the geometry compiler.
    @ParameterizedTest(name = "production spider UV/pixel-reference parity: {0}")
    @ValueSource(strings = {"andesite_casing", "brass_casing", "copper_casing", "shadow_steel_casing",
            "refined_radiance_casing", "railway_casing", "biotech_casing", "explosion_proof_casing",
            "explosion_proof_casing_side", "asurine_casing"})
    void realProductionPlanMatchesCompositeAtEveryFacePixelCenter(String fixture) throws IOException {
        var target = DefinitionParser.parseTarget(ResourceLocation.parse("create_biotech:spider_assembly_table/spider"),
                Files.readString(PROJECT.resolve(
                        "tools/test-resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json")));
        String namespace = fixture.startsWith("biotech_") || fixture.startsWith("explosion_") || fixture.equals("asurine_casing")
                ? "create_biotech" : "create";
        ResourceLocation material = ResourceLocation.fromNamespaceAndPath(namespace, fixture.replace("_side", ""));
        ResourceLocation sourceTexture = ResourceLocation.fromNamespaceAndPath(namespace,
                "textures/block/" + fixture + "_connected.png");
        PixelImage source = readPng(PROJECT.resolve("tools/test-resources/fixtures/connected_spider/"
                + fixture + "_connected.png"));
        // One shared, synchronized palette. Dedicated materials still occupy their palette indices.
        var palette = MaterialPalette.from(java.util.stream.Stream.of(
                "create:andesite_casing", "create:brass_casing", "create:copper_casing", "create:railway_casing",
                "create:refined_radiance_casing", "create:shadow_steel_casing", "create_biotech:asurine_casing",
                "create_biotech:biotech_casing", "create_biotech:explosion_proof_casing")
                .map(ResourceLocation::parse).toList());
        // Independent artwork oracle: one numbered candidate (eye_00), with three dedicated overrides.
        // eye_default is an unnumbered fallback and must not join the generic candidate pool.
        String eyeName = switch (fixture) {
            case "andesite_casing", "copper_casing", "railway_casing" -> "eye_" + fixture;
            default -> "eye_00";
        };
        boolean visibleEyes = !fixture.equals("andesite_casing");
        ResourceLocation expectedEyes = ResourceLocation.parse("create_biotech:entity/spider_assembly_table/" + eyeName);
        List<Layer> layers = target.layersFor(material).stream().map(layer -> layer instanceof TextureLayer eye
                ? (Layer) new TextureLayer(eye.index(),expectedEyes,eye.grid(),eye.source(),eye.destination(),eye.emissive()) : layer).toList();
        List<TextureLayer> textureLayers = layers.stream().filter(TextureLayer.class::isInstance)
                .map(TextureLayer.class::cast).toList();
        assertEquals(1,textureLayers.size(), "Every material selects exactly one eye texture");
        PixelImage overlay = readTexture(expectedEyes);
        Map<ResourceLocation, PixelImage> layerImages = new HashMap<>();
        try (var files = Files.list(PROJECT.resolve("tools/test-resources/assets/create_biotech/textures/entity/spider_assembly_table"))) {
            for (var file : files.filter(path -> path.getFileName().toString().startsWith("eye_")
                    && path.toString().endsWith(".png")).toList()) {
                String name=file.getFileName().toString();
                layerImages.put(ResourceLocation.parse("create_biotech:entity/spider_assembly_table/"+name.substring(0,name.length()-4)),readPng(file));
            }
        }
        boolean dedicatedBody = fixture.equals("andesite_casing");
        var bodyId = ResourceLocation.parse("create_biotech:entity/spider_assembly_table/body_andesite_casing");
        var bodyImage = readTexture(bodyId);
        layerImages.put(bodyId, bodyImage);
        String baseSlot = dedicatedBody ? "_body" : "all";
        var grid = target.sourceGrids().get("all");
        assertEquals(grid.width(), source.width(), "Use the full real CT sheet, not a downsampled tile");
        assertEquals(grid.height(), source.height());
        PixelImage expected;
        if (dedicatedBody) {
            // Independent authored-skin oracle; no generated mapping or body resolver is consulted.
            expected = new PixelImage(bodyImage.width(), bodyImage.height(), bodyImage.pixels());
            PixelReference.alphaOver(expected, overlay);
        } else {
            expected = PixelReference.render(UvMapping.compile(target),
                    Map.of("all", new SourceSlotImage(source, grid)), null, layers, layerImages::get);
        }

        var root = SpiderTestModel.create();
        var originals = CapturedModel.capture(root).quads(target.size());
        assertEquals(66, originals.size(), "Exercise every real machine-owned spider face");
        var snapshot = new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of(), layerImages.keySet());
        var runtime = new DefaultModelRuntime(() -> snapshot, () -> palette,
                (requestedTarget, requestedMaterial, global, required) -> {
                    assertEquals(target.id(), requestedTarget.id());
                    assertEquals(material, requestedMaterial);
                    assertEquals(dedicatedBody ? Set.of() : Set.of("all"), required, "Dedicated bodies bypass generated sources");
                    return Optional.of(Map.of("all", new SourceSprite(sourceTexture, grid, 0, 0, 1, 1)));
                }, 16, (sprite, requestedGrid) -> {
                    PixelImage image = layerImages.get(sprite);
                    if (image == null) return Optional.empty();
                    assertEquals(target.size(), requestedGrid);
                    return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite, image));
                }, ignored -> emptyBakedModel());
        var handle = runtime.resolve(root, target.id(), material, id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV, handle.backend(), fixture + ": " + handle.reason());
        var rendered = new RecordingBuffers();
        handle.render(new PoseStack(),rendered,RenderType::entityCutoutNoCull,0,0,-1);
        Set<RenderType> expectedBatches = new HashSet<>();
        expectedBatches.add(RenderType.entityCutoutNoCull(dedicatedBody ? MaterialTextures.textureId(bodyId) : sourceTexture));
        if (visibleEyes) expectedBatches.add(RenderType.entityCutoutNoCull(MaterialTextures.textureId(expectedEyes)));
        assertEquals(expectedBatches,rendered.vertices.keySet(),
                "Only opaque selected eyes bind a batch; dedicated transparent eyes must not leak fallback eyes");
        var glow = new RecordingBuffers();
        handle.renderEmissive(new PoseStack(),glow,15728640,0,-1);
        assertEquals(visibleEyes ? Set.of(RenderType.eyes(MaterialTextures.textureId(expectedEyes))) : Set.of(),
                glow.vertices.keySet(),"The emissive pass follows the same selected eye texture");
        var plan = handle.plan().orElseThrow();
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(originals.size(), plan.faces().size());
        assertEquals(originals.size(), plan.originalQuads());
        Set<String> overlaySlots = plan.faces().stream().flatMap(List::stream)
                .map(fragment -> fragment.sourceSlot()).filter(slot -> !slot.equals(baseSlot)).collect(Collectors.toSet());
        Map<String, PixelImage> imagesBySlot = new HashMap<>();
        imagesBySlot.put(baseSlot, dedicatedBody ? bodyImage : source);
        for (int i = 0; i < textureLayers.size(); i++) imagesBySlot.put("_layer_" + i,
                layerImages.get(textureLayers.get(i).texture()));
        assertEquals(imagesBySlot.keySet().stream().filter(overlaySlots::contains).count(), overlaySlots.size());
        String overlaySlot = "_layer_0";
        assertEquals(1 + overlaySlots.size(), handle.textureBatches(),
                "Only surviving body texture slots count as body batches; attachments are checked separately");

        int sampled = 0, covered = 0, holes = 0, eyeSamples = 0;
        Set<Integer> seenEyes = new HashSet<>();
        for (int faceIndex = 0; faceIndex < originals.size(); faceIndex++) {
            UvQuad original = originals.get(faceIndex);
            Surface originalSurface = Surface.of(original);
            var fragments = plan.faces().get(faceIndex).stream()
                    .map(fragment -> new FragmentSurface(fragment.sourceSlot(), Surface.of(fragment.quad()))).toList();
            for (var fragment : fragments) {
                UvQuad quad = fragment.surface().quad();
                assertEquals(original.nx(), quad.nx(), "Preserve face normal");
                assertEquals(original.ny(), quad.ny());
                assertEquals(original.nz(), quad.nz());
                for (UvVertex corner : quad.vertices()) {
                    assertNotNull(originalSurface.inside(Vector.of(corner)),
                            fixture + " fragment escaped original face " + faceIndex);
                }
            }
            for (int y = 0; y < target.size().height(); y++) {
                for (int x = 0; x < target.size().width(); x++) {
                    Vector point = originalSurface.positionAtUv(x + .5, y + .5);
                    if (point == null) continue;
                    sampled++;
                    String where = fixture + " face=" + faceIndex + " target=[" + x + "," + y + "]";
                    int covering = 0, actual = 0;
                    String sampledSlot = null;
                    for (var fragment : fragments) {
                        Parameters local = fragment.surface().inside(point);
                        if (local == null) continue;
                        covering++;
                        assertEquals(1, covering, "Overlapping geometry at " + where);
                        sampledSlot = fragment.slot();
                        PixelImage image = imagesBySlot.get(sampledSlot);
                        assertNotNull(image, "No independent source image for slot " + sampledSlot);
                        double u = fragment.surface().u(local), v = fragment.surface().v(local);
                        int sx = (int) Math.floor(u), sy = (int) Math.floor(v);
                        assertTrue(sx >= 0 && sx < image.width() && sy >= 0 && sy < image.height(),
                                "Source UV outside real texture at " + where + ": " + u + "," + v);
                        actual = image.get(sx, sy);
                        if (sampledSlot.equals(overlaySlot)) {
                            assertEquals(x + .5, u, EPSILON, "Fixed overlay U at " + where);
                            assertEquals(y + .5, v, EPSILON, "Fixed overlay V at " + where);
                        }
                    }
                    int expectedPixel = expected.get(x, y);
                    assertEquals(expectedPixel, actual, "UV sampling disagrees with real pixel composition at " + where);
                    if ((expectedPixel >>> 24) == 0) {
                        holes++;
                        assertEquals(0, covering, "Transparent hole must emit no fragment at " + where);
                    } else {
                        covered++;
                        assertEquals(1, covering, "Missing opaque geometry at " + where);
                    }
                    String expectedSlot = expectedTextureSlot(textureLayers, layerImages, x, y);
                    if (expectedSlot != null) {
                        if (expectedSlot.equals(overlaySlot)) {
                            eyeSamples++;
                            seenEyes.add(y * target.size().width() + x);
                        }
                        assertEquals(expectedSlot, sampledSlot, "Highest opaque ordered layer at " + where);
                    } else if (covering != 0) {
                        assertEquals(baseSlot, sampledSlot, "Transparent layers must expose the selected body at " + where);
                    }
                }
            }
        }
        // The 1328 occupied atlas pixels cover every host face; the other 720 are
        // outside the model's UV rectangles. Transparent in-face holes are exercised
        // separately by UvPlanCompilerTest and UvPlanCompilerCutoutOverlayTest.
        // Surface areas: head 384 + body 216 + abdomen 592 + eight legs * 136.
        assertEquals(2280, sampled, "Sample every texel center on all 66 real model faces");
        assertEquals(0, holes, "The production authored mask must cover every real face pixel");
        assertTrue(covered > 0, "The real model must exercise material sampling");
        if (visibleEyes) assertTrue(eyeSamples > 0, "The real model must exercise the selected eyes");
        else assertEquals(0,eyeSamples,"Transparent dedicated eyes leave only the authored body");
        int opaqueEyes = 0;
        for (int pixel : overlay.pixels()) if ((pixel >>> 24) != 0) opaqueEyes++;
        assertEquals(!visibleEyes ? 0 : fixture.equals("railway_casing") ? 4 : 6, opaqueEyes);
        assertEquals(opaqueEyes, seenEyes.size(), "Exactly the selected eyes survive; no original eye layer below them");
        assertEquals(sampled, covered + holes);

        int actualOutput = plan.faces().stream().mapToInt(List::size).sum();
        int actualAdditional = plan.faces().stream().mapToInt(face -> Math.max(0, face.size() - 1)).sum();
        int actualMaximum = plan.faces().stream().mapToInt(List::size).max().orElseThrow();
        assertEquals(actualOutput, plan.outputQuads(), "Report actual emitted geometry");
        assertEquals(actualAdditional, plan.additionalQuads());
        assertEquals(actualMaximum, plan.maxPiecesPerFace());
        var policy = target.renderPolicy();
        assertTrue(actualMaximum <= policy.maxPiecesPerFace());
        assertTrue(actualAdditional <= policy.maxAdditionalQuads());
        COSTS.put(fixture, Map.of("originalQuads", plan.originalQuads(), "outputQuads", actualOutput,
                "additionalQuads", actualAdditional, "maxPiecesPerFace", actualMaximum,
                "textureBatches", handle.textureBatches(), "facePixelSamples", sampled,
                "coveredFacePixels", covered, "transparentFacePixels", holes, "fixedEyeFacePixels", eyeSamples));
    }

    @AfterAll
    static void writeMeasuredProductionCosts() throws IOException {
        if (COSTS.isEmpty()) return;
        assertEquals(10, COSTS.size(), "All production material fixtures, including asurine, must contribute cost data");
        assertEquals(22_800, COSTS.values().stream().mapToInt(cost -> (Integer) cost.get("facePixelSamples")).sum(),
                "Compare all 2,280 real face pixels for all ten material fixtures");
        Path output = PROJECT.resolve("build/material-rendering/production-cost.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(COSTS));
    }

    private static String expectedTextureSlot(List<TextureLayer> layers,
                                              Map<ResourceLocation, PixelImage> images, int x, int y) {
        String slot = null;
        for (int i = 0; i < layers.size(); i++) {
            TextureLayer layer = layers.get(i);
            Integer pixel = layerPixel(layer, images.get(layer.texture()), x, y);
            if (pixel != null && (pixel >>> 24) == 255) slot = "_layer_" + i;
        }
        return slot;
    }

    private static Integer layerPixel(TextureLayer layer, PixelImage image, int x, int y) {
        var destination = layer.destination();
        if (x < destination.x() || y < destination.y()
                || x >= destination.x() + destination.width() || y >= destination.y() + destination.height()) return null;
        int logicalX = layer.source().x() + (x - destination.x()) * layer.source().width() / destination.width();
        int logicalY = layer.source().y() + (y - destination.y()) * layer.source().height() / destination.height();
        int scaleX = image.width() / layer.grid().width(), scaleY = image.height() / layer.grid().height();
        return image.get(logicalX * scaleX + scaleX / 2, logicalY * scaleY + scaleY / 2);
    }

    private static BakedModel emptyBakedModel() {
        return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(),
                new Class<?>[]{BakedModel.class}, (proxy, method, args) -> {
                    if (List.class.isAssignableFrom(method.getReturnType())) return List.of();
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == float.class) return 0f;
                    if (method.getReturnType() == double.class) return 0d;
                    return null;
                });
    }

    private static PixelImage readTexture(ResourceLocation id) throws IOException {
        Path source = textureFixture(id);
        assertTrue(Files.isRegularFile(source), "Missing local test texture " + source);
        return readPng(source);
    }

    private static Path textureFixture(ResourceLocation id) {
        Path asset = PROJECT.resolve("tools/test-resources/assets/" + id.getNamespace()
                + "/textures/" + id.getPath() + ".png");
        if (Files.isRegularFile(asset)) return asset;
        String fileName = id.getPath().substring(id.getPath().lastIndexOf('/') + 1) + ".png";
        return PROJECT.resolve("tools/test-resources/fixtures/connected_spider/" + fileName);
    }

    private static PixelImage readPng(Path path) throws IOException {
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, "Invalid fixture PNG " + path);
        return new PixelImage(image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    private static final class RecordingBuffers implements MultiBufferSource {
        private final Map<RenderType, Integer> vertices = new HashMap<>();

        int vertexCount(RenderType type) {
            return vertices.getOrDefault(type, 0);
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            vertices.putIfAbsent(type, 0);
            return new VertexConsumer() {
                @Override
                public VertexConsumer addVertex(float x, float y, float z) {
                    vertices.merge(type, 1, Integer::sum);
                    return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
                @Override public VertexConsumer setUv(float u, float v) { return this; }
                @Override public VertexConsumer setUv1(int u, int v) { return this; }
                @Override public VertexConsumer setUv2(int u, int v) { return this; }
                @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
            };
        }
    }

    private record FragmentSurface(String slot, Surface surface) { }
    private record Parameters(double a, double b) {
        boolean inside() { return a >= -EPSILON && b >= -EPSILON && a <= 1 + EPSILON && b <= 1 + EPSILON; }
    }
    private record Vector(double x, double y, double z) {
        static Vector of(UvVertex vertex) { return new Vector(vertex.x(), vertex.y(), vertex.z()); }
        Vector minus(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        Vector plus(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        Vector times(double scale) { return new Vector(x * scale, y * scale, z * scale); }
        double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
    }

    // Recover fragment coverage from actual local-space geometry, not from mapping cells.
    // Checking the fourth corner also prevents a malformed non-affine quad passing this sampler.
    private record Surface(UvQuad quad, Vector origin, Vector first, Vector second,
                           double aa, double ab, double bb, double determinant) {
        static Surface of(UvQuad quad) {
            Vector origin = Vector.of(quad.vertices().get(0));
            Vector first = Vector.of(quad.vertices().get(1)).minus(origin);
            Vector second = Vector.of(quad.vertices().get(3)).minus(origin);
            Vector error = origin.plus(first).plus(second).minus(Vector.of(quad.vertices().get(2)));
            assertTrue(error.dot(error) < EPSILON * EPSILON, "Fragment must preserve an affine parallelogram");
            double aa = first.dot(first), ab = first.dot(second), bb = second.dot(second);
            double determinant = aa * bb - ab * ab;
            assertTrue(determinant > 0, "No degenerate emitted quad");
            UvVertex p0 = quad.vertices().get(0), p1 = quad.vertices().get(1);
            UvVertex p2 = quad.vertices().get(2), p3 = quad.vertices().get(3);
            assertEquals(p1.u() + p3.u() - p0.u(), p2.u(), EPSILON, "Affine source U");
            assertEquals(p1.v() + p3.v() - p0.v(), p2.v(), EPSILON, "Affine source V");
            return new Surface(quad, origin, first, second, aa, ab, bb, determinant);
        }
        Parameters inside(Vector point) {
            Vector offset = point.minus(origin);
            double qa = offset.dot(first), qb = offset.dot(second);
            var local = new Parameters((qa * bb - qb * ab) / determinant, (qb * aa - qa * ab) / determinant);
            if (!local.inside()) return null;
            Vector residual = offset.minus(first.times(local.a())).minus(second.times(local.b()));
            return residual.dot(residual) <= EPSILON * EPSILON ? local : null;
        }
        Vector positionAtUv(double u, double v) {
            UvVertex p0 = quad.vertices().get(0), p1 = quad.vertices().get(1), p3 = quad.vertices().get(3);
            double au = p1.u() - p0.u(), av = p1.v() - p0.v();
            double bu = p3.u() - p0.u(), bv = p3.v() - p0.v();
            double du = u - p0.u(), dv = v - p0.v(), uvDeterminant = au * bv - av * bu;
            var local = new Parameters((du * bv - dv * bu) / uvDeterminant, (dv * au - du * av) / uvDeterminant);
            return local.inside() ? origin.plus(first.times(local.a())).plus(second.times(local.b())) : null;
        }
        double u(Parameters local) {
            UvVertex p0 = quad.vertices().get(0), p1 = quad.vertices().get(1), p3 = quad.vertices().get(3);
            return p0.u() + local.a() * (p1.u() - p0.u()) + local.b() * (p3.u() - p0.u());
        }
        double v(Parameters local) {
            UvVertex p0 = quad.vertices().get(0), p1 = quad.vertices().get(1), p3 = quad.vertices().get(3);
            return p0.v() + local.a() * (p1.v() - p0.v()) + local.b() * (p3.v() - p0.v());
        }
    }
}
