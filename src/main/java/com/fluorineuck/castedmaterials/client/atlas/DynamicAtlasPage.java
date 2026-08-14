package com.fluorineuck.castedmaterials.client.atlas;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

final class DynamicAtlasPage {
    private final ResourceLocation id;
    private final ShelfAtlasAllocator allocator = new ShelfAtlasAllocator(512, 512);

    DynamicAtlasPage(ResourceLocation id) {
        this.id = id;
    }

    ResourceLocation id() {
        return id;
    }

    Optional<AtlasAllocation> allocate(int width, int height) {
        return allocator.allocate(width, height);
    }
}
