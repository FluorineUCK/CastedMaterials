package com.fluorineuck.castedmaterials.integration;

import com.fluorineuck.castedmaterials.mapping.IntSize;
import com.fluorineuck.castedmaterials.mapping.DefinitionParser;
import com.fluorineuck.castedmaterials.mapping.UvMapping;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import com.fluorineuck.castedmaterials.mapping.PixelReference;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class PythonExportCompatibilityTest {
    // Python exports are asserted equal to this same schema fixture in test_mapping_contract.py.
    private static final String FIXTURE = "/fixtures/definitions/python_export/asymmetric_target.json";
    private static final String[][] EXPECTED_ROWS = {
            {"ABC", "DEF"}, {"DA", "EB", "FC"}, {"FED", "CBA"},
            {"CF", "BE", "AD"}, {"CBA", "FED"}, {"DEF", "ABC"}
    };

    @Test
    void parsesPythonExportAndSamplesAllTransformsAndSparsePixel() throws IOException {
        var target = DefinitionParser.parseTarget(id("python_export"), fixture());
        assertEquals(new IntSize(256, 256), target.size());
        assertEquals(new IntSize(512, 512), target.sourceGrids().get("all"));
        assertEquals(6, target.regions().size());
        assertEquals(1, target.pixels().size());
        assertTrue(target.overlay().isEmpty());
        assertTrue(target.materialOverrides().isEmpty());

        for (int[] material : new int[][]{{0, 1}, {79, 1}, {0, 2}}) {
            int seed = material[0], scale = material[1];
            var source = new PixelImage(512 * scale, 512 * scale);
            for (int y = 0; y < source.height(); y++) {
                for (int x = 0; x < source.width(); x++) source.set(x, y, coordinateColor(x, y, seed));
            }
            var result = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), null);
            var expected = new PixelImage(256, 256);
            for (int index = 0; index < EXPECTED_ROWS.length; index++) {
                for (int y = 0; y < EXPECTED_ROWS[index].length; y++) {
                    String row = EXPECTED_ROWS[index][y];
                    for (int x = 0; x < row.length(); x++) {
                        int cell = row.charAt(x) - 'A';
                        expected.set(1 + index * 6 + x, 1 + y,
                                coordinateColor((300 + cell % 3) * scale + scale / 2,
                                        (400 + cell / 3) * scale + scale / 2, seed));
                    }
                }
            }
            expected.set(255, 255, coordinateColor(511 * scale + scale / 2, 511 * scale + scale / 2, seed));
            assertArrayEquals(expected.pixels(), result.pixels(), "seed=" + seed + ", scale=" + scale);
        }
    }

    @Test
    void keepsExistingJavaTargetLimitsOnBothAxes() throws IOException {
        for (int axis = 0; axis < 2; axis++) {
            for (int invalid : new int[]{-1, 0, 257, 512}) {
                var root = JsonParser.parseString(fixture()).getAsJsonObject();
                var size = new JsonArray();
                size.add(axis == 0 ? invalid : 256);
                size.add(axis == 1 ? invalid : 256);
                root.add("size", size);
                var error = assertThrows(IllegalArgumentException.class,
                        () -> DefinitionParser.parseTarget(id("python_export"), root.toString()));
                assertTrue(error.getMessage().contains("$.size"), error.getMessage());
            }
        }
    }

    private static int coordinateColor(int x, int y, int seed) {
        int r = (x + seed) % 256;
        int g = (y * 3 + seed) % 256;
        int b = (x * 17 + y * 31 + seed) % 256;
        int a = 64 + (x * 7 + y * 11 + seed) % 192;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static String fixture() throws IOException {
        try (var stream = PythonExportCompatibilityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(stream, "Missing shared Python/Java fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
