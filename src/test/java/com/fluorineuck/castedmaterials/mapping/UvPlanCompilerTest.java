package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.UvPlan.*;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class UvPlanCompilerTest {
    @Test
    void sixTransformsUseIndependentSourceEdgeCoordinatesNotPixelCenters() {
        // Source [3,5,4,2], target origin [7,9]; expected clockwise target corner order.
        float[][][] expected = {
                {{3,5},{7,5},{7,7},{3,7}},
                {{3,7},{3,5},{7,5},{7,7}},
                {{7,7},{3,7},{3,5},{7,5}},
                {{7,5},{7,7},{3,7},{3,5}},
                {{7,5},{3,5},{3,7},{7,7}},
                {{3,7},{7,7},{7,5},{3,5}}
        };
        MappingTransform[] transforms = MappingTransform.values();
        for (int t = 0; t < transforms.length; t++) {
            int width = transforms[t].swapsAxes() ? 2 : 4;
            int height = transforms[t].swapsAxes() ? 4 : 2;
            TargetDefinition target = target(List.of(new RegionMapping("side", new IntRect(3,5,4,2),
                    new IntRect(7,9,width,height), transforms[t])), List.of());
            UvQuad input = quad(7,9,7 + width,9 + height);
            UvPlan plan = UvPlanCompiler.compile(target, List.of(input), RenderPolicy.DEFAULT);
            assertTrue(plan.eligible(), plan.rejectionReason());
            assertEquals(1, plan.outputQuads());
            UvFragment fragment = plan.faces().getFirst().getFirst();
            assertEquals("side", fragment.sourceSlot());
            for (int i = 0; i < 4; i++) {
                assertEquals(expected[t][i][0], fragment.quad().vertices().get(i).u(), 1e-6, transforms[t].name());
                assertEquals(expected[t][i][1], fragment.quad().vertices().get(i).v(), 1e-6, transforms[t].name());
                assertPosition(input.vertices().get(i), fragment.quad().vertices().get(i));
            }
        }
    }

    @Test
    void clipsFractionalFaceAndInterpolatesLocalPositionAcrossSlotBoundary() {
        TargetDefinition target = target(List.of(region("side",0,0,0,0,2,4),
                region("end",8,4,2,0,2,4)), List.of());
        UvPlan plan = UvPlanCompiler.compile(target, List.of(quad(0.5f,0.25f,3.5f,3.75f)), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(2, plan.outputQuads());
        assertEquals(1, plan.additionalQuads());
        UvQuad left = plan.faces().getFirst().getFirst().quad();
        UvQuad right = plan.faces().getFirst().getLast().quad();
        assertEquals(0.5f, left.vertices().getFirst().u());
        assertEquals(2, left.vertices().get(1).u());
        assertEquals(0.5f, left.vertices().get(1).x(), 1e-6);
        assertEquals(0.5f, right.vertices().getFirst().x(), 1e-6);
        assertEquals(8, right.vertices().getFirst().u());
        assertEquals(4.25f, right.vertices().getFirst().v());
        assertEquals(9.5f, right.vertices().get(1).u());
        assertEquals(7.75f, right.vertices().get(2).v());
        assertEquals(1, left.nz());
        assertEquals(1, right.nz());
    }

    @Test
    void keepsReversedWindingAndTiltedAffineGeometry() {
        UvQuad input = new UvQuad(List.of(new UvVertex(1,1,2,4,4), new UvVertex(1,0,1,4,0),
                new UvVertex(0,0,0,0,0), new UvVertex(0,1,1,0,4)), -1,-1,1);
        UvPlan plan = UvPlanCompiler.compile(target(List.of(region("side",0,0,0,0,4,4)), List.of()),
                List.of(input), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(input, plan.faces().getFirst().getFirst().quad());
    }

    @Test
    void retainsNormalizedTargetUvOnEveryRotatedMirroredFragmentWithIdenticalGeometry() {
        TargetDefinition target = target(List.of(
                new RegionMapping("side", new IntRect(3, 5, 2, 2), new IntRect(0, 0, 2, 2), MappingTransform.ROTATE_90),
                new RegionMapping("end", new IntRect(8, 2, 2, 2), new IntRect(2, 0, 2, 2), MappingTransform.MIRROR_X)), List.of());
        UvQuad input = new UvQuad(List.of(new UvVertex(1, 1, 2, 0, 0), new UvVertex(1, 0, 1, 4, 0),
                new UvVertex(0, 0, 0, 4, 2), new UvVertex(0, 1, 1, 0, 2)), -1, -1, 1);
        UvPlan plan = UvPlanCompiler.compile(target, List.of(input), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(2, plan.outputQuads());
        for (UvFragment fragment : plan.faces().getFirst()) {
            assertEquals(fragment.quad().nx(), fragment.targetQuad().nx());
            assertEquals(fragment.quad().ny(), fragment.targetQuad().ny());
            assertEquals(fragment.quad().nz(), fragment.targetQuad().nz());
            for (int vertex = 0; vertex < 4; vertex++) {
                UvVertex source = fragment.quad().vertices().get(vertex);
                UvVertex targetUv = fragment.targetQuad().vertices().get(vertex);
                assertPosition(source, targetUv);
                assertTrue(targetUv.u() >= 0 && targetUv.u() <= 1);
                assertTrue(targetUv.v() >= 0 && targetUv.v() <= 1);
            }
        }
        assertEquals(2f / 32, plan.faces().getFirst().getFirst().targetQuad().vertices().get(1).u(), 1e-6);
        assertEquals(2f / 32, plan.faces().getFirst().getLast().targetQuad().vertices().getFirst().u(), 1e-6);
    }

    @Test
    void mergesAffineCompatibleRegionsAndPixelsBeforeChargingBudget() {
        List<PixelMapping> pixels = new ArrayList<>();
        for (int y = 0; y < 4; y++) {
            for (int x = 2; x < 4; x++) pixels.add(pixel("side", x + 5,y + 7,x,y));
        }
        TargetDefinition target = target(List.of(region("side",5,7,0,0,2,4)), pixels);
        UvPlan plan = UvPlanCompiler.compile(target,List.of(quad(0,0,4,4)),new RenderPolicy(1,0,1));
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(1, plan.maxPiecesPerFace());
        assertEquals(0, plan.additionalQuads());
        assertEquals(9, plan.faces().getFirst().getFirst().quad().vertices().get(2).u());
        assertEquals(11, plan.faces().getFirst().getFirst().quad().vertices().get(2).v());
    }

    @Test
    void mergesRotatedRegionsWithSameGlobalAffineMap() {
        TargetDefinition target = target(List.of(
                new RegionMapping("side",new IntRect(3,5,2,2),new IntRect(7,9,2,2),MappingTransform.ROTATE_90),
                new RegionMapping("side",new IntRect(5,5,2,2),new IntRect(7,11,2,2),MappingTransform.ROTATE_90)), List.of());
        UvPlan plan = UvPlanCompiler.compile(target, List.of(quad(7,9,9,13)),
                new RenderPolicy(1,0,1));
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(1, plan.outputQuads());
        assertEquals(new UvVertex(1,1,0,7,5),plan.faces().getFirst().getFirst().quad().vertices().get(2));
    }

    @Test
    void clipsToEachFaceBeforeMergingTShapedGlobalMapping() {
        TargetDefinition target = target(List.of(region("side",0,0,0,0,2,1),
                region("side",0,1,0,1,4,1),region("side",0,2,0,2,2,1)),List.of());
        UvPlan plan = UvPlanCompiler.compile(target,List.of(quad(0,0,2,3)),
                new RenderPolicy(1,0,1));
        assertTrue(plan.eligible(),plan.rejectionReason());
        assertEquals(1,plan.outputQuads());
        assertEquals(1,xyArea(plan.faces().getFirst().getFirst().quad()),1e-6);
    }

    @Test
    void emittedGeometrySamplesEveryMappedPixelExactlyOnceWithNoCoverageAcrossHole() {
        TargetDefinition target = target(List.of(
                new RegionMapping("side",new IntRect(3,5,2,2),new IntRect(0,0,2,2),MappingTransform.ROTATE_90),
                new RegionMapping("end",new IntRect(8,2,2,3),new IntRect(2,0,2,3),MappingTransform.MIRROR_X)),
                List.of(pixel("side",1,9,0,2)));
        UvPlan plan = UvPlanCompiler.compile(target,List.of(quad(0,0,4,3)),RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(),plan.rejectionReason());
        assertEquals(3,plan.outputQuads());
        assertEquals(11d / 12,plan.faces().getFirst().stream().mapToDouble(f -> xyArea(f.quad())).sum(),1e-6);
        // Literal source pixel centers, independent of the shared mapping interpreter and compiler.
        float[][] expected = {{3.5f,6.5f},{3.5f,5.5f},{9.5f,2.5f},{8.5f,2.5f},
                {4.5f,6.5f},{4.5f,5.5f},{9.5f,3.5f},{8.5f,3.5f},
                {1.5f,9.5f},null,{9.5f,4.5f},{8.5f,4.5f}};
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                float px = (x + 0.5f) / 4, py = (y + 0.5f) / 3;
                int samples = 0;
                for (UvFragment fragment : plan.faces().getFirst()) {
                    UvVertex a = fragment.quad().vertices().get(0), b = fragment.quad().vertices().get(1);
                    UvVertex d = fragment.quad().vertices().get(3);
                    if (px <= a.x() || px >= b.x() || py <= a.y() || py >= d.y()) continue;
                    samples++;
                    assertNotNull(expected[y * 4 + x], "Hole acquired geometry");
                    double horizontal = (px - a.x()) / (b.x() - a.x());
                    double vertical = (py - a.y()) / (d.y() - a.y());
                    assertEquals(expected[y * 4 + x][0],a.u() + horizontal * (b.u() - a.u()) + vertical * (d.u() - a.u()),1e-5);
                    assertEquals(expected[y * 4 + x][1],a.v() + horizontal * (b.v() - a.v()) + vertical * (d.v() - a.v()),1e-5);
                    assertEquals(x < 2 ? "side" : "end",fragment.sourceSlot());
                }
                assertEquals(expected[y * 4 + x] == null ? 0 : 1,samples,"Pixel [" + x + "," + y + "]");
            }
        }
    }

    @Test
    void holesStayTransparentAndDoNotCancelOtherFacesAdditionalQuads() {
        TargetDefinition target = target(List.of(region("side",0,0,0,0,1,2),
                region("side",2,0,2,0,1,2)), List.of());
        UvPlan plan = UvPlanCompiler.compile(target,List.of(quad(0,0,3,2),quad(5,5,6,6)),RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(2, plan.originalQuads());
        assertEquals(2, plan.outputQuads());
        assertEquals(1, plan.additionalQuads());
        assertTrue(plan.faces().get(1).isEmpty());
        assertEquals(2f / 3f, plan.faces().getFirst().stream().mapToDouble(f -> xyArea(f.quad())).sum(), 1e-6);
    }

    @Test
    void reusedFaceUvsChargeRealGeometryAndIgnoreDeclaredComputedCost() {
        TargetDefinition plain = target(List.of(region("side",0,0,0,0,1,2),
                region("end",0,0,1,0,1,2)),List.of());
        TargetDefinition forged = new TargetDefinition(plain.id(),plain.size(),plain.sourceGrids(),plain.regions(),
                plain.pixels(),plain.overlay(),plain.materialOverrides(),Optional.of(new ComputedCost(0,0)));
        UvPlan plan = UvPlanCompiler.compile(forged,Collections.nCopies(3,quad(0,0,2,2)),
                new RenderPolicy(2,2,4));
        assertFalse(plan.eligible());
        assertTrue(plan.rejectionReason().contains("max_additional_quads"));
        assertEquals(3,plan.originalQuads());
        assertEquals(6,plan.outputQuads());
        assertEquals(2,plan.maxPiecesPerFace());
        assertEquals(3,plan.additionalQuads());
        assertTrue(plan.faces().stream().allMatch(List::isEmpty));
    }

    @Test
    void rejectsPieceBudgetAtActualFaceNotJsonEntryCount() {
        TargetDefinition target = target(List.of(region("side",0,0,0,0,1,2),
                region("end",0,0,1,0,1,2)),List.of());
        UvPlan plan = UvPlanCompiler.compile(target,List.of(quad(0,0,2,2)),
                new RenderPolicy(1,256,4));
        assertFalse(plan.eligible());
        assertTrue(plan.rejectionReason().contains("max_pieces_per_face"));
        assertEquals(2,plan.outputQuads());
    }

    @Test
    void highFragmentPixelMapIsRejectedWithoutPixelGeometryExplosion() {
        List<PixelMapping> pixels = new ArrayList<>();
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) pixels.add(pixel("side",0,0,x,y));
        }
        TargetDefinition target = new TargetDefinition(id("target"),new IntSize(256,256),
                Map.of("side",new IntSize(16,16)),List.of(),pixels,Optional.empty(),Map.of(),Optional.empty());
        UvPlan plan = assertTimeout(Duration.ofSeconds(5), () -> UvPlanCompiler.compile(target,
                List.of(quad(0,0,256,256)),RenderPolicy.DEFAULT));
        assertFalse(plan.eligible());
        assertEquals(65536,plan.outputQuads());
        assertEquals(65535,plan.additionalQuads());
        assertTrue(plan.faces().getFirst().isEmpty());
    }

    @Test
    void rejectsNonFiniteOutsideDegenerateSkewedAndNonAffineQuads() {
        TargetDefinition target = target(List.of(region("side",0,0,0,0,4,4)),List.of());
        List<UvQuad> unsupported = List.of(
                quad(-1,0,2,2), quad(0,0,33,2), quad(0,0,0,2), quad(0,0,Float.NaN,2),
                new UvQuad(List.of(new UvVertex(0,0,0,0,0),new UvVertex(1,0,0,3,1),
                        new UvVertex(1,1,0,4,4),new UvVertex(0,1,0,1,3)),0,0,1),
                new UvQuad(List.of(new UvVertex(0,0,0,0,0),new UvVertex(1,0,0,4,0),
                        new UvVertex(1,1,1,4,4),new UvVertex(0,1,0,0,4)),0,0,1),
                new UvQuad(List.of(new UvVertex(0,0,0,0,0),new UvVertex(1,0,0,4,0),
                        new UvVertex(2,1,0,4,4),new UvVertex(0,1,0,0,4)),0,0,1),
                new UvQuad(quad(0,0,4,4).vertices(),0,0,Float.NaN),
                new UvQuad(quad(0,0,4,4).vertices(),0,0,0));
        for (UvQuad input : unsupported) {
            UvPlan plan = UvPlanCompiler.compile(target,List.of(input),RenderPolicy.DEFAULT);
            assertFalse(plan.eligible(), input.toString());
            assertFalse(plan.rejectionReason().isBlank());
            assertTrue(plan.faces().getFirst().isEmpty());
        }
    }

    @Test
    void rejectsBowTieAndDuplicateCornerUvs() {
        var vertices = quad(0,0,4,4).vertices();
        TargetDefinition target = target(List.of(region("side",0,0,0,0,4,4)),List.of());
        for (List<UvVertex> invalid : List.of(List.of(vertices.get(0),vertices.get(2),vertices.get(1),vertices.get(3)),
                List.of(vertices.get(0),vertices.get(1),vertices.get(2),vertices.get(0)))) {
            assertFalse(UvPlanCompiler.compile(target,List.of(new UvQuad(invalid,0,0,1)),RenderPolicy.DEFAULT).eligible());
        }
    }

    @Test
    void validatesProgrammaticMappingInsteadOfOverwritingOverlappingRegions() {
        TargetDefinition target = target(List.of(region("side",0,0,0,0,2,2)),
                List.of(pixel("end",0,0,0,0)));
        UvPlan plan = UvPlanCompiler.compile(target,List.of(quad(0,0,2,2)),RenderPolicy.DEFAULT);
        assertFalse(plan.eligible());
        assertTrue(plan.rejectionReason().contains("mapping"));
    }

    @Test
    void vertexContainersAndCompiledFaceListsAreImmutable() {
        List<UvVertex> mutable = new ArrayList<>(quad(0,0,4,4).vertices());
        UvQuad captured = new UvQuad(mutable,0,0,1);
        mutable.clear();
        assertEquals(4,captured.vertices().size());
        assertThrows(UnsupportedOperationException.class, () -> captured.vertices().clear());
        assertThrows(IllegalArgumentException.class, () -> new UvQuad(List.of(),0,0,1));
        UvPlan plan = UvPlanCompiler.compile(target(List.of(region("side",0,0,0,0,4,4)),List.of()),
                List.of(captured),RenderPolicy.DEFAULT);
        assertThrows(UnsupportedOperationException.class, () -> plan.faces().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.faces().getFirst().clear());
    }

    private static TargetDefinition target(List<RegionMapping> regions,List<PixelMapping> pixels) {
        return new TargetDefinition(id("target"),new IntSize(32,32),
                Map.of("side",new IntSize(16,16),"end",new IntSize(16,16)),regions,pixels,
                Optional.empty(),Map.of(),Optional.empty());
    }

    private static RegionMapping region(String slot,int sx,int sy,int dx,int dy,int w,int h) {
        return new RegionMapping(slot,new IntRect(sx,sy,w,h),new IntRect(dx,dy,w,h),MappingTransform.IDENTITY);
    }

    private static PixelMapping pixel(String slot,int sx,int sy,int dx,int dy) {
        return new PixelMapping(slot,new IntPoint(sx,sy),new IntPoint(dx,dy));
    }

    private static UvQuad quad(float u0,float v0,float u1,float v1) {
        return new UvQuad(List.of(new UvVertex(0,0,0,u0,v0),new UvVertex(1,0,0,u1,v0),
                new UvVertex(1,1,0,u1,v1),new UvVertex(0,1,0,u0,v1)),0,0,1);
    }

    private static void assertPosition(UvVertex expected,UvVertex actual) {
        assertEquals(expected.x(),actual.x(),1e-6);
        assertEquals(expected.y(),actual.y(),1e-6);
        assertEquals(expected.z(),actual.z(),1e-6);
    }

    private static double xyArea(UvQuad quad) {
        double twiceArea = 0;
        for (int i = 0; i < 4; i++) {
            UvVertex a = quad.vertices().get(i), b = quad.vertices().get((i + 1) % 4);
            twiceArea += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(twiceArea) / 2;
    }
}
