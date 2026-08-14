package com.fluorineuck.castedmaterials.client.definition;

import com.fluorineuck.castedmaterials.definition.IntRect;
import com.fluorineuck.castedmaterials.definition.IntSize;
import com.fluorineuck.castedmaterials.definition.MappingTransform;
import com.fluorineuck.castedmaterials.definition.MaterialDefinition;
import com.fluorineuck.castedmaterials.definition.MaterialOverride;
import com.fluorineuck.castedmaterials.definition.RegionMapping;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;
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
    private static final SourceSlotImage IMAGE = new SourceSlotImage(new PixelImage(16, 16), GRID, false);

    @Test
    void targetOverrideWinsOverGlobalDefinition() {
        ResourceLocation targetSprite = id("target_override");
        ResourceLocation globalSprite = id("global_definition");
        List<ResourceLocation> requested = new ArrayList<>();

        Optional<ResolvedSources> resolved = SourceSlotResolver.resolve(
                target(Map.of(MATERIAL, new MaterialOverride(Map.of("all", targetSprite), Optional.empty())), Optional.empty()),
                MATERIAL,
                Optional.of(new MaterialDefinition(MATERIAL, Map.of("all", globalSprite))),
                ignored -> Optional.empty(), lookup(requested), ignored -> Optional.empty());

        assertEquals(IMAGE, resolved.orElseThrow().slots().get("all"));
        assertEquals(List.of(targetSprite), requested);
    }

    @Test
    void globalAllSlotResolvesRequestedAll() {
        ResourceLocation globalSprite = id("global_all");
        List<ResourceLocation> requested = new ArrayList<>();

        Optional<ResolvedSources> resolved = SourceSlotResolver.resolve(target(Map.of(), Optional.empty()), MATERIAL,
                Optional.of(new MaterialDefinition(MATERIAL, Map.of("all", globalSprite))),
                ignored -> Optional.empty(), lookup(requested), ignored -> Optional.empty());

        assertEquals(IMAGE, resolved.orElseThrow().slots().get("all"));
        assertEquals(List.of(globalSprite), requested);
    }

    @Test
    void targetSemanticFallbacksPrecedeGlobalSemanticFallbacks() {
        ResourceLocation targetSide = id("target_side");
        ResourceLocation globalAll = id("global_all");
        List<ResourceLocation> requested = new ArrayList<>();

        Optional<ResolvedSources> resolved = SourceSlotResolver.resolve(
                target("end", Map.of(MATERIAL, new MaterialOverride(Map.of("side", targetSide), Optional.empty())),
                        Optional.empty()), MATERIAL,
                Optional.of(new MaterialDefinition(MATERIAL, Map.of("all", globalAll))),
                ignored -> Optional.empty(), lookup(requested), ignored -> Optional.empty());

        assertEquals(IMAGE, resolved.orElseThrow().slots().get("end"));
        assertEquals(List.of(targetSide), requested);
    }

    @Test
    void missingAllFallsBackToDefaultMaterialSprite() {
        ResourceLocation defaultSprite = id("default_sprite");
        List<ResourceLocation> defaultRequests = new ArrayList<>();
        List<ResourceLocation> imageRequests = new ArrayList<>();

        Optional<ResolvedSources> resolved = SourceSlotResolver.resolve(target(Map.of(), Optional.empty()), MATERIAL,
                Optional.empty(), material -> {
                    defaultRequests.add(material);
                    return Optional.of(defaultSprite);
                }, lookup(imageRequests), ignored -> Optional.empty());

        assertEquals(IMAGE, resolved.orElseThrow().slots().get("all"));
        assertEquals(List.of(MATERIAL), defaultRequests);
        assertEquals(List.of(defaultSprite), imageRequests);
    }

    @Test
    void unresolvedRequiredSlotReturnsEmpty() {
        List<ResourceLocation> imageRequests = new ArrayList<>();

        Optional<ResolvedSources> resolved = SourceSlotResolver.resolve(target(Map.of(), Optional.empty()), MATERIAL,
                Optional.empty(), ignored -> Optional.empty(), lookup(imageRequests), ignored -> Optional.empty());

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

        Optional<ResolvedSources> resolved = SourceSlotResolver.resolve(
                target(Map.of(MATERIAL, new MaterialOverride(Map.of("all", slotSprite), Optional.of(overrideOverlay))),
                        Optional.of(targetOverlay)), MATERIAL, Optional.empty(), ignored -> Optional.empty(),
                lookup(new ArrayList<>()), sprite -> {
                    overlayRequests.add(sprite);
                    return Optional.of(overlay);
                });

        assertEquals(Optional.of(overlay), resolved.orElseThrow().overlay());
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
