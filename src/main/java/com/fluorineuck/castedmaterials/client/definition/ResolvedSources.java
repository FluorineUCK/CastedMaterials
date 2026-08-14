package com.fluorineuck.castedmaterials.client.definition;

import com.fluorineuck.castedmaterials.mapping.PixelImage;
import com.fluorineuck.castedmaterials.mapping.SourceSlotImage;

import java.util.Map;
import java.util.Optional;

public record ResolvedSources(Map<String, SourceSlotImage> slots, Optional<PixelImage> overlay) {
    public ResolvedSources {
        slots = Map.copyOf(slots);
        overlay = overlay == null ? Optional.empty() : overlay;
    }
}
