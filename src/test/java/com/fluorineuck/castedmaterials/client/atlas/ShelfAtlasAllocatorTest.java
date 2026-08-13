package com.fluorineuck.castedmaterials.client.atlas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShelfAtlasAllocatorTest {
    @Test
    void packsExactlyOneHundredTwentyEight64By32EntriesOnOnePage() {
        ShelfAtlasAllocator allocator = new ShelfAtlasAllocator(512, 512);
        List<AtlasAllocation> allocations = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            allocations.add(allocator.allocate(64, 32).orElseThrow());
        }
        assertTrue(allocator.allocate(64, 32).isEmpty());
        assertEquals(128, allocations.stream().distinct().count());
    }

    @Test
    void rejectsInvalidAndOversizedTargetsAndNeverOverlaps() {
        ShelfAtlasAllocator allocator = new ShelfAtlasAllocator(512, 512);
        assertTrue(allocator.allocate(257, 1).isEmpty());
        assertTrue(allocator.allocate(1, 257).isEmpty());
        assertTrue(allocator.allocate(0, 1).isEmpty());
        AtlasAllocation a = allocator.allocate(100, 50).orElseThrow();
        AtlasAllocation b = allocator.allocate(100, 30).orElseThrow();
        assertFalse(a.intersects(b));
    }
}
