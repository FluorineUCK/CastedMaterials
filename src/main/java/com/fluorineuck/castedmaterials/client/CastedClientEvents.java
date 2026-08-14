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
import java.util.function.Supplier;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = CastedMaterials.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CastedClientEvents {
    private static final RuntimeInstallation DEFAULT_RUNTIME_INSTALLATION = new RuntimeInstallation();

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
        enqueueDefaultRuntimeInstallation(event::enqueueWork, DEFAULT_RUNTIME_INSTALLATION,
                DefaultClientRuntime::create, CastedMaterialsClient::install);
    }

    static void enqueueDefaultRuntimeInstallation(Consumer<Runnable> enqueueWork, RuntimeInstallation state,
                                                  Supplier<ClientRuntime> runtimeFactory,
                                                  Consumer<ClientRuntime> installer) {
        Objects.requireNonNull(enqueueWork, "enqueueWork").accept(() ->
                Objects.requireNonNull(state, "state").install(runtimeFactory, installer));
    }

    static final class RuntimeInstallation {
        private ClientRuntime runtime;
        private boolean installed;

        synchronized void install(Supplier<ClientRuntime> runtimeFactory, Consumer<ClientRuntime> installer) {
            if (installed) {
                return;
            }
            if (runtime == null) {
                runtime = Objects.requireNonNull(runtimeFactory, "runtimeFactory").get();
            }
            Objects.requireNonNull(installer, "installer").accept(runtime);
            installed = true;
        }
    }
}
