package me.copimine.endevent.domain;

import java.util.Map;
import java.util.Set;

/**
 * Durable Wave 6 stage and reinforcement-debt state.
 *
 * <p>The ritual sphere snapshot already carried the mage order, the defeat
 * receipts and the prisoner. What it did NOT carry was the thing that actually
 * decides what happens next: which stage is running, which of its guards are
 * already up, and how many reinforcement packs the wave owes versus how many it
 * released.</p>
 *
 * <p>Without those, a restart inside Wave 6 rebuilt the stage sequence from
 * nothing. Guards that were already standing were counted as debt that had
 * never been paid, so the wave released them twice; a mage killed between the
 * save and the restart handed out its signature again; and the final clear could
 * fire while a mandatory pack was still owed, ending the wave early. This codec
 * is the missing receipt: it is written on every save and read back before the
 * sequence resumes.</p>
 *
 * <p>Strict by design. An unknown key, a stage index outside the authored count,
 * or receipts that disagree with the roster order is an error, because a wave
 * that resumes from a state nobody can explain is worse than a wave that refuses
 * to resume.</p>
 */
public final class WaveSixDurableState {
    private static final String PREFIX = "wave6.";
    private static final String STAGE = PREFIX + "stage";
    private static final String GUARD_SLOTS_FILLED = PREFIX + "guard-slots-filled";
    private static final String REINFORCEMENT_OWED = PREFIX + "reinforcement-owed";
    private static final String REINFORCEMENT_SPAWNED = PREFIX + "reinforcement-spawned";
    private static final String FINAL_CLEAR_COMMITTED = PREFIX + "final-clear-committed";
    private static final Set<String> CURRENT_KEYS = Set.of(
            STAGE, GUARD_SLOTS_FILLED, REINFORCEMENT_OWED, REINFORCEMENT_SPAWNED,
            FINAL_CLEAR_COMMITTED);

    private WaveSixDurableState() {
    }

    /**
     * A complete, self-consistent Wave 6 receipt.
     *
     * @param stageIndex            zero-based index of the running stage, equal
     *                              to the number of mages already defeated
     * @param guardSlotsFilled      how many guard posts of the current stage are
     *                              already occupied by live bodies
     * @param reinforcementOwed     packs committed by the wave so far
     * @param reinforcementSpawned  packs actually released into the arena
     * @param finalClearCommitted   the final clear receipt was already granted
     */
    public record State(int stageIndex, int guardSlotsFilled,
                        int reinforcementOwed, int reinforcementSpawned,
                        boolean finalClearCommitted) {
        public State {
            if (stageIndex < 0 || stageIndex > WaveSixSequencePolicy.STAGE_COUNT) {
                throw new IllegalArgumentException("Wave 6 stage index is outside the authored sequence");
            }
            if (guardSlotsFilled < 0) {
                throw new IllegalArgumentException("Wave 6 filled guard slots cannot be negative");
            }
            if (reinforcementOwed < 0 || reinforcementSpawned < 0) {
                throw new IllegalArgumentException("Wave 6 reinforcement counts cannot be negative");
            }
            if (reinforcementSpawned > reinforcementOwed) {
                // Releasing more packs than the wave ever committed means either
                // a duplicated spawn or a lost commit receipt. Never resume on it.
                throw new IllegalArgumentException(
                        "Wave 6 released more reinforcement packs than it committed");
            }
        }

        /** True while a mage stage still has bodies to clear. */
        public boolean stageActive() {
            return stageIndex < WaveSixSequencePolicy.STAGE_COUNT;
        }

        /** Packs that are committed but not yet in the arena. */
        public int outstandingPacks() {
            return reinforcementOwed - reinforcementSpawned;
        }
    }

    /**
     * Encode the durable part of the wave.
     *
     * @return an immutable map, or an empty map when there is nothing to say
     */
    public static Map<String, String> encode(int stageIndex, int guardSlotsFilled,
                                             int reinforcementOwed, int reinforcementSpawned,
                                             boolean finalClearCommitted) {
        State state = new State(stageIndex, guardSlotsFilled,
                reinforcementOwed, reinforcementSpawned, finalClearCommitted);
        return Map.of(
                STAGE, Integer.toString(state.stageIndex()),
                GUARD_SLOTS_FILLED, Integer.toString(state.guardSlotsFilled()),
                REINFORCEMENT_OWED, Integer.toString(state.reinforcementOwed()),
                REINFORCEMENT_SPAWNED, Integer.toString(state.reinforcementSpawned()),
                FINAL_CLEAR_COMMITTED, Boolean.toString(state.finalClearCommitted()));
    }

    /** True when the encoded map carries any Wave 6 durable key at all. */
    public static boolean present(Map<String, String> encoded) {
        return encoded != null && encoded.keySet().stream()
                .anyMatch(key -> key != null && key.startsWith(PREFIX));
    }

    /**
     * Decode a previously written receipt.
     *
     * <p>A snapshot written before this codec existed simply has no keys; that
     * is reported as {@link Data#present()} false so the caller can rebuild from
     * the roster instead of failing the attempt.</p>
     *
     * @param encoded            the persisted objective map
     * @param defeatedIdentities mage identities with a committed defeat receipt
     * @param guardSlots         authored guard posts per caster
     */
    public static Data decode(Map<String, String> encoded,
                              java.util.List<MageSignaturePolicy.MageId> defeatedIdentities,
                              int guardSlots) {
        if (!present(encoded)) {
            return new Data(null, false);
        }
        for (String key : encoded.keySet()) {
            if (key != null && key.startsWith(PREFIX) && !CURRENT_KEYS.contains(key)) {
                throw new IllegalArgumentException("Wave 6 durable state has unknown key: " + key);
            }
        }
        int stageIndex = parseInt(required(encoded, STAGE), STAGE);
        int defeated = defeatedIdentities == null ? 0 : defeatedIdentities.size();
        if (stageIndex != defeated) {
            // The stage index IS the defeat count in a sequential sequence. A
            // disagreement means one of the two receipts is a lie.
            throw new IllegalArgumentException(
                    "Wave 6 stage index disagrees with the defeat receipts");
        }
        int filled = parseInt(required(encoded, GUARD_SLOTS_FILLED), GUARD_SLOTS_FILLED);
        if (guardSlots < 0 || filled > Math.max(1, guardSlots)) {
            throw new IllegalArgumentException("Wave 6 filled guard slots exceed the authored ring");
        }
        State state = new State(stageIndex, filled,
                parseInt(required(encoded, REINFORCEMENT_OWED), REINFORCEMENT_OWED),
                parseInt(required(encoded, REINFORCEMENT_SPAWNED), REINFORCEMENT_SPAWNED),
                parseBoolean(required(encoded, FINAL_CLEAR_COMMITTED), FINAL_CLEAR_COMMITTED));
        return new Data(state, true);
    }

    /** Decoded receipt plus whether the snapshot actually carried one. */
    public record Data(State state, boolean present) {
        public State requireState() {
            if (state == null) {
                throw new IllegalStateException("Wave 6 durable state is absent from the snapshot");
            }
            return state;
        }
    }

    private static String required(Map<String, String> encoded, String key) {
        String value = encoded.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Wave 6 durable state is missing " + key);
        }
        return value;
    }

    private static int parseInt(String value, String key) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 6 durable state has invalid integer in " + key, error);
        }
    }

    private static boolean parseBoolean(String value, String key) {
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) return true;
        if ("false".equalsIgnoreCase(trimmed)) return false;
        throw new IllegalArgumentException("Wave 6 durable state has invalid boolean in " + key);
    }
}
