package com.fluorineuck.castedmaterials.palette;

import com.fluorineuck.castedmaterials.palette.MaterialDiscovery.TagSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialPaletteSyncTest {
    @BeforeEach
    @AfterEach
    void resetPalettes() {
        MaterialPaletteSync.deactivate();
        MaterialPaletteSync.replaceClient(MaterialPalette.empty());
    }

    @Test
    void serverAndClientSnapshotsAreIndependentAndAnEmptyServerPaletteIsActive() {
        assertTrue(MaterialPaletteSync.serverPalette().isEmpty());
        MaterialPalette client = MaterialPalette.from(java.util.List.of(id("client")));
        MaterialPaletteSync.replaceClient(client);
        MaterialPaletteSync.activate(MaterialPalette.empty());
        assertSame(MaterialPalette.empty(), MaterialPaletteSync.serverPalette().orElseThrow());
        assertSame(client, MaterialPaletteSync.clientPalette());
        MaterialPaletteSync.deactivate();
        assertTrue(MaterialPaletteSync.serverPalette().isEmpty());
        assertSame(client, MaterialPaletteSync.clientPalette());
        assertThrows(NullPointerException.class, () -> MaterialPaletteSync.replaceClient(null));
        assertThrows(NullPointerException.class, () -> MaterialPaletteSync.activate(null));
        assertSame(client, MaterialPaletteSync.clientPalette());
        assertTrue(MaterialPaletteSync.serverPalette().isEmpty());
    }

    @Test
    void skipsInvalidPairsAndBroadcastsOnlyChangedReloads() {
        ResourceLocation valid = id("valid");
        ResourceLocation invalid = id("invalid");
        TagSnapshot snapshot = new TagSnapshot(
                Map.of(valid, Items.STONE, invalid, Items.DIRT),
                Map.of(valid, Blocks.STONE, invalid, Blocks.STONE),
                Set.of(valid, invalid), Set.of(valid, invalid));

        MaterialPaletteSync.SyncDecision first =
                MaterialPaletteSync.rebuild(snapshot, MaterialPaletteSync.SyncCause.RELOAD);
        MaterialPaletteSync.SyncDecision unchanged =
                MaterialPaletteSync.rebuild(snapshot, MaterialPaletteSync.SyncCause.RELOAD);
        MaterialPaletteSync.SyncDecision joining =
                MaterialPaletteSync.rebuild(snapshot, MaterialPaletteSync.SyncCause.PLAYER_JOIN);

        assertEquals(java.util.List.of(valid), first.palette().ids());
        assertEquals(1, first.report().issues().size());
        assertTrue(first.broadcast());
        assertFalse(unchanged.broadcast());
        assertTrue(joining.broadcast());
        assertArrayEquals(first.palette().fingerprint(), joining.palette().fingerprint());
    }
}
