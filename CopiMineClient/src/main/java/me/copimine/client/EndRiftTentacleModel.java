package me.copimine.client;

import java.util.List;
import java.util.Locale;

/**
 * Client-side pose contract for the End Rift tentacle display.
 *
 * The server decides whether a grab connects and owns the marker timings.
 * This class only turns the server-selected animation state into a smooth,
 * deterministic pose for the visual layer.
 */
public final class EndRiftTentacleModel {
    public static final String VISUAL_ID = "END_RIFT_TENTACLE_V1";
    public static final List<String> REQUIRED_BONES = List.of(
            "root", "base", "seg_01", "seg_02", "seg_03", "seg_04",
            "tip", "tip_claw_1", "tip_claw_2", "tip_claw_3", "grab_socket");

    private EndRiftTentacleModel() {
    }

    public static boolean supportsAnimation(String animationId) {
        String normalized = normalize(animationId);
        return switch (normalized) {
            case "IDLE", "EMERGE", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD",
                    "THROW", "GRAB_MISS", "HURT", "DEATH", "RETRACT",
                    "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL" -> true;
            default -> false;
        };
    }

    public static Pose pose(String animationId, float progress) {
        String animation = normalize(animationId);
        float p = clamp(progress);
        float eased = p * p * (3.0F - 2.0F * p);
        return switch (animation) {
            case "EMERGE", "SPAWN_UNDER_PLAYER" ->
                    new Pose(0.0F, 0.0F, 0.0F, 0.08F + eased * 0.92F, 0.20F);
            case "TELEGRAPH_GRAB" ->
                    new Pose(-0.18F * eased, 0.10F * eased, -0.28F * eased, 1.0F, 0.25F);
            case "GRAB_SUCCESS" ->
                    new Pose(-0.34F + 0.18F * eased, 0.16F, -0.55F + 0.20F * eased, 1.0F, 0.95F);
            case "HOLD" ->
                    new Pose(-0.16F, 0.08F, -0.22F, 1.0F, 0.58F + 0.08F * (float) Math.sin(p * Math.PI * 2.0F));
            case "THROW" ->
                    new Pose(0.30F * eased, -0.12F * eased, 0.72F * eased, 1.0F, 0.95F - 0.80F * eased);
            case "GRAB_MISS" ->
                    new Pose(0.22F * eased, -0.26F * eased, 0.86F * eased, 1.0F, 0.95F - 0.55F * eased);
            case "HURT" ->
                    new Pose(0.0F, 0.0F, (float) Math.sin(p * Math.PI * 4.0F) * 0.16F, 1.0F, 0.30F);
            case "DEATH", "RETRACT" ->
                    new Pose(0.12F * eased, 0.0F, 0.12F * eased, 1.0F - 0.92F * eased, 0.30F);
            case "SHIELD_CHANNEL" ->
                    new Pose(0.0F, 0.0F, (float) Math.sin(p * Math.PI * 2.0F) * 0.08F, 1.0F, 0.38F);
            case "IDLE" ->
                    new Pose(0.0F, 0.0F, (float) Math.sin(p * Math.PI * 2.0F) * 0.05F, 1.0F, 0.34F);
            default -> pose("IDLE", 0.0F);
        };
    }

    private static String normalize(String animationId) {
        return animationId == null || animationId.isBlank()
                ? "IDLE"
                : animationId.trim().toUpperCase(Locale.ROOT);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    public record Pose(float bendX, float bendZ, float tipYaw,
                       float scaleY, float clawOpen) {
        public boolean isFinite() {
            return Float.isFinite(bendX) && Float.isFinite(bendZ)
                    && Float.isFinite(tipYaw) && Float.isFinite(scaleY)
                    && Float.isFinite(clawOpen);
        }
    }
}
