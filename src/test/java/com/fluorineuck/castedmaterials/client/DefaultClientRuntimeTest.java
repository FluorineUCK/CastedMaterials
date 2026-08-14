package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.atlas.AtlasAllocation;
import com.fluorineuck.castedmaterials.client.atlas.DynamicAtlasManager;
import com.fluorineuck.castedmaterials.client.atlas.TextureBackend;
import com.fluorineuck.castedmaterials.client.cache.RenderKey;
import com.fluorineuck.castedmaterials.client.cache.RenderRepository;
import com.fluorineuck.castedmaterials.client.definition.CastedResourceSnapshot;
import com.fluorineuck.castedmaterials.client.definition.ResolvedSources;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.client.render.UvTransform;
import com.fluorineuck.castedmaterials.definition.IntRect;
import com.fluorineuck.castedmaterials.definition.IntSize;
import com.fluorineuck.castedmaterials.definition.MappingTransform;
import com.fluorineuck.castedmaterials.definition.RegionMapping;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import com.fluorineuck.castedmaterials.material.MaterialPalette;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultClientRuntimeTest {
    private static final Executor DIRECT = Runnable::run;
    private static final ResourceLocation TARGET = id("target");
    private static final ResourceLocation MATERIAL = id("material");
    private static final ResourceLocation OTHER_MATERIAL = id("other_material");
    private static final ResourceLocation FALLBACK = id("fallback");

    @Test
    void absentTargetReturnsFallbackHandle() {
        TestRuntime fixture = runtime(CastedResourceSnapshot.empty(), MaterialPalette.from(List.of(MATERIAL)),
                (target, material, definition) -> Optional.of(sources()));

        var handle = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);

        assertFallback(handle.texture(), handle.uv(), handle.state());
    }

    @Test
    void materialOutsideClientPaletteReturnsFallbackHandle() {
        TestRuntime fixture = runtime(snapshot(1), MaterialPalette.empty(),
                (target, material, definition) -> Optional.of(sources()));

        var handle = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);

        assertFallback(handle.texture(), handle.uv(), handle.state());
    }

    @Test
    void firstResolveQueuesCompositionAndReturnsFallback() {
        AtomicInteger resolverCalls = new AtomicInteger();
        TestRuntime fixture = runtime(snapshot(1), MaterialPalette.from(List.of(MATERIAL)),
                (target, material, definition) -> {
                    resolverCalls.incrementAndGet();
                    return Optional.of(sources());
                });

        var handle = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);

        assertFallback(handle.texture(), handle.uv(), handle.state());
        assertEquals(1, resolverCalls.get());
        assertTrue(fixture.repository.find(new RenderKey(1, TARGET, MATERIAL)).isPresent());
        assertTrue(fixture.backend.uploads.isEmpty());
    }

    @Test
    void readyEntryReturnsPageTextureAndRegionUv() {
        TestRuntime fixture = runtime(snapshot(1), MaterialPalette.from(List.of(MATERIAL)),
                (target, material, definition) -> Optional.of(sources()));
        fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);
        fixture.atlas.drain(Long.MAX_VALUE);

        var handle = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);

        assertEquals(id("casted_materials:dynamic/g1/page_0"), handle.texture());
        assertEquals(new UvTransform(0, 0, 1f / 512, 1f / 512), handle.uv());
        assertEquals(RenderableState.READY, handle.state());
        assertEquals(1, handle.generation());
    }

    @Test
    void generationChangeClearsCacheAndOldAtlas() {
        AtomicReference<CastedResourceSnapshot> snapshot = new AtomicReference<>(snapshot(1));
        TestRuntime fixture = runtime(snapshot, MaterialPalette.from(List.of(MATERIAL)),
                (target, material, definition) -> Optional.of(sources()));
        fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);
        fixture.atlas.drain(Long.MAX_VALUE);
        assertFalse(fixture.backend.created.isEmpty());

        snapshot.set(snapshot(2));
        var handle = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);

        assertFallback(handle.texture(), handle.uv(), handle.state());
        assertEquals(fixture.backend.created.subList(0, 1), fixture.backend.closed);
        assertTrue(fixture.repository.find(new RenderKey(1, TARGET, MATERIAL)).isEmpty());
    }

    @Test
    void resolverExceptionIsQuarantinedToOneCombination() {
        AtomicInteger resolverCalls = new AtomicInteger();
        TestRuntime fixture = runtime(snapshot(1), MaterialPalette.from(List.of(MATERIAL, OTHER_MATERIAL)),
                (target, material, definition) -> {
                    resolverCalls.incrementAndGet();
                    if (material.equals(MATERIAL)) {
                        throw new IllegalStateException("invalid local resource");
                    }
                    return Optional.of(sources());
                });

        var failed = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);
        var repeated = fixture.runtime.resolve(TARGET, MATERIAL, FALLBACK);
        fixture.runtime.resolve(TARGET, OTHER_MATERIAL, FALLBACK);
        fixture.atlas.drain(Long.MAX_VALUE);
        var good = fixture.runtime.resolve(TARGET, OTHER_MATERIAL, FALLBACK);

        assertFallback(failed.texture(), failed.uv(), failed.state());
        assertEquals(RenderableState.QUARANTINED, failed.state());
        assertFallback(repeated.texture(), repeated.uv(), repeated.state());
        assertEquals(2, resolverCalls.get());
        assertEquals(RenderableState.READY, good.state());
    }

    private static void assertFallback(ResourceLocation texture, UvTransform uv, RenderableState state) {
        assertEquals(FALLBACK, texture);
        assertEquals(UvTransform.IDENTITY, uv);
        assertTrue(state == RenderableState.FALLBACK_BASE || state == RenderableState.QUARANTINED);
    }

    private static TestRuntime runtime(CastedResourceSnapshot snapshot, MaterialPalette palette,
                                       DefaultClientRuntime.SourceResolver resolver) {
        return runtime(new AtomicReference<>(snapshot), palette, resolver);
    }

    private static TestRuntime runtime(AtomicReference<CastedResourceSnapshot> snapshot, MaterialPalette palette,
                                       DefaultClientRuntime.SourceResolver resolver) {
        FakeBackend backend = new FakeBackend();
        DynamicAtlasManager atlas = new DynamicAtlasManager(snapshot.get().generation(), backend, () -> 0L, () -> { });
        RenderRepository repository = new RenderRepository(snapshot.get().generation(), DIRECT, atlas);
        return new TestRuntime(new DefaultClientRuntime(snapshot::get, () -> palette, resolver, repository, atlas),
                repository, atlas, backend);
    }

    private static CastedResourceSnapshot snapshot(long generation) {
        return new CastedResourceSnapshot(generation, Map.of(TARGET, target()), Map.of());
    }

    private static TargetDefinition target() {
        IntSize size = new IntSize(1, 1);
        return new TargetDefinition(TARGET, size, Map.of("all", size),
                List.of(new RegionMapping("all", new IntRect(0, 0, 1, 1),
                        new IntRect(0, 0, 1, 1), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
    }

    private static ResolvedSources sources() {
        IntSize size = new IntSize(1, 1);
        return new ResolvedSources(Map.of("all", new SourceSlotImage(
                new PixelImage(1, 1, new int[] { 0xff_ffffff }), size, false)), Optional.empty());
    }

    private record TestRuntime(DefaultClientRuntime runtime, RenderRepository repository,
                               DynamicAtlasManager atlas, FakeBackend backend) { }

    private static final class FakeBackend implements TextureBackend {
        private final List<ResourceLocation> created = new ArrayList<>();
        private final List<ResourceLocation> closed = new ArrayList<>();
        private final List<ResourceLocation> uploads = new ArrayList<>();

        @Override
        public void createPage(ResourceLocation id, int width, int height) {
            created.add(id);
        }

        @Override
        public void upload(ResourceLocation page, AtlasAllocation allocation, int[] abgrPixels) {
            uploads.add(page);
        }

        @Override
        public void closePage(ResourceLocation id) {
            closed.add(id);
        }
    }
}
