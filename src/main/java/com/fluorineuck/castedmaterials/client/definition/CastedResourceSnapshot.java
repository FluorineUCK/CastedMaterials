package com.fluorineuck.castedmaterials.client.definition;

import com.fluorineuck.castedmaterials.definition.MaterialDefinition;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record CastedResourceSnapshot(
        long generation,
        Map<ResourceLocation, TargetDefinition> targets,
        Map<ResourceLocation, MaterialDefinition> materials) {
    public CastedResourceSnapshot {
        targets = Map.copyOf(targets);
        materials = Map.copyOf(materials);
    }

    public static CastedResourceSnapshot empty() {
        return new CastedResourceSnapshot(0, Map.of(), Map.of());
    }
}
