package com.fluorineuck.castedmaterials.client.definition;

import com.fluorineuck.castedmaterials.definition.MaterialDefinition;
import com.fluorineuck.castedmaterials.definition.MaterialDefinitionParser;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;
import com.fluorineuck.castedmaterials.definition.TargetDefinitionParser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class CastedResourceReloadListener
        extends SimplePreparableReloadListener<CastedResourceReloadListener.Prepared> {
    public static final CastedResourceReloadListener INSTANCE = new CastedResourceReloadListener();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String TARGETS = "casted_materials/targets";
    private static final String MATERIALS = "casted_materials/materials";

    private final AtomicReference<CastedResourceSnapshot> current =
            new AtomicReference<>(CastedResourceSnapshot.empty());

    private CastedResourceReloadListener() {
    }

    public CastedResourceSnapshot current() {
        return current.get();
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> targets = new HashMap<>();
        Map<ResourceLocation, JsonElement> materials = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, TARGETS, GSON, targets);
        SimpleJsonResourceReloadListener.scanDirectory(manager, MATERIALS, GSON, materials);
        return new Prepared(parseTargets(targets), parseMaterials(materials));
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        current.updateAndGet(previous -> new CastedResourceSnapshot(
                previous.generation() + 1, prepared.targets(), prepared.materials()));
    }

    private static Map<ResourceLocation, TargetDefinition> parseTargets(
            Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, TargetDefinition> parsed = new HashMap<>();
        resources.forEach((id, json) -> {
            try {
                parsed.put(id, TargetDefinitionParser.parse(id, json.toString()));
            } catch (RuntimeException error) {
                LOGGER.error("Skipping invalid Casted Materials target {}: {}", id, error.getMessage());
            }
        });
        return Map.copyOf(parsed);
    }

    private static Map<ResourceLocation, MaterialDefinition> parseMaterials(
            Map<ResourceLocation, JsonElement> resources) {
        Map<ResourceLocation, MaterialDefinition> parsed = new HashMap<>();
        resources.forEach((id, json) -> {
            try {
                parsed.put(id, MaterialDefinitionParser.parse(id, json.toString()));
            } catch (RuntimeException error) {
                LOGGER.error("Skipping invalid Casted Materials material {}: {}", id, error.getMessage());
            }
        });
        return Map.copyOf(parsed);
    }

    record Prepared(
            Map<ResourceLocation, TargetDefinition> targets,
            Map<ResourceLocation, MaterialDefinition> materials) {
        Prepared {
            targets = Map.copyOf(targets);
            materials = Map.copyOf(materials);
        }
    }
}
