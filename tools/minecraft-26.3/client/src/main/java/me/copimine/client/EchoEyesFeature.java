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

/**
 * The two presentation marks that make a copy read as the owner, drained.
 *
 * <p>ECHO-003 and the user's requirement are the same thing: the copy wears the
 * owner's real skin, that skin is darkened, and the eyes stay bright. The user
 * additionally asked for violet eyes, so the emissive eye layer is tinted violet
 * instead of drawn at full white.</p>
 *
 * <p>Both passes are submitted here rather than in the renderer because a
 * {@link RenderLayer} can pass a colour tint to
 * {@link SubmitNodeCollector#submitModel}, while the vanilla
 * {@code AvatarRenderer.submit} overload used by the caller cannot. The body
 * tint is additive over an already drawn skin: vanilla has no negative
 * brightness, so a dark translucent pass is what actually reads as "darkened".</p>
 */
public final class EchoEyesFeature extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier EYES = Identifier.fromNamespaceAndPath(
            "copimine", "textures/entity/end_event_echo_eyes.png");

    /** Violet eye glow. Bright enough to read at night, still clearly not white. */
    static final int ECHO_EYE_TINT = 0xFFB27CFF;

    public EchoEyesFeature(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light,
                       AvatarRenderState state, float bodyYaw, float headPitch) {
        if (!(state instanceof EndRiftRenderStateAccess bound) || !bound.copimine$isEcho()) return;
        // Violet emissive eyes first, then the darkened body pass on top so the
        // eye layer is not washed out by its own shadowing.
        collector.submitModel(getParentModel(), state, matrices, RenderTypes.eyes(EYES),
                0x00f000f0, OverlayTexture.NO_OVERLAY, ECHO_EYE_TINT);
        collector.submitModel(getParentModel(), state, matrices,
                RenderTypes.entityTranslucent(state.skin.body().texturePath()),
                light, OverlayTexture.NO_OVERLAY, EchoVanillaRenderer.ECHO_BODY_TINT);
    }
}
