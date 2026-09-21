package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.IntRect;
import com.fluorineuck.castedmaterials.mapping.IntSize;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.MappingTransform;
import com.fluorineuck.castedmaterials.mapping.MaterialDefinition;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.MaterialOverride;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition.RegionMapping;
import com.fluorineuck.castedmaterials.mapping.TargetDefinition;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSlotResolverTest {
    private static final ResourceLocation MATERIAL = id("material");
    private static final IntSize GRID = new IntSize(16, 16);
    private static final SourceSlotImage IMAGE = new SourceSlotImage(new PixelImage(16, 16), GRID);

    @Test
    void targetOverrideWinsOverGlobalDefinition() {
        ResourceLocation targetSprite = id("target_override");
        ResourceLocation globalSprite = id("global_definition");
        List<ResourceLocation> requested = new ArrayList<>();

        Optional<Map<String, SourceSlotImage>> resolved = SourceSlotResolver.resolveSlots(
                target(Map.of(MATERIAL, new MaterialOverride(Map.of("all", targetSprite), Optional.empty())), Optional.empty()),
                MATERIAL,
                Optional.of(new MaterialDefinition(MATERIAL, Map.of("all", globalSprite))),
                ignored -> Optional.empty(), lookup(requested));

        assertEquals(IMAGE, resolved.orElseThrow().get("all"));
        assertEquals(List.of(targetSprite), requested);
    }

    @Test
    void globalAllSlotResolvesRequestedAll() {
        ResourceLocation globalSprite = id("global_all");
        List<ResourceLocation> requested = new ArrayList<>();

        Optional<Map<String, SourceSlotImage>> resolved = SourceSlotResolver.resolveSlots(target(Map.of(), Optional.empty()), MATERIAL,
                Optional.of(new MaterialDefinition(MATERIAL, Map.of("all", globalSprite))),
                ignored -> Optional.empty(), lookup(requested));

        assertEquals(IMAGE, resolved.orElseThrow().get("all"));
        assertEquals(List.of(globalSprite), requested);
    }

    @Test
    void targetSemanticFallbacksPrecedeGlobalSemanticFallbacks() {
        ResourceLocation targetSide = id("target_side");
        ResourceLocation globalAll = id("global_all");
        List<ResourceLocation> requested = new ArrayList<>();

        Optional<Map<String, SourceSlotImage>> resolved = SourceSlotResolver.resolveSlots(
                target("end", Map.of(MATERIAL, new MaterialOverride(Map.of("side", targetSide), Optional.empty())),
                        Optional.empty()), MATERIAL,
                Optional.of(new MaterialDefinition(MATERIAL, Map.of("all", globalAll))),
                ignored -> Optional.empty(), lookup(requested));

        assertEquals(IMAGE, resolved.orElseThrow().get("end"));
        assertEquals(List.of(targetSide), requested);
    }

    @Test
    void missingAllFallsBackToDefaultMaterialSprite() {
        ResourceLocation defaultSprite = id("default_sprite");
        List<ResourceLocation> defaultRequests = new ArrayList<>();
        List<ResourceLocation> imageRequests = new ArrayList<>();

        Optional<Map<String, SourceSlotImage>> resolved = SourceSlotResolver.resolveSlots(target(Map.of(), Optional.empty()), MATERIAL,
                Optional.empty(), material -> {
                    defaultRequests.add(material);
                    return Optional.of(defaultSprite);
                }, lookup(imageRequests));

        assertEquals(IMAGE, resolved.orElseThrow().get("all"));
        assertEquals(List.of(MATERIAL), defaultRequests);
        assertEquals(List.of(defaultSprite), imageRequests);
    }

    @Test
    void defaultSpriteDoesNotInventAConnectedFilenameEvenWhenItExists() {
        var sprite = id("block/unregistered_casing");
        var requested = new ArrayList<ResourceLocation>();
        var resolved = SourceSlotResolver.resolveSlots(connectedTarget(), MATERIAL, Optional.empty(),
                ignored -> Optional.of(sprite), (candidate, grid) -> {
                    requested.add(candidate);
                    return candidate.equals(sprite.withSuffix("_connected"))
                            ? Optional.of(new SourceSlotImage(new PixelImage(128,128), grid)) : Optional.empty();
                });
        assertTrue(resolved.isEmpty(), "Only a discovered or explicitly configured sprite may be used");
        assertEquals(List.of(sprite), requested);
    }

    @Test
    void registeredTargetCanHaveAnUnrelatedFilename() {
        var registered = id("block/sheets/arbitrary_ct_atlas");
        var requested = new ArrayList<ResourceLocation>();
        var resolved = SourceSlotResolver.resolveSlots(connectedTarget(), MATERIAL, Optional.empty(),
                ignored -> Optional.of(registered), (candidate, grid) -> {
                    requested.add(candidate);
                    return Optional.of(new SourceSlotImage(new PixelImage(128,128),grid));
                });
        assertEquals(128, resolved.orElseThrow().get("all").image().width());
        assertEquals(List.of(registered), requested);
    }

    @Test
    void registeredTargetRejectsWrongDimensionsWithoutProbingOtherNames() {
        var registered = id("block/wrong_shape");
        var requested = new ArrayList<ResourceLocation>();
        var resolved = SourceSlotResolver.resolveSlots(connectedTarget(), MATERIAL, Optional.empty(),
                ignored -> Optional.of(registered), (candidate, grid) -> {
                    requested.add(candidate);
                    return grid.integerScaleOf(128,64).isPresent()
                            ? Optional.of(new SourceSlotImage(new PixelImage(128,64),grid)) : Optional.empty();
                });
        assertTrue(resolved.isEmpty());
        assertEquals(List.of(registered), requested);
    }

    @Test
    void registeredTargetAcceptsIntegerScaledSheet() {
        var resolved = SourceSlotResolver.resolveSlots(connectedTarget(), MATERIAL, Optional.empty(),
                ignored -> Optional.of(id("block/hd_sheet")), (candidate, grid) ->
                        Optional.of(new SourceSlotImage(new PixelImage(256,256),grid)));
        assertEquals(2, resolved.orElseThrow().get("all").integerScale().orElseThrow());
    }

    private static TargetDefinition connectedTarget() {
        return new TargetDefinition(id("connected_target"), GRID, Map.of("all", new IntSize(128, 128)),
                List.of(new RegionMapping("all", new IntRect(32, 32, 16, 16),
                        new IntRect(0, 0, 16, 16), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
    }

    @Test
    void unresolvedRequiredSlotReturnsEmpty() {
        List<ResourceLocation> imageRequests = new ArrayList<>();

        Optional<Map<String, SourceSlotImage>> resolved = SourceSlotResolver.resolveSlots(target(Map.of(), Optional.empty()), MATERIAL,
                Optional.empty(), ignored -> Optional.empty(), lookup(imageRequests));

        assertTrue(resolved.isEmpty());
        assertTrue(imageRequests.isEmpty());
    }

    @Test
    void targetOverlayWinsOverGlobalOverlay() {
        ResourceLocation slotSprite = id("slot");
        ResourceLocation overrideOverlay = id("override_overlay");
        ResourceLocation targetOverlay = id("target_overlay");
        List<ResourceLocation> overlayRequests = new ArrayList<>();
        PixelImage overlay = new PixelImage(16, 16);

        var target = target(Map.of(MATERIAL, new MaterialOverride(Map.of("all", slotSprite), Optional.of(overrideOverlay))),
                Optional.of(targetOverlay));
        var resolved = SourceSlotResolver.resolveOverlay(target.materialOverrides().get(MATERIAL), target, sprite -> {
            overlayRequests.add(sprite);
            return Optional.of(overlay);
        });
        assertEquals(Optional.of(overlay), resolved);
        assertEquals(List.of(overrideOverlay), overlayRequests);
        assertFalse(overlayRequests.contains(targetOverlay));
    }

    private static BiFunction<ResourceLocation, IntSize, Optional<SourceSlotImage>> lookup(
            List<ResourceLocation> requested) {
        return (sprite, grid) -> {
            requested.add(sprite);
            assertEquals(GRID, grid);
            return Optional.of(IMAGE);
        };
    }

    private static TargetDefinition target(Map<ResourceLocation, MaterialOverride> overrides,
                                           Optional<ResourceLocation> overlay) {
        return target("all", overrides, overlay);
    }

    private static TargetDefinition target(String slot, Map<ResourceLocation, MaterialOverride> overrides,
                                           Optional<ResourceLocation> overlay) {
        return new TargetDefinition(id("target"), GRID, Map.of(slot, GRID),
                List.of(new RegionMapping(slot, new IntRect(0, 0, 16, 16),
                        new IntRect(0, 0, 16, 16), MappingTransform.IDENTITY)),
                List.of(), overlay, overrides, Optional.empty());
    }
}
