package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.definition.IntPoint;
import com.fluorineuck.castedmaterials.definition.MappingTransform;
import com.fluorineuck.castedmaterials.definition.PixelMapping;
import com.fluorineuck.castedmaterials.definition.RegionMapping;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MappingComposer {
    private MappingComposer() {
    }

    public static CompositionResult compose(
            TargetDefinition target,
            Map<String, SourceSlotImage> slots,
            PixelImage overlay) {
        PixelImage output = new PixelImage(target.size().width(), target.size().height());
        Set<RenderFailureCode> diagnostics = new LinkedHashSet<>();
        Map<String, Integer> scales = new java.util.HashMap<>();

        for (String required : requiredSlots(target)) {
            SourceSlotImage source = slots.get(required);
            if (source == null) {
                return fallback(output, RenderFailureCode.MISSING_SOURCE_SLOT);
            }
            if (!source.logicalGrid().equals(target.sourceGrids().get(required))) {
                return fallback(output, RenderFailureCode.SOURCE_GRID_MISMATCH);
            }
            var scale = source.integerScale();
            if (scale.isEmpty()) {
                return fallback(output, RenderFailureCode.SOURCE_ASPECT_MISMATCH);
            }
            scales.put(required, scale.getAsInt());
            if (scale.getAsInt() > 1) {
                diagnostics.add(RenderFailureCode.INTEGER_SCALED_SOURCE);
            }
            if (source.animated()) {
                diagnostics.add(RenderFailureCode.ANIMATED_SOURCE_FIRST_FRAME);
            }
        }

        for (RegionMapping region : target.regions()) {
            copyRegion(output, slots.get(region.sourceSlot()), scales.get(region.sourceSlot()), region);
        }
        for (PixelMapping pixel : target.pixels()) {
            SourceSlotImage source = slots.get(pixel.sourceSlot());
            int scale = scales.get(pixel.sourceSlot());
            output.set(pixel.destination().x(), pixel.destination().y(),
                    source.sample(pixel.source().x(), pixel.source().y(), scale));
        }
        if (overlay != null) {
            if (overlay.width() != output.width() || overlay.height() != output.height()) {
                return fallback(output, RenderFailureCode.OVERLAY_SIZE_MISMATCH);
            }
            output.alphaOver(overlay);
        }
        RenderableState state = diagnostics.isEmpty() ? RenderableState.READY : RenderableState.DEGRADED;
        return new CompositionResult(output, state, new ArrayList<>(diagnostics));
    }

    private static Set<String> requiredSlots(TargetDefinition target) {
        Set<String> required = new LinkedHashSet<>();
        target.regions().forEach(region -> required.add(region.sourceSlot()));
        target.pixels().forEach(pixel -> required.add(pixel.sourceSlot()));
        return required;
    }

    private static CompositionResult fallback(PixelImage output, RenderFailureCode failure) {
        return new CompositionResult(output, RenderableState.FALLBACK_BASE, java.util.List.of(failure));
    }

    private static void copyRegion(PixelImage output, SourceSlotImage source, int scale,
                                   RegionMapping region) {
        if (region.transform() == MappingTransform.IDENTITY && scale == 1) {
            for (int y = 0; y < region.source().height(); y++) {
                output.copyRow(source.image(), region.source().x(), region.source().y() + y,
                        region.destination().x(), region.destination().y() + y, region.source().width());
            }
            return;
        }
        for (int dy = 0; dy < region.destination().height(); dy++) {
            for (int dx = 0; dx < region.destination().width(); dx++) {
                IntPoint local = inverse(region.transform(), dx, dy,
                        region.source().width(), region.source().height());
                int argb = source.sample(region.source().x() + local.x(),
                        region.source().y() + local.y(), scale);
                output.set(region.destination().x() + dx, region.destination().y() + dy, argb);
            }
        }
    }

    private static IntPoint inverse(MappingTransform transform, int x, int y, int width, int height) {
        return switch (transform) {
            case IDENTITY -> new IntPoint(x, y);
            case ROTATE_90 -> new IntPoint(y, height - 1 - x);
            case ROTATE_180 -> new IntPoint(width - 1 - x, height - 1 - y);
            case ROTATE_270 -> new IntPoint(width - 1 - y, x);
            case MIRROR_X -> new IntPoint(width - 1 - x, y);
            case MIRROR_Y -> new IntPoint(x, height - 1 - y);
        };
    }
}
