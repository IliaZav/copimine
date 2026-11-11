package me.copimine.endevent.domain;

/** A single authored melee opportunity between major casts, with its own cooldown. */
public final class BossCloseCombatPolicy {
    public static final long COOLDOWN_MILLIS = 8_000L;
    private BossCloseCombatPolicy() {}

    public static boolean canStrike(BossPhase phase, long now, long nextStrike,
                                    long spellPauseUntil, long nextMajorCast,
                                    double distance, boolean lineOfSight,
                                    boolean controlledTarget) {
        return phase != null && phase != BossPhase.LAST_SEAL
                && now >= nextStrike && now >= spellPauseUntil && now < nextMajorCast
                && Double.isFinite(distance) && distance >= 0
                && distance <= BossContactStrikePolicy.MELEE_SWIPE_RANGE
                && lineOfSight && !controlledTarget;
    }
}
