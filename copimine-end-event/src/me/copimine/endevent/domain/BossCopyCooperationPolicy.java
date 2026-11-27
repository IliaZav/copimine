package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * §10.4.2 / V7-21 — cooperative techniques jointly performed by the Rift
 * Guardian and its combat copies during Phase 5 (RAGE).
 *
 * <p>The three provisional profiles are mirror-substitution, coordinated
 * salvo and reflection chain. All admission, timeline, copy-loss and
 * concurrency rules are pure functions so they can be tested without a
 * live server.</p>
 *
 * <p>Provisional cooldown windows are expressed per §10.4.2 table rows.
 * The mid-point of each window is the default; an explicit configuration
 * clamps to the agreed band.</p>
 *
 * @see BossHazardBudget
 * @see BossAbilityId
 */
public final class BossCopyCooperationPolicy {

    // ------------------------------------------------------------------
    // Technique enumeration
    // ------------------------------------------------------------------

    /** The three provisional cooperative profiles (§10.4.2). */
    public enum Technique {
        MIRROR_SUBSTITUTION("mirror_substitution", 25, 35, 2, 1, 0, false),
        COORDINATED_SALVO("coordinated_salvo", 20, 30, 3, 2, 0, false),
        REFLECTION_CHAIN("reflection_chain", 40, 60, 2, 2, 1, true);

        private final String id;
        private final int minCooldownSeconds;
        private final int maxCooldownSeconds;
        private final int minParticipants;
        private final int minCopies;
        private final int maxSimultaneousBeams;
        private final boolean hardControl;

        Technique(String id, int minCooldownSeconds, int maxCooldownSeconds,
                  int minParticipants, int minCopies,
                  int maxSimultaneousBeams, boolean hardControl) {
            this.id = id;
            this.minCooldownSeconds = Math.max(1, minCooldownSeconds);
            this.maxCooldownSeconds = Math.max(this.minCooldownSeconds, maxCooldownSeconds);
            this.minParticipants = Math.max(1, minParticipants);
            this.minCopies = Math.max(1, minCopies);
            this.maxSimultaneousBeams = Math.max(0, maxSimultaneousBeams);
            this.hardControl = hardControl;
        }

        public String id() {
            return id;
        }

        public int minParticipants() {
            return minParticipants;
        }

        public int minCopies() {
            return minCopies;
        }

        public int maxSimultaneousBeams() {
            return maxSimultaneousBeams;
        }

        public boolean hardControl() {
            return hardControl;
        }

        /** Mid-point of the agreed cooldown window, in seconds. */
        public int defaultCooldownSeconds() {
            return (minCooldownSeconds + maxCooldownSeconds) / 2;
        }

        /** Mid-point of the agreed cooldown window, in server ticks. */
        public int defaultCooldownTicks() {
            return defaultCooldownSeconds() * 20;
        }

        /** Clamp a configured cooldown (in ticks) to the agreed band. */
        public int clampCooldownTicks(int configuredTicks) {
            int min = minCooldownSeconds * 20;
            int max = maxCooldownSeconds * 20;
            return Math.max(min, Math.min(max, configuredTicks));
        }
    }

    // ------------------------------------------------------------------
    // Timeline
    // ------------------------------------------------------------------

    /**
     * One cooperative technique schedule.
     *
     * @param technique    which technique
     * @param startTick    server tick at which the sequence begins
     * @param fixTick      server tick at which the attack is fixed
     *                     (copy destruction after this tick produces
     *                      {@link CopyLoss#SHORTENED} for salvo, else
     *                      {@link CopyLoss#CANCEL})
     * @param executeTicks ordered list of server ticks at which damage
     *                     can land (strike / volley / beam frame)
     * @param recoveryTicks window after the last execute tick during
     *                      which the boss/copies recover
     * @param cleanupTicks  window after recovery for final visual/audio
     *                      cleanup before the slot is freed
     */
    public record Timeline(Technique technique, long startTick, long fixTick,
                           List<Long> executeTicks, int recoveryTicks,
                           int cleanupTicks) {
        public Timeline {
            if (technique == null) {
                throw new IllegalArgumentException("technique is required");
            }
            startTick = Math.max(0L, startTick);
            fixTick = Math.max(startTick, fixTick);
            List<Long> ordered = new ArrayList<>();
            if (executeTicks != null) {
                for (Long tick : executeTicks) {
                    if (tick != null) {
                        ordered.add(Math.max(fixTick, tick));
                    }
                }
            }
            ordered.sort(Long::compareTo);
            executeTicks = List.copyOf(ordered);
            recoveryTicks = Math.max(0, recoveryTicks);
            cleanupTicks = Math.max(0, cleanupTicks);
        }

        /** Whether the cast was fixed before {@code tick}. */
        public boolean fixedAt(long tick) {
            return tick >= fixTick;
        }

        /** Whether this tick is an execute (damage) tick. */
        public boolean executesAt(long tick) {
            return executeTicks.contains(tick);
        }

        /** The last tick on which damage is scheduled. */
        public long lastExecuteTick() {
            return executeTicks.isEmpty() ? fixTick
                    : executeTicks.get(executeTicks.size() - 1);
        }

        /** The tick at which recovery begins. */
        public long recoveryStartTick() {
            return lastExecuteTick() + 1L;
        }

        /** The tick at which the entire technique is complete. */
        public long cleanupTick() {
            return recoveryStartTick() + recoveryTicks + cleanupTicks;
        }

        /** Whether the technique is still within its scheduled window. */
        public boolean activeAt(long tick) {
            return tick >= startTick && tick <= cleanupTick();
        }
    }

    /** Positive timing constants for each technique. */

    public static final int MIRROR_PREPARE_TICKS = 20;
    public static final int MIRROR_STRIKE_WARNING_TICKS = 16;

    public static final int SALVO_CHARGE_TICKS = 30;
    public static final int SALVO_VOLLEY_GAP_TICKS = 12;
    public static final int SALVO_VOLLEYS = 3;

    public static final int CHAIN_CHARGE_TICKS = 24;
    public static final int CHAIN_TRANSFER_GAP_TICKS = 10;
    public static final int CHAIN_TRANSFERS = 3;
    public static final int CHAIN_BEAM_DELAY_TICKS = 6;

    // ------------------------------------------------------------------
    // Timeline factory
    // ------------------------------------------------------------------

    /**
     * Build a Timeline for the given technique starting at {@code startTick}.
     *
     * @return a valid Timeline, or null when {@code technique} is null
     */
    public static Timeline timeline(Technique technique, long startTick) {
        if (technique == null) {
            return null;
        }
        long start = Math.max(0L, startTick);
        return switch (technique) {
            case MIRROR_SUBSTITUTION -> new Timeline(technique, start,
                    start + MIRROR_PREPARE_TICKS,
                    List.of(start + MIRROR_PREPARE_TICKS + MIRROR_STRIKE_WARNING_TICKS),
                    20, 40);
            case COORDINATED_SALVO -> {
                List<Long> volleys = new ArrayList<>();
                for (int index = 0; index < SALVO_VOLLEYS; index++) {
                    volleys.add(start + SALVO_CHARGE_TICKS
                            + (long) index * SALVO_VOLLEY_GAP_TICKS);
                }
                yield new Timeline(technique, start, start + SALVO_CHARGE_TICKS,
                        volleys, 12, 40);
            }
            case REFLECTION_CHAIN -> {
                List<Long> events = new ArrayList<>();
                // Chained transfers across copies
                for (int index = 0; index < CHAIN_TRANSFERS; index++) {
                    events.add(start + CHAIN_CHARGE_TICKS
                            + (long) index * CHAIN_TRANSFER_GAP_TICKS);
                }
                // Final chest beam after last transfer
                events.add(start + CHAIN_CHARGE_TICKS
                        + (long) CHAIN_TRANSFERS * CHAIN_TRANSFER_GAP_TICKS
                        + CHAIN_BEAM_DELAY_TICKS);
                yield new Timeline(technique, start,
                        start + CHAIN_CHARGE_TICKS, events, 20, 40);
            }
        };
    }

    // ------------------------------------------------------------------
    // Admission — the technique may fire
    // ------------------------------------------------------------------

    /** Observable world state the decision procedure is based on. */
    public record AdmissionState(int livingParticipants, int livingCopies,
                                 int activeBeams,
                                 boolean guardianAlive, boolean routeLegal,
                                 boolean heavyActionFree, boolean honestWindow,
                                 boolean hardControlAvailable,
                                 int recentHardControls) {
        public AdmissionState {
            livingParticipants = Math.max(0, livingParticipants);
            livingCopies = Math.max(0, livingCopies);
            activeBeams = Math.max(0, activeBeams);
            recentHardControls = Math.max(0, recentHardControls);
        }
    }

    public record AdmissionDecision(boolean accepted, String reason,
                                    Technique technique, Timeline timeline) {
        public AdmissionDecision {
            reason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;
            if (accepted != (technique != null && timeline != null)) {
                throw new IllegalArgumentException(
                        "accepted decision must carry technique and timeline");
            }
        }
    }

    private static AdmissionDecision refused(String reason) {
        return new AdmissionDecision(false, reason, null, null);
    }

    /**
     * Whether a cooperative technique may be initiated now.
     *
     * @param technique   the technique to check (null → refuse)
     * @param nowTick     current server tick
     * @param lastCastTick tick of the previous cooperative cast
     * @param state       observed world state
     * @param cooldownTicks explicit cooldown (or negative for default)
     * @return admission decision
     */
    public static AdmissionDecision mayFire(Technique technique, long nowTick,
                                            long lastCastTick,
                                            AdmissionState state,
                                            int cooldownTicks) {
        if (technique == null) {
            return refused("INVALID_TECHNIQUE");
        }
        if (!state.guardianAlive()) {
            return refused("GUARDIAN_DEAD");
        }
        if (state.livingParticipants() < technique.minParticipants()) {
            return refused("INSUFFICIENT_PARTICIPANTS");
        }
        if (state.livingCopies() < technique.minCopies()) {
            return refused("INSUFFICIENT_COPIES");
        }
        if (!state.routeLegal()) {
            return refused("NO_ROUTE");
        }
        if (!state.heavyActionFree()) {
            return refused("HEAVY_ACTION_BUSY");
        }
        if (!state.honestWindow()) {
            return refused("NO_HONEST_WINDOW");
        }
        int effectiveCd = technique.clampCooldownTicks(
                cooldownTicks >= 0 ? cooldownTicks : technique.defaultCooldownTicks());
        // Long.MIN_VALUE is the natural "never fired" sentinel; avoid overflow.
        if (lastCastTick != Long.MIN_VALUE && nowTick - lastCastTick < effectiveCd) {
            return refused("COOLDOWN");
        }
        // Beam concurrency — never three simultaneous beams + grab/stun
        int beamBudget = technique.maxSimultaneousBeams();
        if (state.activeBeams() > beamBudget) {
            return refused("BEAM_CONCURRENCY");
        }
        // Hard-control guard — no repeat on the same player
        if (technique.hardControl() && (!state.hardControlAvailable()
                || state.recentHardControls() > 0)) {
            return refused("HARD_CONTROL_BUSY");
        }
        Timeline schedule = timeline(technique, nowTick);
        if (schedule == null) {
            return refused("TIMELINE_ERROR");
        }
        return new AdmissionDecision(true, "ACCEPTED", technique, schedule);
    }

    // ------------------------------------------------------------------
    // Copy-loss behaviour
    // ------------------------------------------------------------------

    /** How the technique reacts to a copy dying during the sequence. */
    public enum CopyLoss {
        /** Abort everything safe; no ghost projectile. */
        CANCEL,
        /** Continue with remaining copies; dead ones skip their shot. */
        SHORTENED
    }

    /**
     * Decide what happens when a participating copy dies.
     *
     * @param technique      the active technique
     * @param fixed          true if the technique was fixed at death time
     * @param survivingCopies how many cooperating copies are still alive
     * @return the appropriate loss outcome
     */
    public static CopyLoss copyLossOutcome(Technique technique, boolean fixed,
                                           int survivingCopies) {
        if (technique == null || !fixed) {
            return CopyLoss.CANCEL;
        }
        return switch (technique) {
            case COORDINATED_SALVO ->
                    survivingCopies > 0 ? CopyLoss.SHORTENED : CopyLoss.CANCEL;
            case MIRROR_SUBSTITUTION, REFLECTION_CHAIN -> CopyLoss.CANCEL;
        };
    }

    /**
     * Whether a dead source may still fire.
     *
     * <p>§10.4.2: "запрещён невидимый выстрел от мёртвого источника" —
     * a dead copy must never produce an invisible shot.</p>
     */
    public static boolean mayFireFrom(boolean sourceAlive) {
        return sourceAlive;
    }

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    /** Whether the technique may overlap with another heavy action. */
    public static boolean allowsConcurrentHeavy(Technique technique) {
        return false; // one heavy action at a time per V7-21
    }

    // ------------------------------------------------------------------
    // Combat rules
    // ------------------------------------------------------------------

    public static boolean damagesPlayersOnly() {
        return true;
    }

    public static boolean friendlyFireAllowed() {
        return false;
    }

    /** Cooperative techniques are flavour; they never complete a phase. */
    public static boolean countsTowardPhaseProgress() {
        return false;
    }

    public static boolean dropsLoot() {
        return false;
    }

    public static boolean requiresCleanup(String reason) {
        return true;
    }

    // ------------------------------------------------------------------
    // Description
    // ------------------------------------------------------------------

    public static String describe(Technique technique, Timeline timeline) {
        if (technique == null) {
            return "BOSS_COPY_COOPERATION refused=unknown-technique";
        }
        if (timeline == null) {
            return "BOSS_COPY_COOPERATION refused=no-timeline technique=" + technique.id();
        }
        return "BOSS_COPY_COOPERATION technique=" + technique.id()
                + " start=" + timeline.startTick()
                + " fix=" + timeline.fixTick()
                + " execute=" + timeline.executeTicks()
                + " cleanup=" + timeline.cleanupTick();
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /** All known techniques. */
    public static List<Technique> supportedTechniques() {
        return List.of(Technique.values());
    }

    /** Resolve a technique by its id (case-insensitive). */
    public static Technique techniqueFor(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalised = id.trim().toLowerCase(Locale.ROOT);
        for (Technique technique : Technique.values()) {
            if (technique.id().equals(normalised)) {
                return technique;
            }
        }
        return null;
    }
}
