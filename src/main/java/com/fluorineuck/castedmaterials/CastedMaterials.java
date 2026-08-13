package com.fluorineuck.castedmaterials;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CastedMaterials.MOD_ID)
public final class CastedMaterials {
    public static final String MOD_ID = "casted_materials";

    public CastedMaterials(IEventBus modBus) {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
