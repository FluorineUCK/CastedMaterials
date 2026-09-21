package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.fixtures.SpiderTestModel;
import com.fluorineuck.castedmaterials.mapping.*;
import com.fluorineuck.castedmaterials.palette.MaterialPalette;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Proxy;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ComposedRuntimeTest {
    static final Map<String,Map<String,Object>> COSTS=new TreeMap<>();
    static final Path ROOT=Path.of(System.getProperty("castedMaterials.projectDir"));
    static final ResourceLocation TARGET=ResourceLocation.parse("create_biotech:spider_assembly_table/spider");
    static final ResourceLocation FALLBACK=ResourceLocation.parse("create_biotech:textures/entity/spider_assembly_table/spider.png");
    @ParameterizedTest @ValueSource(strings={"andesite_casing","brass_casing","copper_casing","railway_casing",
        "shadow_steel_casing","refined_radiance_casing","biotech_casing","explosion_proof_casing","explosion_proof_casing_side","asurine_casing"})
    void productionSkinsRetainPixelsButSubmitOnlyOriginalBodyGeometry(String fixture) throws Exception {
        var f=new Fixture(fixture); var root=SpiderTestModel.create();
        var handle=f.runtime.resolve(root,TARGET,f.material,FALLBACK);
        if (fixture.equals("andesite_casing")) {
            assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());
            assertTrue(f.backend.images.isEmpty(),"Dedicated native body should not be copied");
        } else {
            assertEquals(CastedModelHandle.Backend.COMPOSITE,handle.backend());
            assertEquals(2,f.backend.images.size(),"One color texture and one surviving-eye emission mask");
            String eyeName=switch(fixture){case "copper_casing","railway_casing" -> "eye_"+fixture;default -> "eye_00";};
            var eye=f.images.get(ResourceLocation.parse("create_biotech:entity/spider_assembly_table/"+eyeName));
            var expected=PixelReference.render(UvMapping.compile(f.target),Map.of("all",new SourceSlotImage(f.source,new IntSize(128,128))),null);
            PixelReference.alphaOver(expected,eye);
            assertEquals(expected,f.backend.images.values().iterator().next(),"Pixel parity with independent offline reference");
            assertEquals(eye,new ArrayList<>(f.backend.images.values()).get(1),"Only selected eyes emit");
        }
        assertEquals(66,handle.plan().orElseThrow().outputQuads());
        assertEquals(1,handle.textureBatches());
        var counts=new ArrayList<Integer>();
        MultiBufferSource buffers=type -> recorder(counts);
        handle.render(new PoseStack(),buffers,RenderType::entityCutoutNoCull,15728640,0,-1);
        assertEquals(264,counts.size());
        counts.clear(); handle.renderEmissive(new PoseStack(),buffers,15728640,0,-1);
        assertTrue(counts.size()<264,"Emission must not resubmit the entire fragmented body");
        COSTS.put(fixture,Map.of("backend",handle.backend().name(),"bodyQuads",handle.plan().orElseThrow().outputQuads(),
                "bodyVertices",264,"emissionVertices",counts.size(),"bodyTextureBatches",handle.textureBatches(),
                "uploadedRgbaBytes",f.backend.images.values().stream().mapToInt(i -> i.width()*i.height()*4).sum()));
        int uploads=f.backend.images.size(), reads=f.reads;
        assertSame(handle,f.runtime.resolve(root,TARGET,f.material,FALLBACK));
        f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(uploads,f.backend.images.size(),"Different model instances share generated texture");
        assertEquals(reads,f.reads,"A cached material must not be recomposed or re-read");
    }
    @Test void resourceReloadReleasesAndRebuildsCachedTextures() throws Exception {
        var f=new Fixture("brass_casing");var root=SpiderTestModel.create();
        f.runtime.resolve(root,TARGET,f.material,FALLBACK);
        assertEquals(2,f.backend.images.size());
        f.generation.incrementAndGet();
        var next=f.runtime.resolve(root,TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.COMPOSITE,next.backend());
        assertEquals(2,f.backend.released.size());assertEquals(2,f.backend.images.size());
    }
    @Test void budgetExhaustionRetainsWorkingDirectUvInsteadOfBaseFallback() throws Exception {
        var f=new Fixture("brass_casing",1);
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend()); assertTrue(f.backend.images.isEmpty());
    }
    @Test void animatedSourceRemainsDirectInsteadOfFreezingFirstFrame() throws Exception {
        var f=new Fixture("brass_casing");f.animated=true;
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());assertTrue(f.backend.images.isEmpty());
    }
    @Test void animationMetadataOnEyeAlsoPreventsStaticComposition() throws Exception {
        var f=new Fixture("brass_casing");f.animatedOverlay=true;
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());assertTrue(f.backend.images.isEmpty());
    }
    @Test void highResolutionSourceRemainsDirectWithoutDownsampling() throws Exception {
        var f=new Fixture("brass_casing"); f.images.put(f.sourceId,new PixelImage(256,256));
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());assertTrue(f.backend.images.isEmpty());
    }
    @Test void unknownAtlasProvenanceRemainsDirectRatherThanSamplingWrongFile() throws Exception {
        var f=new Fixture("brass_casing");f.unknownSource=true;
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());assertTrue(f.backend.images.isEmpty());
    }
    @Test void uploadFailurePreservesDirectUvAndDoesNotRetryEveryFrame() throws Exception {
        var f=new Fixture("brass_casing");f.backend.failUpload=true;var root=SpiderTestModel.create();
        var handle=f.runtime.resolve(root,TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());assertTrue(f.backend.images.isEmpty());
        int attempts=f.backend.attempts;
        assertSame(handle,f.runtime.resolve(root,TARGET,f.material,FALLBACK));assertEquals(attempts,f.backend.attempts);
    }
    @Test void reloadClearReleasesTexturesWithoutWaitingForNextModelRender() throws Exception {
        var f=new Fixture("brass_casing");f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(2,f.backend.images.size());f.runtime.clear();
        assertTrue(f.backend.images.isEmpty());assertEquals(2,f.backend.released.size());
    }
    @Test void standaloneHandleUsesComposedEmissionWhileFallbackRequestsNoGenericEyes() throws Exception {
        var f=new Fixture("copper_casing");var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        var types=new HashSet<RenderType>();var vertices=new ArrayList<Integer>();
        MultiBufferSource buffers=type -> {types.add(type);return recorder(vertices);};
        handle.renderEmissive(new PoseStack(),buffers,15728640,0,-1);
        var emission=new ArrayList<>(f.backend.images.keySet()).get(1);
        assertEquals(Set.of(RenderType.eyes(emission)),types);assertFalse(vertices.isEmpty());assertTrue(vertices.size()<264);

        var fallback=CastedModelHandle.fallback(SpiderTestModel.create(),FALLBACK);
        fallback.renderEmissive(new PoseStack(),type -> {
            fail("FALLBACK must not request an eye buffer");
            return null;
        },15728640,0,-1);
    }
    @Test void compositionUsesActualCompactCostInsteadOfRejectingDirectUvFragmentBudget() throws Exception {
        var f=new Fixture("brass_casing",16*1024*1024,true);
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.COMPOSITE,handle.backend());
        assertEquals(66,handle.plan().orElseThrow().outputQuads());assertEquals(0,handle.plan().orElseThrow().additionalQuads());
        assertEquals(1,handle.textureBatches());
    }
    @Test void compositionReadsActualAtlasPixelsWhenSpriteIdAliasesAnotherResource() throws Exception {
        var f=new Fixture("brass_casing");
        var image=new com.mojang.blaze3d.platform.NativeImage(128,128,false);
        image.fillRect(0,0,128,128,0xffff0000); // ABGR blue, deliberately unlike the original casing PNG.
        try(var contents=new net.minecraft.client.renderer.texture.SpriteContents(f.sourceId,
                new net.minecraft.client.resources.metadata.animation.FrameSize(128,128),image,net.minecraft.server.packs.resources.ResourceMetadata.EMPTY)) {
            var ctor=net.minecraft.client.renderer.texture.TextureAtlasSprite.class.getDeclaredConstructor(ResourceLocation.class,
                    net.minecraft.client.renderer.texture.SpriteContents.class,int.class,int.class,int.class,int.class);
            ctor.setAccessible(true);
            var atlas=ctor.newInstance(ResourceLocation.parse("minecraft:textures/atlas/blocks.png"),contents,1024,1024,128,256);
            f.sourceOverride=MaterialTextures.findDirect(f.sourceId,new IntSize(128,128),ignored -> atlas,
                    ignored -> true,(id,grid) -> {fail("An atlas source is authoritative");return Optional.empty();}).orElseThrow();
            var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
            assertEquals(CastedModelHandle.Backend.COMPOSITE,handle.backend());
            var skin=f.backend.images.values().iterator().next();
            assertEquals(0xff0000ff,skin.get(6,0),"Must sample the actual stitched replacement, not the stale same-name PNG");
        }
    }
    @org.junit.jupiter.api.AfterAll static void writeCosts() throws Exception {
        if(COSTS.isEmpty())return;
        assertEquals(10,COSTS.size());
        var file=ROOT.resolve("build/material-rendering/composed-cost.json");Files.createDirectories(file.getParent());
        Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(COSTS));
    }
    @Test void croppedStandaloneSourceRemainsDirectWithoutAnActualPixelProvider() throws Exception {
        var f=new Fixture("brass_casing");
        f.sourceOverride=new MaterialTextures.SourceSprite(MaterialTextures.textureId(f.sourceId),new IntSize(128,128),.25f,.25f,.75f,.75f);
        var handle=f.runtime.resolve(SpiderTestModel.create(),TARGET,f.material,FALLBACK);
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend());assertTrue(f.backend.images.isEmpty());
    }
    static VertexConsumer recorder(List<Integer> count) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},
            (self,method,args) -> {if(method.getName().equals("addVertex"))count.add(1); return method.getReturnType()==VertexConsumer.class?self:null;});
    }
    static class Backend implements ComposedTextureCache.Backend {
        final Map<ResourceLocation,PixelImage> images=new LinkedHashMap<>(); final Set<ResourceLocation> released=new HashSet<>();
        boolean failUpload;int attempts;
        public void upload(ResourceLocation id,PixelImage image){attempts++;images.put(id,image);if(failUpload)throw new IllegalStateException("upload failed");}
        public void release(ResourceLocation id){released.add(id);images.remove(id);}
    }
    static class Fixture {
        final Backend backend=new Backend();final AtomicLong generation=new AtomicLong(1);
        final Map<ResourceLocation,PixelImage> images=new HashMap<>();
        final ResourceLocation material;final TargetDefinition target;final PixelImage source;final DefaultModelRuntime runtime; final ResourceLocation sourceId; int reads; boolean animated, unknownSource, animatedOverlay; MaterialTextures.SourceSprite sourceOverride;
        Fixture(String fixture) throws Exception{this(fixture,16*1024*1024);}
        Fixture(String fixture,long budget) throws Exception { this(fixture,budget,false); }
        Fixture(String fixture,long budget,boolean tightPolicy) throws Exception {
            var json=com.google.gson.JsonParser.parseString(Files.readString(ROOT.resolve("tools/test-resources/assets/create_biotech/casted_materials/targets/spider_assembly_table/spider.json"))).getAsJsonObject();
            if(tightPolicy)json.add("render_policy",com.google.gson.JsonParser.parseString("{\"max_pieces_per_face\":1,\"max_additional_quads\":0,\"max_texture_batches\":1}"));
            target=DefinitionParser.parseTarget(TARGET,json.toString());
            String ns=fixture.startsWith("biotech_")||fixture.startsWith("explosion_")||fixture.equals("asurine_casing")?"create_biotech":"create";
            material=ResourceLocation.fromNamespaceAndPath(ns,fixture.replace("_side",""));
            var sprite=ResourceLocation.fromNamespaceAndPath(ns,"block/"+fixture+"_connected");sourceId=sprite;
            source=png(ROOT.resolve("tools/test-resources/fixtures/connected_spider/"+fixture+"_connected.png"));
            images.put(sprite,source);
            try(var files=Files.list(ROOT.resolve("tools/test-resources/assets/create_biotech/textures/entity/spider_assembly_table"))){
                for(var p:files.filter(p -> p.toString().endsWith(".png")).toList()){
                    String name=p.getFileName().toString();images.put(ResourceLocation.parse("create_biotech:entity/spider_assembly_table/"+name.substring(0,name.length()-4)),png(p));
                }
            }
            runtime=new DefaultModelRuntime(() -> new CastedResources.Snapshot(generation.get(),Map.of(TARGET,target),Map.of(),images.keySet()),
                () -> MaterialPalette.from(List.of(material)),(t,m,g,required) -> Optional.of(Map.of("all", sourceOverride!=null?sourceOverride:
                    new MaterialTextures.SourceSprite(MaterialTextures.textureId(sprite),new IntSize(128,128),0,0,1,1,unknownSource?null:sprite,animated))),128,
                (id,grid) -> {reads++;return Optional.ofNullable(images.get(id)).map(image -> new DefaultModelRuntime.FixedOverlay(id,image,animatedOverlay && id.getPath().contains("eye_")));},
                ignored -> null,new ComposedTextureCache(128,budget,backend));
        }
    }
    static PixelImage png(Path path) throws Exception{
        var i=ImageIO.read(path.toFile());return new PixelImage(i.getWidth(),i.getHeight(),i.getRGB(0,0,i.getWidth(),i.getHeight(),null,0,i.getWidth()));
    }
}
