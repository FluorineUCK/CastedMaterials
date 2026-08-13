package com.fluorineuck.castedmaterials.material;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Set;

public record TagSnapshot(
        Map<ResourceLocation, Item> items,
        Map<ResourceLocation, Block> blocks,
        Set<ResourceLocation> itemTagIds,
        Set<ResourceLocation> blockTagIds) {
    public TagSnapshot {
        items = Map.copyOf(items);
        blocks = Map.copyOf(blocks);
        itemTagIds = Set.copyOf(itemTagIds);
        blockTagIds = Set.copyOf(blockTagIds);
    }
}
