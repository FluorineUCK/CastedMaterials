package com.fluorineuck.castedmaterials.material;

import com.fluorineuck.castedmaterials.api.MaterialSetResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class CastedMaterialState {
    public static final String NBT_KEY = "CastedMaterial";

    private ResourceLocation material;

    public Optional<ResourceLocation> get() {
        return Optional.ofNullable(material);
    }

    public MaterialSetResult set(ResourceLocation next) {
        Objects.requireNonNull(next, "next");
        if (next.equals(material)) {
            return MaterialSetResult.UNCHANGED;
        }
        material = next;
        return MaterialSetResult.CHANGED;
    }

    public void save(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (material == null) {
            tag.remove(NBT_KEY);
        } else {
            tag.putString(NBT_KEY, material.toString());
        }
    }

    public void load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.contains(NBT_KEY, Tag.TAG_STRING)) {
            return;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString(NBT_KEY));
        if (parsed != null) {
            material = parsed;
        }
    }

    public OptionalInt encodeIndex(MaterialPalette palette) {
        Objects.requireNonNull(palette, "palette");
        return material == null ? OptionalInt.empty() : palette.indexOf(material);
    }

    public static Optional<ResourceLocation> decodeIndex(MaterialPalette palette, int index) {
        return Objects.requireNonNull(palette, "palette").idAt(index);
    }
}
