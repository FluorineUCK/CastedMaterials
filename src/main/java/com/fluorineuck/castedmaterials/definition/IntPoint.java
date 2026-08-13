package com.fluorineuck.castedmaterials.definition;

public record IntPoint(int x, int y) {
    public IntPoint {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("Coordinates must be non-negative");
        }
    }
}
