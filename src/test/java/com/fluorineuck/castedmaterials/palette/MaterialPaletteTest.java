package com.fluorineuck.castedmaterials.palette;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialPaletteTest {
    @Test
    void sortsNamespaceBeforePathAndDeduplicates() {
        MaterialPalette palette = MaterialPalette.from(
                List.of(id("z:b"), id("a:z"), id("z:b"), id("a:a")));

        assertEquals(List.of(id("a:a"), id("a:z"), id("z:b")), palette.ids());
        assertEquals(2, palette.indexOf(id("z:b")).orElseThrow());
        assertEquals(id("a:z"), palette.idAt(1).orElseThrow());
        assertTrue(palette.idAt(3).isEmpty());
        assertTrue(palette.idAt(-1).isEmpty());
    }

    @Test
    void fingerprintIsStableForEquivalentInputOrder() {
        assertArrayEquals(
                MaterialPalette.from(List.of(id("b:x"), id("a:y"))).fingerprint(),
                MaterialPalette.from(List.of(id("a:y"), id("b:x"))).fingerprint());
    }

    @Test
    void fingerprintAndIdsCannotBeMutatedByCallers() {
        MaterialPalette palette = MaterialPalette.from(List.of(id("a:x")));
        byte[] first = palette.fingerprint();
        first[0] ^= 0xff;

        assertFalse(java.util.Arrays.equals(first, palette.fingerprint()));
        assertThrows(UnsupportedOperationException.class, () -> palette.ids().add(id("b:y")));
    }
}
