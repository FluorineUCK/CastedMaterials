package com.fluorineuck.castedmaterials.client.render;

public record UvTransform(float offsetU, float offsetV, float scaleU, float scaleV) {
    public static final UvTransform IDENTITY = new UvTransform(0, 0, 1, 1);
    public float mapU(float u) { return offsetU + u * scaleU; }
    public float mapV(float v) { return offsetV + v * scaleV; }
}
