package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.client.render.CastedTextureHandle;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface ClientRuntime {
    CastedTextureHandle resolve(ResourceLocation target, ResourceLocation material, ResourceLocation fallback);
    static ClientRuntime fallbackOnly() {
        return (target, material, fallback) -> CastedTextureHandle.fallback(fallback, RenderableState.FALLBACK_BASE, 0);
    }
}
