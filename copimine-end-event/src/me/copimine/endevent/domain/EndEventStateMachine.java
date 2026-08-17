package me.copimine.endevent.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class EndEventStateMachine {
    private static final Map<EventPhase, Set<EventPhase>> TRANSITIONS = transitions();
    private EventPhase phase;

    public EndEventStateMachine(EventPhase initialPhase) {
        this.phase = initialPhase == null ? EventPhase.RECOVERY_REQUIRED : initialPhase;
    }

    public EventPhase phase() {
        return phase;
    }

    public TransitionResult transition(EventPhase expected, EventPhase next,
                                       String reason, String idempotencyKey) {
        if (expected == null || next == null || expected != phase) {
            return TransitionResult.failure("EXPECTED_PHASE_MISMATCH");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return TransitionResult.failure("IDEMPOTENCY_KEY_REQUIRED");
        }
        if (!TRANSITIONS.getOrDefault(phase, Set.of()).contains(next)) {
            return TransitionResult.failure("ILLEGAL_TRANSITION");
        }
        phase = next;
        return TransitionResult.success(reason == null ? "" : reason.trim(), idempotencyKey.trim());
    }

    public static EventPhase recoveryPhase(EventPhase persisted) {
        if (persisted == null) {
            return EventPhase.RECOVERY_REQUIRED;
        }
        if (persisted == EventPhase.UNLOCKED || persisted == EventPhase.RECOVERY_REQUIRED) {
            return persisted;
        }
        return switch (persisted) {
            case COUNTDOWN, WAVE_1, INTERMISSION_1, WAVE_2, INTERMISSION_2,
                    WAVE_3, BOSS_ACTIVE, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH,
                    VICTORY -> EventPhase.READY_FOR_PLAYERS;
            default -> persisted;
        };
    }

    private static Map<EventPhase, Set<EventPhase>> transitions() {
        EnumMap<EventPhase, Set<EventPhase>> map = new EnumMap<>(EventPhase.class);
        map.put(EventPhase.UNCONFIGURED, EnumSet.of(EventPhase.COLLECTING));
        map.put(EventPhase.COLLECTING, EnumSet.of(EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.READY_FOR_PLAYERS, EnumSet.of(EventPhase.COUNTDOWN, EventPhase.COLLECTING));
        map.put(EventPhase.COUNTDOWN, EnumSet.of(EventPhase.WAVE_1, EventPhase.READY_FOR_PLAYERS));
        map.put(EventPhase.WAVE_1, EnumSet.of(EventPhase.INTERMISSION_1));
        map.put(EventPhase.INTERMISSION_1, EnumSet.of(EventPhase.WAVE_2));
        map.put(EventPhase.WAVE_2, EnumSet.of(EventPhase.INTERMISSION_2));
        map.put(EventPhase.INTERMISSION_2, EnumSet.of(EventPhase.WAVE_3));
        map.put(EventPhase.WAVE_3, EnumSet.of(EventPhase.BOSS_ACTIVE));
        map.put(EventPhase.BOSS_ACTIVE, EnumSet.of(EventPhase.FINAL_RITUAL));
        map.put(EventPhase.FINAL_RITUAL, EnumSet.of(EventPhase.FINAL_WAVE));
        map.put(EventPhase.FINAL_WAVE, EnumSet.of(EventPhase.BOSS_FINISH));
        map.put(EventPhase.BOSS_FINISH, EnumSet.of(EventPhase.VICTORY));
        map.put(EventPhase.VICTORY, EnumSet.of(EventPhase.UNLOCKED));
        map.put(EventPhase.RECOVERY_REQUIRED, EnumSet.of(EventPhase.READY_FOR_PLAYERS));
        return Map.copyOf(map);
    }

    public record TransitionResult(boolean success, String code, String reason, String idempotencyKey) {
        private static TransitionResult success(String reason, String idempotencyKey) {
            return new TransitionResult(true, "OK", reason, idempotencyKey);
        }

        private static TransitionResult failure(String code) {
            return new TransitionResult(false, code, "", "");
        }
    }
}
