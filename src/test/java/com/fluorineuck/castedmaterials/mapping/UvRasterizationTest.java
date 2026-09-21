package com.fluorineuck.castedmaterials.mapping;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class UvRasterizationTest {
    private static UvMapping rotated() {
        return UvMapping.compile(DefinitionParser.parseTarget(id("rotated"), """
            {"schema":1,"size":[3,2],"source_grids":{"all":[2,2]},
             "regions":[{"source_slot":"all","source":[0,0,2,2],"destination":[0,0,2,2],"transform":"rotate_90"}],
             "pixels":[{"source_slot":"all","source":[1,0],"destination":[2,1]}]}
            """));
    }
    @Test void rasterizesRotatedMulticolorAndSparseCellsWithoutFillingHoles() {
        var source = new PixelImage(2,2,new int[]{0xff112233,0xff445566,0xff778899,0xffaabbcc});
        assertArrayEquals(new int[]{0xff778899,0xff112233,0,0xffaabbcc,0xff445566,0xff445566},
                rotated().rasterize(Map.of("all",source),Set.of("all")).pixels());
    }
    @Test void emissionUsesOnlySurvivingEyeCellsAfterHigherPriorityReplacement() {
        var source = new PixelImage(2,2,new int[]{1,2,3,4});
        var eye = new PixelImage(3,2,new int[]{0xffff0000,0xff00ff00,0,0,0,0});
        var cover = new PixelImage(3,2,new int[]{0xff0000ff,0,0,0,0,0});
        var mapping = rotated().withCutoutOverlay(eye,"eye").withCutoutOverlay(cover,"cover");
        var images = Map.of("all",source,"eye",eye,"cover",cover);
        assertArrayEquals(new int[]{0,0xff00ff00,0,0,0,0},mapping.rasterize(images,Set.of("eye")).pixels());
        assertEquals(0xff0000ff,mapping.rasterize(images,images.keySet()).get(0,0));
    }
    @Test void rejectsMissingAndHighResolutionSourcesInsteadOfSilentlyDownsampling() {
        assertThrows(IllegalArgumentException.class,() -> rotated().rasterize(Map.of(),Set.of("all")));
        assertThrows(IllegalArgumentException.class,() -> rotated().rasterize(Map.of("all",new PixelImage(4,4)),Set.of("all")));
    }
    @Test void rejectsLayerResamplingInsteadOfChangingSubTexelColors() {
        var mapping=rotated();
        var rect=new TargetDefinition.IntRect(0,0,2,2);
        var layer=new TargetDefinition.TextureLayer(0,id("zoom"),new IntSize(2,2),rect,
                new TargetDefinition.IntRect(0,0,1,1),false);
        var image=new PixelImage(2,2,new int[]{-1,-2,-3,-4});
        var scaled=mapping.withCutoutLayer(image,layer,"zoom");
        assertThrows(IllegalArgumentException.class,() -> scaled.rasterize(Map.of("zoom",image),Set.of("zoom")));
    }
}
