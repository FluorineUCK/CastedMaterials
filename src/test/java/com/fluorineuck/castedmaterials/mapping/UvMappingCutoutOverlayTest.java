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

class UvMappingCutoutOverlayTest {
    @Test
    void replacesOpaqueCellsAndHolesButRetainsTransparentCellsAndOnlyVisibleRequiredSlots() {
        IntSize size = new IntSize(3, 2), grid = new IntSize(2, 1);
        UvMapping original = UvMapping.compile(target(size,
                Map.of("side", grid, "hidden", grid, "unused", grid), List.of(), List.of(
                        pixel("side", 0, 0, 0, 0), pixel("side", 1, 0, 1, 0), pixel("hidden", 0, 0, 0, 1))));
        PixelImage overlay = new PixelImage(3, 2, new int[]{0xff112233, 0x00abcdef, 0, 0xff445566, 0, 0xff778899});

        UvMapping cutout = original.withCutoutOverlay(overlay, "eyes");

        assertNotSame(original, cutout);
        assertEquals(new UvMapping.Affine("eyes", 1, 0, 0, 1, 0, 0), cutout.at(0, 0));
        assertEquals(cutout.at(0, 0), cutout.at(0, 1));
        assertEquals(cutout.at(0, 0), cutout.at(2, 1));
        assertEquals(2.5, cutout.at(2, 1).mapU(2.5, 1.5));
        assertEquals(1.5, cutout.at(2, 1).mapV(2.5, 1.5));
        assertEquals(original.at(1, 0), cutout.at(1, 0));
        assertNull(cutout.at(2, 0));
        assertNull(cutout.at(1, 1));
        assertEquals(Set.of("side", "eyes"), cutout.requiredSlots());
        assertEquals(Map.of("side", grid, "hidden", grid, "unused", grid, "eyes", size), cutout.sourceGrids());

        PixelImage result = PixelReference.render(cutout, Map.of(
                "side", new SourceSlotImage(new PixelImage(2, 1, new int[]{0xffaabbcc, 0x80718293}), grid),
                "eyes", new SourceSlotImage(overlay, size)), null);
        assertArrayEquals(new int[]{0xff112233, 0x80718293, 0, 0xff445566, 0, 0xff778899}, result.pixels());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> PixelReference.render(cutout, Map.of(), null)).getMessage().contains("missing source slot"));
    }

    @Test
    void mappingsAndExposedCollectionsStayIndependentOfLaterOverlayAndAdapterChanges() {
        IntSize size = new IntSize(2, 1);
        UvMapping original = UvMapping.compile(target(size, Map.of("side", size), List.of(),
                List.of(pixel("side", 0, 0, 0, 0), pixel("side", 1, 0, 1, 0))));
        PixelImage overlay = new PixelImage(2, 1, new int[]{0xff112233, 0});
        UvMapping cutout = original.withCutoutOverlay(overlay, "eyes");
        overlay.set(0, 0, 0);
        overlay.set(1, 0, 0xffabcdef);
        UvMapping next = cutout.withCutoutOverlay(overlay, "second");

        assertEquals("side", original.at(0, 0).slot());
        assertEquals("side", original.at(1, 0).slot());
        assertEquals(Set.of("side"), original.requiredSlots());
        assertEquals(Map.of("side", size), original.sourceGrids());
        assertEquals("eyes", cutout.at(0, 0).slot());
        assertEquals("side", cutout.at(1, 0).slot());
        assertEquals(Set.of("side", "eyes"), cutout.requiredSlots());
        assertEquals(Set.of("eyes", "second"), next.requiredSlots());
        assertEquals("second", next.at(1, 0).slot());
        assertThrows(UnsupportedOperationException.class, () -> cutout.sourceGrids().put("bad", size));
        assertThrows(UnsupportedOperationException.class, () -> cutout.requiredSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.sourceGrids().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.requiredSlots().clear());
    }

    @Test
    void transparentOverlayAddsItsGridWithoutRequiringItsSource() {
        IntSize size = new IntSize(2, 1);
        UvMapping original = UvMapping.compile(target(size, Map.of("side", size), List.of(),
                List.of(pixel("side", 1, 0, 0, 0))));
        UvMapping cutout = original.withCutoutOverlay(new PixelImage(2, 1, new int[]{0x00abcdef, 0}), "eyes");

        assertEquals(Set.of("side"), cutout.requiredSlots());
        assertEquals(Map.of("side", size, "eyes", size), cutout.sourceGrids());
        assertEquals(original.at(0, 0), cutout.at(0, 0));
        assertNull(cutout.at(1, 0));
        PixelImage result = PixelReference.render(cutout, Map.of("side", new SourceSlotImage(
                new PixelImage(2, 1, new int[]{0xff111111, 0xff222222}), size)), null);
        assertArrayEquals(new int[]{0xff222222, 0}, result.pixels());
        assertThrows(IllegalArgumentException.class, () -> cutout.withCutoutOverlay(new PixelImage(2, 1), "eyes"));
    }

    @Test
    void rejectsExistingUnusedSlotsDimensionsAndAnyPartialAlphaWithoutMutatingOriginal() {
        IntSize size = new IntSize(2, 1);
        UvMapping original = UvMapping.compile(target(size, Map.of("side", size, "unused", size), List.of(),
                List.of(pixel("side", 0, 0, 0, 0))));
        for (String slot : List.of("side", "unused")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> original.withCutoutOverlay(new PixelImage(2, 1), slot));
            assertTrue(error.getMessage().contains(slot));
        }
        for (PixelImage wrongSize : List.of(new PixelImage(1, 1), new PixelImage(2, 2))) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> original.withCutoutOverlay(wrongSize, "eyes"));
            assertTrue(error.getMessage().contains("dimension"));
        }
        for (int alpha : new int[]{1, 128, 254}) {
            PixelImage partial = new PixelImage(2, 1, new int[]{0xff123456, alpha << 24 | 0x112233});
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> original.withCutoutOverlay(partial, "eyes"));
            assertTrue(error.getMessage().contains("alpha"));
            assertTrue(error.getMessage().contains(Integer.toString(alpha)));
            assertTrue(error.getMessage().contains("0") && error.getMessage().contains("255"));
            assertEquals("side", original.at(0, 0).slot());
            assertNull(original.at(1, 0));
            assertEquals(Set.of("side"), original.requiredSlots());
        }
    }

    @Test
    void sixTransformsAndOverlayComposeToIndependentLiteralPixelsIncludingUnmappedHoles() {
        int a = 0xff12a4e7, b = 0x806a351b, c = 0xffc12f71;
        int d = 0x407bad31, e = 0xff0418ba, f = 0xffecc319;
        int eye = 0xff91a2b3, hole = 0xff8899aa;
        int[][] expected = {
                {eye, b, 0, c, d, 0, e, f, hole},
                {eye, c, a, f, d, b, 0, 0, hole},
                {eye, e, 0, d, c, 0, b, a, hole},
                {eye, d, f, a, c, e, 0, 0, hole},
                {eye, a, 0, d, c, 0, f, e, hole},
                {eye, f, 0, c, d, 0, a, b, hole}
        };
        IntSize size = new IntSize(3, 3), grid = new IntSize(2, 3);
        SourceSlotImage source = new SourceSlotImage(new PixelImage(2, 3, new int[]{a, b, c, d, e, f}), grid);
        PixelImage overlay = new PixelImage(3, 3, new int[]{eye, 0, 0, 0, 0x00abcdef, 0, 0, 0, hole});
        MappingTransform[] transforms = MappingTransform.values();
        for (int t = 0; t < transforms.length; t++) {
            MappingTransform transform = transforms[t];
            int width = transform.swapsAxes() ? 3 : 2, height = transform.swapsAxes() ? 2 : 3;
            UvMapping original = UvMapping.compile(target(size, Map.of("side", grid),
                    List.of(new RegionMapping("side", new IntRect(0, 0, 2, 3),
                            new IntRect(0, 0, width, height), transform)), List.of()));
            UvMapping cutout = original.withCutoutOverlay(overlay, "eyes");
            PixelImage adapted = PixelReference.render(cutout, Map.of("side", source,
                    "eyes", new SourceSlotImage(overlay, size)), null);
            PixelImage composited = PixelReference.render(original, Map.of("side", source), overlay);
            assertArrayEquals(expected[t], adapted.pixels(), transform.name());
            assertArrayEquals(expected[t], composited.pixels(), transform.name());
        }
    }

    private static TargetDefinition target(IntSize size, Map<String, IntSize> grids,
                                           List<RegionMapping> regions, List<PixelMapping> pixels) {
        return new TargetDefinition(id("cutout_target"), size, grids, regions, pixels,
                Optional.empty(), Map.of(), Optional.empty());
    }

    private static PixelMapping pixel(String slot, int sx, int sy, int dx, int dy) {
        return new PixelMapping(slot, new IntPoint(sx, sy), new IntPoint(dx, dy));
    }

}
