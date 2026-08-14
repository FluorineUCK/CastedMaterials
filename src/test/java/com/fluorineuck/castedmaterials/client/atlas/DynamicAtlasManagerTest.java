package com.fluorineuck.castedmaterials.client.atlas;

import com.fluorineuck.castedmaterials.client.cache.RenderKey;
import com.fluorineuck.castedmaterials.client.cache.UploadResult;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.LongSupplier;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class DynamicAtlasManagerTest {
    @Test
    void atlasRegionMapsAllocationIntoPageUvSpace() {
        AtlasRegion region = new AtlasRegion(id("dynamic/g1/page_0"),
                new AtlasAllocation(64, 32, 64, 32), 512, 512);

        assertEquals(new com.fluorineuck.castedmaterials.client.render.UvTransform(
                0.125f, 0.0625f, 0.125f, 0.0625f), region.uv());
    }

    @Test
    void packsEightBySixteenSpiderTexturesIntoOnePage() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(7, backend, () -> 0L);
        List<UploadResult> results = new ArrayList<>();

        for (int index = 0; index < 128; index++) {
            atlas.enqueue(key(7, index), image(64, 32), results::add);
        }

        assertEquals(128, atlas.drain(Long.MAX_VALUE));
        assertEquals(List.of(id("casted_materials:dynamic/g7/page_0")), backend.created);
        assertEquals(128, backend.uploads.size());
        assertTrue(results.stream().allMatch(UploadResult::uploaded));
    }

    @Test
    void createsASecondPageWhenTheFirstIsFull() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(3, backend, () -> 0L);

        for (int index = 0; index < 129; index++) {
            atlas.enqueue(key(3, index), image(64, 32), ignored -> { });
        }

        assertEquals(129, atlas.drain(Long.MAX_VALUE));
        assertEquals(List.of(id("casted_materials:dynamic/g3/page_0"),
                id("casted_materials:dynamic/g3/page_1")), backend.created);
    }

    @Test
    void refusesTheSeventeenthOneMiBPage() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(3, backend, () -> 0L);
        List<UploadResult> results = new ArrayList<>();

        for (int index = 0; index < 65; index++) {
            atlas.enqueue(key(3, index), image(256, 256), results::add);
        }

        assertEquals(65, atlas.drain(Long.MAX_VALUE));
        assertEquals(16, backend.created.size());
        assertEquals(64, backend.uploads.size());
        assertFalse(results.getLast().uploaded());
        assertEquals(com.fluorineuck.castedmaterials.client.failure.RenderFailureCode.ATLAS_BUDGET_EXHAUSTED,
                results.getLast().failure().orElseThrow());
    }

    @Test
    void drainStopsWhenInjectedClockPassesTwoMilliseconds() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(5, backend,
                new FakeClock(0L, 0L, 2_000_000L));
        atlas.enqueue(key(5, 1), image(1, 1), ignored -> { });
        atlas.enqueue(key(5, 2), image(1, 1), ignored -> { });

        assertEquals(1, atlas.drain(2_000_000L));
        assertEquals(1, backend.uploads.size());
    }

    @Test
    void staleGenerationUploadCompletesAsFailureWithoutTouchingBackend() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(5, backend, () -> 0L);
        List<UploadResult> results = new ArrayList<>();
        atlas.enqueue(key(4, 1), image(1, 1), results::add);

        assertEquals(1, atlas.drain(Long.MAX_VALUE));
        assertEquals(0, backend.created.size());
        assertEquals(0, backend.uploads.size());
        assertEquals(com.fluorineuck.castedmaterials.client.failure.RenderFailureCode.STALE_GENERATION,
                results.get(0).failure().orElseThrow());
    }

    @Test
    void switchGenerationClosesEveryOldPageExactlyOnce() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(5, backend, () -> 0L);
        for (int index = 0; index < 5; index++) {
            atlas.enqueue(key(5, index), image(256, 256), ignored -> { });
        }
        atlas.drain(Long.MAX_VALUE);

        atlas.switchGeneration(6);

        assertEquals(2, backend.closed.size());
        assertEquals(2, new HashSet<>(backend.closed).size());
        assertEquals(backend.created, backend.closed);
    }

    @Test
    void convertsArgbPixelsToAbgrBeforeUploading() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = testAtlas(1, backend, () -> 0L);
        atlas.enqueue(key(1, 1), new PixelImage(1, 1, new int[] { 0x12_34_56_78 }), ignored -> { });

        atlas.drain(Long.MAX_VALUE);

        assertArrayEquals(new int[] { 0x12_78_56_34 }, backend.uploads.get(0).pixels());
    }

    @Test
    void rejectsDrainOffTheRenderThreadBeforeTouchingBackend() {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = new DynamicAtlasManager(1, backend);
        atlas.enqueue(key(1, 1), image(1, 1), ignored -> { });

        assertThrows(IllegalStateException.class, () -> atlas.drain(Long.MAX_VALUE));
        assertTrue(backend.created.isEmpty());
        assertTrue(backend.uploads.isEmpty());
    }
    private static DynamicAtlasManager testAtlas(long generation, FakeBackend backend, LongSupplier clock) {
        return new DynamicAtlasManager(generation, backend, clock, () -> { });
    }
    private static RenderKey key(long generation, int index) {
        return new RenderKey(generation, id("target_" + index), id("material_" + index));
    }

    private static PixelImage image(int width, int height) {
        return new PixelImage(width, height, new int[width * height]);
    }

    private static final class FakeClock implements LongSupplier {
        private final long[] readings;
        private int next;

        private FakeClock(long... readings) {
            this.readings = readings;
        }

        @Override
        public long getAsLong() {
            return readings[Math.min(next++, readings.length - 1)];
        }
    }

    private static final class FakeBackend implements TextureBackend {
        private final List<ResourceLocation> created = new ArrayList<>();
        private final List<UploadCall> uploads = new ArrayList<>();
        private final List<ResourceLocation> closed = new ArrayList<>();

        @Override
        public void createPage(ResourceLocation id, int width, int height) {
            assertEquals(512, width);
            assertEquals(512, height);
            created.add(id);
        }

        @Override
        public void upload(ResourceLocation page, AtlasAllocation allocation, int[] abgrPixels) {
            uploads.add(new UploadCall(page, allocation, abgrPixels.clone()));
        }

        @Override
        public void closePage(ResourceLocation id) {
            closed.add(id);
        }
    }

    private record UploadCall(ResourceLocation page, AtlasAllocation allocation, int[] pixels) { }
}
