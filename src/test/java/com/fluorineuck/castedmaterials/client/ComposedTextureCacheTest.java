package com.fluorineuck.castedmaterials.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ComposedTextureCacheTest {
    @Test
    void equalKeysReuseTheSameTexturesWithoutComposingOrUploadingAgain() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(4, 32768, backend);
        AtomicInteger compositions = new AtomicInteger();
        PixelImage color = new PixelImage(64, 32);
        PixelImage emission = new PixelImage(64, 32);

        var first = cache.resolve(new String("material"), () -> {
            compositions.incrementAndGet();
            return Optional.of(new ComposedTextureCache.Images(color, emission));
        }).orElseThrow();
        var second = cache.resolve(new String("material"), () -> {
            throw new AssertionError("cache hit must not compose");
        }).orElseThrow();

        assertSame(first, second);
        assertEquals(1, compositions.get());
        assertEquals(2, backend.uploads.size());
        assertSame(color, backend.live.get(first.color()));
        assertSame(emission, backend.live.get(first.emission()));
        assertNotEquals(first.color(), first.emission());
        assertEquals("casted_materials", first.color().getNamespace());
        assertEquals("casted_materials", first.emission().getNamespace());
        assertEquals(16384, backend.uploadedBytes);
    }

    @Test
    void colorOnlyUploadsTheExactImageInsteadOfAnAtlasPage() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(4, 8192, backend);
        PixelImage color = new PixelImage(64, 32);

        var textures = cache.resolve("material", () -> images(color)).orElseThrow();

        assertNull(textures.emission());
        assertSame(color, backend.live.get(textures.color()));
        assertEquals(1, backend.uploads.size());
        assertEquals(8192, backend.uploadedBytes);
    }

    @Test
    void entryLimitKeepsExistingTexturesAliveAndSkipsAdditionalComposition() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(1, 1024, backend);
        var first = cache.resolve("first", () -> images(new PixelImage(1, 1))).orElseThrow();

        assertTrue(cache.resolve("second", () -> {
            throw new AssertionError("full cache must not compose");
        }).isEmpty());
        assertEquals(first, cache.resolve("first", Optional::empty).orElseThrow());
        assertEquals(1, backend.live.size());
        assertTrue(backend.releases.isEmpty());
    }

    @Test
    void byteBudgetIncludesColorAndEmissionAndAllowsAnExactFit() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(4, 12, backend);
        var pair = cache.resolve("pair", () -> Optional.of(new ComposedTextureCache.Images(
                new PixelImage(1, 1), new PixelImage(1, 1)))).orElseThrow();
        var single = cache.resolve("single", () -> images(new PixelImage(1, 1))).orElseThrow();

        assertTrue(cache.resolve("overflow", () -> images(new PixelImage(1, 1))).isEmpty());
        assertEquals(12, backend.uploadedBytes);
        assertEquals(3, backend.live.size());
        assertTrue(backend.live.containsKey(pair.emission()));
        assertTrue(backend.live.containsKey(single.color()));
        assertTrue(backend.releases.isEmpty());
    }

    @Test
    void oversizedPairUploadsNeitherImageAndDoesNotConsumeCapacity() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(1, 4, backend);

        assertTrue(cache.resolve("large", () -> Optional.of(new ComposedTextureCache.Images(
                new PixelImage(1, 1), new PixelImage(1, 1)))).isEmpty());
        assertTrue(backend.uploads.isEmpty());
        assertTrue(cache.resolve("small", () -> images(new PixelImage(1, 1))).isPresent());
        assertEquals(1, backend.live.size());
    }

    @Test
    void clearReleasesBothTexturesAndRebuildsWithFreshIdsAndRestoredCapacity() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(1, 8, backend);
        var before = cache.resolve("material", () -> Optional.of(new ComposedTextureCache.Images(
                new PixelImage(1, 1), new PixelImage(1, 1)))).orElseThrow();

        cache.clear();
        cache.clear();

        assertTrue(backend.live.isEmpty());
        assertEquals(2, backend.releases.size());
        assertTrue(backend.releases.containsAll(List.of(before.color(), before.emission())));
        var after = cache.resolve("material", () -> Optional.of(new ComposedTextureCache.Images(
                new PixelImage(1, 1), new PixelImage(1, 1)))).orElseThrow();
        assertNotEquals(before.color(), after.color());
        assertNotEquals(before.emission(), after.emission());
        assertEquals(2, backend.live.size());
    }

    @Test
    void closeReleasesEveryOwnedTextureExactlyOnce() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(2, 8, backend);
        cache.resolve("first", () -> images(new PixelImage(1, 1))).orElseThrow();
        cache.resolve("second", () -> images(new PixelImage(1, 1))).orElseThrow();

        cache.close();
        cache.close();

        assertTrue(backend.live.isEmpty());
        assertEquals(2, backend.releases.size());
        cache.clear();
        assertTrue(cache.resolve("after-close", () -> {
            throw new AssertionError("closed cache must not compose");
        }).isEmpty());
    }

    @Test
    void rejectsMismatchedColorAndEmissionDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new ComposedTextureCache.Images(
                new PixelImage(2, 1), new PixelImage(1, 2)));
    }

    @Test
    void rejectsNegativeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ComposedTextureCache(-1, 4, new RecordingBackend()));
        assertThrows(IllegalArgumentException.class,
                () -> new ComposedTextureCache(1, -1, new RecordingBackend()));
    }

    @Test
    void absentCompositionCanBeRetriedWithoutConsumingCapacity() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(1, 4, backend);

        assertTrue(cache.resolve("material", Optional::empty).isEmpty());
        assertTrue(cache.resolve("material", () -> images(new PixelImage(1, 1))).isPresent());
        assertEquals(1, backend.uploads.size());
    }

    @Test
    void failedColorUploadReleasesItsPartialRegistrationAndAllowsRetry() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(1, 4, backend);
        backend.failOnUpload = 1;

        assertTrue(cache.resolve("material", () -> images(new PixelImage(1, 1))).isEmpty());
        assertTrue(backend.live.isEmpty());
        assertEquals(1, backend.releases.size());
        backend.failOnUpload = -1;
        assertTrue(cache.resolve("material", () -> images(new PixelImage(1, 1))).isPresent());
        assertEquals(1, backend.live.size());
    }

    @Test
    void failedEmissionUploadRollsBackBothTexturesAndAllowsRetry() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache cache = new ComposedTextureCache(1, 8, backend);
        backend.failOnUpload = 2;

        assertTrue(cache.resolve("material", () -> Optional.of(new ComposedTextureCache.Images(
                new PixelImage(1, 1), new PixelImage(1, 1)))).isEmpty());
        assertTrue(backend.live.isEmpty());
        assertEquals(2, backend.releases.size());
        backend.failOnUpload = -1;
        var textures = cache.resolve("material", () -> Optional.of(new ComposedTextureCache.Images(
                new PixelImage(1, 1), new PixelImage(1, 1)))).orElseThrow();
        assertEquals(2, backend.live.size());
        assertFalse(backend.releases.contains(textures.color()));
        assertFalse(backend.releases.contains(textures.emission()));
    }

    @Test
    void independentCachesDoNotReuseResourceIds() {
        RecordingBackend backend = new RecordingBackend();
        ComposedTextureCache first = new ComposedTextureCache(1, 4, backend);
        ComposedTextureCache second = new ComposedTextureCache(1, 4, backend);

        var a = first.resolve("material", () -> images(new PixelImage(1, 1))).orElseThrow();
        var b = second.resolve("material", () -> images(new PixelImage(1, 1))).orElseThrow();

        assertNotEquals(a.color(), b.color());
        first.close();
        assertTrue(backend.live.containsKey(b.color()));
    }

    @Test
    void nativeUploadImagePreservesDimensionsAlphaAndArgbChannels() {
        PixelImage source = new PixelImage(2, 2,
                new int[]{0xffff0000, 0x8000ff00, 0xff0000ff, 0x12345678});

        try (NativeImage image = ComposedTextureCache.toNativeImage(source)) {
            assertEquals(2, image.getWidth());
            assertEquals(2, image.getHeight());
            assertEquals(0xff0000ff, image.getPixelRGBA(0, 0));
            assertEquals(0x8000ff00, image.getPixelRGBA(1, 0));
            assertEquals(0xffff0000, image.getPixelRGBA(0, 1));
            assertEquals(0x12785634, image.getPixelRGBA(1, 1));
        }
    }

    private static Optional<ComposedTextureCache.Images> images(PixelImage color) {
        return Optional.of(new ComposedTextureCache.Images(color, null));
    }

    private static final class RecordingBackend implements ComposedTextureCache.Backend {
        final Map<ResourceLocation, PixelImage> live = new HashMap<>();
        final List<ResourceLocation> uploads = new ArrayList<>();
        final List<ResourceLocation> releases = new ArrayList<>();
        long uploadedBytes;
        int failOnUpload = -1;

        @Override
        public void upload(ResourceLocation id, PixelImage image) {
            uploads.add(id);
            live.put(id, image);
            uploadedBytes += (long) image.width() * image.height() * 4;
            if (uploads.size() == failOnUpload) {
                throw new IllegalStateException("upload failed after partial registration");
            }
        }

        @Override
        public void release(ResourceLocation id) {
            releases.add(id);
            live.remove(id);
        }
    }
}
