package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class OrderedLayerDefinitionTest {
    @Test
    void bodyDirectoryRejectsNonStringOrEmptyIds() {
        for (String value : List.of("true","1","null","{}","[]","\"\"","\"host:\"","\"host:/\"")) {
            var error=assertThrows(IllegalArgumentException.class,() -> parse("\"body_textures\":"+value+","),value);
            assertTrue(error.getMessage().contains("body_textures"),error.getMessage());
        }
    }

    @Test
    void bodyDirectoryEnablesIndependentDedicatedLookupButNotNumberedCandidates() {
        var target=parse("\"body_textures\":\"host:skins/\",");
        var body=target.dedicatedBodyLayer().orElseThrow();
        assertEquals(List.of(id("host:skins/addon/machines/body_casing"),id("host:skins/machines/body_casing")),
                body.materialTextureCandidates(id("addon:machines/casing")));
        assertEquals(target.size(),body.grid()); assertEquals(body.source(),body.destination());
        assertFalse(body.emissive()); assertTrue(body.numberedEyeDirectory().isEmpty());
        assertTrue(parse("").dedicatedBodyLayer().isEmpty());
    }

    @Test
    void numberedEyeCandidatesStayLocalAndSortNumericallyWithDeterministicTies() {
        var eye=(TextureLayer)parse("""
            "layers":[{"index":0,"texture":"host:eyes/eye_default","material_variants":true}],
            """).layers().getFirst();
        var candidates=List.of("host:eyes/eye_10","host:eyes/eye_2","host:eyes/eye_02","host:eyes/eye_00",
                "host:eyes/eye_9999999999999999999999","host:eyes/eye_copper_casing","host:eyes/body_01",
                "host:eyes/eye_1_extra","host:eyes/nested/eye_01","other:eyes/eye_01","host:eyes/eye_02")
                .stream().map(net.minecraft.resources.ResourceLocation::parse).toList();
        assertEquals(List.of("host:eyes/eye_00","host:eyes/eye_02","host:eyes/eye_2","host:eyes/eye_10",
                "host:eyes/eye_9999999999999999999999"),eye.numberedEyeCandidates(candidates).stream().map(Object::toString).toList());
        for (String texture : List.of("body_00","manual")) {
            var layer=new TextureLayer(eye.index(),id("host:eyes/"+texture),eye.grid(),eye.source(),eye.destination(),false,true);
            assertTrue(layer.numberedEyeDirectory().isEmpty());
            assertTrue(layer.numberedEyeCandidates(candidates).isEmpty());
        }
        var explicit=new TextureLayer(eye.index(),eye.texture(),eye.grid(),eye.source(),eye.destination(),false,false);
        assertTrue(explicit.numberedEyeCandidates(candidates).isEmpty());
    }

    @Test
    void namedTextureCandidatesKeepNamespacePathsAndRemainOptIn() {
        var eye = (TextureLayer) parse("""
            "layers":[{"index":0,"texture":"host:eyes/eye_default","material_variants":true}],
            """).layers().getFirst();
        assertEquals(List.of(id("host:eyes/addon/machines/eye_casing"),id("host:eyes/machines/eye_casing")),
                eye.materialTextureCandidates(id("addon:machines/casing")));
        assertEquals(List.of(),eye.materialTextureCandidates(null));
        var explicit = new TextureLayer(eye.index(),eye.texture(),eye.grid(),eye.source(),eye.destination(),eye.emissive());
        assertEquals(List.of(),explicit.materialTextureCandidates(id("addon:casing")));
        for (String value : List.of("1","null","\"true\"")) {
            var error = assertThrows(IllegalArgumentException.class,() -> parse(
                    "\"layers\":[{\"index\":0,\"texture\":\"host:eyes/eye_default\",\"material_variants\":" + value + "}],"));
            assertTrue(error.getMessage().contains("material_variants"));
        }
    }

    @Test
    void materialVariantRolesDoNotCrossMatchOrUseBareNames() {
        for (String role : List.of("eye", "body")) {
            var layer = (TextureLayer) parse("\"layers\":[{\"index\":0,\"texture\":\"host:spider/" + role
                    + "_00\",\"material_variants\":true}],").layers().getFirst();
            assertEquals(List.of(id("host:spider/create/" + role + "_copper_casing"),
                            id("host:spider/" + role + "_copper_casing")),
                    layer.materialTextureCandidates(id("create:copper_casing")));
        }
        for (String ambiguous : List.of("spider", "default", "eyebrow_00", "bodyguard_00")) {
            var layer = (TextureLayer) parse("\"layers\":[{\"index\":0,\"texture\":\"host:spider/" + ambiguous
                    + "\",\"material_variants\":true}],").layers().getFirst();
            assertEquals(List.of(),layer.materialTextureCandidates(id("create:copper_casing")),
                    "A default without a recognized role must never guess a material texture");
        }
    }

    @Test
    void modelOnlySlotsDoNotLeakIntoBaseMappingRequirements() {
        var target = DefinitionParser.parseTarget(id("test:slot_scope"), """
            {"schema":1,"size":[1,1],"source_grids":{"all":[1,1],"hat":[1,1]},
             "pixels":[{"source_slot":"all","source":[0,0],"destination":[0,0]}],
             "layers":[{"index":0,"model":"test:hat_model","source_slot":"hat"}],
             "material_overrides":{"test:no_hat":{"layers":[]}}}
            """);
        assertEquals(java.util.Set.of("all"), target.requiredSlots());
        assertEquals(java.util.Set.of("all"), UvMapping.compile(target).requiredSlots());
        assertTrue(target.layersFor(id("test:no_hat")).isEmpty());
        assertEquals(java.util.Optional.of("hat"), ((ModelLayer) target.layers().getFirst()).sourceSlot());
    }

    @Test
    void parsesTextureAndModelDefaultsAndSortsStably() {
        TargetDefinition target = parse("""
                "layers":[
                  {"index":2,"texture":"test:late"},
                  {"index":-1,"model":"test:hat"},
                  {"index":2,"texture":"test:same","grid":[2,1],"source":[1,0,1,1],"destination":[3,1,1,1],"emissive":true}
                ],
                """);

        assertEquals(List.of(-1, 2, 2), target.layers().stream().map(Layer::index).toList());
        ModelLayer model = (ModelLayer) target.layers().getFirst();
        assertEquals("head", model.part());
        assertEquals(Vec3.ZERO, model.offset());
        assertEquals(Vec3.ZERO, model.rotation());
        assertEquals(new Vec3(1, 1, 1), model.scale());
        assertEquals(Optional.empty(), model.sourceSlot());
        assertEquals(Optional.empty(), model.source());
        TextureLayer firstTie = (TextureLayer) target.layers().get(1);
        assertEquals(id("test:late"), firstTie.texture());
        assertEquals(new IntSize(4, 2), firstTie.grid());
        assertEquals(new IntRect(0, 0, 4, 2), firstTie.source());
        assertEquals(new IntRect(0, 0, 4, 2), firstTie.destination());
        assertFalse(firstTie.emissive());
        assertEquals(id("test:same"), ((TextureLayer) target.layers().get(2)).texture());
    }

    @Test
    void materialLayersReplaceRootEvenWhenExplicitlyEmpty() {
        TargetDefinition target = parse("""
                "layers":[{"index":3,"texture":"test:root"}],
                "material_overrides":{
                  "test:empty":{"layers":[]},
                  "test:replacement":{"layers":[{"index":1,"texture":"test:b"},{"index":0,"texture":"test:a"}]}
                },
                """);

        assertEquals(1, target.layersFor(id("test:unknown")).size());
        assertTrue(target.layersFor(id("test:empty")).isEmpty());
        assertEquals(List.of(0, 1), target.layersFor(id("test:replacement")).stream().map(Layer::index).toList());
    }

    @Test
    void preservesOldConstructorsWithEmptyLayers() {
        TargetDefinition target = new TargetDefinition(id("target"), new IntSize(1, 1), Map.of("s", new IntSize(1, 1)),
                List.of(), List.of(new PixelMapping("s", new IntPoint(0, 0), new IntPoint(0, 0))),
                Optional.empty(), Map.of(), Optional.empty());
        MaterialOverride override = new MaterialOverride(Map.of(), Optional.empty());
        assertTrue(target.layers().isEmpty());
        assertTrue(override.layers().isEmpty());
    }

    @Test
    void validatesLayerShapesKindsNumbersBoundsAndSourceSlotRules() {
        for (String layer : List.of(
                "{\"texture\":\"test:x\"}",
                "{\"index\":1.5,\"texture\":\"test:x\"}",
                "{\"index\":0,\"texture\":\"test:x\",\"model\":\"test:y\"}",
                "{\"index\":0}",
                "{\"index\":0,\"texture\":\"test:x\",\"wat\":1}",
                "{\"index\":0,\"texture\":\"test:x\",\"grid\":[2,2],\"source\":[1,1,2,1]}",
                "{\"index\":0,\"texture\":\"test:x\",\"destination\":[3,0,2,1]}",
                "{\"index\":0,\"model\":\"test:x\",\"source\":[0,0,1,1]}",
                "{\"index\":0,\"model\":\"test:x\",\"source_slot\":\"missing\"}",
                "{\"index\":0,\"model\":\"test:x\",\"offset\":[0,1]}",
                "{\"index\":0,\"model\":\"test:x\",\"rotation\":[0,1,1e400]}",
                "{\"index\":0,\"model\":\"test:x\",\"scale\":[1,0,1]}")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> parse("\"layers\":[" + layer + "],"), layer);
            assertTrue(error.getMessage().contains("$.layers[0]"), error.getMessage());
        }
    }

    @Test
    void modelSlotDefaultsSourceToWholeDeclaredGrid() {
        ModelLayer layer = (ModelLayer) parse("""
                "layers":[{"index":0,"model":"test:hat","part":"body","source_slot":"s","emissive":true}],
                """).layers().getFirst();
        assertEquals("body", layer.part());
        assertEquals(Optional.of("s"), layer.sourceSlot());
        assertEquals(Optional.of(new IntRect(0, 0, 2, 2)), layer.source());
        assertTrue(layer.emissive());
    }

    private static TargetDefinition parse(String extra) {
        return DefinitionParser.parseTarget(id("target"), """
                {"schema":1,"size":[4,2],"source_grids":{"s":[2,2]},
                %s
                "pixels":[{"source_slot":"s","source":[0,0],"destination":[0,0]}]}
                """.formatted(extra));
    }
}
