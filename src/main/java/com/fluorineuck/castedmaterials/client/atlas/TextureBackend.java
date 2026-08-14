package com.fluorineuck.castedmaterials.client.atlas;

import net.minecraft.resources.ResourceLocation;

public interface TextureBackend {
    void createPage(ResourceLocation id, int width, int height);
    void upload(ResourceLocation page, AtlasAllocation allocation, int[] abgrPixels);
    void closePage(ResourceLocation id);
}
