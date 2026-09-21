package com.fluorineuck.castedmaterials.client;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.CasingConnectivity;
import net.minecraft.world.level.block.state.BlockState;

/** Optional client bridge. Call only after checking that Create is loaded. */
final class CreateCasingTextures {
    private CreateCasingTextures() {}

    static MaterialTextures.CasingSource find(BlockState state) {
        return find(state, CreateClient.CASING_CONNECTIVITY);
    }

    static MaterialTextures.CasingSource find(BlockState state, CasingConnectivity connectivity) {
        var entry = connectivity.get(state);
        return entry == null ? new MaterialTextures.CasingSource(false, null)
                : new MaterialTextures.CasingSource(true, entry.getCasing().getTarget());
    }
}
