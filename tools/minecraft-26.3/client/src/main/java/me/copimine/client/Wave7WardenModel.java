package me.copimine.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.util.Mth;

/**
 * Named trial Warden rig: the vanilla colossus plus a rift crown, a chest
 * plate, back spines and shoulder shards.
 *
 * <p>The additions are deliberately read as armour bolted onto the Warden
 * rather than as another creature, because the trial must still be instantly
 * recognisable as "the big one". Every added part is baked into the vanilla
 * head/body so it inherits the Warden's own head tracking and breathing — a
 * part parented to the root would stay fixed while the creature turned.</p>
 */
public final class Wave7WardenModel extends WardenModel {
    private final ModelPart riftCrown;
    private final ModelPart chestPlate;
    private final ModelPart backSpines;
    private final ModelPart leftShard;
    private final ModelPart rightShard;
    private final ModelPart riftCore;

    public Wave7WardenModel(ModelPart root) {
        super(root);
        EndRiftAtlasUvLayout.fit(root, "end_rift_wave7_warden.png");
        ModelPart body = root.getChild("bone").getChild("body");
        this.riftCrown = body.getChild("head").getChild("rift_crown");
        this.chestPlate = body.getChild("rift_chest_plate");
        this.backSpines = body.getChild("rift_back_spines");
        this.leftShard = body.getChild("rift_shard_left");
        this.rightShard = body.getChild("rift_shard_right");
        this.riftCore = body.getChild("rift_core");
    }

    public static LayerDefinition createLayer() {
        return WardenModel.createBodyLayer().apply(mesh -> {
            ModelPartParts.addWardenDetails(mesh.getRoot());
            return mesh;
        });
    }

    /**
     * Animation is layered so the additions never fight the vanilla motion.
     *
     * <p>The Warden's own rig already owns head tracking, arm swing and the
     * roar; the rift pieces only breathe against it. Because each added part is
     * a child of the vanilla body or head, the offsets below are small local
     * adjustments on top of that motion, which is what makes the armour look
     * attached instead of hovering.</p>
     */
    @Override
    public void setupAnim(WardenRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        float pulse = Mth.sin(age * 0.075F);
        float breath = Mth.cos(age * 0.038F);
        riftCrown.yRot = pulse * 0.035F;
        riftCrown.xRot = breath * 0.018F;
        chestPlate.yScale = 1.0F + Math.abs(pulse) * 0.055F;
        chestPlate.xRot = breath * 0.012F;
        backSpines.zRot = pulse * 0.025F;
        backSpines.xRot = -0.04F + breath * 0.02F;
        // The shards fan outward on the breath and pull back on the beat, which
        // reads as a living mantle rather than a static prop.
        leftShard.zRot = -0.10F - Math.abs(pulse) * 0.07F;
        rightShard.zRot = 0.10F + Math.abs(pulse) * 0.07F;
        // Attack intent. The vanilla flags below only fire when the mob's own AI
        // plays a swing, and the trial Warden's AI is off -- its swings are
        // resolved by the plugin -- so they were permanently false and the core
        // never told the player anything. The wave pose is the authoritative
        // signal: it is leased from the same action field the damage uses.
        String wavePose = ((EndRiftRenderStateAccess) state).copimine$wavePose();
        boolean winding = "WAVE_WINDUP".equals(wavePose) || "WAVE_FROZEN".equals(wavePose);
        boolean releasing = "WAVE_RELEASE".equals(wavePose);
        boolean recovering = "WAVE_RECOVER".equals(wavePose);
        boolean charging = winding || releasing
                || state.attackAnimationState.isStarted()
                || state.sonicBoomAnimationState.isStarted();
        float charge = charging ? 1.0F : 0.0F;
        riftCore.yScale = 1.0F + Math.abs(pulse) * 0.10F + charge * 0.22F;
        riftCore.xScale = 1.0F + Math.abs(pulse) * 0.06F + charge * 0.14F;
        riftCore.zScale = riftCore.yScale;
        if (winding) {
            // Coil: the Warden rocks back onto its heels and lifts the swinging
            // arm, and the shards spread wide so the silhouette itself reads as
            // "something is coming" even before the warning particles land.
            float coil = 0.10F + Mth.sin(age * 0.30F) * 0.02F;
            body.xRot -= coil;
            head.xRot += coil * 0.6F;
            leftArm.xRot -= 0.55F;
            rightArm.xRot -= 0.55F;
            leftShard.zRot -= 0.22F;
            rightShard.zRot += 0.22F;
            backSpines.xRot -= 0.10F;
        } else if (releasing) {
            // Commit: the whole body drives forward through the swing.
            body.xRot += 0.16F;
            head.xRot += 0.10F;
            leftArm.xRot += 0.85F;
            rightArm.xRot += 0.85F;
            leftShard.zRot += 0.10F;
            rightShard.zRot -= 0.10F;
            chestPlate.yScale += 0.08F;
        } else if (recovering) {
            // Open: the follow-through leaves the guard down, which is the
            // window the party is meant to punish.
            float slump = 0.06F + Mth.sin(age * 0.16F) * 0.015F;
            body.xRot += slump;
            head.xRot += slump;
            leftArm.xRot += 0.28F;
            rightArm.xRot += 0.28F;
            leftShard.zRot += 0.06F;
            rightShard.zRot -= 0.06F;
        }
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
            body.addOrReplaceChild("rift_shard_left",
                    CubeListBuilder.create().texOffs(96, 16)
                            .addBox(5.0F, -9.0F, -2.0F, 2.0F, 8.0F, 4.0F),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.10F));
            body.addOrReplaceChild("rift_shard_right",
                    CubeListBuilder.create().texOffs(110, 16)
                            .addBox(-7.0F, -9.0F, -2.0F, 2.0F, 8.0F, 4.0F),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.10F));
            body.addOrReplaceChild("rift_core",
                    CubeListBuilder.create().texOffs(72, 24)
                            .addBox(-2.0F, -6.0F, -5.6F, 4.0F, 4.0F, 1.0F),
                    PartPose.ZERO);
        }
    }
}
