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
                    WAVE_3, INTERMISSION_3, WAVE_4, INTERMISSION_4, WAVE_5,
                    BOSS_CINEMATIC, BOSS_ACTIVE, FINAL_DRAIN, FINAL_RITUAL, FINAL_WAVE, BOSS_FINISH,
                    VICTORY_PROCESSING, VICTORY -> EventPhase.READY_FOR_PLAYERS;
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
        map.put(EventPhase.WAVE_3, EnumSet.of(EventPhase.INTERMISSION_3));
        map.put(EventPhase.INTERMISSION_3, EnumSet.of(EventPhase.WAVE_4));
        map.put(EventPhase.WAVE_4, EnumSet.of(EventPhase.INTERMISSION_4));
        map.put(EventPhase.INTERMISSION_4, EnumSet.of(EventPhase.WAVE_5));
        map.put(EventPhase.WAVE_5, EnumSet.of(EventPhase.BOSS_CINEMATIC, EventPhase.BOSS_ACTIVE));
        map.put(EventPhase.BOSS_CINEMATIC, EnumSet.of(EventPhase.BOSS_ACTIVE, EventPhase.READY_FOR_PLAYERS));
        // Canonical five-stage fights finish directly after the one-shot
        // Judgment cast.  The legacy final-drain route remains available for
        // old snapshots and compatibility tests.
        map.put(EventPhase.BOSS_ACTIVE, EnumSet.of(
                EventPhase.BOSS_FINISH, EventPhase.FINAL_DRAIN, EventPhase.FINAL_RITUAL));
        map.put(EventPhase.FINAL_DRAIN, EnumSet.of(EventPhase.FINAL_WAVE));
        map.put(EventPhase.FINAL_RITUAL, EnumSet.of(EventPhase.FINAL_WAVE));
        map.put(EventPhase.FINAL_WAVE, EnumSet.of(EventPhase.BOSS_FINISH));
        map.put(EventPhase.BOSS_FINISH, EnumSet.of(EventPhase.VICTORY_PROCESSING, EventPhase.VICTORY));
        map.put(EventPhase.VICTORY_PROCESSING, EnumSet.of(EventPhase.UNLOCKED));
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
