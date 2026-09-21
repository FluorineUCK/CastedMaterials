package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.CastedResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastedClientEventsTest {
    @Test
    void absentMaterialUsesBaseWithoutInitializingMinecraftRuntime() {
        var model = new net.minecraft.client.model.geom.ModelPart(List.of(), java.util.Map.of());
        var handle = CastedMaterialsClient.resolveModel(model, id("target"), null, id("base"));
        assertEquals(com.fluorineuck.castedmaterials.client.CastedModelHandle.Backend.FALLBACK,
                handle.backend());
        assertEquals("NO_MATERIAL", handle.reason());
    }

    @Test
    void reloadRegistrationUsesTheSingletonListener() {
        List<PreparableReloadListener> listeners = new ArrayList<>();

        CastedClientEvents.registerReloadListener(listeners::add);

        assertEquals(1, listeners.size());
        assertSame(CastedResources.INSTANCE, listeners.getFirst());
    }

    @Test
    void additionalModelRegistrationForwardsEveryDiscoveredAttachmentModel() {
        var registered = new ArrayList<net.minecraft.client.resources.model.ModelResourceLocation>();

        CastedClientEvents.registerAdditionalModels(List.of(id("hat/a"), id("hat/b")), registered::add);

        assertEquals(List.of(
                net.minecraft.client.resources.model.ModelResourceLocation.standalone(id("hat/a")),
                net.minecraft.client.resources.model.ModelResourceLocation.standalone(id("hat/b"))), registered);
    }

}
