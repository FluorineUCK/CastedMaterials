package com.fluorineuck.castedmaterials.client.atlas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShelfAtlasAllocator {
    private static final int MAX_OUTPUT_AXIS = 256;

    private final int width;
    private final int height;
    private final List<Row> rows = new ArrayList<>();

    public ShelfAtlasAllocator(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Atlas dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public synchronized Optional<AtlasAllocation> allocate(int requestedWidth, int requestedHeight) {
        if (requestedWidth <= 0 || requestedHeight <= 0
                || requestedWidth > MAX_OUTPUT_AXIS || requestedHeight > MAX_OUTPUT_AXIS
                || requestedWidth > width || requestedHeight > height) {
            return Optional.empty();
        }
        for (Row row : rows) {
            if (requestedHeight <= row.height && row.usedX + requestedWidth <= width) {
                AtlasAllocation allocation = new AtlasAllocation(
                        row.usedX, row.y, requestedWidth, requestedHeight);
                row.usedX += requestedWidth;
                return Optional.of(allocation);
            }
        }
        int y = rows.isEmpty() ? 0 : rows.getLast().y + rows.getLast().height;
        if (y + requestedHeight > height) {
            return Optional.empty();
        }
        Row row = new Row(y, requestedHeight, requestedWidth);
        rows.add(row);
        return Optional.of(new AtlasAllocation(0, y, requestedWidth, requestedHeight));
    }

    public synchronized void reset() {
        rows.clear();
    }

    private static final class Row {
        private final int y;
        private final int height;
        private int usedX;

        private Row(int y, int height, int usedX) {
            this.y = y;
            this.height = height;
            this.usedX = usedX;
        }
    }
}
