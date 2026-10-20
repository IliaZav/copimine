package me.copimine.client;

import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;

import java.util.Locale;

/** Exact, UUID-bound visual dispatch for the three named Wave 7 trial actors. */
public final class Wave7NamedTrialVisualPolicy {
    public enum ActorModel {
        WARDEN,
        ARCHMAGE,
        MARKSMAN
    }

    public static final String WARDEN_VISUAL = "END_RIFT_WAVE7_WARDEN_V1";
    public static final String ARCHMAGE_VISUAL = "END_RIFT_WAVE7_ARCHMAGE_V1";
    public static final String MARKSMAN_VISUAL = "END_RIFT_WAVE7_MARKSMAN_V1";

    private Wave7NamedTrialVisualPolicy() {
    }

    public static ActorModel modelFor(String visualId) {
        if (visualId == null || visualId.isBlank()) return null;
        return switch (visualId.trim().toUpperCase(Locale.ROOT)) {
            case WARDEN_VISUAL -> ActorModel.WARDEN;
            case ARCHMAGE_VISUAL -> ActorModel.ARCHMAGE;
            case MARKSMAN_VISUAL -> ActorModel.MARKSMAN;
            default -> null;
        };
    }

    /** Prevents a valid actor visual from being applied to a different mob renderer. */
    public static boolean matchesRenderState(String visualId, Class<?> renderStateType) {
        ActorModel model = modelFor(visualId);
        if (model == null || renderStateType == null) return false;
        return switch (model) {
            case WARDEN -> WardenRenderState.class.isAssignableFrom(renderStateType);
            case ARCHMAGE -> EvokerRenderState.class.isAssignableFrom(renderStateType);
            case MARKSMAN -> SkeletonRenderState.class.isAssignableFrom(renderStateType);
        };
    }
}
