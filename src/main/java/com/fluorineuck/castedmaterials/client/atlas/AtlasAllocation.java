package com.fluorineuck.castedmaterials.client.atlas;

public record AtlasAllocation(int x, int y, int width, int height) {
    public AtlasAllocation {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid atlas allocation");
        }
    }

    public boolean intersects(AtlasAllocation other) {
        return x < other.x + other.width && x + width > other.x
                && y < other.y + other.height && y + height > other.y;
    }
}
