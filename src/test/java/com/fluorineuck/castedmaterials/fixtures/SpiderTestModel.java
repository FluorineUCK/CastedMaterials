package com.fluorineuck.castedmaterials.fixtures;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Test-only copy of the fixed spider geometry from Create Biotech's
 * {@code MachineCreatureModels.spider()}. It intentionally contains no host renderer code.
 */
public final class SpiderTestModel {
    private SpiderTestModel() {
    }

    public static ModelPart create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        box(root, "head", 32, 4, -4, -4, -8, 8, 8, 8, PartPose.offset(0, 15, -3));
        box(root, "body0", 0, 0, -3, -3, -3, 6, 6, 6, PartPose.offset(0, 15, 0));
        box(root, "body1", 0, 12, -5, -4, -6, 10, 8, 12, PartPose.offset(0, 15, 9));
        String[] rows = {"hind_leg", "middle_hind_leg", "middle_front_leg", "front_leg"};
        float[] yaw = {Mth.PI / 4, Mth.PI / 8, -Mth.PI / 8, -Mth.PI / 4};
        for (int row = 0; row < rows.length; row++) {
            float roll = row == 0 || row == 3 ? Mth.PI / 4 : 0.58119464f;
            root.addOrReplaceChild("right_" + rows[row],
                    CubeListBuilder.create().texOffs(18, 0).addBox(-15, -1, -1, 16, 2, 2),
                    PartPose.offsetAndRotation(-4, 15, 2 - row, 0, yaw[row], -roll));
            root.addOrReplaceChild("left_" + rows[row],
                    CubeListBuilder.create().texOffs(18, 0).mirror().addBox(-1, -1, -1, 16, 2, 2),
                    PartPose.offsetAndRotation(4, 15, 2 - row, 0, -yaw[row], roll));
        }
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    private static PartDefinition box(PartDefinition parent, String name, int u, int v,
                                      float x, float y, float z, float width, float height, float depth,
                                      PartPose pose) {
        return parent.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, width, height, depth), pose);
    }
}
