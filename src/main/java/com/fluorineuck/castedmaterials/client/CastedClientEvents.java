package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.CastedMaterials;
import com.fluorineuck.castedmaterials.client.definition.CastedResourceReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Objects;
import java.util.function.Consumer;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = CastedMaterials.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CastedClientEvents {
    private static ClientRuntime defaultRuntime;

    private CastedClientEvents() {
    }

    @SubscribeEvent
    static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        registerReloadListener(event::registerReloadListener);
    }

    static void registerReloadListener(Consumer<PreparableReloadListener> registrar) {
        Objects.requireNonNull(registrar, "registrar").accept(CastedResourceReloadListener.INSTANCE);
    }

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        enqueueRuntimeInstallation(event::enqueueWork, CastedClientEvents::installDefaultRuntime);
    }

    static void enqueueRuntimeInstallation(Consumer<Runnable> enqueueWork, Runnable installation) {
        Objects.requireNonNull(enqueueWork, "enqueueWork").accept(Objects.requireNonNull(installation, "installation"));
    }

    private static synchronized void installDefaultRuntime() {
        if (defaultRuntime == null) {
            defaultRuntime = DefaultClientRuntime.create();
        }
        CastedMaterialsClient.install(defaultRuntime);
    }
}
