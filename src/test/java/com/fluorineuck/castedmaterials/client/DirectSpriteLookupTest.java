package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import com.fluorineuck.castedmaterials.mapping.IntSize;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class DirectSpriteLookupTest {
    @Test
    void existingUnstitchedPngCanBeBoundWithoutGeneratingATexture() {
        var grid = new IntSize(16,16);
        var original = new com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite(
                id("textures/entity/custom.png"), grid, 0,0,1,1);
        assertEquals(Optional.of(original), MaterialTextures.findDirect(id("entity/custom"), grid,
                ignored -> null, png -> {
                    assertEquals(original.texture(), png);
                    return true;
                }, (sprite, requestedGrid) -> {
                    assertEquals(id("entity/custom"), sprite);
                    assertEquals(grid, requestedGrid);
                    return Optional.of(original);
                }));
    }

    @Test
    void incompatibleAuthoritativePngStopsPrecedenceInsteadOfSelectingAnotherMaterial() {
        var checks = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> MaterialTextures.findDirect(id("entity/custom"), new IntSize(16, 16),
                ignored -> null, ignored -> { checks.incrementAndGet(); return true; }, (id, grid) -> Optional.empty()));
        assertEquals(1, checks.get());
    }

    @Test
    void missingAtlasAndMissingPngCanContinueNormalSourcePrecedence() {
        assertEquals(Optional.empty(), MaterialTextures.findDirect(id("absent"), new IntSize(16, 16),
                ignored -> null, ignored -> false, (id, grid) -> { fail("Missing PNG must not be decoded"); return Optional.empty(); }));
    }
}
