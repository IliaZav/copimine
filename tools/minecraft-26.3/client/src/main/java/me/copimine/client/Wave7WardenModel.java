package me.copimine.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.util.Mth;

/** Named trial Warden rig with a rift crown, chest plate, and its own idle motion. */
public final class Wave7WardenModel extends WardenModel {
    private final ModelPart riftCrown;
    private final ModelPart chestPlate;
    private final ModelPart backSpines;

    public Wave7WardenModel(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("bone").getChild("body");
        this.riftCrown = body.getChild("head").getChild("rift_crown");
        this.chestPlate = body.getChild("rift_chest_plate");
        this.backSpines = body.getChild("rift_back_spines");
    }

    public static LayerDefinition createLayer() {
        return WardenModel.createBodyLayer().apply(mesh -> {
            ModelPartParts.addWardenDetails(mesh.getRoot());
            return mesh;
        });
    }

    @Override
    public void setupAnim(WardenRenderState state) {
        super.setupAnim(state);
        float pulse = Mth.sin(state.ageInTicks * 0.075F);
        riftCrown.yRot = pulse * 0.035F;
        chestPlate.yScale = 1.0F + Math.abs(pulse) * 0.055F;
        backSpines.zRot = pulse * 0.025F;
    }

    private static final class ModelPartParts {
        private ModelPartParts() {
        }

        private static void addWardenDetails(net.minecraft.client.model.geom.builders.PartDefinition root) {
            var body = root.getChild("bone").getChild("body");
            body.getChild("head").addOrReplaceChild("rift_crown",
                    CubeListBuilder.create().texOffs(72, 0)
                            .addBox(-4.0F, -20.0F, -1.5F, 8.0F, 4.0F, 3.0F),
                    PartPose.ZERO);
            body.addOrReplaceChild("rift_chest_plate",
                    CubeListBuilder.create().texOffs(72, 12)
                            .addBox(-5.0F, -10.0F, -4.8F, 10.0F, 7.0F, 1.0F),
                    PartPose.ZERO);
            body.addOrReplaceChild("rift_back_spines",
                    CubeListBuilder.create().texOffs(96, 0)
                            .addBox(-4.0F, -8.0F, 4.0F, 8.0F, 10.0F, 2.0F),
                    PartPose.ZERO);
        }
    }
}
