package com.fluorineuck.castedmaterials.definition;

public record RegionMapping(String sourceSlot, IntRect source, IntRect destination, MappingTransform transform) {
}
