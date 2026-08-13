package com.fluorineuck.castedmaterials.definition;

public record IntRect(int x, int y, int width, int height) {
    public IntRect {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Rectangle origin must be non-negative and size positive");
        }
    }

    public boolean fits(IntSize size) {
        return (long) x + width <= size.width() && (long) y + height <= size.height();
    }
}
