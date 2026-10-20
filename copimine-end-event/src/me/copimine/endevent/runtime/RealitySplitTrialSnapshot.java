package me.copimine.endevent.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.copimine.endevent.domain.wave7.EchoBehaviorStrategy;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorLifecycle;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.ActorState;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.DuelRecord;
import me.copimine.endevent.runtime.RealitySplitEchoDuelState.Lifecycle;
import me.copimine.endevent.runtime.RealitySplitTrialController.EchoCompletionReceipt;
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
    private static final int NAMED_SCHEMA = 5;
    private static final int PREVIOUS_NAMED_SCHEMA = 4;
    public static final String NAMED_LAYOUT = "named-parallel-trials";
    private static final String EVENT = PREFIX + "event";
    private static final String SELECTED = PREFIX + "selected";
    private static final String ECHO_DUEL_PREFIX = PREFIX + "echo.duel.";
    private static final String ECHO_RECEIPT_PREFIX = PREFIX + "echo.receipt.";
    private static final int MAX_ECHO_RECORD_LENGTH = 65_536;

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
            Map<UUID, DuelRecord> duels = data.personalEchoDuels().get(room);
            if (duels != null) {
                for (UUID owner : duels.keySet().stream().sorted().toList()) {
                    encoded.put(ECHO_DUEL_PREFIX + room + "." + owner, encodeDuel(duels.get(owner)));
                }
            }
            for (UUID owner : state.echoReceipts().keySet().stream().sorted().toList()) {
                encoded.put(ECHO_RECEIPT_PREFIX + room + "." + owner,
                        encodeReceipt(state.echoReceipts().get(owner)));
            }
        }
        return Map.copyOf(encoded);
    }

    public static NamedData decodeNamed(Map<String, String> encoded, UUID expectedEvent,
                                         long expectedGeneration) {
        return decodeNamed(encoded, expectedEvent, expectedGeneration, 0L);
    }

    /** When expectedRuntimeGeneration is positive, every persisted owner duel must match it. */
    public static NamedData decodeNamed(Map<String, String> encoded, UUID expectedEvent,
                                         long expectedGeneration, long expectedRuntimeGeneration) {
        if (encoded == null || encoded.keySet().stream().anyMatch(key -> key == null)) {
            throw new IllegalArgumentException("MALFORMED_NAMED_WAVE7_SNAPSHOT");
        }
        encoded = upgradeKnownNamedSchema(encoded);
        if (expectedRuntimeGeneration < 0L) {
            throw new IllegalArgumentException("named Wave 7 runtime generation is invalid");
        }
        if (!Integer.toString(NAMED_SCHEMA).equals(encoded.get(SCHEMA))
                || !NAMED_LAYOUT.equals(encoded.get(LAYOUT))) {
            throw new IllegalArgumentException("RECOVERY_REQUIRED_UNSUPPORTED_WAVE7_TRIAL_LAYOUT_OR_SCHEMA");
        }
        UUID eventId = parseUuid(encoded.get(EVENT), EVENT);
        long generation = parseLong(encoded.get(GENERATION), GENERATION);
        if (!Long.toString(generation).equals(encoded.get(GENERATION))
                || expectedEvent == null || !expectedEvent.equals(eventId) || expectedGeneration <= 0L
                || generation != expectedGeneration) {
            throw new IllegalArgumentException("named Wave 7 event or generation does not match");
        }
        String selected = encoded.get(SELECTED);
        if (selected == null || selected.length() > 100) {
            throw new IllegalArgumentException("named Wave 7 selected layout is missing or oversized");
        }
        List<RealitySplitTrialController.NamedTrial> layout = java.util.Arrays.stream(selected.split(",", -1))
                .map(raw -> parseEnum(RealitySplitTrialController.NamedTrial.class, raw, SELECTED)).toList();
        if (layout.isEmpty() || layout.size() > 4
                || !layout.stream().map(Enum::name).collect(Collectors.joining(",")).equals(selected)) {
            throw new IllegalArgumentException("invalid named Wave 7 room count");
        }
        Set<String> requiredKeys = new HashSet<>(Set.of(SCHEMA, LAYOUT, EVENT, GENERATION, SELECTED));
        for (int room = 0; room < layout.size(); room++) requiredKeys.add(TRIAL_PREFIX + room);
        Map<Integer, Set<UUID>> roomOwners = new LinkedHashMap<>();
        Map<Integer, Set<UUID>> roomReceipts = new LinkedHashMap<>();
        Map<Integer, RealitySplitTrialController.NamedTrial> roomTrials = new LinkedHashMap<>();
        Map<Integer, RealitySplitTrialController.Stage> roomStages = new LinkedHashMap<>();
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
            if (!(trial.name() + ":" + stage.name() + ":" + encodeOwners(owners) + ":"
                    + encodeOwners(completedOwners)).equals(raw)) {
                throw new IllegalArgumentException("non-canonical named Wave 7 room");
            }
            roomOwners.put(room, owners);
            roomReceipts.put(room, completedOwners);
            roomTrials.put(room, trial);
            roomStages.put(room, stage);
            if (trial == RealitySplitTrialController.NamedTrial.PERSONAL_ECHO) {
                for (UUID owner : owners) requiredKeys.add(echoDuelKey(room, owner));
                for (UUID owner : completedOwners) requiredKeys.add(echoReceiptKey(room, owner));
            }
        }
        Set<String> presentKeys = encoded.keySet().stream().filter(key -> key.startsWith(PREFIX))
                .collect(Collectors.toSet());
        if (!requiredKeys.equals(presentKeys)) {
            throw new IllegalArgumentException("named Wave 7 snapshot has missing or unknown fields");
        }
        Map<Integer, Map<UUID, DuelRecord>> duels = new LinkedHashMap<>();
        Map<Integer, RealitySplitTrialController.NamedTrialState> boundTrials = new LinkedHashMap<>();
        for (int room = 0; room < layout.size(); room++) {
            var trial = roomTrials.get(room);
            var stage = roomStages.get(room);
            if (trial == RealitySplitTrialController.NamedTrial.PERSONAL_ECHO) {
                Map<UUID, DuelRecord> roomDuels = new LinkedHashMap<>();
                for (UUID owner : roomOwners.get(room)) {
                    DuelRecord duel = decodeDuel(encoded.get(echoDuelKey(room, owner)), eventId, generation, room, owner);
                    // A server restart has no process-local runtime generation to compare
                    // against. The persisted duel identity is still bound to this event,
                    // attempt, room and owner; the world adapter must reconcile its actor
                    // UUID and PDC identity before resuming it. A caller with an explicit
                    // live runtime generation still gets the stricter equality check below.
                    if (expectedRuntimeGeneration > 0L
                            && (duel.lifecycle() == Lifecycle.ACTIVE
                            || duel.lifecycle() == Lifecycle.OWNER_OFFLINE)
                            && duel.runtimeGeneration() != expectedRuntimeGeneration) {
                        throw new IllegalArgumentException("personal Echo runtime generation does not match");
                    }
                    roomDuels.put(owner, duel);
                }
                Map<UUID, EchoCompletionReceipt> receipts = new LinkedHashMap<>();
                for (UUID owner : roomReceipts.get(room)) {
                    receipts.put(owner, decodeReceipt(encoded.get(echoReceiptKey(room, owner)), eventId,
                            generation, room, owner));
                }
                var bound = new RealitySplitTrialController.NamedTrialState(room, trial,
                        stage, roomOwners.get(room), receipts);
                duels.put(room, roomDuels);
                boundTrials.put(room, bound);
            } else {
                boundTrials.put(room, new RealitySplitTrialController.NamedTrialState(room, trial, stage,
                        roomOwners.get(room), Map.of()));
            }
        }
        return new NamedData(eventId, generation, layout, boundTrials, duels);
    }

    private static String echoDuelKey(int room, UUID owner) {
        return ECHO_DUEL_PREFIX + room + "." + owner;
    }

    private static String echoReceiptKey(int room, UUID owner) {
        return ECHO_RECEIPT_PREFIX + room + "." + owner;
    }

    private static String encodeDuel(DuelRecord duel) {
        String actor = duel.actor() == null ? "-" : String.join(":",
                duel.actor().actorUuid().toString(), Double.toHexString(duel.actor().currentHealth()),
                Double.toHexString(duel.actor().maximumHealth()), duel.actor().lifecycle().name(),
                Long.toString(duel.actor().revision()), Long.toString(duel.actor().nextActionTick()),
                Long.toString(duel.actor().meleeCooldownUntilTick()),
                Long.toString(duel.actor().rangedCooldownUntilTick()),
                Long.toString(duel.actor().pendingUseUntilTick()), Long.toString(duel.actor().deathSerial()),
                Long.toString(duel.actor().shieldActiveUntilTick()),
                Long.toString(duel.actor().shieldCooldownUntilTick()),
                Long.toString(duel.actor().jumpCooldownUntilTick()));
        String replica = duel.replicaState().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> base64(entry.getKey()) + "=" + base64(entry.getValue()))
                .collect(Collectors.joining(","));
        String strategy = duel.behaviorStrategy() == null ? "-" : base64(duel.behaviorStrategy().encode());
        String encoded = String.join("|", duel.eventId().toString(), Long.toString(duel.attemptGeneration()),
                Long.toString(duel.runtimeGeneration()), Integer.toString(duel.room()), duel.duelId().toString(),
                duel.ownerUuid().toString(), duel.lifecycle().name(), Double.toHexString(duel.ownerStartingHealth()),
                Double.toHexString(duel.ownerMaximumHealth()), Long.toString(duel.returnDeadlineMillis()),
                Long.toString(duel.revision()), actor, Long.toString(duel.replicaRevision()), replica, strategy);
        if (encoded.length() > MAX_ECHO_RECORD_LENGTH) throw new IllegalArgumentException("oversized Echo duel snapshot");
        return encoded;
    }

    private static DuelRecord decodeDuel(String raw, UUID event, long generation, int room, UUID owner) {
        if (raw == null || raw.length() > MAX_ECHO_RECORD_LENGTH) {
            throw new IllegalArgumentException("missing or oversized personal Echo duel record");
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 15) throw new IllegalArgumentException("malformed personal Echo duel record");
        UUID recordEvent = parseUuid(parts[0], "echo event");
        long attempt = parseLong(parts[1], "echo attempt");
        long runtime = parseLong(parts[2], "echo runtime");
        int recordRoom = parseInt(parts[3], "echo room");
        UUID duelId = parseUuid(parts[4], "echo duel");
        UUID recordOwner = parseUuid(parts[5], "echo owner");
        Lifecycle lifecycle = parseEnum(Lifecycle.class, parts[6], "echo lifecycle");
        double ownerHealth = parseDouble(parts[7], "owner health");
        double ownerMaxHealth = parseDouble(parts[8], "owner maximum health");
        long deadline = parseLong(parts[9], "owner return deadline");
        long revision = parseLong(parts[10], "duel revision");
        ActorState actor = decodeActor(parts[11]);
        long replicaRevision = parseLong(parts[12], "replica revision");
        Map<String, String> replica = decodeReplica(parts[13]);
        EchoBehaviorStrategy strategy = parts[14].equals("-") ? null
                : EchoBehaviorStrategy.decode(unbase64(parts[14]));
        if (!event.equals(recordEvent) || attempt != generation || recordRoom != room || !owner.equals(recordOwner)) {
            throw new IllegalArgumentException("foreign personal Echo duel record");
        }
        DuelRecord duel = new DuelRecord(recordEvent, attempt, runtime, recordRoom, duelId, recordOwner,
                lifecycle, ownerHealth, ownerMaxHealth, deadline, revision, actor, replicaRevision, replica,
                strategy);
        if (!encodeDuel(duel).equals(raw)) throw new IllegalArgumentException("non-canonical personal Echo duel record");
        return duel;
    }

    private static ActorState decodeActor(String raw) {
        if (raw.equals("-")) return null;
        String[] parts = raw.split(":", -1);
        if (parts.length != 13) throw new IllegalArgumentException("malformed personal Echo actor record");
        return new ActorState(parseUuid(parts[0], "echo actor"), parseDouble(parts[1], "actor health"),
                parseDouble(parts[2], "actor maximum health"), parseEnum(ActorLifecycle.class, parts[3], "actor lifecycle"),
                parseLong(parts[4], "actor revision"), parseLong(parts[5], "actor next action"),
                parseLong(parts[6], "actor melee cooldown"), parseLong(parts[7], "actor ranged cooldown"),
                parseLong(parts[8], "actor pending use"), parseLong(parts[9], "actor death serial"),
                parseLong(parts[10], "actor shield active deadline"),
                parseLong(parts[11], "actor shield cooldown"), parseLong(parts[12], "actor jump cooldown"));
    }

    /** Upgrade only the previously supported named layout; unknown/legacy layouts still fail closed. */
    private static Map<String, String> upgradeKnownNamedSchema(Map<String, String> encoded) {
        if (!Integer.toString(PREVIOUS_NAMED_SCHEMA).equals(encoded.get(SCHEMA))
                || !NAMED_LAYOUT.equals(encoded.get(LAYOUT))) return encoded;
        Map<String, String> upgraded = new LinkedHashMap<>(encoded);
        upgraded.put(SCHEMA, Integer.toString(NAMED_SCHEMA));
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            if (!entry.getKey().startsWith(ECHO_DUEL_PREFIX)) continue;
            String[] duel = entry.getValue().split("\\|", -1);
            if (duel.length != 15 || "-".equals(duel[11])) continue;
            String[] actor = duel[11].split(":", -1);
            if (actor.length == 10) {
                duel[11] = String.join(":", actor) + ":0:0:0";
                upgraded.put(entry.getKey(), String.join("|", duel));
            }
        }
        return Map.copyOf(upgraded);
    }

    private static Map<String, String> decodeReplica(String raw) {
        if (raw.isEmpty()) return Map.of();
        Map<String, String> replica = new LinkedHashMap<>();
        String[] entries = raw.split(",", -1);
        if (entries.length > 400) throw new IllegalArgumentException("too many personal Echo replica fields");
        for (String entry : entries) {
            String[] pair = entry.split("=", -1);
            if (pair.length != 2) throw new IllegalArgumentException("malformed personal Echo replica field");
            String key = unbase64(pair[0]);
            String value = unbase64(pair[1]);
            if (replica.put(key, value) != null) throw new IllegalArgumentException("duplicate personal Echo replica field");
        }
        return Map.copyOf(replica);
    }

    private static String encodeReceipt(EchoCompletionReceipt receipt) {
        return String.join("|", receipt.eventId().toString(), Long.toString(receipt.generation()),
                Long.toString(receipt.runtimeGeneration()), Integer.toString(receipt.chamber()),
                receipt.duelId().toString(), receipt.owner().toString(), receipt.actorUuid().toString(),
                Long.toString(receipt.deathSerial()));
    }

    private static EchoCompletionReceipt decodeReceipt(String raw, UUID event, long generation,
                                                        int room, UUID owner) {
        if (raw == null || raw.length() > 512) throw new IllegalArgumentException("missing or oversized Echo receipt");
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 8) throw new IllegalArgumentException("malformed Echo receipt");
        EchoCompletionReceipt receipt = new EchoCompletionReceipt(parseUuid(parts[0], "receipt event"),
                parseLong(parts[1], "receipt attempt"), parseLong(parts[2], "receipt runtime"),
                parseInt(parts[3], "receipt room"), parseUuid(parts[4], "receipt duel"),
                parseUuid(parts[5], "receipt owner"), parseUuid(parts[6], "receipt actor"),
                parseLong(parts[7], "receipt death serial"));
        if (!event.equals(receipt.eventId()) || generation != receipt.generation()
                || room != receipt.chamber() || !owner.equals(receipt.owner())
                || !encodeReceipt(receipt).equals(raw)) {
            throw new IllegalArgumentException("foreign or non-canonical Echo receipt");
        }
        return receipt;
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unbase64(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            String decoded = new String(bytes, StandardCharsets.UTF_8);
            if (!base64(decoded).equals(value)) throw new IllegalArgumentException("non-canonical Echo replica encoding");
            return decoded;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("invalid Echo replica encoding", error);
        }
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

    private static double parseDouble(String raw, String key) {
        try {
            double value = Double.valueOf(raw == null ? "" : raw);
            if (!Double.toHexString(value).equals(raw)) throw new IllegalArgumentException("non-canonical double");
            return value;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Wave 7 trial snapshot has invalid double " + key, error);
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
