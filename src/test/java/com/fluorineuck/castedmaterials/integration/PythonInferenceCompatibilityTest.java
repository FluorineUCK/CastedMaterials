package com.fluorineuck.castedmaterials.integration;

import com.fluorineuck.castedmaterials.mapping.DefinitionParser;
import com.fluorineuck.castedmaterials.mapping.RenderPolicy;
import com.fluorineuck.castedmaterials.mapping.UvPlanCompiler;
import com.fluorineuck.castedmaterials.mapping.UvPlan.UvQuad;
import com.fluorineuck.castedmaterials.mapping.UvPlan.UvVertex;
import com.fluorineuck.castedmaterials.mapping.UvMapping;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import com.fluorineuck.castedmaterials.mapping.PixelReference;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class PythonInferenceCompatibilityTest {
    @Test
    void compactSourceBoundsOutputKeepsSelectedUvsAndTransparentGaps() throws IOException {
        // test_bounds_command.py compares fresh exact and fuzzy CLI output to this fixture.
        var target = DefinitionParser.parseTarget(id("compact_bounds"), fixture("compact_bounds"));
        var quad = new UvQuad(List.of(new UvVertex(0, 0, 0, 0, 0), new UvVertex(3, 0, 0, 3, 0),
                new UvVertex(3, 1, 0, 3, 1), new UvVertex(0, 1, 0, 0, 1)), 0, 0, 1);
        var plan = UvPlanCompiler.compile(target, List.of(quad), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(2, plan.outputQuads());
        assertEquals(1, plan.additionalQuads());
        for (int seed : new int[]{0, 79}) {
            for (int scale : new int[]{1, 2}) {
                var source = new PixelImage(22 * scale, 4 * scale);
                for (int y = 0; y < source.height(); y++) {
                    for (int x = 0; x < source.width(); x++) {
                        source.set(x, y, 0xff000000 | x << 16 | y << 8 | seed);
                    }
                }
                var output = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), null);
                assertArrayEquals(new int[]{source.get(20 * scale + scale / 2, 2 * scale + scale / 2), 0,
                        source.get(21 * scale + scale / 2, 2 * scale + scale / 2)}, output.pixels());
            }
        }
    }

    @Test
    void rgbTolerantPythonOutputUsesOrdinaryUvsNotBakedSampleColors() throws IOException {
        var target = DefinitionParser.parseTarget(id("fuzzy_rotated"), fixture("fuzzy_rotated"));
        var quad = new UvQuad(List.of(new UvVertex(0, 0, 0, 0, 0), new UvVertex(6, 0, 0, 6, 0),
                new UvVertex(6, 8, 0, 6, 8), new UvVertex(0, 8, 0, 0, 8)), 0, 0, 1);
        var plan = UvPlanCompiler.compile(target, List.of(quad), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(1, plan.outputQuads());
        assertEquals(0, plan.additionalQuads());
        for (int scale : new int[]{1, 2}) {
            var source = new PixelImage(16 * scale, 12 * scale);
            for (int y = 0; y < source.height(); y++) {
                for (int x = 0; x < source.width(); x++) {
                    source.set(x, y, 0xff000000 | x << 16 | y << 8 | (x ^ y));
                }
            }
            var output = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), null);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 6; x++) {
                    assertEquals(source.get((3 + y) * scale + scale / 2, (9 - x) * scale + scale / 2),
                            output.get(x, y));
                }
            }
        }
    }

    // test_infer_command.py compares fresh CLI output against these same fixtures.
    @Test
    void parsesInferredRotationAndAppliesItToDifferentMaterialsAndSourceScales() throws IOException {
        var target = DefinitionParser.parseTarget(id("inferred_rotation"), fixture("rotated"));
        for (int seed : new int[]{0, 79}) {
            for (int scale : new int[]{1, 2}) {
                var source = new PixelImage(3 * scale, 2 * scale);
                for (int y = 0; y < source.height(); y++) {
                    for (int x = 0; x < source.width(); x++) source.set(x, y, color(x, y, seed));
                }
                var output = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), null);
                String[] rows = {"DA", "EB", "FC"};
                for (int y = 0; y < rows.length; y++) {
                    for (int x = 0; x < rows[y].length(); x++) {
                        int cell = rows[y].charAt(x) - 'A';
                        assertEquals(color((cell % 3) * scale + scale / 2,
                                        (cell / 3) * scale + scale / 2, seed), output.get(x, y));
                    }
                }
            }
        }
    }

    @Test
    void acceptsNonemptyDraftAndLeavesBothUnresolvedKindsTransparent() throws IOException {
        var target = DefinitionParser.parseTarget(id("inferred_draft"), fixture("draft"));
        var source = new PixelImage(3, 1);
        source.set(0, 0, color(0, 0, 79));
        source.set(1, 0, color(1, 0, 79));
        source.set(2, 0, color(2, 0, 79));
        var output = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), null);
        assertArrayEquals(new int[]{color(0, 0, 79), 0, 0}, output.pixels());
        assertTrue(target.overlay().isEmpty());
    }

    @Test
    void acceptsOptimizedTwoSelectionPartitionWithoutOverlapsOrChangedUvs() throws IOException {
        var target = DefinitionParser.parseTarget(id("optimized_partition"), fixture("optimized_partition"));
        assertEquals(1, target.regions().size());
        assertEquals(1, target.pixels().size());
        var source = new PixelImage(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) source.set(x, y, 0xff000000 | x << 16 | y << 8 | (x + y));
        }
        var output = PixelReference.render(UvMapping.compile(target), Map.of("all", new SourceSlotImage(source, target.sourceGrids().get("all"))), null);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                assertEquals(x < 2 || y == 1 ? source.get(x + 4, y + 5) : 0, output.get(x, y));
            }
        }
    }

    @Test
    void modelAwarePythonFixtureKeepsFaceUvsAndComposedCoordinatesAcrossMaterialFamilies() throws IOException {
        var target = DefinitionParser.parseTarget(id("model_aware"), fixture("model_aware"));
        UvQuad left = new UvQuad(List.of(new UvVertex(0,0,0,0,0),new UvVertex(2,0,0,2,0),
                new UvVertex(2,2,0,2,2),new UvVertex(0,2,0,0,2)),0,0,1);
        UvQuad right = new UvQuad(List.of(new UvVertex(2,0,0,2,0),new UvVertex(6,0,0,6,0),
                new UvVertex(6,2,0,6,2),new UvVertex(2,2,0,2,2)),0,0,1);
        var plan = UvPlanCompiler.compile(target,List.of(left,right,right),RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(),plan.rejectionReason());
        assertEquals(3,plan.originalQuads());
        assertEquals(3,plan.outputQuads());
        assertEquals(1,plan.maxPiecesPerFace());
        assertEquals(0,plan.additionalQuads());
        float[][][] corners = {{{1,1},{3,1},{3,3},{1,3}},{{7,3},{11,3},{11,5},{7,5}}};
        for (int face = 0; face < 3; face++) {
            var fragment = plan.faces().get(face).getFirst();
            assertEquals("all",fragment.sourceSlot());
            var original = face == 0 ? left : right;
            for (int vertex = 0; vertex < 4; vertex++) {
                var actual = fragment.quad().vertices().get(vertex);
                assertEquals(corners[face == 0 ? 0 : 1][vertex][0],actual.u());
                assertEquals(corners[face == 0 ? 0 : 1][vertex][1],actual.v());
                assertEquals(original.vertices().get(vertex).x(),actual.x());
                assertEquals(original.vertices().get(vertex).y(),actual.y());
                assertEquals(original.vertices().get(vertex).z(),actual.z());
            }
        }
        // Source coordinate literals are independent of both Java transform code and Python inference.
        int[][] expectedX = {{1,2,7,8,9,10},{1,2,7,8,9,10}};
        int[][] expectedY = {{1,1,3,3,3,3},{2,2,4,4,4,4}};
        for (int seed : new int[]{0,41,79}) {
            for (int scale : new int[]{1,2}) {
                var source = new PixelImage(12 * scale,6 * scale);
                for (int y = 0; y < source.height(); y++) {
                    for (int x = 0; x < source.width(); x++) source.set(x,y,color(x,y,seed));
                }
                var output = PixelReference.render(UvMapping.compile(target), Map.of("all",new SourceSlotImage(source,target.sourceGrids().get("all"))),null);
                for (int y = 0; y < 2; y++) {
                    for (int x = 0; x < 6; x++) {
                        assertEquals(color(expectedX[y][x] * scale + scale / 2,
                                        expectedY[y][x] * scale + scale / 2,seed),output.get(x,y));
                    }
                }
            }
        }
    }

    private static int color(int x, int y, int seed) {
        return (80 + x * 7 + y * 11) << 24 | (17 + x * 23 + seed) << 16
                | (29 + y * 31 + seed) << 8 | (37 + x * 13 + y * 17 + seed);
    }

    private static String fixture(String name) throws IOException {
        try (var stream = PythonInferenceCompatibilityTest.class.getResourceAsStream(
                "/fixtures/definitions/python_inference/" + name + ".json")) {
            assertNotNull(stream, "Missing shared Python/Java inference fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
