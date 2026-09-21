package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.MaterialTextures.SourceSprite;
import com.fluorineuck.castedmaterials.mapping.*;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.palette.MaterialPalette;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialAttachmentRuntimeTest {
    @Test
    void attachmentQuadCountsAgainstAdditionalQuadBudget() {
        var target = target(new RenderPolicy(64, 0, 4));
        var lookups = new AtomicInteger();
        var runtime = runtime(new AtomicReference<>(snapshot(1, target)), lookups);

        var handle = runtime.resolve(root(), target.id(), id("material"), id("fallback"));

        assertEquals(CastedModelHandle.Backend.FALLBACK, handle.backend());
        assertEquals("ATTACHMENT_QUAD_LIMIT", handle.reason());
        assertEquals(1, lookups.get());
    }

    @Test
    void attachmentTexturesCountAgainstBatchBudgetAndSuccessfulPreparationCachesUntilReload() {
        var limited = target(new RenderPolicy(64, 8, 1));
        var snapshots = new AtomicReference<>(snapshot(1, limited));
        var lookups = new AtomicInteger();
        var runtime = runtime(snapshots, lookups);
        var root = root();

        var refused = runtime.resolve(root, limited.id(), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.FALLBACK, refused.backend());
        assertEquals("TEXTURE_BATCH_LIMIT", refused.reason());
        assertEquals(1, lookups.get());

        var allowed = target(new RenderPolicy(64, 8, 2));
        snapshots.set(snapshot(2, allowed));
        var direct = runtime.resolve(root, allowed.id(), id("material"), id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV, direct.backend(), direct.reason());
        assertEquals(2, direct.textureBatches());
        assertEquals(2, lookups.get());
        assertSame(direct, runtime.resolve(root, allowed.id(), id("material"), id("fallback")));
        assertEquals(2, lookups.get(), "cached handle must not bake the attachment again");

        assertEquals(CastedModelHandle.Backend.FALLBACK,
                runtime.resolve(root, allowed.id(), null, id("fallback")).backend());
        assertEquals(2, lookups.get(), "removing the material must not resolve an attachment model");

        snapshots.set(snapshot(3, allowed));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,
                runtime.resolve(root, allowed.id(), id("material"), id("fallback")).backend());
        assertEquals(3, lookups.get(), "reload generation must prepare the attachment again");
    }

    private static DefaultModelRuntime runtime(AtomicReference<CastedResources.Snapshot> snapshots,
                                               AtomicInteger lookups) {
        var sprite = MaterialAttachmentsTest.sprite(id("block_atlas"), id("hat_texture"),
                16, 16, 0, 0, 16, 16);
        var quad = MaterialAttachmentsTest.quad(sprite);
        return new DefaultModelRuntime(snapshots::get,
                () -> MaterialPalette.from(List.of(id("material"))),
                (target, material, global, required) -> Optional.of(Map.of("all",
                        new SourceSprite(id("body_atlas"), target.size(), 0, 0, 1, 1))),
                8, (texture, grid) -> Optional.empty(), model -> {
                    lookups.incrementAndGet();
                    return MaterialAttachmentsTest.model(quad);
                });
    }

    private static CastedResources.Snapshot snapshot(long generation, TargetDefinition target) {
        return new CastedResources.Snapshot(generation, Map.of(target.id(), target), Map.of());
    }

    private static TargetDefinition target(RenderPolicy policy) {
        var size = new IntSize(16, 16);
        var full = new IntRect(0, 0, 16, 16);
        var attachment = new ModelLayer(0, id("hat"), "head", Vec3.ZERO, Vec3.ZERO,
                new Vec3(1, 1, 1), Optional.empty(), Optional.empty(), false);
        return new TargetDefinition(id("target"), size, Map.of("all", size),
                List.of(new RegionMapping("all", full, full, MappingTransform.IDENTITY)), List.of(),
                Optional.empty(), Map.of(), Optional.empty(), policy, List.of(attachment));
    }

    private static ModelPart root() {
        return new ModelPart(List.of(), Map.of("head", new ModelPart(List.of(), Map.of()),
                "body", CapturedModelTest.cubePart()));
    }
}
