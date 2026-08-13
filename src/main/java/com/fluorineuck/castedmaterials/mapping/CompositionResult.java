package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;

import java.util.List;

public record CompositionResult(
        PixelImage image,
        RenderableState state,
        List<RenderFailureCode> diagnostics) {
    public CompositionResult {
        diagnostics = List.copyOf(diagnostics);
    }
}
