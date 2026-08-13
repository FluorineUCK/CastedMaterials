package com.fluorineuck.castedmaterials.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CastedNetwork {
    public static final String PROTOCOL = "1";

    private CastedNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToClient(
                MaterialPalettePayload.TYPE,
                MaterialPalettePayload.STREAM_CODEC,
                (payload, context) -> ClientPaletteStore.replace(payload.toPalette()));
    }
}
