package com.fluorineuck.castedmaterials;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectMetadataTest {
    @Test
    void exposesStableModIdentityAndCasingTags() {
        assertEquals("casted_materials", CastedMaterials.MOD_ID);
        assertEquals(ResourceLocation.fromNamespaceAndPath("create", "casing"),
                CastedIds.CASING_ITEMS.location());
        assertEquals(ResourceLocation.fromNamespaceAndPath("create", "casing"),
                CastedIds.CASING_BLOCKS.location());
    }
}
