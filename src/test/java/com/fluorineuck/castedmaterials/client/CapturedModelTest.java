package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.UvPlanCompiler;
import com.fluorineuck.castedmaterials.mapping.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class CapturedModelTest {
    @Test
    void capturesLocalGeometryOnceAndPreservesLivePoseVisibilityAndAttributes() {
        var child = cubePart();
        var root = new ModelPart(List.of(), Map.of("animated", child));
        var captured = CapturedModel.capture(root);
        var quads = captured.quads(new IntSize(16, 16));
        assertEquals(6, quads.size());
        var target = new TargetDefinition(id("test"), new IntSize(16, 16), Map.of("all", new IntSize(16, 16)),
                List.of(new RegionMapping("all", new IntRect(0, 0, 16, 16), new IntRect(0, 0, 16, 16), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
        var plan = UvPlanCompiler.compile(target, quads, RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        var mesh = captured.bind(plan, Map.of("all", new SourceSprite(id("atlas"), new IntSize(16, 16), 0, 0, 1, 1)));
        child.x = 5; child.yRot = 0.7f;
        var expected = new Recording();
        root.render(new PoseStack(), expected, 15728880, 655360, 0xff123456);
        var actual = new Recording();
        mesh.render(new PoseStack(), ignored -> actual, 15728880, 655360, 0xff123456);
        assertVerticesEqual(expected.vertices, actual.vertices);
        assertEquals(24, actual.vertices.size());
        child.visible = false;
        var hidden = new Recording();
        mesh.render(new PoseStack(), ignored -> hidden, 0, 0, -1);
        assertTrue(hidden.vertices.isEmpty());
        child.visible = true; child.skipDraw = true;
        mesh.render(new PoseStack(), ignored -> hidden, 0, 0, -1);
        assertTrue(hidden.vertices.isEmpty());
    }

    @Test
    void sourceCoordinatesAreNormalizedIntoExistingSpriteBounds() {
        var sprite = new SourceSprite(id("atlas"), new IntSize(128, 128), 0.25f, 0.5f, 0.75f, 1f);
        assertEquals(0.375f, sprite.u(32));
        assertEquals(0.75f, sprite.v(64));
    }

    @Test
    void vanillaSpiderKeepsAll66FacesAndLiveChildTransformsWithoutExtraQuads() {
        var root = net.minecraft.client.model.SpiderModel.createSpiderBodyLayer().bakeRoot();
        var captured = CapturedModel.capture(root);
        var size = new IntSize(64, 32);
        var target = new TargetDefinition(id("spider"), size, Map.of("all", size),
                List.of(new RegionMapping("all", new IntRect(0, 0, 64, 32), new IntRect(0, 0, 64, 32), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
        var plan = UvPlanCompiler.compile(target, captured.quads(size), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertEquals(66, plan.originalQuads());
        assertEquals(66, plan.outputQuads());
        assertEquals(0, plan.additionalQuads());
        var mesh = captured.bind(plan, Map.of("all", new SourceSprite(id("atlas"), size, 0, 0, 1, 1)));
        root.getChild("head").yRot = 0.8f;
        root.getChild("right_front_leg").zRot += 0.4f;
        root.xScale = 1.5f; root.yScale = 0.5f;
        root.skipDraw = true; // Child cubes still render.
        var expected = new Recording();
        var actual = new Recording();
        root.render(new PoseStack(), expected, 41, 23, -1);
        mesh.render(new PoseStack(), ignored -> actual, 41, 23, -1);
        assertEquals(264, actual.vertices.size());
        assertVerticesEqual(expected.vertices, actual.vertices);
        root.visible = false;
        var hidden = new Recording();
        mesh.render(new PoseStack(), ignored -> hidden, 41, 23, -1);
        assertTrue(hidden.vertices.isEmpty());
    }

    public static ModelPart cubePart() {
        return new ModelPart(List.of(new ModelPart.Cube(0, 0, 0, 0, 0, 2, 2, 2,
                0, 0, 0, false, 16, 16, EnumSet.allOf(Direction.class))), Map.of());
    }

    @Test
    void completesEachTextureBatchBeforeAcquiringAnotherSharedBuffer() {
        var size = new IntSize(16, 16);
        var captured = CapturedModel.capture(cubePart());
        var target = new TargetDefinition(id("batches"), size, Map.of("a", size, "b", size),
                List.of(new RegionMapping("a", new IntRect(0, 0, 3, 16), new IntRect(0, 0, 3, 16), MappingTransform.IDENTITY),
                        new RegionMapping("b", new IntRect(3, 0, 13, 16), new IntRect(3, 0, 13, 16), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
        var plan = UvPlanCompiler.compile(target, captured.quads(size), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible());
        var mesh = captured.bind(plan, Map.of("a", new SourceSprite(id("a"), size, 0, 0, 1, 1),
                "b", new SourceSprite(id("b"), size, 0, 0, 1, 1)));
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var vertexCount = new java.util.concurrent.atomic.AtomicInteger();
        mesh.render(new PoseStack(), texture -> {
            int own = active.incrementAndGet(); // Like BufferSource: acquiring a new shared type ends the old buffer.
            return new Recording() {
                @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                        int overlay, int light, float nx, float ny, float nz) {
                    assertEquals(own, active.get(), "writing to a shared buffer that has already ended");
                    vertexCount.incrementAndGet();
                }
            };
        }, 0, 0, -1);
        assertEquals(2, active.get());
        assertEquals(plan.outputQuads() * 4, vertexCount.get());
    }

    @Test
    void alignedLayerUsesFragmentGeometryOriginalTargetUvsLivePoseAndOneSubmissionPerFragment() {
        var size = new IntSize(16, 16);
        var child = cubePart();
        var root = new ModelPart(List.of(), Map.of("animated", child));
        var captured = CapturedModel.capture(root);
        var target = new TargetDefinition(id("split"), size, Map.of("a", size, "b", size),
                List.of(new RegionMapping("a", new IntRect(0, 0, 3, 16), new IntRect(0, 0, 3, 16), MappingTransform.IDENTITY),
                        new RegionMapping("b", new IntRect(3, 0, 13, 16), new IntRect(3, 0, 13, 16), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
        var plan = UvPlanCompiler.compile(target, captured.quads(size), RenderPolicy.DEFAULT);
        assertTrue(plan.eligible(), plan.rejectionReason());
        assertTrue(plan.outputQuads() > plan.originalQuads());
        var mesh = captured.bind(plan, Map.of("a", new SourceSprite(id("a"), size, 0, 0, 1, 1),
                "b", new SourceSprite(id("b"), size, 0, 0, 1, 1)));
        child.x = 5;
        child.yRot = 0.7f;
        var base = new Recording();
        mesh.render(new PoseStack(), ignored -> base, 41, 23, 0xff123456);
        var layer = new Recording();
        mesh.renderLayer(new PoseStack(), layer, 99, 77, 0xffabcdef);
        assertEquals(base.vertices.size(), layer.vertices.size());
        assertEquals(plan.outputQuads() * 4, layer.vertices.size());
        for (int layerStart = 0; layerStart < layer.vertices.size(); layerStart += 4) {
            int matchingBase = -1;
            for (int baseStart = 0; baseStart < base.vertices.size(); baseStart += 4) {
                boolean matches = true;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float[] a = base.vertices.get(baseStart + vertex), b = layer.vertices.get(layerStart + vertex);
                    for (int component : new int[]{0, 1, 2, 5, 6, 7})
                        matches &= Math.abs(a[component] - b[component]) < 1e-6f;
                }
                if (matches) { matchingBase = baseStart; break; }
            }
            assertTrue(matchingBase >= 0, "layer quad must retain a direct fragment's vertex order");
            for (int vertex = 0; vertex < 4; vertex++) {
                float[] b = layer.vertices.get(layerStart + vertex);
                assertEquals(0xffabcdef, (int) b[8]);
                assertEquals(77, (int) b[9]);
                assertEquals(99, (int) b[10]);
                assertTrue(b[3] >= 0 && b[3] <= 1);
                assertTrue(b[4] >= 0 && b[4] <= 1);
            }
        }
        child.visible = false;
        var hidden = new Recording();
        mesh.renderLayer(new PoseStack(), hidden, 0, 0, -1);
        assertTrue(hidden.vertices.isEmpty());
    }

    @Test
    void nonDirectHandlesKeepVanillaFullModelLayerRendering() {
        var root = cubePart();
        root.yRot = .4f;
        var expected = new Recording();
        root.render(new PoseStack(), expected, 99, 77, 0xffabcdef);
        var fallback = CastedModelHandle.fallback(root, id("base"));
        var actual = new Recording();
        fallback.renderLayer(new PoseStack(), actual, 99, 77, 0xffabcdef);
        assertVerticesEqual(expected.vertices, actual.vertices);
    }

    private static void assertVerticesEqual(List<float[]> expected, List<float[]> actual) {
        assertEquals(expected.size(), actual.size());
        // The compiler may cyclically rotate each quad's first vertex but must preserve winding.
        for (int start = 0; start < expected.size(); start += 4) {
            boolean matched = false;
            for (int offset = 0; offset < 4; offset++) {
                boolean same = true;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float[] a = expected.get(start + vertex), b = actual.get(start + (vertex + offset) % 4);
                    for (int i = 0; i < a.length; i++) same &= Math.abs(a[i] - b[i]) < 0.00001f;
                }
                matched |= same;
            }
            assertTrue(matched, "quad starting at " + start);
        }
    }

    private static class Recording implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz) {
            vertices.add(new float[]{x,y,z,u,v,nx,ny,nz,color,overlay,light});
        }
        public VertexConsumer addVertex(float x,float y,float z){throw new AssertionError("bulk expected");}
        public VertexConsumer setColor(int r,int g,int b,int a){throw new AssertionError();}
        public VertexConsumer setUv(float u,float v){throw new AssertionError();}
        public VertexConsumer setUv1(int u,int v){throw new AssertionError();}
        public VertexConsumer setUv2(int u,int v){throw new AssertionError();}
        public VertexConsumer setNormal(float x,float y,float z){throw new AssertionError();}
    }
}
