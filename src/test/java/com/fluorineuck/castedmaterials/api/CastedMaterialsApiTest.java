package com.fluorineuck.castedmaterials.api;

import com.fluorineuck.castedmaterials.material.CastedMaterialState;
import com.fluorineuck.castedmaterials.material.MaterialPalette;
import com.fluorineuck.castedmaterials.material.ServerMaterialPalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CastedMaterialsApiTest {
    @AfterEach
    void resetPalette() {
        ServerMaterialPalette.deactivate();
    }

    @Test
    void distinguishesNoPaletteUnknownChangedAndUnchangedForHolder() {
        ResourceLocation copper = id("create:copper_casing");
        ResourceLocation brass = id("create:brass_casing");
        FakeHolder holder = new FakeHolder();

        assertEquals(MaterialSetResult.NO_ACTIVE_PALETTE,
                CastedMaterialsApi.setMaterial(holder, copper));
        ServerMaterialPalette.activate(MaterialPalette.from(List.of(copper)));
        assertEquals(MaterialSetResult.UNKNOWN_MATERIAL,
                CastedMaterialsApi.setMaterial(holder, brass));
        assertEquals(MaterialSetResult.CHANGED,
                CastedMaterialsApi.setMaterial(holder, copper));
        assertEquals(MaterialSetResult.UNCHANGED,
                CastedMaterialsApi.setMaterial(holder, copper));
        assertEquals(copper, CastedMaterialsApi.getMaterial(holder).orElseThrow());
        assertEquals(1, holder.changeCount);
    }

    @Test
    void storesMaterialOnAnyItemStackThroughTheRegisteredComponent() {
        ResourceLocation copper = id("create:copper_casing");
        ItemStack stack = new ItemStack(Items.STICK);
        ServerMaterialPalette.activate(MaterialPalette.from(List.of(copper)));

        assertEquals(MaterialSetResult.CHANGED,
                CastedMaterialsApi.setMaterial(stack, copper));
        assertEquals(MaterialSetResult.UNCHANGED,
                CastedMaterialsApi.setMaterial(stack, copper));
        assertEquals(copper, CastedMaterialsApi.getMaterial(stack).orElseThrow());
    }

    private static final class FakeHolder implements CastedMaterialHolder {
        private final CastedMaterialState state = new CastedMaterialState();
        private int changeCount;

        @Override
        public CastedMaterialState castedMaterialState() {
            return state;
        }

        @Override
        public void onCastedMaterialChanged() {
            changeCount++;
        }
    }
}
