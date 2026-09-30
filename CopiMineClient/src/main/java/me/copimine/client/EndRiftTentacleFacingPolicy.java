package me.copimine.client;

import java.util.Locale;

/** Keeps target tracking out of hurt/death poses and limits abrupt render turns. */
public final class EndRiftTentacleFacingPolicy {
    private static final float MAX_RADIANS_PER_SECOND = 8.0F;
    private static final long MAX_STEP_MILLIS = 100L;
    private static final float FULL_TURN = (float) (Math.PI * 2.0D);

    private EndRiftTentacleFacingPolicy() {
    }

    public static boolean tracksTarget(String animation) {
        String state = normalize(animation);
        return switch (state) {
            case "TELEGRAPH_GRAB", "GRAB_SUCCESS", "HOLD", "THROW" -> true;
            default -> false;
        };
    }

    /** Move toward the requested angle along the shortest arc at a bounded rate. */
    public static float advanceYaw(float currentYaw, float targetYaw, long elapsedMillis) {
        if (!Float.isFinite(currentYaw)) {
            currentYaw = 0.0F;
        }
        if (!Float.isFinite(targetYaw)) {
            return currentYaw;
        }
        float delta = wrapRadians(targetYaw - currentYaw);
        long elapsed = Math.max(0L, Math.min(MAX_STEP_MILLIS, elapsedMillis));
        float maximumStep = MAX_RADIANS_PER_SECOND * elapsed / 1000.0F;
        if (Math.abs(delta) <= maximumStep) {
            return wrapRadians(currentYaw + delta);
        }
        return wrapRadians(currentYaw + Math.copySign(maximumStep, delta));
    }

    private static float wrapRadians(float radians) {
        float wrapped = radians % FULL_TURN;
        if (wrapped > Math.PI) {
            wrapped -= FULL_TURN;
        } else if (wrapped < -Math.PI) {
            wrapped += FULL_TURN;
        }
        return wrapped;
    }

    private static String normalize(String animation) {
        if (animation == null || animation.isBlank()) {
            return "READY";
        }
        String state = animation.trim();
        int separator = state.indexOf('|');
        if (separator >= 0) {
            state = state.substring(0, separator);
        }
        return state.toUpperCase(Locale.ROOT);
    }
}
