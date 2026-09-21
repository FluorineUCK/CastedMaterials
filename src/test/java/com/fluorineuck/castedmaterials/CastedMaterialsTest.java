package com.fluorineuck.castedmaterials;

import com.fluorineuck.castedmaterials.client.CastedClientEvents;
import com.fluorineuck.castedmaterials.palette.MaterialPalettePayload;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CastedMaterialsTest {
    @Test
    void standaloneModAndClientEventsShareIdentity() {
        assertNotNull(CastedMaterials.class.getAnnotation(Mod.class));
        assertEquals("casted_materials", CastedMaterials.class.getAnnotation(Mod.class).value());
        assertEquals(CastedMaterials.MOD_ID, CastedMaterials.OWNER_MOD_ID);
        var subscriber = CastedClientEvents.class.getAnnotation(EventBusSubscriber.class);
        assertNotNull(subscriber);
        assertEquals(CastedMaterials.MOD_ID, subscriber.modid());
    }

    @Test
    void serializedComponentAndPaletteIdsRemainCompatibleWithExistingSaves() {
        assertEquals("casted_materials:material", CastedMaterials.MATERIAL.getId().toString());
        assertEquals("casted_materials:material_palette", MaterialPalettePayload.TYPE.id().toString());
        assertEquals("casted_materials", CastedMaterials.DATA_NAMESPACE);
    }
}
