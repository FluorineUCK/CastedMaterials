package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class UvMappingCompositionTest {
    @Test
    void rejectsOverlappingProgrammaticMappingLikeTheUvCompiler() {
        TargetDefinition overlapping = target(2, 3,
                List.of(new RegionMapping("side", new IntRect(0, 0, 2, 3),
                        new IntRect(0, 0, 2, 3), MappingTransform.IDENTITY)),
                List.of(new PixelMapping("side", new IntPoint(1, 2), new IntPoint(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> UvMapping.compile(overlapping));
    }

    @Test
    void rejectsMismatchedTransformDimensionsLikeTheUvCompiler() {
        TargetDefinition mismatched = target(2, 2,
                List.of(new RegionMapping("side", new IntRect(0, 0, 2, 3),
                        new IntRect(0, 0, 2, 2), MappingTransform.ROTATE_90)), List.of());
        assertThrows(IllegalArgumentException.class, () -> UvMapping.compile(mismatched));
    }

    private static final int[] SIX = {
            0xff000001, 0xff000002,
            0xff000003, 0xff000004,
            0xff000005, 0xff000006
    };

    @Test
    void appliesEverySupportedTransformExactly() {
        assertTransform(MappingTransform.IDENTITY, 2, 3,
                new int[]{1, 2, 3, 4, 5, 6});
        assertTransform(MappingTransform.ROTATE_90, 3, 2,
                new int[]{5, 3, 1, 6, 4, 2});
        assertTransform(MappingTransform.ROTATE_180, 2, 3,
                new int[]{6, 5, 4, 3, 2, 1});
        assertTransform(MappingTransform.ROTATE_270, 3, 2,
                new int[]{2, 4, 6, 1, 3, 5});
        assertTransform(MappingTransform.MIRROR_X, 2, 3,
                new int[]{2, 1, 4, 3, 6, 5});
        assertTransform(MappingTransform.MIRROR_Y, 2, 3,
                new int[]{5, 6, 3, 4, 1, 2});
    }

    @Test
    void appliesRectangleThenSparsePixelThenOverlay() {
        PixelImage source = new PixelImage(2, 2, new int[]{0xff112233, 0xff223344, 0xff334455, 0xff445566});
        TargetDefinition target = target(4, 2, new IntSize(2, 2),
                List.of(new RegionMapping("side", new IntRect(0, 0, 2, 2),
                        new IntRect(0, 0, 2, 2), MappingTransform.IDENTITY)),
                List.of(new PixelMapping("side", new IntPoint(1, 1), new IntPoint(3, 1))));
        PixelImage overlay = new PixelImage(4, 2);
        overlay.set(0, 0, 0x800000ff);

        PixelImage result = PixelReference.render(UvMapping.compile(target), Map.of("side", new SourceSlotImage(source, new IntSize(2, 2))), overlay);

        assertEquals(0xff081199, result.get(0, 0));
        assertEquals(0xff445566, result.get(3, 1));
    }

    @Test
    void samplesIntegerScaledFixturesAndRejectsMissingOrInvalidSources() {
        PixelImage scaled = new PixelImage(4, 6);
        scaled.set(1, 1, 0xffabcdef);
        PixelImage sampled = PixelReference.render(UvMapping.compile(target(1, 1, List.of(new RegionMapping("side", new IntRect(0, 0, 1, 1),
                        new IntRect(0, 0, 1, 1), MappingTransform.IDENTITY)), List.of())), Map.of("side", new SourceSlotImage(scaled, new IntSize(2, 3))), null);
        assertEquals(0xffabcdef, sampled.get(0, 0));

        TargetDefinition one = target(1, 1,
                List.of(new RegionMapping("side", new IntRect(0, 0, 1, 1),
                        new IntRect(0, 0, 1, 1), MappingTransform.IDENTITY)), List.of());
        var mapping = UvMapping.compile(one);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PixelReference.render(mapping, Map.of(), null)).getMessage().contains("missing source slot"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PixelReference.render(mapping,
                Map.of("side", new SourceSlotImage(new PixelImage(3, 2), new IntSize(2, 3))), null))
                .getMessage().contains("uniform integer scale"));
    }

    private static void assertTransform(MappingTransform transform, int width, int height, int[] lowBytes) {
        TargetDefinition target = target(width, height,
                List.of(new RegionMapping("side", new IntRect(0, 0, 2, 3),
                        new IntRect(0, 0, width, height), transform)), List.of());
        PixelImage result = PixelReference.render(UvMapping.compile(target), Map.of("side", new SourceSlotImage(new PixelImage(2, 3, SIX), new IntSize(2, 3))), null);
        int[] actual = result.pixels();
        for (int i = 0; i < actual.length; i++) {
            assertEquals(lowBytes[i], actual[i] & 0xff, transform + " pixel " + i);
        }
    }

    private static TargetDefinition target(int width, int height,
                                           List<RegionMapping> regions, List<PixelMapping> pixels) {
        return target(width, height, new IntSize(2, 3), regions, pixels);
    }

    private static TargetDefinition target(int width, int height, IntSize sourceGrid,
                                           List<RegionMapping> regions, List<PixelMapping> pixels) {
        return new TargetDefinition(id("target"), new IntSize(width, height),
                Map.of("side", sourceGrid), regions, pixels,
                Optional.empty(), Map.of(), Optional.empty());
    }
}
