package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict codec for the generation-scoped progress of Wave 7 trials. */
public final class RealitySplitTrialSnapshot {
    private static final String PREFIX = "reality-split-trial.";
    private static final String GENERATION = PREFIX + "generation";
    private static final String TRIAL_PREFIX = PREFIX + "room.";

    private RealitySplitTrialSnapshot() {
    }

    public static Map<String, String> encode(long generation,
                                             Map<Integer, RealitySplitTrialController.TrialState> trials) {
        if (generation <= 0L || trials == null || trials.isEmpty()
                || trials.size() > 4) {
            throw new IllegalArgumentException("Wave 7 trial snapshot is empty or invalid");
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(GENERATION, Long.toString(generation));
        for (Map.Entry<Integer, RealitySplitTrialController.TrialState> entry
                : trials.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            int chamber = entry.getKey() == null ? -1 : entry.getKey();
            RealitySplitTrialController.TrialState state = entry.getValue();
            if (chamber < 0 || state == null || state.chamber() != chamber
                    || chamber >= 4 || !validStage(state)) {
                throw new IllegalArgumentException("Wave 7 trial snapshot contains invalid state");
            }
            encoded.put(TRIAL_PREFIX + chamber, state.trial().name() + ":"
                    + state.stage().name() + ":" + state.progress() + ":" + state.required());
        }
        return Map.copyOf(encoded);
    }

    public static Data decode(Map<String, String> encoded, long expectedGeneration) {
        if (encoded == null || encoded.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            return new Data(Long.MIN_VALUE, Map.of());
        }
        long generation = parseLong(encoded.get(GENERATION), GENERATION);
        if (expectedGeneration <= 0L || generation != expectedGeneration) {
            throw new IllegalArgumentException("Wave 7 trial snapshot generation does not match");
        }
        Map<Integer, RealitySplitTrialController.TrialState> trials = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            if (!entry.getKey().startsWith(TRIAL_PREFIX)) continue;
            int chamber = parseInt(entry.getKey().substring(TRIAL_PREFIX.length()), entry.getKey());
            String[] parts = entry.getValue().split(":", -1);
            if (chamber < 0 || chamber >= 4 || parts.length != 4) {
                throw new IllegalArgumentException("Wave 7 trial entry is malformed");
            }
            RealitySplitTrialController.Trial trial = parseEnum(
                    RealitySplitTrialController.Trial.class, parts[0], entry.getKey());
            RealitySplitTrialController.Stage stage = parseEnum(
                    RealitySplitTrialController.Stage.class, parts[1], entry.getKey());
            RealitySplitTrialController.TrialState state = new RealitySplitTrialController.TrialState(
                    chamber, trial, stage, parseInt(parts[2], entry.getKey()),
                    parseInt(parts[3], entry.getKey()));
            if (!validStage(state) || trials.put(chamber, state) != null) {
                throw new IllegalArgumentException("Wave 7 trial entry has invalid progress");
            }
        }
        if (trials.isEmpty()) {
            throw new IllegalArgumentException("Wave 7 trial snapshot has no rooms");
        }
        return new Data(generation, Map.copyOf(trials));
    }

    private static boolean validStage(RealitySplitTrialController.TrialState state) {
        int expected = switch (state.trial()) {
            case WARDEN, RIFT_HUNTER -> 1;
            case RIFT_REFLECTION, JUGGERNAUT -> 3;
        };
        if (state.required() != expected || state.progress() < 0
                || state.progress() > expected) {
            return false;
        }
        return switch (state.trial()) {
            case WARDEN, RIFT_HUNTER -> state.progress() == 0
                    && (state.stage() == RealitySplitTrialController.Stage.ACTIVE
                    || state.stage() == RealitySplitTrialController.Stage.COMPLETE);
            case RIFT_REFLECTION -> state.progress() == expected
                    ? state.stage() == RealitySplitTrialController.Stage.COMPLETE
                    : state.stage() == RealitySplitTrialController.Stage.ACTIVE;
            case JUGGERNAUT -> state.progress() == expected
                    ? state.stage() == RealitySplitTrialController.Stage.EXPOSED
                    || state.stage() == RealitySplitTrialController.Stage.COMPLETE
                    : state.stage() == RealitySplitTrialController.Stage.ACTIVE;
        };
    }

    private static int parseInt(String raw, String key) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 trial snapshot has invalid integer " + key, error);
        }
    }

    private static long parseLong(String raw, String key) {
        try {
            return Long.parseLong(raw == null ? "" : raw.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 trial snapshot has invalid long " + key, error);
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String raw, String key) {
        try {
            return Enum.valueOf(type, raw == null ? "" : raw.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 trial snapshot has invalid enum " + key, error);
        }
    }

    public record Data(long generation,
                       Map<Integer, RealitySplitTrialController.TrialState> trials) {
        public Data {
            trials = Map.copyOf(trials == null ? Map.of() : trials);
        }
    }
}
