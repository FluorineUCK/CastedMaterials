package com.fluorineuck.castedmaterials.material;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerMaterialPalette {
    private static final MaterialPaletteStore STORE = new MaterialPaletteStore();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private ServerMaterialPalette() {
    }

    public static MaterialPaletteStore store() {
        return STORE;
    }

    public static Optional<MaterialPalette> active() {
        return ACTIVE.get() ? Optional.of(STORE.current()) : Optional.empty();
    }

    public static void activate(MaterialPalette palette) {
        STORE.replace(palette);
        ACTIVE.set(true);
    }

    public static void deactivate() {
        ACTIVE.set(false);
        STORE.replace(MaterialPalette.empty());
    }
}
