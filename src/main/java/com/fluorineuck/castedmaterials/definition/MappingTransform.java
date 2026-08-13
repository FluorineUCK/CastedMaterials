package com.fluorineuck.castedmaterials.definition;

import java.util.Locale;

public enum MappingTransform {
    IDENTITY,
    ROTATE_90,
    ROTATE_180,
    ROTATE_270,
    MIRROR_X,
    MIRROR_Y;

    public static MappingTransform parse(String value, String path) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ": unsupported transform " + value, error);
        }
    }

    public boolean swapsAxes() {
        return this == ROTATE_90 || this == ROTATE_270;
    }
}
