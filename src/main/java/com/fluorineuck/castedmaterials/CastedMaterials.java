package com.fluorineuck.castedmaterials;

import com.fluorineuck.castedmaterials.registry.CastedDataComponents;
import com.fluorineuck.castedmaterials.network.CastedNetwork;
import com.fluorineuck.castedmaterials.network.ServerPaletteEvents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CastedMaterials.MOD_ID)
public final class CastedMaterials {
    public static final String MOD_ID = "casted_materials";

    public CastedMaterials(IEventBus modBus) {
        CastedDataComponents.register(modBus);
        modBus.addListener(CastedNetwork::register);
        NeoForge.EVENT_BUS.addListener(ServerPaletteEvents::onDatapackSync);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
