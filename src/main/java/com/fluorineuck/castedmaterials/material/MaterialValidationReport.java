package com.fluorineuck.castedmaterials.material;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record MaterialValidationReport(List<ResourceLocation> validIds, List<Issue> issues) {
    public MaterialValidationReport {
        validIds = List.copyOf(validIds);
        issues = List.copyOf(issues);
    }

    public record Issue(ResourceLocation id, MaterialPairValidator.ErrorCode code) {
    }
}
