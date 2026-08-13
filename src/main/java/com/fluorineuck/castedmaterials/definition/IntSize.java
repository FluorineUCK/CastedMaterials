package com.fluorineuck.castedmaterials.definition;

public record IntSize(int width, int height) {
    public IntSize {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
    }
}
