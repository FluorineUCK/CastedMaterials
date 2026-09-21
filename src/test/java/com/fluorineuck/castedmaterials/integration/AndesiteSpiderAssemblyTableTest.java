package com.fluorineuck.castedmaterials.integration;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.MappingTransform;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.RegionMapping;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition;
import com.fluorineuck.castedmaterials.mapping.DefinitionParser;
import com.fluorineuck.castedmaterials.mapping.PixelReference;
import com.fluorineuck.castedmaterials.mapping.UvMapping;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class AndesiteSpiderAssemblyTableTest {
    private static final String TARGET =
            "/fixtures/definitions/create_biotech/spider_assembly_table_andesite.json";
    private static final String ANDESITE = "/fixtures/create/andesite_casing.png";
    private static final String SPIDER =
            "/fixtures/create_biotech/spider_assembly_table/spider.png";
    private static final String SPIDER_EYES =
            "/fixtures/create_biotech/spider_assembly_table/spider_eyes.png";

    @Test
    void coversEveryUsedSpiderUvPixelWithRealAndesiteCasing() throws IOException {
        TargetDefinition target = DefinitionParser.parseTarget(
                id("create_biotech:spider_assembly_table/andesite"), readText(TARGET));
        PixelImage andesite = readPng(ANDESITE);
        PixelImage spider = readPng(SPIDER);
        PixelImage eyes = readPng(SPIDER_EYES);

        assertEquals("c7da24aacfb6170ebd570e5a302a6c9d3f3c0b324314e3df79236209667f192a",
                sha256(ANDESITE));
        assertEquals("93d330a24bda6316db42cd6198120f5a540f591305db671a599ca966888a4704",
                sha256(SPIDER));
        assertEquals("e7db2dd0f6fa00e6b0cea46c2db554e9cf53224f5625612456da6d0332f4a3b1",
                sha256(SPIDER_EYES));
        assertEquals(16, andesite.width());
        assertEquals(16, andesite.height());
        assertEquals(64, spider.width());
        assertEquals(32, spider.height());

        PixelImage result = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(andesite, target.sourceGrids().get("all"))), null);

        assertEquals(64, result.width());
        assertEquals(32, result.height());
        assertExactMask(spider, result);
        assertRegionsUseAndesite(target, andesite, result);
        assertTrue(changedOpaquePixels(spider, result) >= 1_000);

        Path previewDirectory = Path.of(
                System.getProperty("castedMaterials.projectDir", "."),
                "build", "casted-materials-preview");
        Files.createDirectories(previewDirectory);
        writePng(result, previewDirectory.resolve("andesite_spider.png"));
        PixelImage withEyes = new PixelImage(64, 32, result.pixels());
        PixelReference.alphaOver(withEyes, eyes);
        writePng(withEyes, previewDirectory.resolve("andesite_spider_with_eyes.png"));
    }

    private static void assertExactMask(PixelImage expected, PixelImage actual) {
        int opaque = 0;
        for (int y = 0; y < expected.height(); y++) {
            for (int x = 0; x < expected.width(); x++) {
                boolean expectedOpaque = (expected.get(x, y) >>> 24) != 0;
                boolean actualOpaque = (actual.get(x, y) >>> 24) != 0;
                assertEquals(expectedOpaque, actualOpaque, "alpha mask at " + x + "," + y);
                if (actualOpaque) opaque++;
            }
        }
        assertEquals(1_344, opaque);
    }

    private static void assertRegionsUseAndesite(
            TargetDefinition target, PixelImage andesite, PixelImage output) {
        for (RegionMapping region : target.regions()) {
            assertEquals(MappingTransform.IDENTITY, region.transform());
            for (int y = 0; y < region.destination().height(); y++) {
                for (int x = 0; x < region.destination().width(); x++) {
                    assertEquals(
                            andesite.get(region.source().x() + x, region.source().y() + y),
                            output.get(region.destination().x() + x, region.destination().y() + y),
                            "mapped pixel in " + region.destination());
                }
            }
        }
    }

    private static int changedOpaquePixels(PixelImage original, PixelImage mapped) {
        int changed = 0;
        for (int y = 0; y < original.height(); y++) {
            for (int x = 0; x < original.width(); x++) {
                if ((original.get(x, y) >>> 24) != 0 && original.get(x, y) != mapped.get(x, y)) changed++;
            }
        }
        return changed;
    }

    private static String readText(String resource) throws IOException {
        try (InputStream stream = requiredResource(resource)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static PixelImage readPng(String resource) throws IOException {
        try (InputStream stream = requiredResource(resource)) {
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, "Invalid PNG " + resource);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return new PixelImage(image.getWidth(), image.getHeight(), pixels);
        }
    }

    private static void writePng(PixelImage image, Path path) throws IOException {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, image.width(), image.height(), image.pixels(), 0, image.width());
        assertTrue(ImageIO.write(buffered, "PNG", path.toFile()));
    }

    private static String sha256(String resource) throws IOException {
        try (InputStream stream = requiredResource(resource)) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static InputStream requiredResource(String path) {
        InputStream stream = AndesiteSpiderAssemblyTableTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing test fixture " + path);
        return stream;
    }

}
