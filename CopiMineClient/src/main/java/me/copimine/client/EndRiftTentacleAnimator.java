package me.copimine.client;

import java.util.Locale;
import java.util.Set;

/**
 * Pure deterministic animation math for the articulated End Rift tentacle.
 * It has no world, entity or network access, so it can be exercised in unit
 * tests and cannot become a second server-side gameplay authority.
 */
public final class EndRiftTentacleAnimator {
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final float[] PHASE_OFFSETS = {0.0F, -0.20F, -0.38F, -0.55F, -0.70F};
    private static final Set<String> STATES = Set.of(
            "EMERGING", "READY", "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD", "THROW",
            "MISS_RECOVERY", "HIT_RECOVERY", "DYING", "DEAD_RESPAWN", "RETRACT",
            "SPAWN_UNDER_PLAYER", "SHIELD_CHANNEL", "RECOVERY");

    private EndRiftTentacleAnimator() {
    }

    public static boolean supportsAnimation(String animationId) {
        return STATES.contains(normalize(animationId));
    }

    public static boolean loops(String animationId) {
        return switch (normalize(animationId)) {
            case "READY", "HOLD", "SHIELD_CHANNEL" -> true;
            default -> false;
        };
    }

    public static int durationTicks(String animationId) {
        return switch (normalize(animationId)) {
            case "READY" -> 60;
            case "EMERGING" -> 18;
            case "TELEGRAPH_GRAB" -> 20;
            case "GRAB_SUCCESS" -> 14;
            case "HOLD" -> 20;
            case "THROW" -> 12;
            case "MISS_RECOVERY" -> 14;
            case "HIT_RECOVERY" -> 7;
            case "DYING" -> 20;
            case "DEAD_RESPAWN" -> 40;
            case "RETRACT" -> 16;
            case "SPAWN_UNDER_PLAYER" -> 7;
            case "SHIELD_CHANNEL" -> 40;
            case "RECOVERY" -> 16;
            default -> 60;
        };
    }

    public static EndRiftTentaclePose.TentaclePose poseFor(String animationId,
                                                              float progress,
                                                              long deterministicSeed) {
        String animation = normalize(animationId);
        float p = clamp(progress);
        float smooth = smoothStep(p);
        float phase = phaseFor(deterministicSeed);
        float cycle = loops(animation) ? p * TWO_PI : p * (float) Math.PI;

        float rootOffset = switch (animation) {
            case "EMERGING" -> -4.75F + smooth * 4.75F;
            case "SPAWN_UNDER_PLAYER" -> -1.20F + smooth * 1.20F;
            case "RETRACT" -> -smooth * 1.20F;
            case "DYING" -> -smooth * 0.85F;
            case "DEAD_RESPAWN" -> -1.20F;
            default -> 0.0F;
        };
        float rootScale = animation.equals("SPAWN_UNDER_PLAYER") ? 0.93F : 1.0F;
        EndRiftTentaclePose.BoneTransform root = bone(0.0F, rootOffset * 16.0F, 0.0F,
                animation.equals("DYING") ? smooth * 0.10F : 0.0F, 0.0F,
                animation.equals("DYING") ? smooth * 0.20F : 0.0F,
                rootScale, rootScale, rootScale);

        float stateIntensity = switch (animation) {
            case "TELEGRAPH_GRAB" -> smooth;
            case "GRAB_SUCCESS" -> 0.72F + smooth * 0.28F;
            case "HOLD" -> 0.48F;
            case "THROW" -> 1.0F - smooth;
            case "MISS_RECOVERY" -> smooth;
            case "HIT_RECOVERY" -> 1.0F - smooth;
            case "RECOVERY" -> 1.0F - smooth;
            default -> 0.0F;
        };
        float bendDirection = animation.equals("THROW") || animation.equals("MISS_RECOVERY") ? -1.0F : 1.0F;

        EndRiftTentaclePose.BoneTransform base = segment(0, cycle, phase, stateIntensity,
                bendDirection, animation);
        EndRiftTentaclePose.BoneTransform seg01 = segment(1, cycle, phase, stateIntensity,
                bendDirection, animation);
        EndRiftTentaclePose.BoneTransform seg02 = segment(2, cycle, phase, stateIntensity,
                bendDirection, animation);
        EndRiftTentaclePose.BoneTransform seg03 = segment(3, cycle, phase, stateIntensity,
                bendDirection, animation);
        EndRiftTentaclePose.BoneTransform seg04 = segment(4, cycle, phase, stateIntensity,
                bendDirection, animation);
        EndRiftTentaclePose.BoneTransform seg05 = segment(5, cycle, phase, stateIntensity,
                bendDirection, animation);
        EndRiftTentaclePose.BoneTransform tip = tip(cycle, phase, stateIntensity, animation);

        float clawOpen = switch (animation) {
            case "TELEGRAPH_GRAB" -> 1.0F - smooth * 0.10F;
            case "GRAB_SUCCESS" -> 0.90F - smooth * 0.78F;
            case "HOLD" -> 0.12F + (float) Math.sin(cycle * 2.0F) * 0.025F;
            case "THROW" -> 0.12F + smooth * 0.88F;
            case "MISS_RECOVERY" -> 0.20F + smooth * 0.78F;
            case "DYING", "RETRACT" -> 0.25F;
            default -> 0.28F + (float) Math.sin(cycle) * 0.06F;
        };
        EndRiftTentaclePose.BoneTransform claw1 = claw(1, clawOpen, phase, animation);
        EndRiftTentaclePose.BoneTransform claw2 = claw(2, clawOpen, phase, animation);
        EndRiftTentaclePose.BoneTransform claw3 = claw(3, clawOpen, phase, animation);
        EndRiftTentaclePose.BoneTransform claw4 = claw(4, clawOpen, phase, animation);

        float socketY = switch (animation) {
            // During a real grab the upper chain curls down around the
            // server-authoritative player anchor, rather than leaving the
            // player floating at the neutral five-block tip height.
            case "GRAB_SUCCESS", "HOLD" -> 2.25F;
            case "THROW" -> 2.25F + smooth * 2.50F;
            default -> 4.75F;
        };
        EndRiftTentaclePose.Socket socket = new EndRiftTentaclePose.Socket(
                0.0F, socketY, animation.equals("TELEGRAPH_GRAB") ? 0.08F * smooth : 0.0F);
        return new EndRiftTentaclePose.TentaclePose(root, base, seg01, seg02, seg03,
                seg04, seg05, tip, claw1, claw2, claw3, claw4, socket, rootScale);
    }

    public static float normalizedProgress(String animationId, long elapsedTicks) {
        int duration = Math.max(1, durationTicks(animationId));
        long safeElapsed = Math.max(0L, elapsedTicks);
        if (loops(animationId)) {
            return (float) ((safeElapsed % duration) / (double) duration);
        }
        return clamp(safeElapsed / (float) duration);
    }

    private static EndRiftTentaclePose.BoneTransform segment(int index, float cycle,
                                                               float phase, float intensity,
                                                               float direction, String animation) {
        float normalized = Math.min(1.0F, index / 5.0F);
        float localPhase = phase + PHASE_OFFSETS[Math.min(index, PHASE_OFFSETS.length - 1)];
        float sway = (float) Math.sin(cycle + localPhase) * (0.035F + normalized * 0.11F);
        float roll = (float) Math.cos(cycle * 0.83F + localPhase) * (0.02F + normalized * 0.07F);
        float pitch = sway;
        float yaw = roll * 0.72F;
        if (animation.equals("EMERGING") || animation.equals("SPAWN_UNDER_PLAYER")) {
            pitch += (1.0F - intensity) * (0.34F - normalized * 0.05F);
            roll += (1.0F - intensity) * direction * 0.08F;
        } else if (animation.equals("DYING")) {
            pitch += intensity * (0.08F + normalized * 0.22F);
            roll += intensity * direction * 0.12F;
        } else {
            pitch += intensity * direction * (0.07F + normalized * 0.16F);
            yaw += intensity * direction * (0.025F + normalized * 0.08F);
        }
        if (animation.equals("GRAB_SUCCESS") || animation.equals("HOLD")) {
            // Alternating local pitch keeps the articulated chain close to its
            // carrier while lowering the claw cage to the server grab anchor.
            // Every segment still receives its own transform; this is not a
            // whole-display rotation masquerading as animation.
            float curl = intensity * (1.12F + normalized * 0.14F);
            pitch += (index % 2 == 0 ? 1.0F : -1.0F) * curl;
        } else if (animation.equals("THROW")) {
            float releaseCurl = (1.0F - intensity) * 1.10F;
            pitch += (index % 2 == 0 ? 1.0F : -1.0F) * releaseCurl;
        }
        return bone(0.0F, 0.0F, 0.0F, pitch, yaw, roll, 1.0F,
                animation.equals("RETRACT") ? 1.0F - intensity * 0.18F : 1.0F, 1.0F);
    }

    private static EndRiftTentaclePose.BoneTransform tip(float cycle, float phase,
                                                          float intensity, String animation) {
        float pitch = (float) Math.sin(cycle + phase - 0.70F) * 0.14F;
        float yaw = (float) Math.cos(cycle * 0.9F + phase) * 0.10F;
        float roll = (float) Math.sin(cycle * 1.1F + phase) * 0.09F;
        if (animation.equals("TELEGRAPH_GRAB") || animation.equals("GRAB_SUCCESS")) {
            pitch += intensity * 0.20F;
            yaw -= intensity * 0.14F;
        } else if (animation.equals("THROW") || animation.equals("MISS_RECOVERY")) {
            pitch -= intensity * 0.22F;
            yaw += intensity * 0.18F;
        }
        return bone(0.0F, 0.0F, 0.0F, pitch, yaw, roll, 1.0F,
                animation.equals("RETRACT") ? 1.0F - intensity * 0.22F : 1.0F, 1.0F);
    }

    private static EndRiftTentaclePose.BoneTransform claw(int index, float openness,
                                                           float phase, String animation) {
        float open = clamp(openness);
        float micro = (float) Math.sin(phase + index * 1.31F) * 0.025F;
        float side = index == 1 ? -1.0F : index == 2 ? 1.0F : 0.0F;
        float depth = index == 3 ? -1.0F : index == 4 ? 1.0F : 0.0F;
        float pitch = -0.12F - open * 0.38F + micro;
        float yaw = side * (0.10F + open * 0.20F) + depth * 0.04F;
        float roll = -side * (0.12F + open * 0.28F) + depth * 0.05F;
        if (animation.equals("HOLD")) {
            pitch += 0.04F;
        }
        return bone(0.0F, 0.0F, 0.0F, pitch, yaw, roll, 1.0F, 1.0F, 1.0F);
    }

    private static EndRiftTentaclePose.BoneTransform bone(float tx, float ty, float tz,
                                                           float pitch, float yaw, float roll,
                                                           float sx, float sy, float sz) {
        return new EndRiftTentaclePose.BoneTransform(tx, ty, tz, pitch, yaw, roll,
                sx, sy, sz);
    }

    private static float phaseFor(long seed) {
        long mixed = seed ^ (seed >>> 33) ^ 0x9E3779B97F4A7C15L;
        return (float) ((Math.floorMod(mixed, 10_000L) / 10_000.0D) * TWO_PI);
    }

    private static float smoothStep(float value) {
        float p = clamp(value);
        return p * p * (3.0F - 2.0F * p);
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    private static String normalize(String animationId) {
        if (animationId == null || animationId.isBlank()) {
            return "READY";
        }
        String raw = animationId.trim();
        int separator = raw.indexOf('|');
        if (separator >= 0) {
            raw = raw.substring(0, separator);
        }
        raw = raw.toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "IDLE" -> "READY";
            case "EMERGE" -> "EMERGING";
            case "GRAB_MISS" -> "MISS_RECOVERY";
            case "HURT" -> "HIT_RECOVERY";
            case "DEATH" -> "DYING";
            default -> raw;
        };
    }
}
