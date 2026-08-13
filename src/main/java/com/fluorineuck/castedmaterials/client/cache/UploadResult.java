package com.fluorineuck.castedmaterials.client.cache;

import com.fluorineuck.castedmaterials.client.atlas.AtlasAllocation;
import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;

import java.util.Optional;

public record UploadResult(
        boolean uploaded,
        Optional<AtlasAllocation> allocation,
        Optional<RenderFailureCode> failure) {
    public UploadResult {
        allocation = allocation == null ? Optional.empty() : allocation;
        failure = failure == null ? Optional.empty() : failure;
    }
}
