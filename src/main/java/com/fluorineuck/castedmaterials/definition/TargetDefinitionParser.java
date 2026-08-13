package com.fluorineuck.castedmaterials.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TargetDefinitionParser {
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "size", "source_grids", "regions", "pixels",
            "overlay", "material_overrides", "computed_cost");
    private static final Set<String> REGION_FIELDS = Set.of("source_slot", "source", "destination", "transform");
    private static final Set<String> PIXEL_FIELDS = Set.of("source_slot", "source", "destination");
    private static final Set<String> OVERRIDE_FIELDS = Set.of("slots", "overlay");
    private static final Set<String> COST_FIELDS = Set.of("max_pieces_per_source_quad", "additional_quads");

    private TargetDefinitionParser() {
    }

    public static TargetDefinition parse(ResourceLocation id, String json) {
        JsonObject root = ParserSupport.root(json);
        ParserSupport.fields(root, ROOT_FIELDS, "$");
        if (ParserSupport.integer(root, "schema", "$") != 1) {
            throw new IllegalArgumentException("$.schema: only schema 1 is supported");
        }
        IntSize size = ParserSupport.size(root.get("size"), "$.size");
        if (size.width() > 256 || size.height() > 256) {
            throw new IllegalArgumentException("$.size: output dimensions cannot exceed 256");
        }
        Map<String, IntSize> grids = parseGrids(root);
        BitSet written = new BitSet(size.width() * size.height());
        List<RegionMapping> regions = parseRegions(root, size, grids, written);
        List<PixelMapping> pixels = parsePixels(root, size, grids, written);
        if (regions.isEmpty() && pixels.isEmpty()) {
            throw new IllegalArgumentException("$: mapping cannot be empty");
        }
        Optional<ResourceLocation> overlay = optionalId(root, "overlay", "$.overlay");
        Map<ResourceLocation, MaterialOverride> overrides = parseOverrides(root);
        Optional<ComputedCost> cost = parseCost(root);
        return new TargetDefinition(id, size, grids, regions, pixels, overlay, overrides, cost);
    }

    private static Map<String, IntSize> parseGrids(JsonObject root) {
        if (!root.has("source_grids") || !root.get("source_grids").isJsonObject()) {
            throw new IllegalArgumentException("$.source_grids: expected object");
        }
        Map<String, IntSize> grids = new LinkedHashMap<>();
        root.getAsJsonObject("source_grids").entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> grids.put(entry.getKey(),
                        ParserSupport.size(entry.getValue(), "$.source_grids." + entry.getKey())));
        return grids;
    }

    private static List<RegionMapping> parseRegions(JsonObject root, IntSize target,
                                                     Map<String, IntSize> grids, BitSet written) {
        JsonArray array = optionalArray(root, "regions", "$.regions");
        List<RegionMapping> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.regions[" + i + "]";
            JsonObject object = object(array.get(i), path);
            ParserSupport.fields(object, REGION_FIELDS, path);
            String slot = ParserSupport.string(object, "source_slot", path);
            IntSize sourceGrid = requireGrid(grids, slot, path + ".source_slot");
            IntRect source = ParserSupport.rect(object.get("source"), path + ".source");
            IntRect destination = ParserSupport.rect(object.get("destination"), path + ".destination");
            MappingTransform transform = MappingTransform.parse(
                    object.has("transform") ? object.get("transform").getAsString() : "identity",
                    path + ".transform");
            if (!source.fits(sourceGrid)) {
                throw new IllegalArgumentException(path + ".source: outside source grid");
            }
            if (!destination.fits(target)) {
                throw new IllegalArgumentException(path + ".destination: outside target");
            }
            int expectedWidth = transform.swapsAxes() ? source.height() : source.width();
            int expectedHeight = transform.swapsAxes() ? source.width() : source.height();
            if (destination.width() != expectedWidth || destination.height() != expectedHeight) {
                throw new IllegalArgumentException(path + ".destination: dimensions do not match transform");
            }
            mark(destination, target.width(), written, path + ".destination");
            result.add(new RegionMapping(slot, source, destination, transform));
        }
        return result;
    }

    private static List<PixelMapping> parsePixels(JsonObject root, IntSize target,
                                                   Map<String, IntSize> grids, BitSet written) {
        JsonArray array = optionalArray(root, "pixels", "$.pixels");
        List<PixelMapping> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String path = "$.pixels[" + i + "]";
            JsonObject object = object(array.get(i), path);
            ParserSupport.fields(object, PIXEL_FIELDS, path);
            String slot = ParserSupport.string(object, "source_slot", path);
            IntSize sourceGrid = requireGrid(grids, slot, path + ".source_slot");
            IntPoint source = ParserSupport.point(object.get("source"), path + ".source");
            IntPoint destination = ParserSupport.point(object.get("destination"), path + ".destination");
            if (source.x() >= sourceGrid.width() || source.y() >= sourceGrid.height()) {
                throw new IllegalArgumentException(path + ".source: outside source grid");
            }
            if (destination.x() >= target.width() || destination.y() >= target.height()) {
                throw new IllegalArgumentException(path + ".destination: outside target");
            }
            int bit = destination.y() * target.width() + destination.x();
            if (written.get(bit)) {
                throw new IllegalArgumentException(path + ".destination: overlaps an earlier mapping");
            }
            written.set(bit);
            result.add(new PixelMapping(slot, source, destination));
        }
        return result;
    }

    private static Map<ResourceLocation, MaterialOverride> parseOverrides(JsonObject root) {
        if (!root.has("material_overrides")) {
            return Map.of();
        }
        JsonObject all = object(root.get("material_overrides"), "$.material_overrides");
        Map<ResourceLocation, MaterialOverride> result = new LinkedHashMap<>();
        all.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = "$.material_overrides." + entry.getKey();
            ResourceLocation materialId = ParserSupport.id(entry.getKey(), path);
            JsonObject value = object(entry.getValue(), path);
            ParserSupport.fields(value, OVERRIDE_FIELDS, path);
            Map<String, ResourceLocation> slots = new LinkedHashMap<>();
            if (value.has("slots")) {
                JsonObject slotObject = object(value.get("slots"), path + ".slots");
                slotObject.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(slot ->
                        slots.put(slot.getKey(), ParserSupport.id(slot.getValue().getAsString(),
                                path + ".slots." + slot.getKey())));
            }
            result.put(materialId, new MaterialOverride(slots, optionalId(value, "overlay", path + ".overlay")));
        });
        return result;
    }

    private static Optional<ComputedCost> parseCost(JsonObject root) {
        if (!root.has("computed_cost")) {
            return Optional.empty();
        }
        JsonObject cost = object(root.get("computed_cost"), "$.computed_cost");
        ParserSupport.fields(cost, COST_FIELDS, "$.computed_cost");
        try {
            return Optional.of(new ComputedCost(
                    ParserSupport.integer(cost, "max_pieces_per_source_quad", "$.computed_cost"),
                    ParserSupport.integer(cost, "additional_quads", "$.computed_cost")));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("$.computed_cost: " + error.getMessage(), error);
        }
    }

    private static IntSize requireGrid(Map<String, IntSize> grids, String slot, String path) {
        IntSize grid = grids.get(slot);
        if (grid == null) {
            throw new IllegalArgumentException(path + ": slot is absent from source_grids");
        }
        return grid;
    }

    private static void mark(IntRect rect, int targetWidth, BitSet written, String path) {
        for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
            for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
                int bit = y * targetWidth + x;
                if (written.get(bit)) {
                    throw new IllegalArgumentException(path + ": overlaps an earlier mapping");
                }
                written.set(bit);
            }
        }
    }

    private static JsonArray optionalArray(JsonObject root, String name, String path) {
        if (!root.has(name)) {
            return new JsonArray();
        }
        if (!root.get(name).isJsonArray()) {
            throw new IllegalArgumentException(path + ": expected array");
        }
        return root.getAsJsonArray(name);
    }

    private static JsonObject object(JsonElement value, String path) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(path + ": expected object");
        }
        return value.getAsJsonObject();
    }

    private static Optional<ResourceLocation> optionalId(JsonObject object, String name, String path) {
        if (!object.has(name)) {
            return Optional.empty();
        }
        return Optional.of(ParserSupport.id(object.get(name).getAsString(), path));
    }
}
