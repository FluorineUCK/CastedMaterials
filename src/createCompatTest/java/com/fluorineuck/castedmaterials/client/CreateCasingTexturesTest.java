package com.fluorineuck.castedmaterials.client;

import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CreateCasingTexturesTest {
    @Test void realRegistrationWinsWithNonConnectedFilename() throws Exception {
        var id = ResourceLocation.parse("test:block/other_folder/number_42");
        try (var contents = new SpriteContents(id, new FrameSize(16, 16),
                new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(contents);
            var connectivity = registered(sprite);
            var found = CreateCasingTextures.find(Blocks.STONE.defaultBlockState(), connectivity);
            assertTrue(found.registered());
            assertSame(sprite, found.sprite());
            assertEquals(Optional.of(id), MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),
                    state -> CreateCasingTextures.find(state, connectivity),
                    state -> { fail("Registered CT is authoritative"); return null; }));
        }
    }
    @Test void registeredMissingTargetDoesNotFallThrough() {
        var connectivity = registered(null);
        var found = CreateCasingTextures.find(Blocks.STONE.defaultBlockState(), connectivity);
        assertTrue(found.registered());
        assertNull(found.sprite());
        assertTrue(MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),
                state -> CreateCasingTextures.find(state, connectivity),
                state -> { fail("Missing registered CT must not use particles"); return null; }).isEmpty());
    }
    @Test void unregisteredBlockUsesParticle() {
        var connectivity = new CasingConnectivity();
        var id = ResourceLocation.parse("test:block/particle");
        try (var contents = new SpriteContents(id, new FrameSize(16, 16),
                new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            var sprite = new TestSprite(contents);
            assertFalse(CreateCasingTextures.find(Blocks.STONE.defaultBlockState(), connectivity).registered());
            assertEquals(Optional.of(id), MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),
                    state -> CreateCasingTextures.find(state, connectivity), state -> sprite));
        }
    }
    private static CasingConnectivity registered(TextureAtlasSprite targetSprite) {
        var connectivity = new CasingConnectivity();
        connectivity.makeCasing(Blocks.STONE, new CTSpriteShiftEntry(null) {
            @Override public TextureAtlasSprite getTarget() { return targetSprite; }
        });
        return connectivity;
    }
    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(SpriteContents contents) {
            super(ResourceLocation.parse("minecraft:textures/atlas/blocks.png"), contents, 16, 16, 0, 0);
        }
    }
}
