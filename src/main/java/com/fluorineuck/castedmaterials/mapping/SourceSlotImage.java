package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.definition.IntSize;

import java.util.OptionalInt;

public record SourceSlotImage(PixelImage image, IntSize logicalGrid, boolean animated) {
    public OptionalInt integerScale() {
        if (image.width() % logicalGrid.width() != 0 || image.height() % logicalGrid.height() != 0) {
            return OptionalInt.empty();
        }
        int x = image.width() / logicalGrid.width();
        int y = image.height() / logicalGrid.height();
        return x > 0 && x == y ? OptionalInt.of(x) : OptionalInt.empty();
    }

    public int sample(int logicalX, int logicalY, int scale) {
        int offset = scale / 2;
        return image.get(logicalX * scale + offset, logicalY * scale + offset);
    }
}
