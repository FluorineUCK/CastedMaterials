package com.fluorineuck.castedmaterials.network;

import com.fluorineuck.castedmaterials.material.TagSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class ServerPaletteSynchronizerTest {
    @Test
    void skipsInvalidPairsAndBroadcastsOnlyChangedReloads() {
        ResourceLocation valid = id("valid");
        ResourceLocation invalid = id("invalid");
        TagSnapshot snapshot = new TagSnapshot(
                Map.of(valid, Items.STONE, invalid, Items.DIRT),
                Map.of(valid, Blocks.STONE, invalid, Blocks.STONE),
                Set.of(valid, invalid), Set.of(valid, invalid));
        ServerPaletteSynchronizer synchronizer = new ServerPaletteSynchronizer();

        ServerPaletteSynchronizer.SyncDecision first =
                synchronizer.rebuild(snapshot, ServerPaletteSynchronizer.SyncCause.RELOAD);
        ServerPaletteSynchronizer.SyncDecision unchanged =
                synchronizer.rebuild(snapshot, ServerPaletteSynchronizer.SyncCause.RELOAD);
        ServerPaletteSynchronizer.SyncDecision joining =
                synchronizer.rebuild(snapshot, ServerPaletteSynchronizer.SyncCause.PLAYER_JOIN);

        assertEquals(java.util.List.of(valid), first.palette().ids());
        assertEquals(1, first.report().issues().size());
        assertTrue(first.broadcast());
        assertFalse(unchanged.broadcast());
        assertTrue(joining.broadcast());
        assertArrayEquals(first.palette().fingerprint(), joining.palette().fingerprint());
    }
}
