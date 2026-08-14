package com.fluorineuck.castedmaterials.client.cache;

import com.fluorineuck.castedmaterials.client.atlas.AtlasRegion;
import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;

import java.util.Optional;

public record UploadResult(
        boolean uploaded,
        Optional<AtlasRegion> region,
        Optional<RenderFailureCode> failure) {
    public UploadResult {
        region = region == null ? Optional.empty() : region;
        failure = failure == null ? Optional.empty() : failure;
    }
}
