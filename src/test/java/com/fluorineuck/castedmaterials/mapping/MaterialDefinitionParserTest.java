package com.fluorineuck.castedmaterials.mapping;

import org.junit.jupiter.api.Test;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialDefinitionParserTest {
    @Test
    void parsesNamedSpriteSlots() {
        MaterialDefinition definition = DefinitionParser.parseMaterial(id("material"),
                "{\"schema\":1,\"slots\":{\"side\":\"test:block/side\",\"end\":\"test:block/end\"}}");
        assertEquals(id("test:block/side"), definition.slots().get("side"));
        assertEquals(id("test:block/end"), definition.slots().get("end"));
    }

    @Test
    void rejectsBadSchemaEmptySlotsInvalidIdsAndUnknownFields() {
        assertBad("{\"schema\":2,\"slots\":{\"side\":\"test:x\"}}", "$.schema");
        assertBad("{\"schema\":1,\"slots\":{}}", "$.slots");
        assertBad("{\"schema\":1,\"slots\":{\"side\":\"bad id\"}}", "$.slots.side");
        assertBad("{\"schema\":1,\"slots\":{\"side\":\"test:x\"},\"extra\":0}", "$.extra");
    }

    private static void assertBad(String json, String path) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DefinitionParser.parseMaterial(id("material"), json));
        assertTrue(error.getMessage().contains(path), error.getMessage());
    }
}
