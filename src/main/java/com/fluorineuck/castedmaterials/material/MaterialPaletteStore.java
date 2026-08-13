package com.fluorineuck.castedmaterials.material;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MaterialPaletteStore {
    private final AtomicReference<MaterialPalette> current =
            new AtomicReference<>(MaterialPalette.empty());

    public MaterialPalette current() {
        return current.get();
    }

    public MaterialPalette replace(MaterialPalette next) {
        return current.getAndSet(Objects.requireNonNull(next, "next"));
    }
}
