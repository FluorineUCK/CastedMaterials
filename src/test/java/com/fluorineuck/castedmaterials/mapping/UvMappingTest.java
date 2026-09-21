package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class UvMappingTest {
    @Test
    void oneInterpretationProducesLiteralEdgesCentersAndAsymmetricColorsForAllTransforms() {
        int[] colors = {0xff12a4e7, 0x806a351b, 0xffc12f71, 0x407bad31, 0xff0418ba, 0xffecc319};
        // Literal authoring expectations, NOT calculated with the production inverse transform.
        int[][] indices = {{0,1,2,3,4,5}, {4,2,0,5,3,1}, {5,4,3,2,1,0},
                {1,3,5,0,2,4}, {1,0,3,2,5,4}, {4,5,2,3,0,1}};
        double[][] topLeftEdges = {{3,5}, {3,8}, {5,8}, {5,5}, {5,5}, {3,8}};
        IntSize grid = new IntSize(12, 13);
        PixelImage source = new PixelImage(12, 13);
        for (int i = 0; i < 6; i++) source.set(3 + i % 2, 5 + i / 2, colors[i]);
        MappingTransform[] transforms = MappingTransform.values();
        for (int t = 0; t < transforms.length; t++) {
            int width = transforms[t].swapsAxes() ? 3 : 2, height = transforms[t].swapsAxes() ? 2 : 3;
            var target = target(new IntSize(16, 16), Map.of("side", grid),
                    List.of(new RegionMapping("side", new IntRect(3,5,2,3),
                            new IntRect(7,9,width,height), transforms[t])), List.of());
            var mapping = UvMapping.compile(target);
            var cell = mapping.at(7, 9);
            assertEquals(topLeftEdges[t][0], cell.mapU(7,9), transforms[t].name());
            assertEquals(topLeftEdges[t][1], cell.mapV(7,9), transforms[t].name());
            var result = PixelReference.render(mapping, Map.of("side", new SourceSlotImage(source, grid)), null);
            for (int i = 0; i < 6; i++) {
                int x = 7 + i % width, y = 9 + i / width, selected = indices[t][i];
                assertSame(cell, mapping.at(x, y));
                assertEquals(3.5 + selected % 2, cell.mapU(x + .5, y + .5), transforms[t].name());
                assertEquals(5.5 + selected / 2, cell.mapV(x + .5, y + .5), transforms[t].name());
                assertEquals(colors[selected], result.get(x, y), transforms[t].name());
            }
            assertNull(mapping.at(0, 0));
            assertEquals(0, result.get(0,0));
        }
    }

    @Test
    void sparseMappingNeedsOnlyUsedSlotsAndRetainsTransparentHoles() {
        var grid = new IntSize(512, 1); // Source grids are not subject to the target's 256 limit.
        var target = target(new IntSize(3, 2), Map.of("used", grid, "unused", grid), List.of(),
                List.of(new PixelMapping("used", new IntPoint(511, 0), new IntPoint(2, 1))));
        assertEquals(Set.of("used"), target.requiredSlots());
        assertThrows(UnsupportedOperationException.class, () -> target.requiredSlots().add("unused"));
        PixelImage source = new PixelImage(512, 1);
        source.set(511, 0, 0xff33a9b1);
        var mapping = UvMapping.compile(target);
        assertEquals(511.5, mapping.at(2,1).mapU(2.5,1.5));
        assertArrayEquals(new int[]{0,0,0,0,0,0xff33a9b1},
                PixelReference.render(mapping, Map.of("used", new SourceSlotImage(source, grid)), null).pixels());
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.at(3,0));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.at(-1,1));
    }

    @Test
    void validatesTargetLimitWithoutOverflowingLargeSourceTranslations() {
        int max = Integer.MAX_VALUE;
        var source = new IntRect(max - 3, max - 2, 3, 2);
        var grids = Map.of("side", new IntSize(max, max));
        var mapping = UvMapping.compile(target(new IntSize(256, 256), grids,
                List.of(new RegionMapping("side", source, new IntRect(250,250,3,2), MappingTransform.ROTATE_180)), List.of()));
        assertEquals((double) max, mapping.at(250,250).mapU(250,250));
        assertEquals(max - .5, mapping.at(250,250).mapV(250.5,250.5));
        for (int side : new int[]{257, 512}) {
            for (IntSize size : List.of(new IntSize(side,1), new IntSize(1,side))) {
                var invalid = target(size, grids, List.of(),
                        List.of(new PixelMapping("side", new IntPoint(0,0), new IntPoint(0,0))));
                var error = assertThrows(IllegalArgumentException.class, () -> UvMapping.compile(invalid));
                assertTrue(error.getMessage().contains(size.width() + "x" + size.height()));
                assertTrue(error.getMessage().contains("256"));
            }
        }
    }

    @Test
    void referenceRejectsBadInputsAndBlendsOverlay() {
        IntSize grid = new IntSize(1,1);
        var mapping = UvMapping.compile(target(grid, Map.of("side",grid), List.of(),
                List.of(new PixelMapping("side",new IntPoint(0,0),new IntPoint(0,0)))));
        var wrongGrid = assertThrows(IllegalArgumentException.class, () -> PixelReference.render(mapping,
                Map.of("side",new SourceSlotImage(new PixelImage(2,2),new IntSize(2,2))),null));
        assertTrue(wrongGrid.getMessage().contains("grid mismatch"));
        var source = Map.of("side", new SourceSlotImage(new PixelImage(1,1), grid));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PixelReference.render(mapping,source,new PixelImage(2,1))).getMessage().contains("overlay dimensions"));
        var composed = PixelReference.render(mapping,source,new PixelImage(1,1,new int[]{0x80d437a1}));
        assertEquals(0x80d437a1,composed.get(0,0));
    }

    private static TargetDefinition target(IntSize size, Map<String,IntSize> grids,
                                            List<RegionMapping> regions, List<PixelMapping> pixels) {
        return new TargetDefinition(id("target"),size,grids,regions,pixels,Optional.empty(),Map.of(),Optional.empty());
    }
}
