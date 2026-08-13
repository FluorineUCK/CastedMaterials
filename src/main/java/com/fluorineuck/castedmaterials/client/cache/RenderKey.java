package com.fluorineuck.castedmaterials.client.cache;

import net.minecraft.resources.ResourceLocation;

public record RenderKey(long generation, ResourceLocation target, ResourceLocation material) {
}
