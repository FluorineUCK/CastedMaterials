package com.fluorineuck.castedmaterials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.IntRect;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.ModelLayer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Function;

/** Immutable baked-model decorations attached to live model parts. */
public final class MaterialAttachments {
    private MaterialAttachments() {}

    private static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    private static final int POSITION = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION) / Integer.BYTES;
    private static final int UV = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0) / Integer.BYTES;
    private static final int NORMAL = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.NORMAL) / Integer.BYTES;

    public static Prepared prepare(ModelPart root, List<ModelLayer> layers,
                                   Function<ResourceLocation, BakedModel> modelLookup,
                                   Map<String, SourceSprite> sources) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(modelLookup, "modelLookup");
        Objects.requireNonNull(sources, "sources");
        List<Attachment> attachments = new ArrayList<>();
        LinkedHashSet<ResourceLocation> textures = new LinkedHashSet<>();
        int count = 0;
        for (ModelLayer layer : layers) {
            List<ModelPart> path = resolvePath(root, layer.part());
            BakedModel model = modelLookup.apply(layer.model());
            if (model == null) throw new IllegalArgumentException("missing attachment model " + layer.model());
            SourceSprite remap = layer.sourceSlot().map(slot -> {
                SourceSprite source = sources.get(slot);
                if (source == null) throw new IllegalArgumentException("missing attachment source " + slot + " for " + layer.model());
                return source;
            }).orElse(null);
            IntRect sourceRect = layer.source().orElseGet(() -> remap == null ? null
                    : new IntRect(0, 0, remap.grid().width(), remap.grid().height()));
            List<Quad> quads = capture(model, remap, sourceRect);
            quads.forEach(quad -> textures.add(quad.texture));
            count += quads.size();
            attachments.add(new Attachment(path, layer, quads));
        }
        return new Prepared(attachments, List.copyOf(textures), count);
    }

    private static List<ModelPart> resolvePath(ModelPart root, String name) {
        List<ModelPart> path = new ArrayList<>();
        path.add(root);
        ModelPart current = root;
        try {
            for (String component : name.split("/")) {
                if (component.isBlank()) throw new IllegalArgumentException("empty component");
                current = current.getChild(component);
                path.add(current);
            }
        } catch (RuntimeException missing) {
            throw new IllegalArgumentException("missing attachment part " + name, missing);
        }
        return List.copyOf(path);
    }

    private static List<Quad> capture(BakedModel model, SourceSprite remap, IntRect source) {
        List<BakedQuad> baked = new ArrayList<>();
        baked.addAll(model.getQuads(null, null, RandomSource.create(0)));
        for (Direction direction : Direction.values())
            baked.addAll(model.getQuads(null, direction, RandomSource.create(0)));
        List<Quad> result = new ArrayList<>(baked.size());
        for (BakedQuad quad : baked) {
            ResourceLocation texture = remap == null ? quad.getSprite().atlasLocation() : remap.texture();
            int[] data = quad.getVertices();
            List<Vertex> vertices = new ArrayList<>(4);
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * STRIDE;
                float x = Float.intBitsToFloat(data[base + POSITION]);
                float y = Float.intBitsToFloat(data[base + POSITION + 1]);
                float z = Float.intBitsToFloat(data[base + POSITION + 2]);
                // BLOCK stores signed normal bytes in the low XYZ bytes, then padding.
                int normal = data[base + NORMAL];
                float nx = (byte) normal / 127f;
                float ny = (byte) (normal >>> 8) / 127f;
                float nz = (byte) (normal >>> 16) / 127f;
                float u = Float.intBitsToFloat(data[base + UV]);
                float v = Float.intBitsToFloat(data[base + UV + 1]);
                if (remap != null) {
                    float localU = (u - quad.getSprite().getU0()) / (quad.getSprite().getU1() - quad.getSprite().getU0());
                    float localV = (v - quad.getSprite().getV0()) / (quad.getSprite().getV1() - quad.getSprite().getV0());
                    u = remap.u(source.x() + localU * source.width());
                    v = remap.v(source.y() + localV * source.height());
                }
                vertices.add(new Vertex(x, y, z, u, v, nx, ny, nz));
            }
            result.add(new Quad(texture, List.copyOf(vertices)));
        }
        return List.copyOf(result);
    }

    public static final class Prepared {
        private final List<Attachment> attachments;
        private final List<ResourceLocation> textures;
        private final int quadCount;
        private Prepared(List<Attachment> attachments, List<ResourceLocation> textures, int quadCount) {
            this.attachments = List.copyOf(attachments); this.textures = textures; this.quadCount = quadCount;
        }
        public List<ResourceLocation> textures() { return textures; }
        public int quadCount() { return quadCount; }

        public void render(PoseStack pose, MultiBufferSource buffers,
                           Function<ResourceLocation, RenderType> renderType,
                           int light, int overlay, int color) {
            Vector3f position = new Vector3f(), normal = new Vector3f();
            for (Attachment attachment : attachments) {
                if (attachment.path.stream().anyMatch(part -> !part.visible)) continue;
                pose.pushPose();
                try {
                    for (ModelPart part : attachment.path) part.translateAndRotate(pose);
                    var layer = attachment.layer;
                    pose.translate(layer.offset().x / 16.0, layer.offset().y / 16.0, layer.offset().z / 16.0);
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) layer.rotation().z));
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) layer.rotation().y));
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) layer.rotation().x));
                    pose.scale((float) layer.scale().x, (float) layer.scale().y, (float) layer.scale().z);
                    int layerLight = layer.emissive() ? LightTexture.FULL_BRIGHT : light;
                    for (Quad quad : attachment.quads) {
                        // Fetch only when this quad is about to be submitted; BufferSource may end
                        // the prior shared builder when the RenderType changes.
                        VertexConsumer consumer = buffers.getBuffer(renderType.apply(quad.texture));
                        var transform = pose.last();
                        for (Vertex vertex : quad.vertices) {
                            transform.pose().transformPosition(vertex.x, vertex.y, vertex.z, position);
                            transform.transformNormal(vertex.nx, vertex.ny, vertex.nz, normal);
                            consumer.addVertex(position.x, position.y, position.z, color, vertex.u, vertex.v,
                                    overlay, layerLight, normal.x, normal.y, normal.z);
                        }
                    }
                } finally { pose.popPose(); }
            }
        }
    }

    private record Attachment(List<ModelPart> path, ModelLayer layer, List<Quad> quads) {}
    private record Quad(ResourceLocation texture, List<Vertex> vertices) {}
    private record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
}
