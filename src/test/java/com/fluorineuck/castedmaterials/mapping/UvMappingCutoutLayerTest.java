package com.fluorineuck.castedmaterials.mapping;

import com.fluorineuck.castedmaterials.mapping.TargetDefinition.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class UvMappingCutoutLayerTest {
    @Test
    void dedicatedBodyClearsGeneratedCellsButKeepsOriginalMappingImmutableAndHdDetail() {
        var base=base(new IntSize(2,1));
        var layer=new TextureLayer(0,id("body"),new IntSize(2,1),new IntRect(0,0,2,1),new IntRect(0,0,2,1),false);
        var image=new PixelImage(4,2,new int[]{0xff112233,0xff223344,0,0,0xff334455,0xff445566,0,0});
        var body=base.withCutoutBody(image,layer,"body");
        assertEquals("body",body.at(0,0).slot()); assertNull(body.at(1,0));
        assertEquals("base",base.at(1,0).slot());
        assertEquals(java.util.Set.of("body"),body.requiredSlots());
        var pixels=PixelReference.render(body,Map.of("body",new SourceSlotImage(image,new IntSize(2,1))),null);
        assertEquals(0xff445566,pixels.get(0,0)); assertEquals(0,pixels.get(1,0));
        assertThrows(IllegalArgumentException.class,() -> base.withCutoutBody(new PixelImage(2,1,new int[]{0x80112233,0}),layer,"body"));
    }

    @Test
    void cropScalesToDestinationAndBindsOriginalTextureSlot() {
        UvMapping base = base(new IntSize(4, 2));
        TextureLayer layer = new TextureLayer(0, id("test:faces"), new IntSize(2, 1),
                new IntRect(1, 0, 1, 1), new IntRect(0, 0, 4, 2), false);
        PixelImage image = new PixelImage(2, 1, new int[]{0, 0xffabcdef});

        UvMapping result = base.withCutoutLayer(image, layer, "layer:test:faces");

        for (int y = 0; y < 2; y++) for (int x = 0; x < 4; x++) {
            assertEquals("layer:test:faces", result.at(x, y).slot());
            assertEquals(1 + (x + .5) / 4, result.at(x, y).mapU(x + .5, y + .5), 1e-9);
            assertEquals((y + .5) / 2, result.at(x, y).mapV(x + .5, y + .5), 1e-9);
        }
        assertEquals(new IntSize(2, 1), result.sourceGrids().get("layer:test:faces"));
    }

    @Test
    void transparentLogicalTexelsPreserveEarlierMappingAndHdTexelsMustBeUniform() {
        UvMapping base = base(new IntSize(2, 1));
        TextureLayer layer = new TextureLayer(0, id("test:hd"), new IntSize(2, 1),
                new IntRect(0, 0, 2, 1), new IntRect(0, 0, 2, 1), false);
        UvMapping result = base.withCutoutLayer(new PixelImage(4, 2, new int[]{
                0, 0, 0xffffffff, 0xffffffff,
                0, 0, 0xffffffff, 0xffffffff}), layer, "hd");
        assertEquals("base", result.at(0, 0).slot());
        assertEquals("hd", result.at(1, 0).slot());

        PixelImage inconsistent = new PixelImage(4, 2, new int[]{
                0, 0xffffffff, 0xffffffff, 0xffffffff,
                0, 0, 0xffffffff, 0xffffffff});
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> base.withCutoutLayer(inconsistent, layer, "bad")).getMessage().contains("agree"));
    }

    @Test
    void rejectsNonIntegerImageScalePartialAlphaAndDuplicateSlot() {
        UvMapping base = base(new IntSize(2, 1));
        TextureLayer layer = new TextureLayer(0, id("test:x"), new IntSize(2, 1),
                new IntRect(0, 0, 2, 1), new IntRect(0, 0, 2, 1), false);
        assertThrows(IllegalArgumentException.class,
                () -> base.withCutoutLayer(new PixelImage(3, 1), layer, "new"));
        assertThrows(IllegalArgumentException.class,
                () -> base.withCutoutLayer(new PixelImage(2, 1, new int[]{0x80112233, 0}), layer, "new"));
        assertThrows(IllegalArgumentException.class,
                () -> base.withCutoutLayer(new PixelImage(2, 1), layer, "base"));
    }

    @Test
    void oldOverlayAdapterStillUsesFullTargetIdentity() {
        UvMapping base = base(new IntSize(2, 1));
        UvMapping result = base.withCutoutOverlay(new PixelImage(2, 1, new int[]{0xffabcdef, 0}), "overlay");
        assertEquals("overlay", result.at(0, 0).slot());
        assertEquals(.5, result.at(0, 0).mapU(.5, .5));
        assertEquals("base", result.at(1, 0).slot());
    }

    private static UvMapping base(IntSize size) {
        TargetDefinition target = new TargetDefinition(id("target"), size, Map.of("base", size), List.of(),
                java.util.stream.IntStream.range(0, size.width() * size.height())
                        .mapToObj(i -> new PixelMapping("base", new IntPoint(i % size.width(), i / size.width()),
                                new IntPoint(i % size.width(), i / size.width()))).toList(),
                Optional.empty(), Map.of(), Optional.empty());
        return UvMapping.compile(target);
    }
}
