package me.copimine.endevent.domain;

/**
 * Master contract 17.1: the Last Seal cocoon is guarded by six to eight large
 * defensive Kagune that die permanently inside one encounter generation.
 *
 * <p>The old design scheduled a 90-second respawn per slot. The 11-November
 * contract removes that behaviour entirely: a killed defender stays destroyed
 * for the generation, the death is written to a durable ledger, and a restart
 * may only restore defenders that were still alive. This class owns the count,
 * the placement ring and the ledger semantics; the Bukkit adapter owns the
 * entities.</p>
 */
public final class KaguneCocoonGuardianPolicy {
    /** Contract 17.1: six to eight large defenders. */
    public static final int MIN_COUNT = 6;
    public static final int MAX_COUNT = 8;

    /**
     * 17.1 requires the cocoon to sit closer to the boss than the old widely
     * spaced guardians. The retired combat ring used 5.5 blocks
     * ({@link TentacleGuardianLayoutPolicy#PERMANENT_RING_RADIUS}); the cocoon
     * hugs the Guardian instead.
     */
    public static final double COCOON_RING_RADIUS = 3.75D;
    /** Lowest radius that still keeps eight 1.9-wide bodies from overlapping. */
    public static final double MIN_COCOON_RING_RADIUS = 3.5D;

    private KaguneCocoonGuardianPolicy() {
    }

    /** Six for small parties, seven for medium, eight for large. */
    public static int countFor(int admittedPlayers) {
        int players = Math.max(0, admittedPlayers);
        if (players <= 0) {
            return 0;
        }
        if (players <= 5) {
            return MIN_COUNT;
        }
        return players <= 10 ? 7 : MAX_COUNT;
    }

    /** Slot range for a given party profile; never above the hard maximum. */
    public static boolean slotInRange(int slot, int admittedPlayers) {
        return slot >= 0 && slot < countFor(admittedPlayers);
    }

    /**
     * Deterministic cocoon ring. Slot zero sits at -90 degrees and the ring
     * proceeds counter-clockwise, so a restart rebuilds the identical set.
     */
    public static RingOffset offsetFor(int slot, int count) {
        int safeCount = Math.max(1, Math.min(MAX_COUNT, count));
        int safeSlot = Math.floorMod(slot, safeCount);
        double angle = -Math.PI / 2.0D + (Math.PI * 2.0D * safeSlot / safeCount);
        return new RingOffset(Math.cos(angle) * COCOON_RING_RADIUS,
                Math.sin(angle) * COCOON_RING_RADIUS);
    }

    /** True while at least one required defender still stands. */
    public static boolean cocoonActive(int admittedPlayers, int livingGuardians) {
        return countFor(admittedPlayers) > 0 && livingGuardians > 0;
    }

    /**
     * Contract 17.1 / 17.3: once every required defender is dead the cocoon is
     * broken for good. A broken cocoon is a property of the generation, so it
     * can only be cleared by starting a new encounter generation.
     */
    public static boolean cocoonBroken(boolean alreadyBrokenThisGeneration,
                                       int admittedPlayers, int livingGuardians) {
        if (alreadyBrokenThisGeneration) {
            return true;
        }
        int required = countFor(admittedPlayers);
        return required > 0 && livingGuardians <= 0;
    }

    /** A broken cocoon never shields the boss again, whatever the counters say. */
    public static boolean shieldsBoss(boolean cocoonBrokenThisGeneration,
                                      int livingGuardians) {
        return !cocoonBrokenThisGeneration && livingGuardians > 0;
    }

    /** Restart policy: only defenders that were alive come back. */
    public static boolean mayRestore(boolean recordedDestroyed) {
        return !recordedDestroyed;
    }

    /** A destroyed defender is permanent: there is no respawn deadline any more. */
    public static boolean mayRespawnAfterKill() {
        return false;
    }

    /** Ledger key for one defender inside one encounter generation. */
    public static String ledgerKey(long generation, int slot) {
        if (generation <= 0L || slot < 0) {
            throw new IllegalArgumentException("ledger key needs a live generation and slot");
        }
        return "kagune-cocoon-guardian." + generation + "." + slot;
    }

    /** Offset of one defender from the cocoon anchor, in blocks. */
    public record RingOffset(double x, double z) {
        public RingOffset {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("cocoon offset must be finite");
            }
        }

        public double radius() {
            return Math.sqrt(x * x + z * z);
        }
    }
}