package com.fluorineuck.castedmaterials.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialTextureDiscoveryTest {
    @Test
    void registeredTargetWinsWithoutLoadingBakedModelOrGuessingFilenames() throws Exception {
        var targetId = id("block/another_folder/number_42");
        try (var target = sprite(targetId,128,128)) {
            var connectivity = registered(target.sprite());
            assertEquals(Optional.of(targetId), MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),
                    connectivity, state -> { fail("Registered CT must not inspect the model"); return null; }));
        }
    }

    @Test
    void unregisteredBlockUsesParticleSprite() throws Exception {
        try (var particle = sprite(id("block/plain_particle"),16,16)) {
            assertEquals(Optional.of(id("block/plain_particle")), MaterialTextures.findDefault(
                    Blocks.STONE.defaultBlockState(),state -> new MaterialTextures.CasingSource(false, null),state -> particle.sprite()));
        }
    }

    @Test
    void missingRegisteredTargetDoesNotSilentlySelectParticle() {
        assertTrue(MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(), registered(null),
                state -> { fail("Registered target is authoritative"); return null; }).isEmpty());
    }

    @Test
    void missingParticleReturnsNoSource() throws Exception {
        try (var missing = sprite(MissingTextureAtlasSprite.getLocation(),16,16)) {
            assertTrue(MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),state -> new MaterialTextures.CasingSource(false, null),
                    state -> missing.sprite()).isEmpty());
        }
    }

    @Test
    void discoveredTargetUsesActualAtlasPixelsAndValidatesLogicalDimensions() throws Exception {
        try (var target = sprite(id("block/aliased_sheet"),128,128)) {
            var discovered = MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),registered(target.sprite()),
                    state -> { fail("Model lookup is not needed"); return null; }).orElseThrow();
            var grid = new com.fluorineuck.castedmaterials.mapping.IntSize(128,128);
            var bound = MaterialTextures.findDirect(discovered,grid,candidate -> {
                assertEquals(id("block/aliased_sheet"),candidate);
                return target.sprite();
            }, resource -> { fail("Atlas pixels need no PNG lookup"); return false; }, (resource,size) -> Optional.empty()).orElseThrow();
            assertEquals(0xff123456,bound.pixels().get().get(0,0));
            assertEquals(.125f,bound.u0());
            assertEquals(.25f,bound.v0());
            assertTrue(MaterialTextures.findDirect(discovered,
                    new com.fluorineuck.castedmaterials.mapping.IntSize(128,64),
                    candidate -> target.sprite(),resource -> false,(resource,size) -> Optional.empty()).isEmpty());
        }
    }

    private static java.util.function.Function<net.minecraft.world.level.block.state.BlockState, MaterialTextures.CasingSource>
            registered(TextureAtlasSprite targetSprite) {
        return state -> new MaterialTextures.CasingSource(true, targetSprite);
    }

    private static SpriteFixture sprite(ResourceLocation id,int width,int height) throws Exception {
        var image = new NativeImage(width,height,false);
        image.fillRect(0,0,width,height,0xff563412);
        var contents = new SpriteContents(id,new FrameSize(width,height),image,ResourceMetadata.EMPTY);
        var constructor = TextureAtlasSprite.class.getDeclaredConstructor(ResourceLocation.class,
                SpriteContents.class,int.class,int.class,int.class,int.class);
        constructor.setAccessible(true);
        return new SpriteFixture(contents,constructor.newInstance(
                ResourceLocation.parse("minecraft:textures/atlas/blocks.png"),contents,1024,1024,128,256));
    }

    private record SpriteFixture(SpriteContents contents,TextureAtlasSprite sprite) implements AutoCloseable {
        @Override public void close() { contents.close(); }
    }
}
