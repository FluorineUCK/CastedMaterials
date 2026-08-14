package com.fluorineuck.castedmaterials.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class MinecraftTextureBackend implements TextureBackend {
    private final Map<ResourceLocation, DynamicTexture> pages = new HashMap<>();

    @Override
    public void createPage(ResourceLocation id, int width, int height) {
        RenderSystem.assertOnRenderThread();
        NativeImage image = new NativeImage(width, height, false);
        image.fillRect(0, 0, width, height, 0);
        DynamicTexture texture = new DynamicTexture(image);
        pages.put(id, texture);
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    @Override
    public void upload(ResourceLocation page, AtlasAllocation allocation, int[] abgrPixels) {
        RenderSystem.assertOnRenderThread();
        DynamicTexture texture = requiredPage(page);
        if (abgrPixels.length != allocation.width() * allocation.height()) {
            throw new IllegalArgumentException("Pixel data does not match the atlas allocation");
        }
        NativeImage image = texture.getPixels();
        for (int y = 0; y < allocation.height(); y++) {
            for (int x = 0; x < allocation.width(); x++) {
                image.setPixelRGBA(allocation.x() + x, allocation.y() + y,
                        abgrPixels[y * allocation.width() + x]);
            }
        }
        texture.upload();
    }

    @Override
    public void closePage(ResourceLocation id) {
        RenderSystem.assertOnRenderThread();
        DynamicTexture texture = pages.remove(id);
        if (texture != null) {
            texture.close();
        }
    }

    private DynamicTexture requiredPage(ResourceLocation id) {
        DynamicTexture texture = pages.get(id);
        if (texture == null) {
            throw new IllegalStateException("Unknown dynamic atlas page " + id);
        }
        return texture;
    }
}
