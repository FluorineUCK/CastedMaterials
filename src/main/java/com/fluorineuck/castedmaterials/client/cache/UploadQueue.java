package com.fluorineuck.castedmaterials.client.cache;

import com.fluorineuck.castedmaterials.mapping.PixelImage;

import java.util.function.Consumer;

@FunctionalInterface
public interface UploadQueue {
    void enqueue(RenderKey key, PixelImage image, Consumer<UploadResult> completion);
}
