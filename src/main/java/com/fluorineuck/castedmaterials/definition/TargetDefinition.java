package com.fluorineuck.castedmaterials.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record TargetDefinition(
        ResourceLocation id,
        IntSize size,
        Map<String, IntSize> sourceGrids,
        List<RegionMapping> regions,
        List<PixelMapping> pixels,
        Optional<ResourceLocation> overlay,
        Map<ResourceLocation, MaterialOverride> materialOverrides,
        Optional<ComputedCost> computedCost) {
    public TargetDefinition {
        sourceGrids = Map.copyOf(sourceGrids);
        regions = List.copyOf(regions);
        pixels = List.copyOf(pixels);
        overlay = overlay == null ? Optional.empty() : overlay;
        materialOverrides = Map.copyOf(materialOverrides);
        computedCost = computedCost == null ? Optional.empty() : computedCost;
    }
}
