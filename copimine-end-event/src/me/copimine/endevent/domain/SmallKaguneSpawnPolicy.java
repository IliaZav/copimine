package me.copimine.endevent.domain;

/**
 * Master contract 18: a small attacking Kagune is an independent finite enemy
 * type, not a temporary cast visual.
 *
 * <p>The retired {@link SmallKagunePolicy} was a boss spell that spent the
 * shared six-temporary-tentacle budget, expired on a timer and could never hold
 * more than a handful. The 11-November contract wants a real enemy: it spawns
 * roughly every 15-20 seconds while the cocoon battle is live, the arena may
 * hold up to thirty of them, the batch scales with the admitted party, and each
 * one lives until players actually kill it. Six temporary tentacles must never
 * cap it.</p>
 */
public final class SmallKaguneSpawnPolicy {
    /** Contract 18.1: "up to 30 living small Kagune on the arena". */
    public static final int MAX_LIVING = 30;
    /** Contract 18.1: "spawn approximately every 15-20 seconds". */
    public static final long SPAWN_MIN_INTERVAL_MILLIS = 15_000L;
    public static final long SPAWN_MAX_INTERVAL_MILLIS = 20_000L;
    /** The retired temporary-tentacle limit, kept only to prove it is ignored. */
    public static final int RETIRED_TEMPORARY_LIMIT = 6;

    private SmallKaguneSpawnPolicy() {
    }

    /**
     * Batch for one spawn wave. A small party gets one or two; a full arena gets
     * four. The batch is deliberately party-scaled instead of always being
     * capped at thirty.
     */
    public static int batchSize(int admittedPlayers) {
        int players = Math.max(0, admittedPlayers);
        if (players <= 0) {
            return 0;
        }
        if (players <= 5) {
            return 1;
        }
        return players <= 10 ? 2 : players <= 15 ? 3 : 4;
    }

    /** How many may still appear right now. */
    public static int admission(int admittedPlayers, int living) {
        int room = MAX_LIVING - Math.max(0, living);
        return Math.max(0, Math.min(batchSize(admittedPlayers), room));
    }

    /** True while a new spawn wave is allowed at all. */
    public static boolean spawningAllowed(boolean cocoonBrokenThisGeneration,
                                          boolean encounterActive) {
        return encounterActive && !cocoonBrokenThisGeneration;
    }

    /**
     * Contract 18: after the cocoon breaks no new small Kagune appear, but the
     * living ones stay killable and keep their own lifecycle.
     */
    public static boolean keepsLiving(boolean cocoonBrokenThisGeneration, int living) {
        return living > 0;
    }

    /** They die only when killed: there is no forced lifetime any more. */
    public static boolean expiresOnLifetime() {
        return false;
    }

    /** The temporary tentacle budget must never bound this enemy type. */
    public static boolean sharesTemporaryTentacleLimit() {
        return false;
    }

    /** Deterministic cadence inside [15 s, 20 s]; never a hidden short lifetime. */
    public static long intervalMillis(int step, long seed) {
        long spread = SPAWN_MAX_INTERVAL_MILLIS - SPAWN_MIN_INTERVAL_MILLIS + 1L;
        long mixed = mix(seed ^ (step * 0x9E3779B97F4A7C15L));
        return SPAWN_MIN_INTERVAL_MILLIS + Math.floorMod(mixed, spread);
    }

    /** Next absolute due time for a spawn step. */
    public static long nextDueMillis(long nowMillis, int step, long seed) {
        return Math.max(0L, nowMillis) + intervalMillis(step, seed);
    }

    public static boolean due(long nowMillis, long dueMillis) {
        return dueMillis >= 0L && nowMillis >= dueMillis;
    }

    /** Death of one small Kagune frees exactly one slot, never below zero. */
    public static int afterDeath(int living) {
        return Math.max(0, living - 1);
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}