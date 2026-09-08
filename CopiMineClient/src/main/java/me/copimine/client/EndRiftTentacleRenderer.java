package me.copimine.client;

import net.minecraft.util.Identifier;

import java.util.Locale;

/** Visual adapter for the server-owned tentacle animation state. */
public final class EndRiftTentacleRenderer {
    private static final Identifier TEXTURE = Identifier.of(
            "copimineclient", "textures/entity/end_rift_tentacle_hd.png");

    private EndRiftTentacleRenderer() {
    }

    public static Identifier textureForVisual(String visualId) {
        return EndRiftTentacleModel.VISUAL_ID.equals(normalize(visualId)) ? TEXTURE : null;
    }

    public static EndRiftTentacleModel.Pose poseFor(String visualId,
                                                    String animationId,
                                                    long elapsedTicks) {
        if (!EndRiftTentacleModel.VISUAL_ID.equals(normalize(visualId))) {
            return EndRiftTentacleModel.pose("IDLE", 0.0F);
        }
        int duration = durationTicks(animationId);
        long safeElapsed = Math.max(0L, elapsedTicks);
        float progress = EndRiftTentacleModel.supportsAnimation(animationId)
                ? (float) ((safeElapsed % duration) / (double) duration)
                : 0.0F;
        return EndRiftTentacleModel.pose(animationId, progress);
    }

    private static int durationTicks(String animationId) {
        return switch (normalize(animationId)) {
            case "IDLE" -> 60;
            case "EMERGE", "SPAWN_UNDER_PLAYER" -> 18;
            case "TELEGRAPH_GRAB" -> 20;
            case "GRAB_SUCCESS", "GRAB_MISS" -> 14;
            case "HOLD", "SHIELD_CHANNEL" -> 40;
            case "THROW" -> 12;
            case "HURT" -> 7;
            case "DEATH" -> 20;
            case "RETRACT" -> 16;
            default -> 60;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
