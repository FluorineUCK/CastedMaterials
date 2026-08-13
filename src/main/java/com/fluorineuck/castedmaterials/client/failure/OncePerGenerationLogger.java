package com.fluorineuck.castedmaterials.client.failure;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class OncePerGenerationLogger {
    private final Set<Key> seen = ConcurrentHashMap.newKeySet();
    private final Consumer<String> sink;

    public OncePerGenerationLogger(Consumer<String> sink) {
        this.sink = sink;
    }

    public void log(long generation, ResourceLocation target, ResourceLocation material,
                    RenderFailureCode code, String message) {
        if (seen.add(new Key(generation, target, material, code))) {
            sink.accept(message);
        }
    }

    public void discardBefore(long generation) {
        seen.removeIf(key -> key.generation < generation);
    }

    private record Key(long generation, ResourceLocation target,
                       ResourceLocation material, RenderFailureCode code) {
    }
}
