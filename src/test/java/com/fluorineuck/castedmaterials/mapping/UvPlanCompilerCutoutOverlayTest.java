package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.UvPlan.*;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.*;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.UvMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class UvPlanCompilerCutoutOverlayTest {
    @Test
    void compilesTheSuppliedInterpretationInsteadOfRecreatingTheOriginalTarget() {
        IntSize size = new IntSize(3, 2);
        TargetDefinition target = target(size, List.of(new RegionMapping("side", new IntRect(0, 0, 1, 2),
                new IntRect(0, 0, 1, 2), MappingTransform.IDENTITY)));
        PixelImage overlay = new PixelImage(3, 2, new int[]{0xff112233, 0, 0xff445566, 0xff778899, 0, 0xffaabbcc});
        UvMapping interpreted = UvMapping.compile(target).withCutoutOverlay(overlay, "eyes");
        UvPlan plan = UvPlanCompiler.compile(interpreted, List.of(quad(0, 0, 3, 2)), new RenderPolicy(2, 1, 1));

        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(2, plan.outputQuads());
        assertEquals(2, plan.maxPiecesPerFace());
        assertEquals(1, plan.additionalQuads());
        assertTrue(plan.faces().getFirst().stream().allMatch(fragment -> fragment.sourceSlot().equals("eyes")));
        assertEquals(new UvVertex(0, 0, 0, 0, 0), plan.faces().getFirst().getFirst().quad().vertices().getFirst());
        assertEquals(new UvVertex(1, 1, 0, 3, 2), plan.faces().getFirst().getLast().quad().vertices().get(2));
        assertPixel(plan, 0.5f, 0.5f, "eyes", 0.5f, 0.5f, 3, 2);
        assertPixel(plan, 1.5f, 0.5f, null, 0, 0, 3, 2);
        assertPixel(plan, 2.5f, 1.5f, "eyes", 2.5f, 1.5f, 3, 2);
    }

    @Test
    void mergesAdjacentOverlayPixelsBeforeChargingPerFaceAndAdditionalBudgets() {
        IntSize size = new IntSize(4, 2);
        UvMapping original = UvMapping.compile(target(size, List.of(new RegionMapping("side", new IntRect(0, 0, 4, 2),
                new IntRect(0, 0, 4, 2), MappingTransform.IDENTITY))));
        PixelImage overlay = new PixelImage(4, 2,
                new int[]{0xff111111, 0xff222222, 0, 0, 0xff333333, 0xff444444, 0, 0});
        UvMapping cutout = original.withCutoutOverlay(overlay, "eyes");
        List<UvQuad> faces = List.of(quad(0, 0, 4, 2), quad(0, 0, 4, 2));
        UvPlan accepted = UvPlanCompiler.compile(cutout, faces, new RenderPolicy(2, 2, 2));

        assertTrue(accepted.eligible(), accepted.rejectionReason());
        assertEquals(4, accepted.outputQuads());
        assertEquals(2, accepted.maxPiecesPerFace());
        assertEquals(2, accepted.additionalQuads());
        assertEquals(List.of("eyes", "side"), accepted.faces().getFirst().stream().map(UvFragment::sourceSlot).toList());

        UvPlan pieceRejected = UvPlanCompiler.compile(cutout, faces, new RenderPolicy(1, 2, 2));
        assertFalse(pieceRejected.eligible());
        assertTrue(pieceRejected.rejectionReason().contains("max_pieces_per_face"));
        assertEquals(4, pieceRejected.outputQuads());
        assertTrue(pieceRejected.faces().stream().allMatch(List::isEmpty));

        UvPlan totalRejected = UvPlanCompiler.compile(cutout, faces, new RenderPolicy(2, 1, 2));
        assertFalse(totalRejected.eligible());
        assertTrue(totalRejected.rejectionReason().contains("max_additional_quads"));
        assertEquals(2, totalRejected.additionalQuads());
        assertTrue(totalRejected.faces().stream().allMatch(List::isEmpty));

        PixelImage opaque = new PixelImage(4, 2,
                new int[]{-1, -1, -1, -1, -1, -1, -1, -1});
        UvPlan fullyMerged = UvPlanCompiler.compile(original.withCutoutOverlay(opaque, "eyes"), faces,
                new RenderPolicy(1, 0, 1));
        assertTrue(fullyMerged.eligible(), fullyMerged.rejectionReason());
        assertEquals(2, fullyMerged.outputQuads());
        assertEquals(0, fullyMerged.additionalQuads());
    }

    @Test
    void mixedTransformsKeepLiteralSourceCoordinatesAndReplaceWithoutOverlappingPasses() {
        IntSize size = new IntSize(12, 3);
        TargetDefinition target = target(size, List.of(
                region(0, MappingTransform.IDENTITY), region(2, MappingTransform.ROTATE_90),
                region(4, MappingTransform.ROTATE_180), region(6, MappingTransform.ROTATE_270),
                region(8, MappingTransform.MIRROR_X), region(10, MappingTransform.MIRROR_Y)));
        PixelImage overlay = new PixelImage(12, 3);
        for (int x = 0; x < 12; x += 2) overlay.set(x, 0, 0xff112233);
        overlay.set(11, 2, 0xffabcdef);
        UvPlan plan = UvPlanCompiler.compile(UvMapping.compile(target).withCutoutOverlay(overlay, "eyes"), List.of(quad(0, 0, 12, 3)),
                new RenderPolicy(32, 32, 2));
        assertTrue(plan.eligible(), plan.rejectionReason());
        // Literal coordinates for the remaining three pixels in each transformed 2x2 region.
        float[][][] expected = {
                {{1.5f, 0.5f}, {0.5f, 1.5f}, {1.5f, 1.5f}},
                {{0.5f, 0.5f}, {1.5f, 1.5f}, {1.5f, 0.5f}},
                {{0.5f, 1.5f}, {1.5f, 0.5f}, {0.5f, 0.5f}},
                {{1.5f, 1.5f}, {0.5f, 0.5f}, {0.5f, 1.5f}},
                {{0.5f, 0.5f}, {1.5f, 1.5f}, {0.5f, 1.5f}},
                {{1.5f, 1.5f}, {0.5f, 0.5f}, {1.5f, 0.5f}}
        };
        for (int t = 0; t < 6; t++) {
            int x = t * 2;
            assertPixel(plan, x + 0.5f, 0.5f, "eyes", x + 0.5f, 0.5f, 12, 3);
            assertPixel(plan, x + 1.5f, 0.5f, "side", expected[t][0][0], expected[t][0][1], 12, 3);
            assertPixel(plan, x + 0.5f, 1.5f, "side", expected[t][1][0], expected[t][1][1], 12, 3);
            assertPixel(plan, x + 1.5f, 1.5f, "side", expected[t][2][0], expected[t][2][1], 12, 3);
        }
        for (int x = 0; x < 11; x++) assertPixel(plan, x + 0.5f, 2.5f, null, 0, 0, 12, 3);
        assertPixel(plan, 11.5f, 2.5f, "eyes", 11.5f, 2.5f, 12, 3);
    }

    private static void assertPixel(UvPlan plan, float x, float y, String slot, float u, float v, int width, int height) {
        float px = x / width, py = y / height;
        int coverage = 0;
        for (UvFragment fragment : plan.faces().getFirst()) {
            UvVertex a = fragment.quad().vertices().get(0), b = fragment.quad().vertices().get(1);
            UvVertex d = fragment.quad().vertices().get(3);
            if (px <= a.x() || px >= b.x() || py <= a.y() || py >= d.y()) continue;
            coverage++;
            assertEquals(slot, fragment.sourceSlot(), "slot at " + x + "," + y);
            double horizontal = (px - a.x()) / (b.x() - a.x()), vertical = (py - a.y()) / (d.y() - a.y());
            assertEquals(u, a.u() + horizontal * (b.u() - a.u()) + vertical * (d.u() - a.u()), 1e-5);
            assertEquals(v, a.v() + horizontal * (b.v() - a.v()) + vertical * (d.v() - a.v()), 1e-5);
        }
        assertEquals(slot == null ? 0 : 1, coverage, "coverage at " + x + "," + y);
    }

    private static RegionMapping region(int x, MappingTransform transform) {
        return new RegionMapping("side", new IntRect(0, 0, 2, 2), new IntRect(x, 0, 2, 2), transform);
    }

    private static TargetDefinition target(IntSize size, List<RegionMapping> regions) {
        return new TargetDefinition(id("cutout_target"), size, Map.of("side", size), regions, List.of(),
                Optional.empty(), Map.of(), Optional.empty());
    }

    private static UvQuad quad(float u0, float v0, float u1, float v1) {
        return new UvQuad(List.of(new UvVertex(0, 0, 0, u0, v0), new UvVertex(1, 0, 0, u1, v0),
                new UvVertex(1, 1, 0, u1, v1), new UvVertex(0, 1, 0, u0, v1)), 0, 0, 1);
    }

}
