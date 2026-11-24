package me.copimine.endevent.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure, durable core of the official victory path (companion spec §8, and the
 * {@code EndRiftVictoryCoordinator} component named in V6 master §OV4.10).
 *
 * <p>The spec calls the sequence
 * {@code ACCEPTED LEGAL LETHAL HIT -> DURABLE DEFEAT -> DYING PRESENTATION ->
 * FINISH -> VICTORY PROCESSING -> DURABLE REWARDS -> WORLDCORE END OPEN ->
 * OPTIONAL GATE/MEMORIAL -> CLEANUP/UNLOCKED}. Two requirements make that
 * sequence hard to get right by hand: the defeat must commit <em>exactly once</em>
 * even when two lethal hits land in the same tick, and each later step must run
 * <em>at most once</em> so a crash between an external commit and its persisted
 * receipt cannot replay loot or re-open the End.</p>
 *
 * <p>This class is the deterministic part of that coordinator: it owns the
 * stage advance (monotone, one step at a time, never backwards), the frozen
 * official roster, the once-only reward operations keyed by operation id, and
 * the once-only WorldCore End-open intent. It is generation-scoped like
 * {@link GuardianDeathLedger}: a callback from a wiped generation is refused
 * rather than applied to the new encounter. It holds no Paper types, touches no
 * world, and never issues loot itself.</p>
 */
public final class EndRiftVictoryCoordinatorPolicy {

    /** Progress key prefix; versioned so an old snapshot cannot be misread. */
    public static final String KEY_PREFIX = "end-rift-victory.";
    public static final String VERSION = "v1";

    /** The monotone stage ladder, in the spec's order. */
    public enum Stage {
        /** No legal lethal hit accepted yet. */
        NONE,
        /** Durable defeat committed: gameplay damage and new casts must stop. */
        DEFEAT,
        /** The controlled two-second {@code Dying2} presentation is running. */
        DYING,
        /** Durable rewards are being issued through the existing Artifacts path. */
        REWARDS,
        /** The mandatory WorldCore global End unlock has been committed. */
        END_OPEN,
        /** The optional configured physical gate / Core memorial is placed. */
        MEMORIAL,
        /** Boss-owned actors, hazards, tasks and effects are removed. */
        CLEANUP,
        /** The encounter is complete; restart must not replay any step. */
        UNLOCKED
    }

    /** Outcome of a defeat-commit attempt. */
    public enum Commit {
        COMMITTED,
        ALREADY_COMMITTED,
        NOT_OFFICIAL
    }

    /** Outcome of a stage-advance attempt. */
    public enum Advance {
        ADVANCED,
        ALREADY_SETTLED,
        OUT_OF_ORDER,
        NOT_COMMITTED
    }

    /** Outcome of a reward-operation attempt. */
    public enum RewardGrant {
        GRANTED,
        ALREADY_APPLIED,
        NOT_COMMITTED,
        INVALID
    }

    private final String eventId;
    private final long generation;
    private Stage stage = Stage.NONE;
    private boolean official;
    private Set<UUID> roster = Set.of();
    private final LinkedHashMap<String, String> rewardOps = new LinkedHashMap<>();
    private boolean endUnlockCommitted;

    public EndRiftVictoryCoordinatorPolicy(String eventId, long generation) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("victory coordinator needs an event id");
        }
        if (generation <= 0L) {
            throw new IllegalArgumentException("victory coordinator needs a live generation");
        }
        this.eventId = eventId.trim();
        this.generation = generation;
    }

    public String eventId() {
        return eventId;
    }

    public long generation() {
        return generation;
    }

    public Stage stage() {
        return stage;
    }

    public boolean official() {
        return official;
    }

    /** True once the stage has reached at least {@code candidate}. */
    public boolean atLeast(Stage candidate) {
        return stage.ordinal() >= candidate.ordinal();
    }

    /** The frozen official roster committed with the defeat, if any. */
    public Set<UUID> roster() {
        return roster;
    }

    /**
     * Commits the durable defeat exactly once. Repeated lethal hits, a duplicate
     * callback or a second concurrent hit all return
     * {@link Commit#ALREADY_COMMITTED} and never change the committed roster or
     * official flag. A diagnostic/administrative kill is refused with
     * {@link Commit#NOT_OFFICIAL} and records nothing, so it can never issue
     * official loot or open the End.
     */
    public Commit commitDefeat(boolean official, Set<UUID> occupantRoster) {
        if (stage != Stage.NONE) {
            return Commit.ALREADY_COMMITTED;
        }
        if (!official) {
            return Commit.NOT_OFFICIAL;
        }
        Objects.requireNonNull(occupantRoster, "roster");
        if (occupantRoster.isEmpty() || occupantRoster.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("official roster must contain distinct players");
        }
        this.official = true;
        this.roster = Set.copyOf(occupantRoster);
        this.stage = Stage.DEFEAT;
        return Commit.COMMITTED;
    }

    /**
     * Advances one step along the ladder. The stage never moves backwards and
     * never skips: a replay of the current stage returns
     * {@link Advance#ALREADY_SETTLED}, and anything else returns
     * {@link Advance#OUT_OF_ORDER}. Before the defeat is committed every advance
     * is refused with {@link Advance#NOT_COMMITTED}.
     */
    public Advance advance(Stage next) {
        Objects.requireNonNull(next, "next stage");
        if (!official) {
            return Advance.NOT_COMMITTED;
        }
        if (next == stage) {
            return Advance.ALREADY_SETTLED;
        }
        if (next.ordinal() <= stage.ordinal()) {
            return Advance.OUT_OF_ORDER;
        }
        if (next.ordinal() != stage.ordinal() + 1) {
            return Advance.OUT_OF_ORDER;
        }
        stage = next;
        return Advance.ADVANCED;
    }

    /** Convenience: advance to {@code next} and require success. */
    public boolean advanceOrFail(Stage next) {
        return advance(next) == Advance.ADVANCED;
    }

    /**
     * Records one durable reward operation keyed by its operation id. The same
     * operation id can be applied exactly once; retries re-use the SAME id and
     * therefore return {@link RewardGrant#ALREADY_APPLIED} instead of paying
     * twice. Operations are only accepted once rewards are being issued.
     */
    public RewardGrant recordReward(String operationId) {
        if (operationId == null || operationId.trim().isEmpty()) {
            return RewardGrant.INVALID;
        }
        if (!official || !atLeast(Stage.REWARDS)) {
            return RewardGrant.NOT_COMMITTED;
        }
        String key = operationId.trim();
        if (rewardOps.containsKey(key)) {
            return RewardGrant.ALREADY_APPLIED;
        }
        rewardOps.put(key, "COMMITTED");
        return RewardGrant.GRANTED;
    }

    /** True when the operation id has already been recorded for this event. */
    public boolean rewardApplied(String operationId) {
        return operationId != null && rewardOps.containsKey(operationId.trim());
    }

    public int rewardOperationCount() {
        return rewardOps.size();
    }

    /**
     * Commits the WorldCore global End-open intent exactly once. Returns whether
     * this call actually committed it. A restart that finds the intent already
     * committed must not re-issue it.
     */
    public boolean commitEndUnlock() {
        if (!official || !atLeast(Stage.END_OPEN)) {
            return false;
        }
        if (endUnlockCommitted) {
            return false;
        }
        endUnlockCommitted = true;
        return true;
    }

    public boolean endUnlockCommitted() {
        return endUnlockCommitted;
    }

    /**
     * An async redirect may act only when the durable intent is committed AND a
     * live authoritative read-back still reports the End open. A previously
     * locked WorldCore that has not applied the state yet must not be trusted.
     */
    public boolean endUnlockVisible(boolean liveReadBack) {
        return endUnlockCommitted && liveReadBack;
    }

    /** A commit or unlock callback is stale once the encounter generation moved. */
    public boolean acceptsGeneration(long callbackGeneration) {
        return callbackGeneration == generation;
    }

    /** Compact durable progress for the stage, roster fence and once-only receipts. */
    public Map<String, String> encode() {
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(KEY_PREFIX + "generation", Long.toString(generation));
        encoded.put(KEY_PREFIX + "stage", stage.name());
        if (official) {
            encoded.put(KEY_PREFIX + "official", "DONE");
        }
        for (UUID player : roster) {
            encoded.put(KEY_PREFIX + "roster." + player, "DONE");
        }
        for (String operation : rewardOps.keySet()) {
            encoded.put(KEY_PREFIX + "reward." + operation, "COMMITTED");
        }
        if (endUnlockCommitted) {
            encoded.put(KEY_PREFIX + "end-open", VERSION);
        }
        return Map.copyOf(encoded);
    }

    /** Restore a coordinator from progress, or null when nothing was recorded. */
    public static EndRiftVictoryCoordinatorPolicy decode(Map<String, String> progress, long generation) {
        if (progress == null
                || progress.keySet().stream().noneMatch(key -> key.startsWith(KEY_PREFIX))) {
            return null;
        }
        long stored;
        try {
            stored = Long.parseLong(progress.get(KEY_PREFIX + "generation"));
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("INVALID_VICTORY_GENERATION", malformed);
        }
        if (stored != generation || stored <= 0L) {
            throw new IllegalArgumentException("FOREIGN_VICTORY_GENERATION");
        }
        String rawStage = progress.get(KEY_PREFIX + "stage");
        Stage restored;
        try {
            restored = Stage.valueOf(rawStage);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("INVALID_VICTORY_STAGE");
        }
        // A live coordinator is keyed by the event id; recovery uses a stable
        // placeholder because the id is not part of this progress blob.
        EndRiftVictoryCoordinatorPolicy coordinator =
                new EndRiftVictoryCoordinatorPolicy("recovered", generation);
        Set<UUID> restoredRoster = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : progress.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX)) {
                continue;
            }
            String suffix = key.substring(KEY_PREFIX.length());
            if (suffix.equals("generation") || suffix.equals("stage")) {
                continue;
            }
            if (!"DONE".equals(entry.getValue()) && !"COMMITTED".equals(entry.getValue())
                    && !VERSION.equals(entry.getValue())) {
                throw new IllegalArgumentException("INVALID_VICTORY_RECEIPT");
            }
            if (suffix.startsWith("roster.")) {
                try {
                    restoredRoster.add(UUID.fromString(suffix.substring("roster.".length())));
                } catch (RuntimeException invalid) {
                    throw new IllegalArgumentException("INVALID_VICTORY_ROSTER", invalid);
                }
            } else if (suffix.startsWith("reward.")) {
                coordinator.rewardOps.put(suffix.substring("reward.".length()), "COMMITTED");
            } else if (suffix.equals("official")) {
                coordinator.official = true;
            } else if (suffix.equals("end-open")) {
                coordinator.endUnlockCommitted = true;
            } else {
                throw new IllegalArgumentException("UNKNOWN_VICTORY_KEY");
            }
        }
        if (restored.ordinal() < Stage.DEFEAT.ordinal()) {
            throw new IllegalArgumentException("INVALID_VICTORY_STAGE");
        }
        coordinator.stage = restored;
        coordinator.roster = Set.copyOf(restoredRoster);
        if (!coordinator.official) {
            throw new IllegalArgumentException("VICTORY_STAGE_WITHOUT_OFFICIAL_DEFEAT");
        }
        if (coordinator.endUnlockCommitted && !coordinator.atLeast(Stage.END_OPEN)) {
            throw new IllegalArgumentException("VICTORY_END_OPEN_BEFORE_UNLOCK_STAGE");
        }
        return coordinator;
    }
}
