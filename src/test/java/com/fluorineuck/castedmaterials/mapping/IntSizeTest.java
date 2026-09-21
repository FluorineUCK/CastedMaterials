package com.fluorineuck.castedmaterials.mapping;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntSizeTest {
    @Test
    void physicalSourcesMayExceedTargetLimitButMustBeUniformIntegerScaled() {
        var grid = new IntSize(128,64);
        assertEquals(1, grid.integerScaleOf(128,64).orElseThrow());
        assertEquals(4, grid.integerScaleOf(512,256).orElseThrow());
        for (int[] dimensions : new int[][]{{0,0},{-128,-64},{64,32},{256,64},{129,65}}) {
            assertTrue(grid.integerScaleOf(dimensions[0],dimensions[1]).isEmpty());
        }
    }
}
