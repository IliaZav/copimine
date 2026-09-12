package me.copimine.endevent.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Strict, idempotency-keyed state graph for the current seven-wave event. */
public final class EndEventStateMachine {
    private static final Map<EventPhase, Set<EventPhase>> TRANSITIONS = transitions();
    private EventPhase phase;
    private final Map<String, AppliedTransition> appliedTransitions = new HashMap<>();

    public EndEventStateMachine(EventPhase initialPhase) {
        this.phase = initialPhase == null ? EventPhase.RECOVERY_REQUIRED : initialPhase;
    }

    public EventPhase phase() {
        return phase;
    }

    /** Validate a transition without mutating the graph or its idempotency ledger. */
    public TransitionResult previewTransition(EventPhase expected, EventPhase next,
                                              String reason, String idempotencyKey) {
        return validate(expected, next, reason, idempotencyKey);
    }

    public TransitionResult transition(EventPhase expected, EventPhase next,
                                       String reason, String idempotencyKey) {
        TransitionResult validation = validate(expected, next, reason, idempotencyKey);
        if (!validation.success() || "IDEMPOTENT_REPLAY".equals(validation.code())) {
            return validation;
        }
        String key = validation.idempotencyKey();
        String safeReason = validation.reason();
        phase = next;
        appliedTransitions.put(key, new AppliedTransition(expected, next, safeReason));
        return TransitionResult.success(safeReason, key);
    }

    private TransitionResult validate(EventPhase expected, EventPhase next,
                                      String reason, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return TransitionResult.failure("IDEMPOTENCY_KEY_REQUIRED");
        }
        String key = idempotencyKey.trim();
        AppliedTransition previous = appliedTransitions.get(key);
        if (previous != null) {
            if (previous.expected() != expected || previous.next() != next) {
                return TransitionResult.failure("IDEMPOTENCY_KEY_CONFLICT");
            }
            return TransitionResult.replay(previous.reason(), key);
        }
        if (expected == null || next == null || expected != phase) {
            return TransitionResult.failure("EXPECTED_PHASE_MISMATCH");
        }
        if (!TRANSITIONS.getOrDefault(phase, Set.of()).contains(next)) {
            return TransitionResult.failure("ILLEGAL_TRANSITION");
        }
        String safeReason = reason == null ? "" : reason.trim();
        return TransitionResult.success(safeReason, key);
    }

    /** A restart never resumes transient combat; it returns to a safe setup state. */
    public static EventPhase recoveryPhase(EventPhase persisted) {
        if (persisted == null || persisted == EventPhase.UNLOCKED
                || persisted == EventPhase.RECOVERY_REQUIRED) {
            return persisted == null ? EventPhase.RECOVERY_REQUIRED : persisted;
        }
        return switch (persisted) {
            case COLLECTING, READY_FOR_PLAYERS -> persisted;
            default -> EventPhase.READY_FOR_PLAYERS;
        };
    }

    private static Map<EventPhase, Set<EventPhase>> transitions() {
        EnumMap<EventPhase, Set<EventPhase>> map = new EnumMap<>(EventPhase.class);
        map.put(EventPhase.UNCONFIGURED, EnumSet.of(EventPhase.COLLECTING));
        map.put(EventPhase.COLLECTING, EnumSet.of(EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.READY_FOR_PLAYERS, EnumSet.of(
                EventPhase.START_RITUAL, EventPhase.COLLECTING));
        map.put(EventPhase.START_RITUAL, EnumSet.of(
                EventPhase.WAVE_1, EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.WAVE_1, EnumSet.of(EventPhase.INTERMISSION_1));
        map.put(EventPhase.INTERMISSION_1, EnumSet.of(EventPhase.WAVE_2));
        map.put(EventPhase.WAVE_2, EnumSet.of(EventPhase.INTERMISSION_2));
        map.put(EventPhase.INTERMISSION_2, EnumSet.of(EventPhase.WAVE_3));
        map.put(EventPhase.WAVE_3, EnumSet.of(EventPhase.INTERMISSION_3));
        map.put(EventPhase.INTERMISSION_3, EnumSet.of(EventPhase.WAVE_4));
        map.put(EventPhase.WAVE_4, EnumSet.of(EventPhase.CORE_RESTORATION));
        map.put(EventPhase.CORE_RESTORATION, EnumSet.of(EventPhase.WAVE_5,
                EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.WAVE_5, EnumSet.of(EventPhase.INTERMISSION_5));
        map.put(EventPhase.INTERMISSION_5, EnumSet.of(EventPhase.WAVE_6,
                EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.WAVE_6, EnumSet.of(EventPhase.INTERMISSION_6,
                EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.INTERMISSION_6, EnumSet.of(EventPhase.WAVE_7,
                EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.WAVE_7, EnumSet.of(EventPhase.PRE_BOSS_COOLDOWN,
                EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.PRE_BOSS_COOLDOWN, EnumSet.of(
                EventPhase.BOSS_CINEMATIC, EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.BOSS_CINEMATIC, EnumSet.of(
                EventPhase.BOSS_ACTIVE, EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.BOSS_ACTIVE, EnumSet.of(
                EventPhase.BOSS_FINISH, EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.BOSS_FINISH, EnumSet.of(EventPhase.VICTORY_PROCESSING));
        map.put(EventPhase.VICTORY_PROCESSING, EnumSet.of(EventPhase.UNLOCKED));
        map.put(EventPhase.UNLOCKED, Set.of());
        map.put(EventPhase.RECOVERY_REQUIRED, EnumSet.of(EventPhase.READY_FOR_PLAYERS));
        return Map.copyOf(map);
    }

    public record TransitionResult(boolean success, String code, String reason,
                                   String idempotencyKey) {
        private static TransitionResult success(String reason, String idempotencyKey) {
            return new TransitionResult(true, "OK", reason, idempotencyKey);
        }

        private static TransitionResult replay(String reason, String idempotencyKey) {
            return new TransitionResult(true, "IDEMPOTENT_REPLAY", reason, idempotencyKey);
        }

        private static TransitionResult failure(String code) {
            return new TransitionResult(false, code, "", "");
        }
    }

    private record AppliedTransition(EventPhase expected, EventPhase next, String reason) { }
}
