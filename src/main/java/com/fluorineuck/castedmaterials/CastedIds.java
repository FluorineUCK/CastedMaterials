package com.fluorineuck.castedmaterials;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class CastedIds {
    private static final ResourceLocation CREATE_CASING =
            ResourceLocation.fromNamespaceAndPath("create", "casing");

    public static final TagKey<Item> CASING_ITEMS = TagKey.create(Registries.ITEM, CREATE_CASING);
    public static final TagKey<Block> CASING_BLOCKS = TagKey.create(Registries.BLOCK, CREATE_CASING);

    private CastedIds() {
    }
}
