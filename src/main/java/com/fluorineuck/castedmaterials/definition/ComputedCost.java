package com.fluorineuck.castedmaterials.definition;

public record ComputedCost(int maxPiecesPerSourceQuad, int additionalQuads) {
    public ComputedCost {
        if (maxPiecesPerSourceQuad < 0 || additionalQuads < 0) {
            throw new IllegalArgumentException("Computed cost cannot be negative");
        }
    }
}
