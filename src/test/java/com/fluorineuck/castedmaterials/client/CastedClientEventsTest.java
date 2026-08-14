package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.definition.CastedResourceReloadListener;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.client.render.CastedTextureHandle;
import com.fluorineuck.castedmaterials.client.render.UvTransform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CastedClientEventsTest {
    @Test
    void reloadRegistrationUsesTheSingletonListener() {
        List<PreparableReloadListener> listeners = new ArrayList<>();

        CastedClientEvents.registerReloadListener(listeners::add);

        assertEquals(1, listeners.size());
        assertSame(CastedResourceReloadListener.INSTANCE, listeners.getFirst());
    }

    @Test
    void repeatedClientSetupCreatesAndInstallsOneFacadeRuntime() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger creations = new AtomicInteger();
        List<ClientRuntime> installed = new ArrayList<>();
        ResourceLocation installedTexture = id("installed_runtime");
        ClientRuntime expected = (target, material, fallback) ->
                new CastedTextureHandle(installedTexture, UvTransform.IDENTITY, RenderableState.READY, 99);
        Consumer<ClientRuntime> installFacade = runtime -> {
            installed.add(runtime);
            CastedMaterialsClient.install(runtime);
        };
        CastedClientEvents.RuntimeInstallation state = new CastedClientEvents.RuntimeInstallation();

        try {
            CastedClientEvents.enqueueDefaultRuntimeInstallation(queued::add, state,
                    () -> {
                        creations.incrementAndGet();
                        return expected;
                    }, installFacade);
            CastedClientEvents.enqueueDefaultRuntimeInstallation(queued::add, state,
                    () -> {
                        creations.incrementAndGet();
                        return expected;
                    }, installFacade);
            queued.forEach(Runnable::run);

            assertEquals(2, queued.size());
            assertEquals(1, creations.get());
            assertEquals(1, installed.size());
            assertSame(expected, installed.getFirst());
            assertEquals(installedTexture,
                    CastedMaterialsClient.resolve(id("target"), id("material"), id("fallback")).texture());
        } finally {
            CastedMaterialsClient.install(ClientRuntime.fallbackOnly());
        }
    }
}
