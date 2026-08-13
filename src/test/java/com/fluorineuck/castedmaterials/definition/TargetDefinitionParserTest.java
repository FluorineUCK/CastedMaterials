package com.fluorineuck.castedmaterials.definition;

import org.junit.jupiter.api.Test;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class TargetDefinitionParserTest {
    private static final String VALID = """
            {
              "schema": 1,
              "size": [64, 32],
              "source_grids": {"side": [16, 16], "end": [16, 16]},
              "regions": [
                {"source_slot":"side","source":[0,0,16,8],"destination":[0,0,8,16],"transform":"rotate_90"},
                {"source_slot":"end","source":[0,0,4,4],"destination":[8,0,4,4],"transform":"mirror_x"}
              ],
              "pixels": [{"source_slot":"end","source":[3,5],"destination":[20,7]}],
              "overlay": "test:overlay",
              "material_overrides": {
                "create:brass_casing": {"slots":{"side":"test:special"},"overlay":"test:special_overlay"}
              },
              "computed_cost": {"max_pieces_per_source_quad": 6, "additional_quads": 37}
            }
            """;

    @Test
    void parsesValidatedSchemaAndAllTransforms() {
        TargetDefinition target = TargetDefinitionParser.parse(id("target"), VALID);

        assertEquals(new IntSize(64, 32), target.size());
        assertEquals(new IntSize(16, 16), target.sourceGrids().get("side"));
        assertEquals(MappingTransform.ROTATE_90, target.regions().getFirst().transform());
        assertEquals(id("test:special"),
                target.materialOverrides().get(id("create:brass_casing")).slots().get("side"));
        assertEquals(37, target.computedCost().orElseThrow().additionalQuads());
    }

    @Test
    void rejectsInvalidSchemaSizeAndUnknownFieldsWithPaths() {
        assertPath("{\"schema\":2,\"size\":[1,1],\"source_grids\":{},\"pixels\":[]}", "$.schema");
        assertPath("{\"schema\":1,\"size\":[257,1],\"source_grids\":{},\"pixels\":[]}", "$.size");
        assertPath("{\"schema\":1,\"size\":[1,1],\"source_grids\":{},\"pixels\":[],\"wat\":1}", "$.wat");
    }

    @Test
    void rejectsMissingSlotBoundsDimensionMismatchOverlapAndEmptyMappings() {
        assertPath(base("{\"source_slot\":\"missing\",\"source\":[0,0],\"destination\":[0,0]}"), "$.pixels[0].source_slot");
        assertPath(base("{\"source_slot\":\"side\",\"source\":[16,0],\"destination\":[0,0]}"), "$.pixels[0].source");
        String mismatch = """
                {"schema":1,"size":[16,16],"source_grids":{"side":[16,16]},
                "regions":[{"source_slot":"side","source":[0,0,4,2],"destination":[0,0,4,2],"transform":"rotate_90"}]}
                """;
        assertPath(mismatch, "$.regions[0].destination");
        String overlap = """
                {"schema":1,"size":[16,16],"source_grids":{"side":[16,16]},
                "regions":[{"source_slot":"side","source":[0,0,2,2],"destination":[0,0,2,2],"transform":"identity"}],
                "pixels":[{"source_slot":"side","source":[3,3],"destination":[1,1]}]}
                """;
        assertPath(overlap, "$.pixels[0].destination");
        assertPath("{\"schema\":1,\"size\":[16,16],\"source_grids\":{\"side\":[16,16]}}", "$");
    }

    private static String base(String pixel) {
        return "{\"schema\":1,\"size\":[16,16],\"source_grids\":{\"side\":[16,16]},\"pixels\":[" + pixel + "]}";
    }

    private static void assertPath(String json, String path) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TargetDefinitionParser.parse(id("target"), json));
        assertTrue(error.getMessage().contains(path), error.getMessage());
    }
}
