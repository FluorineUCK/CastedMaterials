package com.fluorineuck.castedmaterials.network;

import com.fluorineuck.castedmaterials.material.MaterialPalette;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientPaletteStore {
    private static final AtomicReference<MaterialPalette> CURRENT =
            new AtomicReference<>(MaterialPalette.empty());

    private ClientPaletteStore() {
    }

    public static MaterialPalette current() {
        return CURRENT.get();
    }

    public static void replace(MaterialPalette palette) {
        CURRENT.set(Objects.requireNonNull(palette, "palette"));
    }
}
