package com.fluorineuck.castedmaterials.api;

import com.fluorineuck.castedmaterials.material.CastedMaterialState;

public interface CastedMaterialHolder {
    CastedMaterialState castedMaterialState();

    default void onCastedMaterialChanged() {
    }
}
