package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.definition.CastedResourceReloadListener;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    void clientSetupQueuesOneRuntimeInstallation() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger installations = new AtomicInteger();

        CastedClientEvents.enqueueRuntimeInstallation(queued::add, installations::incrementAndGet);
        queued.getFirst().run();

        assertEquals(1, queued.size());
        assertEquals(1, installations.get());
    }
}
