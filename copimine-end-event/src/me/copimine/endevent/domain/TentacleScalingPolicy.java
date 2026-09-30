package me.copimine.endevent.domain;

/** Bounded tentacle pressure for the six boss phases. */
public final class TentacleScalingPolicy {
    public static final int MAX_PERMANENT = 8;
    public static final int MAX_TEMPORARY = 6;
    /** Canonical server/client combat dimensions for the articulated rig. */
    public static final double DEFAULT_LOGICAL_LENGTH = 6.25D;
    public static final double DEFAULT_HITBOX_WIDTH = 1.90D;
    public static final double DEFAULT_HITBOX_HEIGHT = DEFAULT_LOGICAL_LENGTH;
    /** Server item carriers retain a 6.25-block culling/fallback envelope. */
    public static final double DEFAULT_CLIENT_RENDER_SCALE = 1.0D;
    /** Minimum horizontal gap between two visible tentacle roots. */
    public static final double MIN_SPAWN_SEPARATION = 2.75D;
    /** Cast visuals refill in two-second steps instead of appearing once per phase. */
    public static final long TEMPORARY_CAST_INTERVAL_TICKS = 40L;

    private TentacleScalingPolicy() {
    }

    /** Cast visuals may attempt on the first eligible boss tick, not after a hidden cooldown. */
    public static long initialTemporaryAttemptTick(long currentTick) {
        return Math.max(0L, currentTick);
    }

    public static int permanentFor(int players, BossPhase stage) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0 || stage == null) {
            return 0;
        }
        int partyTarget = safePlayers <= 2 ? 2
                : safePlayers <= 4 ? 3
                : safePlayers <= 7 ? 4
                : safePlayers <= 10 ? 5
                : safePlayers <= 15 ? 6
                : 8;
        // Guardian tentacles are a final-seal mechanic. Earlier stages may
        // use bounded temporary grab visuals, but never permanent guardians.
        return stage == BossPhase.LAST_SEAL ? Math.min(partyTarget, MAX_PERMANENT) : 0;
    }

    public static int temporaryFor(int players, BossPhase stage) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0 || stage == null
                || (stage != BossPhase.RAGE && stage != BossPhase.LAST_SEAL)) {
            return 0;
        }
        int slots = safePlayers <= 4 ? 2
                : safePlayers <= 8 ? 3
                : safePlayers <= 12 ? 4
                : safePlayers <= 16 ? 5
                : 6;
        return Math.min(MAX_TEMPORARY, slots);
    }
}
