package com.fluorineuck.castedmaterials.client.definition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class DefaultMaterialSpriteLookup {
    private DefaultMaterialSpriteLookup() {
    }

    /** Must be called on the render thread after client models have been baked. */
    public static Optional<ResourceLocation> find(ResourceLocation materialId) {
        return BuiltInRegistries.BLOCK.getOptional(materialId).flatMap(block -> {
            BlockState state = block.defaultBlockState();
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            TextureAtlasSprite particle = model.getParticleIcon(ModelData.EMPTY);
            if (!isMissing(particle)) {
                return Optional.of(particle.contents().name());
            }
            return largestQuadSprite(model, state);
        });
    }

    private static Optional<ResourceLocation> largestQuadSprite(BakedModel model, BlockState state) {
        Map<ResourceLocation, Double> areas = new HashMap<>();
        accumulate(areas, model.getQuads(state, null, RandomSource.create()));
        for (Direction direction : Direction.values()) {
            accumulate(areas, model.getQuads(state, direction, RandomSource.create()));
        }
        return areas.entrySet().stream()
                .min(Map.Entry.<ResourceLocation, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey);
    }

    private static void accumulate(Map<ResourceLocation, Double> areas, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            areas.merge(quad.getSprite().contents().name(), planarArea(quad.getVertices()), Double::sum);
        }
    }

    private static boolean isMissing(TextureAtlasSprite sprite) {
        return sprite == null || MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    private static double planarArea(int[] vertices) {
        if (vertices.length < 32) {
            return 0;
        }
        double[] first = vertex(vertices, 0);
        double[] second = vertex(vertices, 1);
        double[] third = vertex(vertices, 2);
        double[] fourth = vertex(vertices, 3);
        return triangleArea(first, second, third) + triangleArea(first, third, fourth);
    }

    private static double[] vertex(int[] vertices, int index) {
        int base = index * 8;
        return new double[]{
                Float.intBitsToFloat(vertices[base]),
                Float.intBitsToFloat(vertices[base + 1]),
                Float.intBitsToFloat(vertices[base + 2])
        };
    }

    private static double triangleArea(double[] first, double[] second, double[] third) {
        double abX = second[0] - first[0];
        double abY = second[1] - first[1];
        double abZ = second[2] - first[2];
        double acX = third[0] - first[0];
        double acY = third[1] - first[1];
        double acZ = third[2] - first[2];
        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;
        return Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) / 2;
    }
}
