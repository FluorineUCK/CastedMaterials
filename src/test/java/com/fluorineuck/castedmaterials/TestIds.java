package com.fluorineuck.castedmaterials;

import net.minecraft.resources.ResourceLocation;

public final class TestIds {
    public static ResourceLocation id(String value) {
        return value.indexOf(':') >= 0
                ? ResourceLocation.parse(value)
                : ResourceLocation.fromNamespaceAndPath("test", value);
    }

    private TestIds() {
    }
}
