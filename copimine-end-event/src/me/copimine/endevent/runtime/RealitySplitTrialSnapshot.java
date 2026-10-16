package me.copimine.endevent.runtime;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.copimine.endevent.runtime.RealitySplitTrialController.NamedData;

/** Strict codec for the generation-scoped progress of Wave 7 trials. */
public final class RealitySplitTrialSnapshot {
    private static final String PREFIX = "reality-split-trial.";
    private static final String SCHEMA = PREFIX + "schema";
    private static final String LAYOUT = PREFIX + "layout";
    private static final int LEGACY_SCHEMA = 1;
    private static final String LEGACY_LAYOUT = "legacy-four-trials";
    private static final String GENERATION = PREFIX + "generation";
    private static final String TRIAL_PREFIX = PREFIX + "room.";
    private static final int NAMED_SCHEMA = 2;
    public static final String NAMED_LAYOUT = "named-parallel-trials";
    private static final String EVENT = PREFIX + "event";
    private static final String SELECTED = PREFIX + "selected";

    private RealitySplitTrialSnapshot() {
    }

    public static Map<String, String> encode(long generation,
                                             Map<Integer, RealitySplitTrialController.TrialState> trials) {
        if (generation <= 0L || trials == null || trials.isEmpty()
                || trials.size() > 4) {
            throw new IllegalArgumentException("Wave 7 trial snapshot is empty or invalid");
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        validateLegacy(trials);
        encoded.put(SCHEMA, Integer.toString(LEGACY_SCHEMA));
        encoded.put(LAYOUT, LEGACY_LAYOUT);
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
        if (encoded != null && encoded.keySet().stream().anyMatch(key -> key == null)) {
            throw new IllegalArgumentException("Wave 7 trial snapshot has null keys");
        }
        if (encoded == null || encoded.keySet().stream().noneMatch(key -> key.startsWith(PREFIX))) {
            return new Data(Long.MIN_VALUE, Map.of());
        }
        // An entirely unversioned snapshot belongs to the old runtime. Never
        // reinterpret it as Echo/Marksman/Archmage merely by renaming an enum.
        if (encoded.containsKey(SCHEMA) || encoded.containsKey(LAYOUT)) {
            if (parseInt(encoded.get(SCHEMA), SCHEMA) != LEGACY_SCHEMA
                    || !LEGACY_LAYOUT.equals(encoded.get(LAYOUT))) {
                throw new IllegalArgumentException("UNSUPPORTED_WAVE7_TRIAL_LAYOUT_OR_SCHEMA");
            }
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
        validateLegacy(trials);
        return new Data(generation, Map.copyOf(trials));
    }

    /** This is a new layout. Active schema-1 gameplay has no safe implicit migration. */
    public static Map<String, String> encodeNamed(NamedData data) {
        if (data == null) throw new IllegalArgumentException("named Wave 7 snapshot is missing");
        Map<String, String> encoded = new LinkedHashMap<>();
        encoded.put(SCHEMA, Integer.toString(NAMED_SCHEMA));
        encoded.put(LAYOUT, NAMED_LAYOUT);
        encoded.put(EVENT, data.eventId().toString());
        encoded.put(GENERATION, Long.toString(data.generation()));
        encoded.put(SELECTED, data.selectedLayout().stream().map(Enum::name).collect(Collectors.joining(",")));
        for (int room = 0; room < data.selectedLayout().size(); room++) {
            var state = data.trials().get(room);
            encoded.put(TRIAL_PREFIX + room, state.trial().name() + ":" + state.stage().name()
                    + ":" + encodeOwners(state.owners()) + ":" + encodeOwners(state.echoReceipts().keySet()));
        }
        return Map.copyOf(encoded);
    }

    public static NamedData decodeNamed(Map<String, String> encoded, UUID expectedEvent,
                                         long expectedGeneration) {
        if (encoded == null || encoded.keySet().stream().anyMatch(key -> key == null)) {
            throw new IllegalArgumentException("MALFORMED_NAMED_WAVE7_SNAPSHOT");
        }
        if (!Integer.toString(NAMED_SCHEMA).equals(encoded.get(SCHEMA))
                || !NAMED_LAYOUT.equals(encoded.get(LAYOUT))) {
            throw new IllegalArgumentException("RECOVERY_REQUIRED_UNSUPPORTED_WAVE7_TRIAL_LAYOUT_OR_SCHEMA");
        }
        UUID eventId = parseUuid(encoded.get(EVENT), EVENT);
        long generation = parseLong(encoded.get(GENERATION), GENERATION);
        if (expectedEvent == null || !expectedEvent.equals(eventId) || expectedGeneration <= 0L
                || generation != expectedGeneration) {
            throw new IllegalArgumentException("named Wave 7 event or generation does not match");
        }
        String selected = encoded.get(SELECTED);
        if (selected == null || selected.length() > 100) {
            throw new IllegalArgumentException("named Wave 7 selected layout is missing or oversized");
        }
        List<RealitySplitTrialController.NamedTrial> layout = java.util.Arrays.stream(selected.split(",", -1))
                .map(raw -> parseEnum(RealitySplitTrialController.NamedTrial.class, raw, SELECTED)).toList();
        if (layout.isEmpty() || layout.size() > 4) {
            throw new IllegalArgumentException("invalid named Wave 7 room count");
        }
        Set<String> requiredKeys = new HashSet<>(Set.of(SCHEMA, LAYOUT, EVENT, GENERATION, SELECTED));
        for (int room = 0; room < layout.size(); room++) requiredKeys.add(TRIAL_PREFIX + room);
        Set<String> presentKeys = encoded.keySet().stream().filter(key -> key.startsWith(PREFIX))
                .collect(Collectors.toSet());
        if (!requiredKeys.equals(presentKeys)) {
            throw new IllegalArgumentException("named Wave 7 snapshot has missing or unknown fields");
        }
        Map<Integer, RealitySplitTrialController.NamedTrialState> trials = new LinkedHashMap<>();
        for (int room = 0; room < layout.size(); room++) {
            String raw = encoded.get(TRIAL_PREFIX + room);
            if (raw == null || raw.length() > 1600) {
                throw new IllegalArgumentException("named Wave 7 room is missing or oversized");
            }
            String[] parts = raw.split(":", -1);
            if (parts.length != 4) throw new IllegalArgumentException("malformed named Wave 7 room");
            var trial = parseEnum(RealitySplitTrialController.NamedTrial.class, parts[0], TRIAL_PREFIX + room);
            var stage = parseEnum(RealitySplitTrialController.Stage.class, parts[1], TRIAL_PREFIX + room);
            Set<UUID> owners = parseOwners(parts[2], false);
            Set<UUID> completedOwners = parseOwners(parts[3], true);
            Map<UUID, RealitySplitTrialController.EchoCompletionReceipt> receipts = new LinkedHashMap<>();
            for (UUID owner : completedOwners) {
                receipts.put(owner, new RealitySplitTrialController.EchoCompletionReceipt(eventId, generation, room, owner));
            }
            trials.put(room, new RealitySplitTrialController.NamedTrialState(room, trial, stage, owners, receipts));
        }
        return new NamedData(eventId, generation, layout, trials);
    }

    private static String encodeOwners(Set<UUID> owners) {
        return owners.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    private static Set<UUID> parseOwners(String raw, boolean mayBeEmpty) {
        if (mayBeEmpty && raw.isEmpty()) return Set.of();
        String[] values = raw.split(",", -1);
        if (values.length > 20) throw new IllegalArgumentException("too many named Wave 7 owners");
        Set<UUID> owners = new HashSet<>();
        for (String value : values) {
            if (!owners.add(parseUuid(value, "owner"))) {
                throw new IllegalArgumentException("duplicate named Wave 7 owner");
            }
        }
        return Set.copyOf(owners);
    }

    private static UUID parseUuid(String raw, String key) {
        try {
            UUID uuid = UUID.fromString(raw);
            if (!uuid.toString().equals(raw)) throw new IllegalArgumentException("non-canonical UUID");
            return uuid;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid named Wave 7 UUID " + key, error);
        }
    }

    private static void validateLegacy(Map<Integer, RealitySplitTrialController.TrialState> trials) {
        var identities = List.of(RealitySplitTrialController.Trial.WARDEN,
                RealitySplitTrialController.Trial.RIFT_REFLECTION,
                RealitySplitTrialController.Trial.JUGGERNAUT,
                RealitySplitTrialController.Trial.RIFT_HUNTER);
        if (trials == null || trials.isEmpty() || trials.size() > 4) {
            throw new IllegalArgumentException("invalid legacy Wave 7 trial count");
        }
        for (int room = 0; room < trials.size(); room++) {
            var state = trials.get(room);
            if (state == null || state.chamber() != room || state.trial() != identities.get(room)
                    || !validStage(state)) {
                throw new IllegalArgumentException("legacy Wave 7 room identity or progress is inconsistent");
            }
        }
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
