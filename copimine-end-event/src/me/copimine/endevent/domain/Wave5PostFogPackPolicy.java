package me.copimine.endevent.domain;

/**
 * Audit wave 5 / W5-004: one finite pack of 10-15 enemies after EVERY fog.
 *
 * <p>The contract is explicit: "After EACH fog, including the fifth, spawn one
 * finite pack of 10-15 enemies. Do not end immediately on clearing an early
 * pack. Completion requires five completed cycles, all mandatory pack intents
 * resolved and final clearance."</p>
 *
 * <p>The shipped code had three separate violations of that sentence:</p>
 * <ol>
 *   <li>the fifth cycle was excluded from the call site entirely, so the last
 *       fog spawned nothing;</li>
 *   <li>the reinforcement was skipped whenever the live cap was already met, and
 *       nothing remembered that a pack was still owed;</li>
 *   <li>the pack size was whatever the first entry of the old spawn schedule
 *       happened to be, not the required ten to fifteen.</li>
 * </ol>
 *
 * <p>This policy owns the pack SIZE and the OWED-INTENT bookkeeping. It never
 * bypasses the live cap: when the cap blocks a pack the intent stays owed and is
 * retried, so the pack is deferred rather than lost (W5-005 keeps one pressure
 * owner and still caps simultaneous actors).</p>
 */
public final class Wave5PostFogPackPolicy {
    /** Smallest mandatory pack, W5-004. */
    public static final int MIN_PACK = 10;
    /** Largest mandatory pack, W5-004. */
    public static final int MAX_PACK = 15;
    /** Total fog cycles that each owe exactly one pack. */
    public static final int REQUIRED_CYCLES = 5;
    /** Background delay before an owed pack that hit the cap is retried. */
    public static final long RETRY_MILLIS = 3_000L;

    private Wave5PostFogPackPolicy() { }

    /**
     * Pack size for a party, inside the contract window and scaled by pressure.
     *
     * <p>Ten actors is the authored floor for any party; the party pressure
     * raises it up to fifteen. The result is always within
     * {@code [MIN_PACK, MAX_PACK]} because that window is the requirement, not a
     * tuning suggestion.</p>
     */
    public static int packSize(int livingPlayers) {
        int players = Math.max(WaveScalingPolicy.MIN_PLAYERS,
                Math.min(WaveScalingPolicy.MAX_PLAYERS, livingPlayers));
        // Ten for the smallest party, fifteen at the global roster.
        int span = MAX_PACK - MIN_PACK;
        int extra = (int) Math.floor(span
                * (players - WaveScalingPolicy.MIN_PLAYERS)
                / (double) (WaveScalingPolicy.MAX_PLAYERS - WaveScalingPolicy.MIN_PLAYERS));
        return Math.max(MIN_PACK, Math.min(MAX_PACK, MIN_PACK + extra));
    }

    /** True when the cycle just completed owes a pack (every cycle does). */
    public static boolean cycleOwesPack(int completedCycles) {
        return completedCycles > 0 && completedCycles <= REQUIRED_CYCLES;
    }

    /**
     * How many packs are still owed after {@code completedCycles} fog cycles were
     * resolved by {@code packsSpawned} real packs.
     *
     * <p>Never negative, so a duplicate spawn cannot go into debt and demand a
     * pack that the contract never asked for.</p>
     */
    public static int packsOwed(int completedCycles, int packsSpawned) {
        int completed = Math.max(0, Math.min(REQUIRED_CYCLES, completedCycles));
        return Math.max(0, completed - Math.max(0, packsSpawned));
    }

    /** True when wave completion still has to wait for an owed pack. */
    public static boolean blocksCompletion(int completedCycles, int packsSpawned) {
        return packsOwed(completedCycles, packsSpawned) > 0;
    }

    /**
     * Split a pack across the ordinary roles, keeping melee and ranged mixed.
     *
     * <p>Endermen take the larger melee half so a pack is never a single mob
     * type, following the configured two-to-three melee/skeleton ratio.</p>
     */
    public static WaveMechanicsPolicy.WaveCounts compose(int packSize) {
        int total = Math.max(0, packSize);
        if (total == 0) return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        int skeletons = total * 2 / 5;
        int melee = total - skeletons;
        int endermen = (melee + 1) / 2;
        int spiders = melee - endermen;
        return new WaveMechanicsPolicy.WaveCounts(endermen, spiders, skeletons, 0, 0);
    }
}
