package com.fluorineuck.castedmaterials.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

final class ParserSupport {
    private ParserSupport() {
    }

    static JsonObject root(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("$: invalid JSON object", error);
        }
    }

    static void fields(JsonObject object, Set<String> allowed, String path) {
        for (String name : object.keySet()) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException(path + "." + name + ": unknown field");
            }
        }
    }

    static int integer(JsonObject object, String name, String path) {
        try {
            JsonElement value = object.get(name);
            if (value == null || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            double number = value.getAsDouble();
            int integer = value.getAsInt();
            if (number != integer) {
                throw new IllegalArgumentException();
            }
            return integer;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + "." + name + ": expected integer", error);
        }
    }

    static String string(JsonObject object, String name, String path) {
        try {
            return object.get(name).getAsString();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + "." + name + ": expected string", error);
        }
    }

    static ResourceLocation id(String value, String path) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(path + ": invalid ResourceLocation " + value);
        }
        return parsed;
    }

    static IntSize size(JsonElement element, String path) {
        JsonArray array = array(element, 2, path);
        try {
            return new IntSize(exactInt(array.get(0), path + "[0]"), exactInt(array.get(1), path + "[1]"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    static IntPoint point(JsonElement element, String path) {
        JsonArray array = array(element, 2, path);
        try {
            return new IntPoint(exactInt(array.get(0), path + "[0]"), exactInt(array.get(1), path + "[1]"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    static IntRect rect(JsonElement element, String path) {
        JsonArray array = array(element, 4, path);
        try {
            return new IntRect(exactInt(array.get(0), path + "[0]"), exactInt(array.get(1), path + "[1]"),
                    exactInt(array.get(2), path + "[2]"), exactInt(array.get(3), path + "[3]"));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    private static JsonArray array(JsonElement element, int size, String path) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != size) {
            throw new IllegalArgumentException(path + ": expected array of length " + size);
        }
        return element.getAsJsonArray();
    }

    private static int exactInt(JsonElement element, String path) {
        try {
            double number = element.getAsDouble();
            int integer = element.getAsInt();
            if (number != integer) {
                throw new IllegalArgumentException();
            }
            return integer;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + ": expected integer", error);
        }
    }
}
