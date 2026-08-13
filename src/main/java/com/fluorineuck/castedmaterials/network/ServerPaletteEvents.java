package com.fluorineuck.castedmaterials.network;

import com.fluorineuck.castedmaterials.CastedIds;
import com.fluorineuck.castedmaterials.material.TagSnapshot;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ServerPaletteEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ServerPaletteSynchronizer SYNCHRONIZER = new ServerPaletteSynchronizer();

    private ServerPaletteEvents() {
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        TagSnapshot snapshot = snapshot();
        ServerPaletteSynchronizer.SyncCause cause = event.getPlayer() == null
                ? ServerPaletteSynchronizer.SyncCause.RELOAD
                : ServerPaletteSynchronizer.SyncCause.PLAYER_JOIN;
        ServerPaletteSynchronizer.SyncDecision decision = SYNCHRONIZER.rebuild(snapshot, cause);
        if (cause == ServerPaletteSynchronizer.SyncCause.RELOAD && decision.broadcast()) {
            decision.report().issues().forEach(issue -> LOGGER.warn(
                    "Skipping invalid #create:casing material {}: {}", issue.id(), issue.code()));
        }
        if (!decision.broadcast()) {
            return;
        }
        MaterialPalettePayload payload = MaterialPalettePayload.from(decision.palette());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static TagSnapshot snapshot() {
        Set<ResourceLocation> itemIds = tagIds(BuiltInRegistries.ITEM, CastedIds.CASING_ITEMS);
        Set<ResourceLocation> blockIds = tagIds(BuiltInRegistries.BLOCK, CastedIds.CASING_BLOCKS);
        Set<ResourceLocation> union = new HashSet<>(itemIds);
        union.addAll(blockIds);
        Map<ResourceLocation, net.minecraft.world.item.Item> items = new HashMap<>();
        Map<ResourceLocation, net.minecraft.world.level.block.Block> blocks = new HashMap<>();
        union.forEach(id -> {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> items.put(id, item));
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> blocks.put(id, block));
        });
        return new TagSnapshot(items, blocks, itemIds, blockIds);
    }

    private static <T> Set<ResourceLocation> tagIds(Registry<T> registry, TagKey<T> tag) {
        Set<ResourceLocation> ids = new HashSet<>();
        registry.getTag(tag).ifPresent(named -> named.forEach(holder -> ids.add(registry.getKey(holder.value()))));
        return Set.copyOf(ids);
    }
}
