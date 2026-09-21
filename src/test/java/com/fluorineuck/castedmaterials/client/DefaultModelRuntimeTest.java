package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.client.CastedResources;
import com.fluorineuck.castedmaterials.client.*;
import com.fluorineuck.castedmaterials.mapping.*;
import com.fluorineuck.castedmaterials.palette.MaterialPalette;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class DefaultModelRuntimeTest {
    @Test
    void targetPolicyCanPermitMoreTextureBatchesThanDefault() {
        var grid = new IntSize(16, 16);
        var slots = new HashMap<String, IntSize>();
        var sources = new HashMap<String, SourceSprite>();
        var regions = new ArrayList<RegionMapping>();
        for (int x = 0; x < 5; x++) {
            String slot = "slot" + x;
            slots.put(slot, grid);
            sources.put(slot, new SourceSprite(id("atlas" + x), grid, 0, 0, 1, 1));
            var rect = new IntRect(x, 0, x == 4 ? 12 : 1, 16);
            regions.add(new RegionMapping(slot, rect, rect, MappingTransform.IDENTITY));
        }
        var target = new TargetDefinition(id("target"), grid, slots, regions, List.of(),
                Optional.empty(), Map.of(), Optional.empty(), new RenderPolicy(64, 2048, 5));
        var snapshot = new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of());
        var runtime = new DefaultModelRuntime(() -> snapshot, () -> MaterialPalette.from(List.of(id("material"))),
                (t, m, g, required) -> Optional.of(sources),
                8, (sprite, size) -> Optional.empty());
        var handle = runtime.resolve(model(), target.id(), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV, handle.backend(), handle.reason());
        assertEquals(5, handle.textureBatches());
    }

    @Test
    void legacyCompositeHintCannotSelectGeneratedTextureBackend() {
        var target = DefinitionParser.parseTarget(id("target"), """
                {"schema":1,"size":[16,16],"source_grids":{"all":[16,16]},
                 "regions":[{"source_slot":"all","source":[0,0,16,16],"destination":[0,0,16,16]}],
                 "render_policy":{"mode":"composite"}}
                """);
        var snapshots = new AtomicReference<>(new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()));
        var handle = runtime(snapshots,new AtomicInteger(),false)
                .resolve(model(),target.id(),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());
    }

    @Test
    void eligibleModelUsesCachedUvHandle() {
        var lookups = new AtomicInteger();
        var snapshots = new AtomicReference<>(snapshot(1, Optional.empty()));
        var runtime = runtime(snapshots, lookups, false);
        var root = model();
        var first = runtime.resolve(root, id("target"), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV, first.backend());
        assertEquals(6, first.plan().orElseThrow().outputQuads());
        for (int i = 0; i < 20; i++) assertSame(first, runtime.resolve(root, id("target"), id("material"), id("fallback")));
        assertEquals(1, lookups.get());
        snapshots.set(snapshot(2, Optional.empty()));
        assertNotSame(first, runtime.resolve(root, id("target"), id("material"), id("fallback")));
        assertEquals(2, lookups.get());
    }

    @Test
    void missingSpriteIsCachedAndUsesBaseFallback() {
        var lookups = new AtomicInteger();
        var runtime = runtime(new AtomicReference<>(snapshot(1, Optional.empty())), lookups, true);
        var root = model();
        for (int i = 0; i < 3; i++) {
            var handle = runtime.resolve(root, id("target"), id("material"), id("fallback"));
            assertEquals(CastedModelHandle.Backend.FALLBACK, handle.backend());
        }
        assertEquals(1, lookups.get());
    }

    @Test
    void overlayLoadFailureUsesBaseFallbackWithoutBindingSources() {
        var lookups = new AtomicInteger();
        var runtime = runtime(new AtomicReference<>(snapshot(1, Optional.of(id("overlay")))), lookups, false);
        assertEquals(CastedModelHandle.Backend.FALLBACK,
                runtime.resolve(model(), id("target"), id("material"), id("fallback")).backend());
        assertEquals(0, lookups.get());
    }

    @Test
    void unavailableDeclaredOverlayUsesBaseUntilResourceReload() {
        var snapshots = new AtomicReference<>(snapshot(7, Optional.of(id("missing_overlay"))));
        var loads = new AtomicInteger();
        var runtime = new DefaultModelRuntime(snapshots::get, () -> MaterialPalette.from(List.of(id("material"))),
                (target, material, global, required) -> Optional.of(Map.of("all",
                        new SourceSprite(id("atlas"),target.size(),0,0,1,1))),
                8, (sprite, grid) -> {
                    loads.incrementAndGet();
                    return snapshots.get().generation() == 7 ? Optional.empty() : Optional.of(
                            new DefaultModelRuntime.FixedOverlay(sprite,
                                    new com.fluorineuck.castedmaterials.mapping.PixelImage(16,16)));
                });
        var root = model();
        for (int i = 0; i < 3; i++) {
            var result = runtime.resolve(root,id("target"),id("material"),id("fallback"));
            assertEquals(CastedModelHandle.Backend.FALLBACK, result.backend());
            assertEquals("OVERLAY_UNAVAILABLE", result.reason());
        }
        assertEquals(1, loads.get());
        snapshots.set(snapshot(8, Optional.of(id("missing_overlay"))));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,
                runtime.resolve(root,id("target"),id("material"),id("fallback")).backend());
        assertEquals(2, loads.get());
    }

    @Test
    void absentMaterialAndUnknownTargetNeverCompileOrResolveSources() {
        var lookups = new AtomicInteger();
        var runtime = runtime(new AtomicReference<>(snapshot(1, Optional.empty())), lookups, false);
        assertEquals(CastedModelHandle.Backend.FALLBACK, runtime.resolve(model(), id("missing"), id("material"), id("fallback")).backend());
        assertEquals(CastedModelHandle.Backend.FALLBACK, runtime.resolve(model(), id("target"), null, id("fallback")).backend());
        assertEquals(0, lookups.get());
    }

    private static DefaultModelRuntime runtime(AtomicReference<CastedResources.Snapshot> snapshot, AtomicInteger lookups,
                                                boolean missing) {
        return new DefaultModelRuntime(snapshot::get, () -> MaterialPalette.from(List.of(id("material"))),
                (target, material, global, required) -> {
                    lookups.incrementAndGet();
                    return missing ? Optional.empty() : Optional.of(Map.of("all",
                            new SourceSprite(id("blocks_atlas"), new IntSize(16, 16), 0, 0, 1, 1)));
                }, 16,
                (sprite, grid) -> { throw new IllegalStateException("unsupported overlay"); });
    }

    @Test
    void targetTextureBatchBudgetIsEnforced() {
        var base = snapshot(1, Optional.empty()).targets().get(id("target"));
        var lookups = new AtomicInteger();
        var target = new TargetDefinition(base.id(), base.size(),
                Map.of("left", base.size(), "right", base.size()),
                List.of(new RegionMapping("left", new IntRect(0, 0, 3, 16), new IntRect(0, 0, 3, 16), MappingTransform.IDENTITY),
                        new RegionMapping("right", new IntRect(3, 0, 13, 16), new IntRect(3, 0, 13, 16), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty(), new RenderPolicy( 8, 256, 1));
        var snapshots = new AtomicReference<>(new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of()));
        var runtime = new DefaultModelRuntime(snapshots::get, () -> MaterialPalette.from(List.of(id("material"))),
                (t, m, g, required) -> {
                    lookups.incrementAndGet();
                    return Optional.of(Map.of("left", new SourceSprite(id("atlas1"), base.size(), 0, 0, 1, 1),
                            "right", new SourceSprite(id("atlas2"), base.size(), 0, 0, 1, 1)));
                }, 2, (sprite, grid) -> Optional.empty());
        var root = model();
        var handle = runtime.resolve(root, target.id(), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.FALLBACK, handle.backend());
        assertEquals("TEXTURE_BATCH_LIMIT", handle.reason());
        assertEquals(1, lookups.get());
    }

    @Test
    void missingBindingsRetryOnlyAfterReload() {
        var lookups = new AtomicInteger();
        var snapshots = new AtomicReference<>(snapshot(3, Optional.empty()));
        var runtime = new DefaultModelRuntime(snapshots::get, () -> MaterialPalette.from(List.of(id("material"))),
                (t, m, g, required) -> {
                    lookups.incrementAndGet();
                    return snapshots.get().generation() == 3 ? Optional.empty() : Optional.of(Map.of("all",
                            new SourceSprite(id("atlas"), new IntSize(16, 16), 0, 0, 1, 1)));
                }, 2, (sprite, grid) -> Optional.empty());
        var root = model();
        for (int i = 0; i < 5; i++) assertEquals(CastedModelHandle.Backend.FALLBACK,
                runtime.resolve(root, id("target"), id("material"), id("fallback")).backend());
        assertEquals(1, lookups.get());
        snapshots.set(snapshot(4, Optional.empty()));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,
                runtime.resolve(root, id("target"), id("material"), id("fallback")).backend());
        assertEquals(2, lookups.get());
    }

    @Test
    void zeroAdditionalQuadBudgetRejectsSplitBeforeBindingSources() {
        var original = snapshot(1, Optional.empty()).targets().get(id("target"));
        var target = new TargetDefinition(original.id(), original.size(), original.sourceGrids(),
                List.of(new RegionMapping("all", new IntRect(0, 0, 1, 16), new IntRect(0, 0, 1, 16), MappingTransform.IDENTITY),
                        new RegionMapping("all", new IntRect(0, 0, 15, 16), new IntRect(1, 0, 15, 16), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty(), new RenderPolicy(64, 0, 4));
        var snapshots = new AtomicReference<>(new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of()));
        var lookups = new AtomicInteger();
        var handle = runtime(snapshots, lookups, false).resolve(model(), target.id(), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.FALLBACK, handle.backend());
        assertTrue(handle.reason().contains("max_additional_quads"), handle.reason());
        assertEquals(0, lookups.get());
    }

    @Test
    void partialAlphaUsesBaseFallbackAndCachesRejection() {
        var loads = new AtomicInteger();
        var snapshot = snapshot(1,Optional.of(id("overlay")));
        var runtime = new DefaultModelRuntime(() -> snapshot,() -> MaterialPalette.from(List.of(id("material"))),
                (target, material, global, required) -> { fail("Rejected overlay must not bind sources");return Optional.empty(); },
                8,(sprite,grid) -> {
                    loads.incrementAndGet();
                    var overlay = new com.fluorineuck.castedmaterials.mapping.PixelImage(16,16);
                    overlay.set(0,0,0x80ff007f);
                    return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite,overlay));
                });
        var root = model();
        for (int i=0;i<3;i++) {
            var result = runtime.resolve(root,id("target"),id("material"),id("fallback"));
            assertEquals(CastedModelHandle.Backend.FALLBACK,result.backend());
            assertTrue(result.reason().contains("alpha"),result.reason());
        }
        assertEquals(1,loads.get());
    }

    @Test
    void opaqueOverrideCanReplaceEverySourceAndMissingOverrideUsesTargetOverlay() {
        var original = snapshot(1,Optional.of(id("base_overlay"))).targets().get(id("target"));
        var target = new TargetDefinition(original.id(),original.size(),original.sourceGrids(),original.regions(),original.pixels(),
                original.overlay(),Map.of(id("material"),new MaterialOverride(Map.of(),Optional.of(id("missing_override")))),
                Optional.empty());
        var snap = new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of());
        var loads = new ArrayList<net.minecraft.resources.ResourceLocation>();
        var runtime = new DefaultModelRuntime(() -> snap,() -> MaterialPalette.from(List.of(id("material"))),
                (t,m,g,required) -> { assertTrue(required.isEmpty());return Optional.of(Map.of()); },
                8,
                (sprite,grid) -> {
                    loads.add(sprite);
                    if (sprite.equals(id("missing_override"))) return Optional.empty();
                    int[] pixels = new int[256];Arrays.fill(pixels,0xfff05e12);
                    return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite,
                            new com.fluorineuck.castedmaterials.mapping.PixelImage(16,16,pixels)));
                });
        var root = model();
        var handle = runtime.resolve(root,id("target"),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend(),handle.reason());
        assertEquals(1,handle.textureBatches());
        assertEquals(6,handle.plan().orElseThrow().outputQuads());
        assertSame(handle,runtime.resolve(root,id("target"),id("material"),id("fallback")));
        assertEquals(List.of(id("missing_override"),id("base_overlay")),loads);
    }

    @Test
    void reloadedTargetBatchLimitsIncludeFixedOverlayTexture() {
        var original = snapshot(1,Optional.of(id("overlay"))).targets().get(id("target"));
        var target = new TargetDefinition(original.id(),original.size(),original.sourceGrids(),original.regions(),original.pixels(),
                original.overlay(),Map.of(),Optional.empty(),new RenderPolicy(64,2048,2));
        var snapshots = new AtomicReference<>(new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()));
        var runtime = new DefaultModelRuntime(snapshots::get,() -> MaterialPalette.from(List.of(id("material"))),
                (t,m,g,required) -> Optional.of(Map.of("all",new SourceSprite(id("atlas"),target.size(),0,0,1,1))),
                8,
                (sprite,grid) -> {
                    var image = new com.fluorineuck.castedmaterials.mapping.PixelImage(16,16);
                    for (int y=0;y<16;y++) for(int x=0;x<16;x+=2) image.set(x,y,0xffd5810a);
                    return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite,image));
                });
        var root = model();
        var direct = runtime.resolve(root,id("target"),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,direct.backend(),direct.reason());
        assertEquals(2,direct.textureBatches());
        var limited = new TargetDefinition(target.id(), target.size(), target.sourceGrids(), target.regions(), target.pixels(),
                target.overlay(), Map.of(), Optional.empty(), new RenderPolicy(64, 2048, 1));
        snapshots.set(new CastedResources.Snapshot(2, Map.of(limited.id(), limited), Map.of()));
        var refused = runtime.resolve(root,id("target"),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.FALLBACK,refused.backend());
        assertEquals("TEXTURE_BATCH_LIMIT",refused.reason());
        snapshots.set(new CastedResources.Snapshot(3, Map.of(target.id(), target), Map.of()));
        var recovered = runtime.resolve(root,id("target"),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,recovered.backend());
        assertNotSame(direct,recovered);
        assertEquals(3, recovered.generation());
        assertSame(recovered, runtime.resolve(root,id("target"),id("material"),id("fallback")));
    }

    private static CastedResources.Snapshot snapshot(long generation, Optional<net.minecraft.resources.ResourceLocation> overlay) {
        var target = new TargetDefinition(id("target"), new IntSize(16, 16), Map.of("all", new IntSize(16, 16)),
                List.of(new RegionMapping("all", new IntRect(0, 0, 16, 16), new IntRect(0, 0, 16, 16), MappingTransform.IDENTITY)),
                List.of(), overlay, Map.of(), Optional.empty());
        return new CastedResources.Snapshot(generation, Map.of(target.id(), target), Map.of());
    }

    private static ModelPart model() {
        return new ModelPart(List.of(new ModelPart.Cube(0,0,0,0,0,2,2,2,0,0,0,false,16,16,
                EnumSet.allOf(Direction.class))), Map.of());
    }
}
