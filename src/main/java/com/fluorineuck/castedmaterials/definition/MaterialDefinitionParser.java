package com.fluorineuck.castedmaterials.definition;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MaterialDefinitionParser {
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "slots");

    private MaterialDefinitionParser() {
    }

    public static MaterialDefinition parse(ResourceLocation id, String json) {
        JsonObject root = ParserSupport.root(json);
        ParserSupport.fields(root, ROOT_FIELDS, "$");
        if (ParserSupport.integer(root, "schema", "$") != 1) {
            throw new IllegalArgumentException("$.schema: only schema 1 is supported");
        }
        if (!root.has("slots") || !root.get("slots").isJsonObject()) {
            throw new IllegalArgumentException("$.slots: expected object");
        }
        JsonObject slotsObject = root.getAsJsonObject("slots");
        if (slotsObject.isEmpty()) {
            throw new IllegalArgumentException("$.slots: at least one slot is required");
        }
        Map<String, ResourceLocation> slots = new LinkedHashMap<>();
        slotsObject.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey().isBlank()) {
                throw new IllegalArgumentException("$.slots: slot names cannot be blank");
            }
            slots.put(entry.getKey(), ParserSupport.id(entry.getValue().getAsString(),
                    "$.slots." + entry.getKey()));
        });
        return new MaterialDefinition(id, slots);
    }
}
