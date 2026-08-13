package com.fluorineuck.castedmaterials.material;

import com.fluorineuck.castedmaterials.api.MaterialSetResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class CastedMaterialStateTest {
    @Test
    void persistsFullIdAndUsesPaletteIndexOnlyForWireState() {
        ResourceLocation copper = id("create:copper_casing");
        MaterialPalette palette = MaterialPalette.from(List.of(copper));
        CastedMaterialState state = new CastedMaterialState();

        assertEquals(MaterialSetResult.CHANGED, state.set(copper));
        CompoundTag tag = new CompoundTag();
        state.save(tag);

        assertEquals("create:copper_casing", tag.getString("CastedMaterial"));
        assertEquals(0, state.encodeIndex(palette).orElseThrow());
        assertEquals(copper, CastedMaterialState.decodeIndex(palette, 0).orElseThrow());

        CastedMaterialState loaded = new CastedMaterialState();
        loaded.load(tag);
        assertEquals(copper, loaded.get().orElseThrow());
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
        assertTrue(CastedMaterialState.decodeIndex(MaterialPalette.from(List.of(copper)), 1).isEmpty());
        assertTrue(CastedMaterialState.decodeIndex(MaterialPalette.from(List.of(copper)), -1).isEmpty());
    }
}
