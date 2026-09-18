package me.copimine.endevent.domain;

/** Pure rules for the Rift Core Shard's once-per-cooldown void rescue. */
public final class AbyssAnchorPolicy {
    public static final int DEFAULT_COOLDOWN_SECONDS = 30 * 60;
    public static final int MIN_COOLDOWN_SECONDS = 1;
    public static final int MAX_COOLDOWN_SECONDS = 24 * 60 * 60;
    public static final double RESCUE_HEALTH = 2.0D;

    private AbyssAnchorPolicy() {
    }

    public static Decision evaluate(boolean authenticShard, boolean activeAttempt,
                                    boolean safePointAvailable, long nowMillis,
                                    long cooldownUntilMillis, int cooldownSeconds) {
        if (!authenticShard) {
            return Decision.denied("missing-authentic-shard");
        }
        if (activeAttempt) {
            return Decision.denied("active-attempt");
        }
        if (!safePointAvailable) {
            return Decision.denied("unsafe-core-point");
        }
        if (cooldownUntilMillis > nowMillis) {
            return Decision.denied("cooldown");
        }
        return new Decision(true, cooldownUntil(nowMillis, cooldownSeconds), RESCUE_HEALTH, "rescued");
    }

    public static long cooldownUntil(long nowMillis, int cooldownSeconds) {
        long safeSeconds = Math.max(MIN_COOLDOWN_SECONDS,
                Math.min(MAX_COOLDOWN_SECONDS, cooldownSeconds));
        long duration = safeSeconds * 1000L;
        if (nowMillis > Long.MAX_VALUE - duration) {
            return Long.MAX_VALUE;
        }
        return nowMillis + duration;
    }

    public static boolean isCooldownActive(long nowMillis, long cooldownUntilMillis) {
        return cooldownUntilMillis > nowMillis;
    }

    /** Anchor has explicit priority over a vanilla Totem on a void fall. */
    public static boolean hasPriorityOverTotem(boolean authenticShard, boolean voidDamage,
                                                boolean activeAttempt, boolean safePointAvailable,
                                                long nowMillis, long cooldownUntilMillis,
                                                int cooldownSeconds) {
        return voidDamage && evaluate(authenticShard, activeAttempt, safePointAvailable,
                nowMillis, cooldownUntilMillis, cooldownSeconds).rescued();
    }

    public record Decision(boolean rescued, long cooldownUntilMillis,
                           double health, String reason) {
        public Decision {
            reason = reason == null ? "" : reason;
        }

        private static Decision denied(String reason) {
            return new Decision(false, 0L, 0.0D, reason);
        }
    }
}
