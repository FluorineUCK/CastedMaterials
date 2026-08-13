package com.fluorineuck.castedmaterials.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class MaterialPairValidator {
    public static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::getNamespace)
                    .thenComparing(ResourceLocation::getPath);

    private MaterialPairValidator() {
    }

    public static MaterialValidationReport validate(
            Map<ResourceLocation, Item> items,
            Map<ResourceLocation, Block> blocks,
            Set<ResourceLocation> itemTagIds,
            Set<ResourceLocation> blockTagIds) {
        var candidates = new TreeSet<>(ID_ORDER);
        candidates.addAll(itemTagIds);
        candidates.addAll(blockTagIds);

        var valid = new ArrayList<ResourceLocation>();
        var issues = new ArrayList<MaterialValidationReport.Issue>();
        for (ResourceLocation id : candidates) {
            ErrorCode error = validateCandidate(id, items, blocks, itemTagIds, blockTagIds);
            if (error == null) {
                valid.add(id);
            } else {
                issues.add(new MaterialValidationReport.Issue(id, error));
            }
        }
        return new MaterialValidationReport(valid, issues);
    }

    private static ErrorCode validateCandidate(
            ResourceLocation id,
            Map<ResourceLocation, Item> items,
            Map<ResourceLocation, Block> blocks,
            Set<ResourceLocation> itemTagIds,
            Set<ResourceLocation> blockTagIds) {
        if (!itemTagIds.contains(id)) {
            return ErrorCode.BLOCK_TAG_ONLY;
        }
        if (!blockTagIds.contains(id)) {
            return ErrorCode.ITEM_TAG_ONLY;
        }

        Item item = items.get(id);
        if (item == null) {
            return ErrorCode.MISSING_ITEM;
        }
        Block block = blocks.get(id);
        if (block == null) {
            return ErrorCode.MISSING_BLOCK;
        }
        if (!(item instanceof BlockItem blockItem)) {
            return ErrorCode.NOT_BLOCK_ITEM;
        }
        if (blockItem.getBlock() != block) {
            return ErrorCode.BLOCK_ITEM_POINTS_ELSEWHERE;
        }
        return null;
    }

    public enum ErrorCode {
        ITEM_TAG_ONLY,
        BLOCK_TAG_ONLY,
        MISSING_ITEM,
        MISSING_BLOCK,
        NOT_BLOCK_ITEM,
        BLOCK_ITEM_POINTS_ELSEWHERE
    }
}
