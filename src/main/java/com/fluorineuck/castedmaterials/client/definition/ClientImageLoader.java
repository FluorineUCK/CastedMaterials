package com.fluorineuck.castedmaterials.client.definition;

import com.fluorineuck.castedmaterials.definition.IntSize;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class ClientImageLoader {
    private ClientImageLoader() {
    }

    public static Optional<SourceSlotImage> load(ResourceManager resources, ResourceLocation spriteId,
                                                  IntSize logicalGrid) {
        ResourceLocation png = ResourceLocation.fromNamespaceAndPath(
                spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        Optional<Resource> resource = resources.getResource(png);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream stream = resource.get().open(); NativeImage nativeImage = NativeImage.read(stream)) {
            PixelImage image = toPixelImage(nativeImage);
            SourceSlotImage source = new SourceSlotImage(image, logicalGrid,
                    resources.getResource(png.withSuffix(".mcmeta")).isPresent());
            return source.integerScale().isPresent() ? Optional.of(source) : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static PixelImage toPixelImage(NativeImage nativeImage) {
        PixelImage image = new PixelImage(nativeImage.getWidth(), nativeImage.getHeight());
        for (int y = 0; y < nativeImage.getHeight(); y++) {
            for (int x = 0; x < nativeImage.getWidth(); x++) {
                image.set(x, y, abgrToArgb(nativeImage.getPixelRGBA(x, y)));
            }
        }
        return image;
    }

    private static int abgrToArgb(int abgr) {
        int alpha = abgr >>> 24;
        int blue = abgr >>> 16 & 0xff;
        int green = abgr >>> 8 & 0xff;
        int red = abgr & 0xff;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
