package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Vanilla player pose with a restrained emissive eye overlay for detached echoes. */
public final class EchoEyesFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier EYES = Identifier.fromNamespaceAndPath(
            "copimine", "textures/entity/end_event_echo_eyes.png");

    public EchoEyesFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float bodyYaw, float headPitch) {
        if (!(state instanceof EndRiftRenderStateAccess bound) || !bound.copimine$isEcho()) return;
        collector.submitModel(getParentModel(), state, matrices, RenderTypes.eyes(EYES),
                0x00f000f0, OverlayTexture.NO_OVERLAY, 0xffffffff);
    }
}
