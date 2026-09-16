package me.copimine.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The event Enderman geometry is separate from vanilla's model.  The base
 * humanoid parts keep the renderer/feature contract, while the rift shell,
 * core, and variant crest are actual additional model parts rather than a
 * texture-only override.
 */
public final class RiftEventEndermanModel extends EndermanEntityModel<EndermanEntity> {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;

    private final ModelPart root;
    private final ModelPart riftCore;
    private final ModelPart riftShell;
    private final ModelPart variantCrest;
    private final boolean elite;

    public RiftEventEndermanModel(ModelPart root, boolean elite) {
        super(root);
        this.root = root;
        this.riftCore = root.getChild("rift_core");
        this.riftShell = root.getChild("rift_shell");
        this.variantCrest = root.getChild("variant_crest");
        this.elite = elite;
    }

    public static TexturedModelData getTexturedModelData(boolean elite) {
        ModelData data = BipedEntityModel.getModelData(Dilation.NONE, -14.0F);
        ModelPartData root = data.getRoot();
        root.addChild("rift_core", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-2.5F, -5.0F, -3.0F, 5.0F, 5.0F, 2.0F),
                ModelTransform.pivot(0.0F, -8.0F, -2.25F));
        root.addChild("rift_shell", ModelPartBuilder.create()
                        .uv(16, 0).cuboid(-4.5F, -1.0F, -2.5F, 9.0F, 3.0F, 5.0F),
                ModelTransform.pivot(0.0F, -10.0F, 0.0F));
        root.addChild("variant_crest", ModelPartBuilder.create()
                        .uv(32, 0).cuboid(-3.5F, -11.0F, -1.0F, 7.0F, elite ? 4.0F : 2.0F, 2.0F),
                ModelTransform.pivot(0.0F, -14.0F, 0.0F));
        return TexturedModelData.of(data, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void setAngles(EndermanEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        root.traverse().forEach(ModelPart::resetTransform);
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        float pulse = MathHelper.sin(animationProgress * 0.16F);
        riftCore.yScale = 1.0F + pulse * (elite ? 0.16F : 0.10F);
        riftCore.xScale = 1.0F + pulse * 0.06F;
        riftCore.zScale = 1.0F + pulse * 0.06F;
        riftShell.pitch = pulse * 0.035F;
        riftShell.yaw = pulse * (elite ? 0.10F : 0.06F);
        variantCrest.pitch = pulse * (elite ? 0.16F : 0.08F);
    }

    public boolean isElite() {
        return elite;
    }

    public ModelPart getPart() {
        return root;
    }
}
