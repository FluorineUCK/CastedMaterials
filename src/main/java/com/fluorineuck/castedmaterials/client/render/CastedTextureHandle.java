package com.fluorineuck.castedmaterials.client.render;

import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record CastedTextureHandle(ResourceLocation texture, UvTransform uv,
                                  RenderableState state, long generation) {
    public CastedTextureHandle {
        Objects.requireNonNull(texture); Objects.requireNonNull(uv); Objects.requireNonNull(state);
    }
    public static CastedTextureHandle fallback(ResourceLocation texture, RenderableState state, long generation) {
        return new CastedTextureHandle(texture, UvTransform.IDENTITY, state, generation);
    }
    public VertexConsumer wrap(VertexConsumer delegate) {
        return uv.equals(UvTransform.IDENTITY) ? delegate : new UvTransformingVertexConsumer(delegate, uv);
    }
}
