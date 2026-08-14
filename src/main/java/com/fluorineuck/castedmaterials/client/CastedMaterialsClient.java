package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.client.render.CastedTextureHandle;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class CastedMaterialsClient {
    private static volatile ClientRuntime runtime = ClientRuntime.fallbackOnly();
    private CastedMaterialsClient() {}
    public static CastedTextureHandle resolve(ResourceLocation targetId, ResourceLocation materialId,
                                               ResourceLocation fallbackTexture) {
        try { return runtime.resolve(targetId, materialId, fallbackTexture); }
        catch (RuntimeException ignored) { return CastedTextureHandle.fallback(fallbackTexture, RenderableState.QUARANTINED, 0); }
    }
    static void install(ClientRuntime next) {
        next = Objects.requireNonNull(next, "runtime");
        if (runtime != next) {
            runtime = next;
        }
    }
}
