package com.fluorineuck.castedmaterials.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

public record MaterialOverride(Map<String, ResourceLocation> slots, Optional<ResourceLocation> overlay) {
    public MaterialOverride {
        slots = Map.copyOf(slots);
        overlay = overlay == null ? Optional.empty() : overlay;
    }
}
