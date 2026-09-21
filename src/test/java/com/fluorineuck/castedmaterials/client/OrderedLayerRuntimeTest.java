package com.fluorineuck.castedmaterials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.fluorineuck.castedmaterials.mapping.*;
import com.fluorineuck.castedmaterials.palette.MaterialPalette;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class OrderedLayerRuntimeTest {
    private static final String BASE = """
        {"schema":1,"size":[16,16],"source_grids":{"all":[16,16]},
        "regions":[{"source_slot":"all","source":[0,0,16,16],"destination":[0,0,16,16]}],
        "layers":%s}
        """;

    @Test void materialNamedEyesReplaceInsteadOfStackAndRefreshOnReload() {
        var target = DefinitionParser.parseTarget(id("target"), BASE.formatted("""
            [{"index":0,"texture":"test:eyes/eye_default","material_variants":true,"emissive":true}]
            """));
        var material = id("other:copper_casing");
        var qualified = id("eyes/other/eye_copper_casing");
        var shortName = id("eyes/eye_copper_casing");
        var fallback = id("eyes/eye_default");
        var available = new HashMap<ResourceLocation, PixelImage>();
        available.put(qualified,opaque()); available.put(shortName,new PixelImage(16,16)); available.put(fallback,opaque());
        // These unrelated resources must never become candidates for an eye layer.
        available.put(id("eyes/other/body_copper_casing"),opaque());
        available.put(id("eyes/other/copper_casing"),opaque());
        available.put(id("eyes/body_copper_casing"),opaque());
        available.put(id("eyes/copper_casing"),opaque());
        var loads = new ArrayList<ResourceLocation>();
        var snapshot = new AtomicReference<>(new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()));
        var runtime = new DefaultModelRuntime(snapshot::get,() -> MaterialPalette.from(List.of(material)),
            (t,m,g,r) -> Optional.of(Map.of("all",new MaterialTextures.SourceSprite(id("atlas"),t.size(),0,0,1,1))),8,
            (sprite,grid) -> { loads.add(sprite); return Optional.ofNullable(available.get(sprite)).map(image -> new DefaultModelRuntime.FixedOverlay(sprite,image)); });
        var root = CapturedModelTest.cubePart();
        var first = runtime.resolve(root,target.id(),material,id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,first.backend(),first.reason());
        assertEquals(List.of(qualified),loads,"Namespaced file wins; default is not even loaded");
        var seen = new ArrayList<String>();
        first.render(new PoseStack(), type -> { seen.add(type.toString()); return new Recording(); },
                net.minecraft.client.renderer.RenderType::entityCutoutNoCull,0,0,-1);
        assertEquals(1,seen.size());
        assertTrue(seen.getFirst().contains("eyes/other/eye_copper_casing.png"),seen.toString());
        assertSame(first,runtime.resolve(root,target.id(),material,id("fallback")));
        assertEquals(1,loads.size());
        available.remove(qualified);
        snapshot.set(new CastedResources.Snapshot(2,Map.of(target.id(),target),Map.of()));
        var transparent = runtime.resolve(root,target.id(),material,id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,transparent.backend(),transparent.reason());
        assertEquals(List.of(qualified,qualified,shortName),loads);
        assertEquals(java.util.Set.of("all"),transparent.plan().orElseThrow().faces().stream().flatMap(List::stream)
                .map(fragment -> fragment.sourceSlot()).collect(java.util.stream.Collectors.toSet()),"Transparent replacement exposes casing, not original eyes");
        var eyes = new Recording();
        transparent.renderEmissive(new PoseStack(), ignored -> eyes,0,0,-1);
        assertEquals(0,eyes.count,"No old eye emission through transparent replacement");
        available.remove(shortName);
        snapshot.set(new CastedResources.Snapshot(3,Map.of(target.id(),target),Map.of()));
        var defaults = runtime.resolve(root,target.id(),material,id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,defaults.backend(),defaults.reason());
        assertEquals(fallback,loads.getLast());
        defaults.renderEmissive(new PoseStack(), ignored -> eyes,0,0,-1);
        assertEquals(24,eyes.count);
    }

    @Test void explicitMaterialEyeTextureOverridesNamingSugar() {
        var json = BASE.formatted("[{\"index\":0,\"texture\":\"test:eyes/eye_default\",\"material_variants\":true}]");
        var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        obj.add("material_overrides",com.google.gson.JsonParser.parseString("""
            {"test:material":{"layers":[{"index":0,"texture":"test:eyes/manual"}]}}
            """));
        var target = DefinitionParser.parseTarget(id("target"),obj.toString());
        var loads = new ArrayList<ResourceLocation>();
        var runtime = new DefaultModelRuntime(() -> new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()),
            () -> MaterialPalette.from(List.of(id("material"))), (t,m,g,r) -> Optional.of(Map.of()),8,
            (sprite,grid) -> { loads.add(sprite); return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite,opaque())); });
        var handle = runtime.resolve(CapturedModelTest.cubePart(),target.id(),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend(),handle.reason());
        assertEquals(List.of(id("eyes/manual")),loads);
    }

    @Test void sortedLayersBindOnlyWinnerAndCacheUntilReload() {
        var target = DefinitionParser.parseTarget(id("target"), BASE.formatted("""
            [{"index":20,"texture":"test:top"},{"index":10,"texture":"test:lower"},
             {"index":20,"texture":"test:tie_winner"}]
            """));
        var generation = new AtomicReference<>(new CastedResources.Snapshot(1, Map.of(target.id(), target), Map.of()));
        var loads = new ArrayList<ResourceLocation>();
        var runtime = new DefaultModelRuntime(generation::get, () -> MaterialPalette.from(List.of(id("material"))),
            (t,m,g,required) -> { assertTrue(required.isEmpty()); return Optional.of(Map.of()); }, 8,
            (sprite,grid) -> { loads.add(sprite); return Optional.of(new DefaultModelRuntime.FixedOverlay(sprite, opaque())); });
        var root = CapturedModelTest.cubePart();
        var first = runtime.resolve(root,target.id(),id("material"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,first.backend(),first.reason());
        assertEquals(1,first.textureBatches());
        assertEquals(List.of(id("lower"),id("top"),id("tie_winner")),loads);
        var seen = new ArrayList<String>();
        first.render(new PoseStack(), type -> { seen.add(type.toString()); return new Recording(); },
            net.minecraft.client.renderer.RenderType::entityCutoutNoCull,0,0,-1);
        assertEquals(1,seen.size());
        assertTrue(seen.getFirst().contains("tie_winner"),seen.toString());
        assertSame(first,runtime.resolve(root,target.id(),id("material"),id("fallback")));
        assertEquals(3,loads.size());
        generation.set(new CastedResources.Snapshot(2,Map.of(target.id(),target),Map.of()));
        assertNotSame(first,runtime.resolve(root,target.id(),id("material"),id("fallback")));
        assertEquals(6,loads.size());
    }

    @Test void coveredEmissiveLayerDoesNotLeakEyes() throws Exception {
        for (boolean covered : List.of(false,true)) {
            var target = DefinitionParser.parseTarget(id("target"), BASE.formatted(
                "[{\"index\":0,\"texture\":\"test:eyes\",\"emissive\":true}" +
                (covered ? ",{\"index\":1,\"texture\":\"test:face\"}]" : "]")));
            var runtime = runtime(target,new AtomicInteger(),false);
            var handle = runtime.resolve(CapturedModelTest.cubePart(),target.id(),id("material"),id("fallback"));
            assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend(),handle.reason());
            var vertices = new Recording();
            net.minecraft.client.renderer.MultiBufferSource buffers = ignored -> vertices;
            CastedModelHandle.class.getMethod("renderEmissive",PoseStack.class,
                net.minecraft.client.renderer.MultiBufferSource.class,int.class,int.class,int.class)
                .invoke(handle,new PoseStack(),buffers,15728640,0,-1);
            assertEquals(covered ? 0 : 24,vertices.count);
        }
    }

    @Test void missingLayerRejectionIsCachedAndRecoversOnReload() {
        var target = DefinitionParser.parseTarget(id("target"),BASE.formatted("[{\"index\":0,\"texture\":\"test:missing\"}]"));
        var loads = new AtomicInteger();
        var runtime = runtime(target,loads,true);
        var root = CapturedModelTest.cubePart();
        for (int i=0;i<4;i++) {
            var handle = runtime.resolve(root,target.id(),id("material"),id("fallback"));
            assertEquals(CastedModelHandle.Backend.FALLBACK,handle.backend());
            assertTrue(handle.reason().contains("LAYER"),handle.reason());
        }
        assertEquals(1,loads.get());
        assertEquals(CastedModelHandle.Backend.FALLBACK,runtime.resolve(root,target.id(),null,id("fallback")).backend());
    }

    @Test void numberedEyesCycleByPaletteIndexButNeverBodies() throws Exception {
        var target = DefinitionParser.parseTarget(id("target"), BASE.formatted("""
            [{"index":0,"texture":"test:eyes/eye_00","material_variants":true}]
            """));
        var available = new LinkedHashMap<ResourceLocation, PixelImage>();
        // Deliberately not lexicographic/numeric order. Invalid candidates must not occupy indices.
        for (String name : List.of("eye_10", "eye_02", "eye_00", "body_01", "nested/eye_01", "eye_b", "other:eyes/eye_01"))
            available.put(id(name.contains(":") ? name : "eyes/" + name), opaque());
        var partial = opaque(); partial.set(0,0,0x80112233);
        available.put(id("eyes/eye_01"),partial);
        available.put(id("eyes/eye_03"),new PixelImage(17,16));
        var resources = freshResources();
        reload(resources,target,available.keySet());
        var palette = new AtomicReference<>(MaterialPalette.from(List.of(id("e"),id("c"),id("a"),id("d"),id("b"))));
        var loads = new ArrayList<ResourceLocation>();
        var runtime = new DefaultModelRuntime(resources::current,palette::get,
            (t,m,g,r) -> Optional.of(Map.of("all",new MaterialTextures.SourceSprite(id("atlas"),t.size(),0,0,1,1))),16,
            (sprite,grid) -> { loads.add(sprite); return Optional.ofNullable(available.get(sprite)).map(image -> new DefaultModelRuntime.FixedOverlay(sprite,image)); });
        var root = CapturedModelTest.cubePart();
        String[] expected = {"eye_00","eye_b","eye_10","eye_00","eye_02"};
        for (int i=0;i<expected.length;i++) {
            var material = id(String.valueOf((char)('a'+i)));
            var handle = runtime.resolve(root,target.id(),material,id("fallback"));
            assertTexture(handle,"eyes/"+expected[i]+".png");
            int count=loads.size();
            assertSame(handle,runtime.resolve(root,target.id(),material,id("fallback")));
            assertEquals(count,loads.size(),"No per-frame loads");
        }
        assertEquals(1,Collections.frequency(loads,id("eyes/eye_02")),"Pool decoded once across materials");
        // Palette may be resynchronized without a resource reload; cached model must follow its new index.
        palette.set(MaterialPalette.from(List.of(id("c"),id("d"),id("e"))));
        assertTexture(runtime.resolve(root,target.id(),id("d"),id("fallback")),"eyes/eye_02.png");
        available.remove(id("eyes/eye_02"));
        reload(resources,target,available.keySet());
        assertTexture(runtime.resolve(root,target.id(),id("d"),id("fallback")),"eyes/eye_10.png");

        var body = DefinitionParser.parseTarget(id("target"),BASE.formatted("""
            [{"index":0,"texture":"test:eyes/body_00","material_variants":true}]
            """));
        available.put(id("eyes/body_00"),opaque());
        available.put(id("eyes/body_d"),opaque());
        reload(resources,body,available.keySet());
        assertTexture(runtime.resolve(root,body.id(),id("e"),id("fallback")),"eyes/body_00.png");
        assertTexture(runtime.resolve(root,body.id(),id("d"),id("fallback")),"eyes/body_d.png");
        var pinned = DefinitionParser.parseTarget(id("target"),BASE.formatted("""
            [{"index":0,"texture":"test:eyes/eye_00"}]
            """));
        reload(resources,pinned,available.keySet());
        assertTexture(runtime.resolve(root,pinned.id(),id("e"),id("fallback")),"eyes/eye_00.png");
    }

    @Test void transparentNumberedEyeIsValidAndMissingOrEmptyPoolsUseExplicitDefault() throws Exception {
        var target=DefinitionParser.parseTarget(id("target"),BASE.formatted("""
            [{"index":0,"texture":"test:eyes/eye_default","material_variants":true,"emissive":true}]
            """));
        var available=new HashMap<ResourceLocation,PixelImage>();
        available.put(id("eyes/eye_00"),opaque());
        available.put(id("eyes/eye_01"),new PixelImage(16,16));
        available.put(id("eyes/eye_default"),opaque());
        var resources=freshResources();
        reload(resources,target,available.keySet());
        var runtime=new DefaultModelRuntime(resources::current,() -> MaterialPalette.from(List.of(id("a"),id("b"))),
            (t,m,g,r) -> Optional.of(Map.of("all",new MaterialTextures.SourceSprite(id("atlas"),t.size(),0,0,1,1))),8,
            (sprite,grid) -> Optional.ofNullable(available.get(sprite)).map(image -> new DefaultModelRuntime.FixedOverlay(sprite,image)));
        var root=CapturedModelTest.cubePart();
        var transparent=runtime.resolve(root,target.id(),id("b"),id("fallback"));
        assertTexture(transparent,"atlas");
        var glow=new Recording(); transparent.renderEmissive(new PoseStack(),ignored -> glow,0,0,-1);
        assertEquals(0,glow.count);
        available.remove(id("eyes/eye_00")); available.remove(id("eyes/eye_01"));
        reload(resources,target,available.keySet());
        assertTexture(runtime.resolve(root,target.id(),id("b"),id("fallback")),"eyes/eye_default.png");
        var invalid=opaque(); invalid.set(0,0,0x80112233); available.put(id("eyes/eye_01"),invalid);
        reload(resources,target,available.keySet());
        assertTexture(runtime.resolve(root,target.id(),id("b"),id("fallback")),"eyes/eye_default.png");
    }

    @Test void dedicatedBodyPriorityAndReloadDoNotTurnBodiesIntoACandidatePool() {
        String json=BASE.formatted("[]").replace("\"layers\":[]","\"body_textures\":\"test:skins\",\"layers\":[]");
        var target=DefinitionParser.parseTarget(id("target"),json);
        var snapshot=new AtomicReference<>(new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()));
        var available=new HashMap<ResourceLocation,PixelImage>();
        var qualified=id("skins/other/body_casing"); var plain=id("skins/body_casing");
        available.put(qualified,opaque()); available.put(plain,opaque());
        available.put(id("skins/body_00"),opaque()); available.put(id("skins/eye_casing"),opaque());
        var loads=new ArrayList<ResourceLocation>(); var requirements=new ArrayList<Set<String>>();
        var runtime=new DefaultModelRuntime(snapshot::get,() -> MaterialPalette.from(List.of(id("other:casing"),id("other:unknown"))),
            (t,m,g,r) -> { requirements.add(r); return Optional.of(r.isEmpty()?Map.of():Map.of("all",new MaterialTextures.SourceSprite(id("atlas"),t.size(),0,0,1,1))); },16,
            (sprite,grid) -> { loads.add(sprite); return Optional.ofNullable(available.get(sprite)).map(image -> new DefaultModelRuntime.FixedOverlay(sprite,image)); });
        var root=CapturedModelTest.cubePart();
        var first=runtime.resolve(root,target.id(),id("other:casing"),id("fallback"));
        assertTexture(first,"skins/other/body_casing.png");
        assertEquals(List.of(qualified),loads); assertTrue(requirements.getFirst().isEmpty());
        assertSame(first,runtime.resolve(root,target.id(),id("other:casing"),id("fallback")));
        available.remove(qualified);
        snapshot.set(new CastedResources.Snapshot(2,Map.of(target.id(),target),Map.of()));
        assertTexture(runtime.resolve(root,target.id(),id("other:casing"),id("fallback")),"skins/body_casing.png");
        // An explicitly configured layer remains above the named base.
        var explicit=DefinitionParser.parseTarget(id("target"),json.replace("\"layers\":[]",
            "\"layers\":[{\"index\":0,\"texture\":\"test:manual\"}]"));
        available.put(id("manual"),opaque());
        snapshot.set(new CastedResources.Snapshot(3,Map.of(target.id(),explicit),Map.of()));
        assertTexture(runtime.resolve(root,target.id(),id("other:casing"),id("fallback")),"manual.png");
        // No named match: do not use body_00 or eyes, retain generated UVs.
        snapshot.set(new CastedResources.Snapshot(4,Map.of(target.id(),target),Map.of()));
        assertTexture(runtime.resolve(root,target.id(),id("other:unknown"),id("fallback")),"atlas");
        assertFalse(loads.contains(id("skins/body_00"))); assertFalse(loads.contains(id("skins/eye_casing")));
        // Reject an incompatible qualified image, then try the short name.
        available.put(qualified,new PixelImage(17,16));
        snapshot.set(new CastedResources.Snapshot(5,Map.of(target.id(),target),Map.of()));
        assertTexture(runtime.resolve(root,target.id(),id("other:casing"),id("fallback")),"skins/body_casing.png");
        // Dedicated alpha holes must stay holes instead of leaking generated casing underneath.
        available.put(qualified,new PixelImage(16,16));
        snapshot.set(new CastedResources.Snapshot(6,Map.of(target.id(),target),Map.of()));
        var transparent=runtime.resolve(root,target.id(),id("other:casing"),id("fallback"));
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,transparent.backend(),transparent.reason());
        assertEquals(0,transparent.plan().orElseThrow().outputQuads());
        available.remove(qualified); available.remove(plain);
        snapshot.set(new CastedResources.Snapshot(7,Map.of(target.id(),target),Map.of()));
        assertTexture(runtime.resolve(root,target.id(),id("other:casing"),id("fallback")),"atlas");
    }

    private static void assertTexture(CastedModelHandle handle, String texture) {
        assertEquals(CastedModelHandle.Backend.DIRECT_UV,handle.backend(),handle.reason());
        var seen = new ArrayList<String>();
        handle.render(new PoseStack(),type -> { seen.add(type.toString()); return new Recording(); },
            net.minecraft.client.renderer.RenderType::entityCutoutNoCull,0,0,-1);
        assertEquals(1,seen.size());
        assertTrue(seen.getFirst().contains(texture),seen.toString());
    }

    private static CastedResources freshResources() throws Exception {
        var ctor=CastedResources.class.getDeclaredConstructor(); ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void reload(CastedResources resources, TargetDefinition target, Set<ResourceLocation> sprites) {
        // Reuse the parsed fixture's geometry but serialize just its single layer for the real reload path.
        var layer=(TargetDefinition.TextureLayer)target.layers().getFirst();
        String source=BASE.formatted("[{\"index\":0,\"texture\":\""+layer.texture()+"\",\"material_variants\":"+layer.materialVariants()+"}]");
        var files=new HashMap<ResourceLocation,net.minecraft.server.packs.resources.Resource>();
        files.put(id("casted_materials/targets/target.json"),new net.minecraft.server.packs.resources.Resource(null,
            () -> new java.io.ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        for (var sprite:sprites) files.put(sprite.withPath("textures/"+sprite.getPath()+".png"),
            new net.minecraft.server.packs.resources.Resource(null,() -> new java.io.ByteArrayInputStream(new byte[0])));
        var manager=(net.minecraft.server.packs.resources.ResourceManager)java.lang.reflect.Proxy.newProxyInstance(
            OrderedLayerRuntimeTest.class.getClassLoader(),new Class<?>[]{net.minecraft.server.packs.resources.ResourceManager.class},
            (proxy,method,args) -> {
                if (!method.getName().equals("listResources")) throw new AssertionError(method.getName());
                @SuppressWarnings("unchecked") var filter=(java.util.function.Predicate<ResourceLocation>)args[1];
                var found=new HashMap<ResourceLocation,net.minecraft.server.packs.resources.Resource>();
                files.forEach((id,resource) -> { if (id.getPath().startsWith(args[0]+"/") && filter.test(id)) found.put(id,resource); });
                return found;
            });
        resources.apply(resources.prepare(manager,null),manager,null);
    }

    private static DefaultModelRuntime runtime(TargetDefinition target, AtomicInteger loads, boolean missing) {
        return new DefaultModelRuntime(() -> new CastedResources.Snapshot(1,Map.of(target.id(),target),Map.of()),
            () -> MaterialPalette.from(List.of(id("material"))),
            (t,m,g,required) -> Optional.of(Map.of("all",new MaterialTextures.SourceSprite(id("atlas"),target.size(),0,0,1,1))),
            8,(sprite,grid) -> { loads.incrementAndGet(); return missing ? Optional.empty() : Optional.of(new DefaultModelRuntime.FixedOverlay(sprite,opaque())); });
    }
    private static PixelImage opaque() { int[] p = new int[256]; Arrays.fill(p,0xffe7a415); return new PixelImage(16,16,p); }
    static class Recording implements VertexConsumer {
        int count;
        public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz) { count++; }
        public VertexConsumer addVertex(float x,float y,float z){throw new AssertionError();}
        public VertexConsumer setColor(int r,int g,int b,int a){throw new AssertionError();}
        public VertexConsumer setUv(float u,float v){throw new AssertionError();}
        public VertexConsumer setUv1(int u,int v){throw new AssertionError();}
        public VertexConsumer setUv2(int u,int v){throw new AssertionError();}
        public VertexConsumer setNormal(float x,float y,float z){throw new AssertionError();}
    }
}
