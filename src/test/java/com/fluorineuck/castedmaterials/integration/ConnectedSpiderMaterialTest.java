package com.fluorineuck.castedmaterials.integration;

import com.fluorineuck.castedmaterials.mapping.SourceSlotResolver;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.MappingTransform;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition;
import com.fluorineuck.castedmaterials.mapping.DefinitionParser;
import com.fluorineuck.castedmaterials.mapping.UvMapping;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import com.fluorineuck.castedmaterials.mapping.PixelReference;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConnectedSpiderMaterialTest {
    private static final Path PROJECT = Path.of(System.getProperty("castedMaterials.projectDir"));
    private static final Path PACK = PROJECT.resolve("tools/resourcepacks/connected_spider");
    private static final Path INPUTS = PROJECT.resolve("tools/test-resources/fixtures/connected_spider");
    private static final Path OUTPUT = PROJECT.resolve("build/casted-materials-preview/connected-spider/java");
    private static final ResourceLocation TARGET = ResourceLocation.parse("create_biotech:spider_assembly_table/spider");

    // Catches wrong material overrides, treating the CT sheet as a 16x16 image,
    // sampling blank CT cells, missing UV islands, and changing the fixed eyes.
    @ParameterizedTest
    @ValueSource(strings = {"andesite", "brass", "copper", "shadow_steel", "refined_radiance", "railway"})
    void samplesRealConnectedSheetsAndPreservesAuthoredMaskAndEyes(String material) throws IOException {
        TargetDefinition target = target();
        PixelImage authored = readPng(INPUTS.resolve("spider_andesite_authored.png"));
        PixelImage overlay = overlay(target);
        ResourceLocation materialId = ResourceLocation.parse("create:" + material + "_casing");
        List<ResourceLocation> loaded = new ArrayList<>();
        var slots = SourceSlotResolver.resolveSlots(target, materialId, Optional.empty(), ignored -> {
            fail("Explicit connected-sheet override must not use particle/default texture lookup");
            return Optional.empty();
        }, (sprite, grid) -> {
            loaded.add(sprite);
            return Optional.of(new SourceSlotImage(fixture(sprite), grid))
                            .filter(source -> source.integerScale().isPresent());
        }).orElseThrow();
        var resolvedOverlay = SourceSlotResolver.resolveOverlay(target.materialOverrides().get(materialId), target,
                ignored -> Optional.of(overlay)).orElseThrow();

        assertEquals(List.of(ResourceLocation.parse("create:block/" + material + "_casing_connected")), loaded);
        assertEquals(128, slots.get("all").logicalGrid().width());
        assertEquals(128, slots.get("all").logicalGrid().height());
        assertTrue(target.regions().stream().anyMatch(region -> region.source().x() >= 16 || region.source().y() >= 16),
                "This fixture must exercise selected CT positions outside the base 16x16 tile");
        assertTrue(target.regions().size() + target.pixels().size() <= 256, "Keep the small texture mapping compact");

        var result = PixelReference.render(UvMapping.compile(target), slots, resolvedOverlay);
        assertEquals(64, result.width());
        assertEquals(32, result.height());
        int opaque = 0;
        int eyes = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int original = authored.get(x, y);
                int actual = result.get(x, y);
                assertEquals(original >>> 24, actual >>> 24, material + " alpha at " + x + "," + y);
                if ((actual >>> 24) != 0) opaque++;
                if ((overlay.get(x, y) >>> 24) != 0) {
                    eyes++;
                    assertEquals(original, overlay.get(x, y), "Overlay must preserve the authored eye");
                    assertEquals(original, actual, "Eyes must not be recolored with the casing");
                } else if (material.equals("andesite") && (original >>> 24) != 0) {
                    for (int shift : new int[]{0, 8, 16}) {
                        assertTrue(Math.abs((original >>> shift & 255) - (actual >>> shift & 255)) <= 16,
                                "Andesite adaptation exceeds 16/channel at " + x + "," + y);
                    }
                }
            }
        }
        assertEquals(1328, opaque);
        assertEquals(14, eyes);
        assertSelectedPixels(target, slots.get("all").image(), overlay, result);
        writePng(result, OUTPUT.resolve(material + ".png"));
    }

    // Catches accidental whole-sheet downsampling or a changed logical-grid scale.
    @ParameterizedTest
    @ValueSource(strings = {"biotech_casing", "explosion_proof_casing", "explosion_proof_casing_side"})
    void samplesRegisteredBiotechConnectedSheetsWithoutMaterialOverrides(String spriteName) throws IOException {
        TargetDefinition target = target();
        ResourceLocation material = ResourceLocation.parse("create_biotech:"
                + spriteName.replace("_side", ""));
        assertFalse(target.materialOverrides().containsKey(material));
        List<ResourceLocation> loaded = new ArrayList<>();
        var slots = SourceSlotResolver.resolveSlots(target, material, Optional.empty(),
                ignored -> Optional.of(ResourceLocation.parse("create_biotech:block/" + spriteName + "_connected")),
                (sprite, grid) -> {
                    loaded.add(sprite);
                    return Optional.of(new SourceSlotImage(fixture(sprite), grid))
                            .filter(source -> source.integerScale().isPresent());
                }).orElseThrow();
        var resolvedOverlay = SourceSlotResolver.resolveOverlay(target.materialOverrides().get(material), target,
                ignored -> Optional.of(overlayUnchecked(target))).orElseThrow();
        assertEquals(List.of(ResourceLocation.parse("create_biotech:block/" + spriteName + "_connected")), loaded);
        var result = PixelReference.render(UvMapping.compile(target), slots, resolvedOverlay);
        PixelImage authored = readPng(INPUTS.resolve("spider_andesite_authored.png"));
        PixelImage eyes = overlay(target);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(authored.get(x, y) >>> 24, result.get(x, y) >>> 24,
                        spriteName + " mask at " + x + "," + y);
                if ((eyes.get(x, y) >>> 24) != 0) assertEquals(eyes.get(x, y), result.get(x, y));
            }
        }
        assertSelectedPixels(target, slots.get("all").image(), eyes, result);
        writePng(result, OUTPUT.resolve(spriteName + ".png"));
    }

    private static PixelImage overlayUnchecked(TargetDefinition target) {
        try {
            return overlay(target);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Test
    void doubledConnectedSheetProducesTheSameLogicalTexture() throws IOException {
        TargetDefinition target = target();
        PixelImage source = readPng(INPUTS.resolve("andesite_casing_connected.png"));
        PixelImage doubled = new PixelImage(256, 256);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) doubled.set(x, y, source.get(x / 2, y / 2));
        }
        var normal = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), overlay(target));
        var scaled = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(doubled, target.sourceGrids().get("all"))), overlay(target));
        assertEquals(normal, scaled);
    }

    @Test
    void unavailableConnectedSheetDoesNotProduceAnIncompleteSpider() throws IOException {
        var resolved = SourceSlotResolver.resolveSlots(target(), ResourceLocation.parse("create:andesite_casing"),
                Optional.empty(), ignored -> Optional.empty(), (sprite, grid) -> Optional.empty());
        assertTrue(resolved.isEmpty());
    }

    private static void assertSelectedPixels(TargetDefinition target, PixelImage source,
                                            PixelImage overlay, PixelImage actual) {
        for (var region : target.regions()) {
            assertEquals(MappingTransform.IDENTITY, region.transform());
            for (int y = 0; y < region.source().height(); y++) {
                for (int x = 0; x < region.source().width(); x++) {
                    int selected = source.get(region.source().x() + x, region.source().y() + y);
                    assertEquals(255, selected >>> 24, "Selected CT region must not hit unused cells");
                    int dx = region.destination().x() + x, dy = region.destination().y() + y;
                    if ((overlay.get(dx, dy) >>> 24) == 0) assertEquals(selected, actual.get(dx, dy));
                }
            }
        }
        for (var pixel : target.pixels()) {
            int selected = source.get(pixel.source().x(), pixel.source().y());
            assertEquals(255, selected >>> 24);
            if ((overlay.get(pixel.destination().x(), pixel.destination().y()) >>> 24) == 0) {
                assertEquals(selected, actual.get(pixel.destination().x(), pixel.destination().y()));
            }
        }
    }

    private static TargetDefinition target() throws IOException {
        return DefinitionParser.parseTarget(TARGET, Files.readString(PACK.resolve(
                "assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json")));
    }

    private static PixelImage overlay(TargetDefinition target) throws IOException {
        ResourceLocation id = target.overlay().orElseThrow();
        return readPng(PACK.resolve("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png"));
    }

    private static PixelImage fixture(ResourceLocation sprite) {
        try {
            return readPng(INPUTS.resolve(sprite.getPath().substring(sprite.getPath().lastIndexOf('/') + 1) + ".png"));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static PixelImage readPng(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        assertNotNull(image, "Invalid PNG " + path);
        return new PixelImage(image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    private static void writePng(PixelImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        BufferedImage output = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, image.width(), image.height(), image.pixels(), 0, image.width());
        assertTrue(ImageIO.write(output, "PNG", path.toFile()));
    }
}
