package com.fluorineuck.castedmaterials.client.definition;

import com.fluorineuck.castedmaterials.definition.IntSize;
import com.fluorineuck.castedmaterials.definition.MaterialDefinition;
import com.fluorineuck.castedmaterials.definition.MaterialOverride;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class SourceSlotResolver {
    private SourceSlotResolver() {
    }

    public static Optional<ResolvedSources> resolve(
            TargetDefinition target,
            ResourceLocation materialId,
            Optional<MaterialDefinition> globalDefinition,
            Function<ResourceLocation, Optional<ResourceLocation>> defaultSpriteLookup,
            BiFunction<ResourceLocation, IntSize, Optional<SourceSlotImage>> imageLookup,
            Function<ResourceLocation, Optional<PixelImage>> overlayLookup) {
        MaterialOverride override = target.materialOverrides().get(materialId);
        Map<String, ResourceLocation> targetSlots = override == null ? Map.of() : override.slots();
        Map<String, ResourceLocation> globalSlots = globalDefinition.map(MaterialDefinition::slots).orElse(Map.of());
        Map<String, SourceSlotImage> resolved = new LinkedHashMap<>();

        for (String slot : requiredSlots(target)) {
            IntSize logicalGrid = target.sourceGrids().get(slot);
            Optional<SourceSlotImage> image = resolveSlot(slot, logicalGrid, targetSlots, globalSlots,
                    materialId, defaultSpriteLookup, imageLookup);
            if (image.isEmpty()) {
                return Optional.empty();
            }
            resolved.put(slot, image.get());
        }

        Optional<PixelImage> overlay = resolveOverlay(override, target, overlayLookup);
        return Optional.of(new ResolvedSources(resolved, overlay));
    }

    private static LinkedHashSet<String> requiredSlots(TargetDefinition target) {
        LinkedHashSet<String> slots = new LinkedHashSet<>();
        target.regions().forEach(region -> slots.add(region.sourceSlot()));
        target.pixels().forEach(pixel -> slots.add(pixel.sourceSlot()));
        return slots;
    }

    private static Optional<SourceSlotImage> resolveSlot(
            String slot,
            IntSize logicalGrid,
            Map<String, ResourceLocation> targetSlots,
            Map<String, ResourceLocation> globalSlots,
            ResourceLocation materialId,
            Function<ResourceLocation, Optional<ResourceLocation>> defaultSpriteLookup,
            BiFunction<ResourceLocation, IntSize, Optional<SourceSlotImage>> imageLookup) {
        Optional<SourceSlotImage> exactTarget = load(targetSlots.get(slot), logicalGrid, imageLookup);
        if (exactTarget.isPresent()) {
            return exactTarget;
        }
        Optional<SourceSlotImage> exactGlobal = load(globalSlots.get(slot), logicalGrid, imageLookup);
        if (exactGlobal.isPresent()) {
            return exactGlobal;
        }

        for (String fallback : fallbacks(slot)) {
            if (fallback.equals(slot)) {
                continue;
            }
            Optional<SourceSlotImage> targetFallback = load(targetSlots.get(fallback), logicalGrid, imageLookup);
            if (targetFallback.isPresent()) {
                return targetFallback;
            }
        }
        for (String fallback : fallbacks(slot)) {
            if (fallback.equals(slot)) {
                continue;
            }
            Optional<SourceSlotImage> globalFallback = load(globalSlots.get(fallback), logicalGrid, imageLookup);
            if (globalFallback.isPresent()) {
                return globalFallback;
            }
        }
        return defaultSpriteLookup.apply(materialId).flatMap(sprite -> imageLookup.apply(sprite, logicalGrid));
    }

    private static Optional<SourceSlotImage> load(
            ResourceLocation sprite,
            IntSize logicalGrid,
            BiFunction<ResourceLocation, IntSize, Optional<SourceSlotImage>> imageLookup) {
        return sprite == null ? Optional.empty() : imageLookup.apply(sprite, logicalGrid);
    }

    private static List<String> fallbacks(String slot) {
        return switch (slot) {
            case "side" -> List.of("side", "all", "particle", "dominant");
            case "end" -> List.of("all", "side", "particle", "dominant");
            case "all" -> List.of("all", "side", "particle", "dominant");
            default -> List.of("all", "side", "particle", "dominant");
        };
    }

    private static Optional<PixelImage> resolveOverlay(
            MaterialOverride override,
            TargetDefinition target,
            Function<ResourceLocation, Optional<PixelImage>> overlayLookup) {
        if (override != null && override.overlay().isPresent()) {
            Optional<PixelImage> overrideOverlay = overlayLookup.apply(override.overlay().get());
            if (overrideOverlay.isPresent()) {
                return overrideOverlay;
            }
        }
        return target.overlay().flatMap(overlayLookup);
    }
}
