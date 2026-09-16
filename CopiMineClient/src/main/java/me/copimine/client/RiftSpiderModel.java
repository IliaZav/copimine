package me.copimine.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Adapted event spider rig. The supplied archive contains the 64x32 spider
 * skin but no spider geometry, so the vanilla spider skeleton is kept as the
 * gameplay-compatible base and the event shell/core/spines are added as real
 * model parts instead of leaving the spider on the vanilla renderer.
 */
public final class RiftSpiderModel extends SpiderEntityModel<SpiderEntity> {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 32;

    public enum Variant {
        ORDINARY,
        ELITE,
        WAVE_GUARDIAN,
        RITUAL_GUARD
    }

    private final ModelPart root;
    private final ModelPart riftCore;
    private final ModelPart riftShell;
    private final ModelPart riftSpines;
    private final ModelPart eliteCarapace;
    private final ModelPart guardianSpine;
    private final ModelPart guardSeal;
    private final ModelPart ritualFocus;
    private final Variant variant;

    public RiftSpiderModel(ModelPart root) {
        this(root, Variant.ORDINARY);
    }

    public RiftSpiderModel(ModelPart root, Variant variant) {
        super(root);
        this.root = root;
        this.riftCore = root.getChild("rift_core");
        this.riftShell = root.getChild("rift_shell");
        this.riftSpines = root.getChild("rift_spines");
        this.eliteCarapace = root.getChild("elite_carapace");
        this.guardianSpine = root.getChild("guardian_spine");
        this.guardSeal = root.getChild("guard_seal");
        this.ritualFocus = root.getChild("ritual_focus");
        this.variant = variant;
        this.eliteCarapace.visible = variant != Variant.ORDINARY;
        this.guardianSpine.visible = variant == Variant.WAVE_GUARDIAN;
        this.guardSeal.visible = variant == Variant.RITUAL_GUARD;
        this.ritualFocus.visible = variant == Variant.RITUAL_GUARD;
    }

    public static TexturedModelData getTexturedModelData() {
        return getTexturedModelData(Variant.ORDINARY);
    }

    public static TexturedModelData getTexturedModelData(Variant variant) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Vanilla-compatible head/body and eight articulated legs.
        root.addChild("head", cube(32, 4, -4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F),
                ModelTransform.pivot(0.0F, 15.0F, -3.0F));
        root.addChild("body", cube(0, 0, -3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                ModelTransform.pivot(0.0F, 15.0F, 0.0F));
        root.addChild("body1", cube(0, 12, -5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F),
                ModelTransform.pivot(0.0F, 15.0F, 9.0F));

        addLeg(root, "right_hind_leg", -4.0F, 15.0F, 2.0F, false);
        addLeg(root, "left_hind_leg", 4.0F, 15.0F, 2.0F, true);
        addLeg(root, "right_middle_hind_leg", -4.0F, 15.0F, 1.0F, false);
        addLeg(root, "left_middle_hind_leg", 4.0F, 15.0F, 1.0F, true);
        addLeg(root, "right_middle_front_leg", -4.0F, 15.0F, 0.0F, false);
        addLeg(root, "left_middle_front_leg", 4.0F, 15.0F, 0.0F, true);
        addLeg(root, "right_front_leg", -4.0F, 15.0F, -1.0F, false);
        addLeg(root, "left_front_leg", 4.0F, 15.0F, -1.0F, true);

        // The user supplied archive has no spider mesh. These additional
        // solid parts make the adapted event silhouette visible while still
        // using the supplied spider texture atlas.
        root.addChild("rift_core", cube(32, 16, -3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
                ModelTransform.pivot(0.0F, 15.0F, 3.0F));
        root.addChild("rift_shell", cube(0, 16, -4.5F, -3.0F, -5.0F, 9.0F, 5.0F, 10.0F),
                ModelTransform.pivot(0.0F, 15.0F, 7.0F));
        root.addChild("rift_spines", ModelPartBuilder.create()
                        .uv(48, 16).cuboid(-1.0F, -7.0F, -1.0F, 2.0F, 4.0F, 2.0F)
                        .uv(48, 22).cuboid(-1.0F, -5.0F, 2.0F, 2.0F, 3.0F, 2.0F)
                        .uv(56, 16).cuboid(-1.0F, -5.0F, -4.0F, 2.0F, 3.0F, 2.0F),
                ModelTransform.pivot(0.0F, 15.0F, 6.0F));

        root.addChild("elite_carapace", ModelPartBuilder.create()
                        .uv(0, 24).cuboid(-5.5F, -4.2F, -6.5F, 11.0F, 2.2F, 13.0F)
                        .uv(0, 24).cuboid(-6.2F, -2.4F, -4.8F, 1.6F, 2.2F, 8.0F)
                        .uv(0, 24).cuboid(4.6F, -2.4F, -4.8F, 1.6F, 2.2F, 8.0F),
                ModelTransform.pivot(0.0F, 15.0F, 7.0F));
        root.addChild("guardian_spine", ModelPartBuilder.create()
                        .uv(24, 24).cuboid(-1.0F, -7.0F, 5.8F, 2.0F, 3.0F, 2.0F)
                        .uv(24, 29).cuboid(-1.2F, -4.6F, 6.0F, 2.4F, 3.2F, 1.6F)
                        .uv(32, 24).cuboid(-1.4F, -2.0F, 6.1F, 2.8F, 2.8F, 1.4F),
                ModelTransform.pivot(0.0F, 15.0F, 4.0F));
        root.addChild("guard_seal", ModelPartBuilder.create()
                        .uv(38, 24).cuboid(-2.4F, -5.4F, -8.15F, 4.8F, 3.8F, 0.45F)
                        .uv(38, 29).cuboid(-1.1F, -6.6F, -8.25F, 2.2F, 1.2F, 0.35F),
                ModelTransform.pivot(0.0F, 15.0F, -2.0F));
        root.addChild("ritual_focus", ModelPartBuilder.create()
                        .uv(46, 24).cuboid(-1.5F, -1.5F, -8.6F, 3.0F, 3.0F, 1.0F)
                        .uv(54, 24).cuboid(-0.6F, -2.5F, -8.45F, 1.2F, 1.0F, 0.7F),
                ModelTransform.pivot(0.0F, 15.0F, 1.0F));

        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static void addLeg(ModelPartData root, String name, float x, float y,
                               float z, boolean mirrored) {
        root.addChild(name, ModelPartBuilder.create().uv(18, 0)
                        .mirrored(mirrored).cuboid(mirrored ? -2.0F : -14.0F,
                                -1.0F, -1.0F, 16.0F, 2.0F, 2.0F),
                ModelTransform.pivot(x, y, z));
    }

    private static ModelPartBuilder cube(int u, int v, float x, float y, float z,
                                         float width, float height, float depth) {
        return ModelPartBuilder.create().uv(u, v).cuboid(x, y, z, width, height, depth);
    }

    @Override
    public void setAngles(SpiderEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        // Display entities reuse the same model instance. Reset the added
        // parts before vanilla applies its deterministic spider leg pose.
        root.traverse().forEach(ModelPart::resetTransform);
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float pulse = MathHelper.sin(animationProgress * 0.16F);
        riftCore.yScale = 1.0F + pulse * 0.10F;
        riftCore.xScale = 1.0F + pulse * 0.06F;
        riftCore.zScale = 1.0F + pulse * 0.06F;
        riftShell.roll = pulse * 0.035F;
        riftShell.yaw = pulse * 0.045F;
        riftSpines.pitch = pulse * 0.10F;
        riftSpines.yaw = pulse * 0.08F;
        eliteCarapace.roll = pulse * (variant == Variant.ELITE ? 0.04F : 0.025F);
        eliteCarapace.yaw = pulse * 0.025F;
        guardianSpine.pitch = pulse * 0.08F;
        guardianSpine.yaw = pulse * 0.11F;
        guardSeal.yaw = pulse * 0.12F;
        ritualFocus.yScale = 1.0F + pulse * 0.16F;
    }

    public Variant variant() {
        return variant;
    }

    public ModelPart getPart() {
        return root;
    }
}
