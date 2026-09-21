package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.ModelLayer;
import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.core.Direction;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialAttachmentsTest {
    @Test
    void rejectsMissingLivePartWithLayerContext() {
        ModelPart root = new ModelPart(List.of(), Map.of());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                MaterialAttachments.prepare(root, List.of(layer("body/hat")), ignored -> emptyModel(), Map.of()));
        assertTrue(error.getMessage().contains("body/hat"), error.getMessage());
    }

    @Test
    void rejectsMissingBakedModel() {
        ModelPart head = new ModelPart(List.of(), Map.of());
        ModelPart root = new ModelPart(List.of(), Map.of("head", head));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                MaterialAttachments.prepare(root, List.of(layer("head")), ignored -> null, Map.of()));
        assertTrue(error.getMessage().contains("test:hat"), error.getMessage());
    }

    @Test
    void prepareCapturesModelsOnceAndPreservesLayerOrder() {
        ModelPart head = new ModelPart(List.of(), Map.of());
        ModelPart root = new ModelPart(List.of(), Map.of("head", head));
        int[] lookups = {0};
        var prepared = MaterialAttachments.prepare(root, List.of(layer("head"), layer("head")), ignored -> {
            lookups[0]++;
            return emptyModel();
        }, Map.of());
        assertEquals(2, lookups[0]);
        assertEquals(0, prepared.quadCount());
        assertEquals(List.of(), prepared.textures());
    }


    @Test
    void remapsRealQuadAndFollowsLivePartTransformAndVisibility() {
        ModelPart head = new ModelPart(List.of(), Map.of());
        head.x = 16;
        ModelPart root = new ModelPart(List.of(), Map.of("head", head));
        TextureAtlasSprite original = sprite(id("block_atlas"), id("original"), 32, 32, 8, 0, 16, 16);
        BakedQuad quad = quad(original);
        SourceSprite replacement = new SourceSprite(id("material_atlas"),
                new com.fluorineuck.castedmaterials.mapping.IntSize(16, 16), .1f, .2f, .9f, 1f);
        ModelLayer layer = new ModelLayer(0, id("hat"), "head", new Vec3(16, 0, 0), Vec3.ZERO,
                new Vec3(1, 1, 1), Optional.of("all"), Optional.of(
                new com.fluorineuck.castedmaterials.mapping.TargetDefinition.IntRect(4, 0, 8, 16)), true);
        var prepared = MaterialAttachments.prepare(root, List.of(layer), ignored -> model(quad), Map.of("all", replacement));
        assertEquals(1, prepared.quadCount());
        assertEquals(List.of(id("material_atlas")), prepared.textures());

        Recording recording = new Recording();
        prepared.render(new PoseStack(), ignored -> recording,
                net.minecraft.client.renderer.RenderType::entityCutoutNoCull, 0, 7, -1);
        assertEquals(4, recording.vertices.size());
        assertEquals(1.75f, recording.vertices.getFirst()[0], .0001f); // live head + configured pixel offset
        assertEquals(.5f, recording.vertices.getFirst()[1], .0001f);
        assertEquals(-.75f, recording.vertices.getFirst()[2], .0001f);
        assertEquals(.2f, recording.vertices.getFirst()[4], .0001f);
        assertEquals(-1f, recording.vertices.getFirst()[6], .0001f);
        assertEquals(63 / 127f, recording.vertices.getFirst()[7], .0001f);
        assertEquals(31 / 127f, recording.vertices.getFirst()[8], .0001f);
        assertEquals(-1f, recording.vertices.get(1)[8], .0001f);
        assertEquals(-1f, recording.vertices.get(2)[7], .0001f);
        assertArrayEquals(new float[]{0,0,0}, java.util.Arrays.copyOfRange(recording.vertices.get(3),6,9));
        assertEquals(.3f, recording.vertices.getFirst()[3], .0001f);
        assertEquals(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                (int) recording.vertices.getFirst()[5]);

        head.x = -16;
        recording.vertices.clear();
        prepared.render(new PoseStack(), ignored -> recording,
                net.minecraft.client.renderer.RenderType::entityCutoutNoCull, 0, 7, -1);
        assertEquals(-.25f, recording.vertices.getFirst()[0], .0001f);
        head.visible = false;
        recording.vertices.clear();
        prepared.render(new PoseStack(), ignored -> recording,
                net.minecraft.client.renderer.RenderType::entityCutoutNoCull, 0, 7, -1);
        assertTrue(recording.vertices.isEmpty());
    }

    private static ModelLayer layer(String part) {
        return new ModelLayer(0, id("hat"), part, Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1),
                Optional.empty(), Optional.empty(), false);
    }

    static BakedModel model(BakedQuad quad) {
        return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getQuads")) return args[1] == null ? List.of(quad) : List.of();
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
    }

    static BakedQuad quad(TextureAtlasSprite sprite) {
        var writer = new QuadBakingVertexConsumer();
        writer.setSprite(sprite);
        writer.setDirection(Direction.SOUTH);
        writer.setShade(true);
        float[][] values = {{-.25f,.5f,-.75f, sprite.getU0(),sprite.getV0()}, {1.25f,-.5f,.75f,sprite.getU1(),sprite.getV0()},
                {1.5f,1.75f,-1.25f,sprite.getU1(),sprite.getV1()}, {-.75f,1.25f,1.5f,sprite.getU0(),sprite.getV1()}};
        float[][] normals = {{-1f, .5f, .25f}, {.25f, -.5f, -1f}, {.5f, -1f, .25f}, {0,0,0}};
        for (int i=0;i<4;i++) {
            writer.addVertex(values[i][0], values[i][1], values[i][2])
                    .setUv(values[i][3], values[i][4])
                    .setNormal(normals[i][0], normals[i][1], normals[i][2]);
        }
        return writer.bakeQuad();
    }

    static TextureAtlasSprite sprite(net.minecraft.resources.ResourceLocation atlas,
                                              net.minecraft.resources.ResourceLocation name,
                                              int atlasWidth, int atlasHeight, int x, int y, int width, int height) {
        NativeImage image = new NativeImage(width,height,false);
        SpriteContents contents = new SpriteContents(name,new FrameSize(width,height),image,ResourceMetadata.EMPTY);
        return new TestSprite(atlas,contents,atlasWidth,atlasHeight,x,y);
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(net.minecraft.resources.ResourceLocation atlas, SpriteContents contents,
                   int atlasWidth, int atlasHeight, int x, int y) {
            super(atlas,contents,atlasWidth,atlasHeight,x,y);
        }
    }

    private static final class Recording implements VertexConsumer {
        final java.util.ArrayList<float[]> vertices = new java.util.ArrayList<>();
        public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz) {
            vertices.add(new float[]{x,y,z,u,v,light,nx,ny,nz});
        }
        public VertexConsumer addVertex(float x,float y,float z){throw new AssertionError();}
        public VertexConsumer setColor(int r,int g,int b,int a){throw new AssertionError();}
        public VertexConsumer setUv(float u,float v){throw new AssertionError();}
        public VertexConsumer setUv1(int u,int v){throw new AssertionError();}
        public VertexConsumer setUv2(int u,int v){throw new AssertionError();}
        public VertexConsumer setNormal(float x,float y,float z){throw new AssertionError();}
    }

    private static BakedModel emptyModel() {
        return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getQuads")) return List.of();
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
    }
}
