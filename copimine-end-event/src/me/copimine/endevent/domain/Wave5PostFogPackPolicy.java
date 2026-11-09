package me.copimine.endevent.domain;

import java.util.Objects;

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
    public static final int REQUIRED_CYCLES = BlackFogTimingPolicy.CYCLE_COUNT;
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
     * One cycle's pack receipt: which generation and cycle the pack belonged to.
     *
     * <p>Point 12 of the November 9 brief: randomness and one-shot actions must
     * survive a retry without executing twice. "How many packs were spawned" is
     * not enough to prove that. If the counter is read back after a restart that
     * began a NEW generation, a stale count silently licenses the new fight with
     * a debt it never incurred — or cancels one it did. A receipt carries the
     * identity of what it paid for, so a count from another fight cannot be
     * mistaken for this one's.</p>
     */
    public record Receipt(String eventId, long generation, int targetCycles, int cycle) {
        public Receipt {
            if (eventId == null || eventId.isBlank() || generation <= 0L
                    || targetCycles < BlackFogTimingPolicy.MIN_CYCLES
                    || targetCycles > BlackFogTimingPolicy.CYCLE_COUNT
                    || cycle < 1 || cycle > targetCycles) {
                throw new IllegalArgumentException("Wave 5 pack receipt is outside its bounds");
            }
        }

        /** True when this receipt really belongs to the supplied live fight. */
        public boolean matches(String liveEventId, long liveGeneration, int liveTargetCycles) {
            return Objects.equals(eventId, liveEventId) && generation == liveGeneration
                    && targetCycles == liveTargetCycles;
        }
    }

    /**
     * How many packs a checkpoint that was frozen for {@code frozenTargetCycles}
     * may legitimately claim inside a fight that now runs {@code liveTargetCycles}.
     *
     * <p>A count above the live target cannot be spent: it would settle debts
     * that this smaller fight never ran. Clamping is deliberate rather than
     * refusing outright, because shrinking the target on a reconnected roster is
     * legal, and the already-delivered packs are real.</p>
     */
    public static int schedulablePacks(int frozenTargetCycles, int liveTargetCycles, int packsSpawned) {
        int frozen = Math.max(0, Math.min(REQUIRED_CYCLES, frozenTargetCycles));
        int live = Math.max(0, Math.min(REQUIRED_CYCLES, liveTargetCycles));
        int delivered = Math.max(0, Math.min(frozen, packsSpawned));
        return Math.min(delivered, live);
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

    /**
     * How many of a pack's actors must have survived the spawn attempt.
     *
     * <p>A pack is all-or-nothing. The contract asks for one pack of ten to
     * fifteen after every fog; a pack of four is not a smaller pack, it is a
     * broken one, and counting it would settle the debt with nothing on the
     * floor. The caller must therefore roll back anything it placed rather
     * than keep a partial pack and retry next to it — two partial packs add up
     * to more actors than the contract allows.</p>
     */
    public static boolean packComplete(int planned, int spawned) {
        return planned > 0 && spawned == planned;
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
