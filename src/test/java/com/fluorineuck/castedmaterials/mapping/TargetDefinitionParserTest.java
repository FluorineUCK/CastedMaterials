package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
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
        TargetDefinition target = DefinitionParser.parseTarget(id("target"), VALID);

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

    @Test
    void acceptsExplicitAndPartiallySpecifiedRenderPolicy() {
        TargetDefinition direct = DefinitionParser.parseTarget(id("target"), policy("""
                {"mode":"direct","max_pieces_per_face":3,"max_additional_quads":0,"max_texture_batches":2}
                """));
        assertEquals(3, direct.renderPolicy().maxPiecesPerFace());
        assertEquals(0, direct.renderPolicy().maxAdditionalQuads());
        assertEquals(2, direct.renderPolicy().maxTextureBatches());
        for (String legacyMode : new String[]{"auto", "direct", "composite"}) {
            assertEquals(RenderPolicy.DEFAULT, DefinitionParser.parseTarget(id("target"),
                    policy("{\"mode\":\"" + legacyMode + "\"}")).renderPolicy());
        }
        assertEquals(RenderPolicy.DEFAULT,
                DefinitionParser.parseTarget(id("target"), policy("{}")).renderPolicy());
    }

    @Test
    void oldJsonAndEightArgumentConstructorUseDefaultPolicy() {
        TargetDefinition parsed = DefinitionParser.parseTarget(id("target"), VALID);
        TargetDefinition oldConstructor = new TargetDefinition(parsed.id(), parsed.size(), parsed.sourceGrids(),
                parsed.regions(), parsed.pixels(), parsed.overlay(), parsed.materialOverrides(), parsed.computedCost());
        assertEquals(RenderPolicy.DEFAULT, parsed.renderPolicy());
        assertEquals(RenderPolicy.DEFAULT, oldConstructor.renderPolicy());
    }

    @Test
    void rejectsMalformedRenderPolicyWithPrecisePaths() {
        for (String value : new String[]{"null", "[]", "false", "1"}) {
            assertPath(policy(value), "$.render_policy");
        }
        for (String mode : new String[]{"null", "true", "7", "[]", "\"DIRECT\"", "\"unknown\""}) {
            assertPath(policy("{\"mode\":" + mode + "}"), "$.render_policy.mode");
        }
        assertPath(policy("{\"max_pieces_per_face\":0}"), "$.render_policy.max_pieces_per_face");
        assertPath(policy("{\"max_texture_batches\":0}"), "$.render_policy.max_texture_batches");
        for (String field : new String[]{"max_pieces_per_face", "max_additional_quads", "max_texture_batches"}) {
            for (String value : new String[]{"-1", "1.25", "\"4\"", "true", "null", "2147483648"}) {
                assertPath(policy("{\"" + field + "\":" + value + "}"), "$.render_policy." + field);
            }
        }
        assertPath(policy("{\"max_pieces\":2}"), "$.render_policy.max_pieces");
    }

    @Test
    void policyRejectsFractionalNumbersEvenWhenDoubleRoundingWouldHideTheFraction() {
        for (String field : new String[]{"max_pieces_per_face", "max_additional_quads", "max_texture_batches"}) {
            assertPath(policy("{\"" + field + "\":1.0000000000000001}"), "$.render_policy." + field);
            assertPath(policy("{\"" + field + "\":2147483647.0000000001}"), "$.render_policy." + field);
        }
        assertPath(policy("{\"max_additional_quads\":1e-999}"), "$.render_policy.max_additional_quads");
        assertEquals(10, DefinitionParser.parseTarget(id("target"),
                policy("{\"max_additional_quads\":1e1}")).renderPolicy().maxAdditionalQuads());
    }

    private static String policy(String json) {
        return VALID.replace("\"schema\": 1,", "\"schema\": 1, \"render_policy\":" + json + ",");
    }

    private static String base(String pixel) {
        return "{\"schema\":1,\"size\":[16,16],\"source_grids\":{\"side\":[16,16]},\"pixels\":[" + pixel + "]}";
    }

    private static void assertPath(String json, String path) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DefinitionParser.parseTarget(id("target"), json));
        assertTrue(error.getMessage().contains(path), error.getMessage());
    }
}
