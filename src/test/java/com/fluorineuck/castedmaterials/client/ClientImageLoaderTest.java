package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.mapping.IntSize;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientImageLoaderTest {
    private static final ResourceLocation SPRITE = id("test_sprite");
    private static final ResourceLocation PNG = id("textures/test_sprite.png");

    @Test
    void convertsNativeImageAbgrPixelsToArgbAtIntegerScaleOne() {
        PixelImage image = MaterialTextures.loadImage(resources(), SPRITE, new IntSize(2, 2)).orElseThrow();

        assertArrayEquals(new int[]{0xffff0000, 0x8000ff00, 0xff0000ff, 0x00000000}, image.pixels());
        assertEquals(2, image.width());
        assertEquals(2, image.height());
    }

    @Test
    void returnsEmptyWhenImageDoesNotFitLogicalGridByIntegerScale() {
        Optional<PixelImage> image = MaterialTextures.loadImage(resources(), SPRITE, new IntSize(3, 2));

        assertTrue(image.isEmpty());
    }

    private static ResourceManager resources() {
        Resource image = new Resource(null, ClientImageLoaderTest::fixture);
        return new ResourceManager() {
            @Override
            public Optional<Resource> getResource(ResourceLocation location) {
                return PNG.equals(location) ? Optional.of(image) : Optional.empty();
            }

            @Override
            public Set<String> getNamespaces() {
                return Set.of("test");
            }

            @Override
            public List<Resource> getResourceStack(ResourceLocation location) {
                return getResource(location).stream().toList();
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
                return Map.of();
            }

            @Override
            public Map<ResourceLocation, List<Resource>> listResourceStacks(String path,
                                                                              Predicate<ResourceLocation> filter) {
                return Map.of();
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.empty();
            }
        };
    }

    private static InputStream fixture() {
        InputStream stream = ClientImageLoaderTest.class.getResourceAsStream("/client-image-loader/rgba-2x2.png");
        if (stream == null) {
            throw new IllegalStateException("missing PNG test resource");
        }
        return stream;
    }
}
