package com.fluorineuck.castedmaterials.mapping;

import java.util.Arrays;

public final class PixelImage {
    private final int width;
    private final int height;
    private final int[] pixels;

    public PixelImage(int width, int height) {
        this(width, height, new int[Math.multiplyExact(width, height)]);
    }

    public PixelImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels.length != Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("Invalid image dimensions or pixel count");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int get(int x, int y) {
        return pixels[index(x, y)];
    }

    public void set(int x, int y, int argb) {
        pixels[index(x, y)] = argb;
    }

    public int[] pixels() {
        return pixels.clone();
    }

    public void copyRow(PixelImage source, int sourceX, int sourceY, int destinationX,
                        int destinationY, int length) {
        source.checkRange(sourceX, sourceY, length);
        checkRange(destinationX, destinationY, length);
        System.arraycopy(source.pixels, sourceY * source.width + sourceX,
                pixels, destinationY * width + destinationX, length);
    }

    public void alphaOver(PixelImage overlay) {
        if (overlay.width != width || overlay.height != height) {
            throw new IllegalArgumentException("Overlay dimensions must match");
        }
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = alphaOver(pixels[i], overlay.pixels[i]);
        }
    }

    private static int alphaOver(int destination, int source) {
        int sa = source >>> 24;
        if (sa == 0) return destination;
        if (sa == 255) return source;
        int da = destination >>> 24;
        int inverse = 255 - sa;
        int outA = sa + (da * inverse + 127) / 255;
        if (outA == 0) return 0;
        int r = channel(destination, source, 16, sa, da, inverse, outA);
        int g = channel(destination, source, 8, sa, da, inverse, outA);
        int b = channel(destination, source, 0, sa, da, inverse, outA);
        return outA << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int destination, int source, int shift,
                               int sa, int da, int inverse, int outA) {
        long sc = source >>> shift & 0xff;
        long dc = destination >>> shift & 0xff;
        long numerator = sc * sa * 255L + dc * da * inverse;
        return (int) ((numerator + outA * 127L) / (outA * 255L));
    }

    private int index(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException(x + "," + y + " outside " + width + "x" + height);
        }
        return y * width + x;
    }

    private void checkRange(int x, int y, int length) {
        if (x < 0 || y < 0 || y >= height || length < 0 || x + length > width) {
            throw new IndexOutOfBoundsException("Invalid row range");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PixelImage image && width == image.width && height == image.height
                && Arrays.equals(pixels, image.pixels);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(pixels);
    }
}
