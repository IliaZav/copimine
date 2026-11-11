package me.copimine.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.endermite.EndermiteModel;

/** Ram brood: articulated carapace and crystal spines using the existing event skin. */
public final class RiftEndermiteModel extends EndermiteModel {
    public RiftEndermiteModel(ModelPart root) {
        super(root);
        EndRiftAtlasUvLayout.fit(root, "end_rift_endermite.png");
    }

    public static LayerDefinition createLayer() {
        return EndermiteModel.createBodyLayer().apply(mesh -> {
            for (int index = 0; index < 4; index++) {
                var segment = mesh.getRoot().getChild("segment" + index);
                segment.addOrReplaceChild("rift_carapace", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.25F, -0.5F, -1.6F, 4.5F, 1.2F, 3.2F), PartPose.ZERO);
                segment.addOrReplaceChild("rift_spine", CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.6F, -3.0F, -0.6F, 1.2F, 3.0F, 1.2F),
                        PartPose.rotation(-0.25F, 0, 0));
            }
            return mesh;
        });
    }
}
