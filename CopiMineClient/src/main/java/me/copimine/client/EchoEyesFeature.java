package me.copimine.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** The vanilla head/UV/pose, with a tiny event-only emissive eye mask. */
public final class EchoEyesFeature extends FeatureRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier EYES = Identifier.of("copimine", "textures/entity/end_event_echo_eyes.png");
    public EchoEyesFeature(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> context) { super(context); }
    @Override public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                 AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
                                 float delta, float animationProgress, float headYaw, float headPitch) {
        if (!(player instanceof EchoPlayerView)) return;
        getContextModel().head.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)),
                0xf000f0, OverlayTexture.DEFAULT_UV);
    }
}
