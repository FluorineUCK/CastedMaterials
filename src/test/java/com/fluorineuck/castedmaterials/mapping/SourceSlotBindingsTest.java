package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import com.fluorineuck.castedmaterials.mapping.*;
import org.junit.jupiter.api.Test;
import java.util.*;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class SourceSlotBindingsTest {
    @Test
    void usesTheSamePrecedenceForNonPixelSourceBindings() {
        var target = target();
        var requests = new ArrayList<String>();
        var bindings = SourceSlotResolver.resolveSlots(target, id("material"), Optional.empty(),
                ignored -> Optional.of(id("block/registered_sheet")), (sprite, grid) -> {
                    requests.add(sprite.getPath());
                    return sprite.equals(id("block/registered_sheet")) ? Optional.of("sprite-binding") : Optional.empty();
                });
        assertEquals(Map.of("all", "sprite-binding"), bindings.orElseThrow());
        assertEquals(List.of("block/registered_sheet"), requests);
    }

    @Test
    void exactOverridesPrecedeDefaultAndUnusedSlotsNeedNoBinding() {
        var material = new MaterialDefinition(id("material"), Map.of("all", id("explicit")));
        var bindings = SourceSlotResolver.resolveSlots(target(), id("material"), Optional.of(material),
                ignored -> { fail("no default lookup required"); return Optional.empty(); },
                (sprite, grid) -> Optional.of(sprite), Set.of("all"));
        assertEquals(id("explicit"), bindings.orElseThrow().get("all"));
        assertEquals(Map.of(), SourceSlotResolver.resolveSlots(target(), id("material"), Optional.empty(),
                ignored -> { fail("unused target requires no source"); return Optional.empty(); },
                (sprite, grid) -> Optional.empty(), Set.of()).orElseThrow());
    }

    private static TargetDefinition target() {
        return new TargetDefinition(id("target"), new IntSize(2, 2), Map.of("all", new IntSize(128, 128)),
                List.of(new RegionMapping("all", new IntRect(16, 16, 2, 2), new IntRect(0, 0, 2, 2), MappingTransform.IDENTITY)),
                List.of(), Optional.empty(), Map.of(), Optional.empty());
    }
}
