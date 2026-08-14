package com.fluorineuck.castedmaterials.client.atlas;

import com.fluorineuck.castedmaterials.client.render.UvTransform;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AtlasRegion(ResourceLocation texture, AtlasAllocation allocation, int pageWidth, int pageHeight) {
    public AtlasRegion {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(allocation, "allocation");
        if (pageWidth <= 0 || pageHeight <= 0
                || allocation.x() + allocation.width() > pageWidth
                || allocation.y() + allocation.height() > pageHeight) {
            throw new IllegalArgumentException("Allocation must fit in its atlas page");
        }
    }

    public UvTransform uv() {
        return new UvTransform((float) allocation.x() / pageWidth,
                (float) allocation.y() / pageHeight,
                (float) allocation.width() / pageWidth,
                (float) allocation.height() / pageHeight);
    }
}
