package com.fluorineuck.castedmaterials.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record MaterialDefinition(ResourceLocation id, Map<String, ResourceLocation> slots) {
    public MaterialDefinition {
        slots = Map.copyOf(slots);
    }
}
