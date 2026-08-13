package com.fluorineuck.castedmaterials.client.render;

import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import org.junit.jupiter.api.Test;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class CastedTextureHandleTest {
    @Test
    void fallbackIsAlwaysNonNullAndUsesIdentityUv() {
        CastedTextureHandle handle = CastedTextureHandle.fallback(id("fallback"), RenderableState.FALLBACK_BASE, 1);
        assertEquals(id("fallback"), handle.texture());
        assertEquals(UvTransform.IDENTITY, handle.uv());
    }
}
