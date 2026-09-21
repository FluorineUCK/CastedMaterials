package com.fluorineuck.castedmaterials;

import com.fluorineuck.castedmaterials.CastedMaterialsApi.MaterialSetResult;
import com.fluorineuck.castedmaterials.palette.MaterialPalette;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class CastedMaterialStateTest {
    @Test
    void persistsFullIdAndResolvesPaletteIndices() {
        ResourceLocation copper = id("create:copper_casing");
        MaterialPalette palette = MaterialPalette.from(List.of(copper));
        CastedMaterialState state = new CastedMaterialState();

        assertEquals(MaterialSetResult.CHANGED, state.set(copper));
        CompoundTag tag = new CompoundTag();
        state.save(tag);

        assertEquals("create:copper_casing", tag.getString("CastedMaterial"));
        assertEquals(0, palette.indexOf(state.get().orElseThrow()).orElseThrow());
        assertEquals(copper, palette.idAt(0).orElseThrow());

        CastedMaterialState loaded = new CastedMaterialState();
        loaded.load(tag);
        assertEquals(copper, loaded.get().orElseThrow());
    }

    @Test
    void clearIsIdempotent() {
        CastedMaterialState state = new CastedMaterialState();

        assertEquals(MaterialSetResult.UNCHANGED, state.clear());
        assertEquals(MaterialSetResult.CHANGED, state.set(id("create:andesite_casing")));
        assertEquals(MaterialSetResult.CHANGED, state.clear());
        assertTrue(state.get().isEmpty());
        assertEquals(MaterialSetResult.UNCHANGED, state.clear());
    }

    @Test
    void loadingClearedServerSnapshotRemovesPreviouslySyncedClientMaterial() {
        CastedMaterialState server = new CastedMaterialState();
        CastedMaterialState client = new CastedMaterialState();
        server.set(id("create:copper_casing"));
        CompoundTag installed = new CompoundTag();
        server.save(installed);
        client.load(installed);
        assertEquals(server.get(), client.get());

        server.clear();
        CompoundTag removed = new CompoundTag();
        server.save(removed);
        assertFalse(removed.contains(CastedMaterialState.NBT_KEY));
        client.load(removed);

        assertTrue(client.get().isEmpty(), "A full snapshot without CastedMaterial must clear stale client state");
        client.load(removed);
        assertTrue(client.get().isEmpty(), "Repeated empty snapshots must remain empty");
    }

    @Test
    void invalidNbtDoesNotReplacePreviouslyValidState() {
        ResourceLocation copper = id("create:copper_casing");
        CastedMaterialState state = new CastedMaterialState();
        state.set(copper);
        CompoundTag malformed = new CompoundTag();
        malformed.putString("CastedMaterial", "invalid id with spaces");

        state.load(malformed);

        assertEquals(copper, state.get().orElseThrow());
    }

    @Test
    void reportsUnchangedAndRejectsUnknownPaletteIndices() {
        ResourceLocation copper = id("create:copper_casing");
        CastedMaterialState state = new CastedMaterialState();

        assertEquals(MaterialSetResult.CHANGED, state.set(copper));
        assertEquals(MaterialSetResult.UNCHANGED, state.set(copper));
        assertTrue(MaterialPalette.from(List.of(copper)).idAt(1).isEmpty());
        assertTrue(MaterialPalette.from(List.of(copper)).idAt(-1).isEmpty());
    }
}
