package me.copimine.dungeon.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure, generation-fenced room lifecycle transition validator (spec OV4.4).
 *
 * <p>Every transition is idempotent (a replayed same-state callback is harmless)
 * and carries an attempt/generation id: a transition computed for an older
 * generation is rejected as stale. No transition may skip the irreversible
 * checkpoints or re-arm a room without passing through RESETTING.</p>
 */
public final class RoomStateTransitionValidator {

    public enum Decision {
        ALLOWED,
        SELF_TRANSITION_IDEMPOTENT,
        ILLEGAL_TRANSITION,
        STALE_GENERATION
    }

    private static final Map<RoomState, Set<RoomState>> LEGAL = new EnumMap<>(RoomState.class);

    static {
        LEGAL.put(RoomState.DRAFT, EnumSet.of(RoomState.PUBLISHED_IDLE, RoomState.ABORTING));
        LEGAL.put(RoomState.PUBLISHED_IDLE, EnumSet.of(RoomState.ARMING, RoomState.DRAFT,
                RoomState.ABORTING, RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.ARMING, EnumSet.of(RoomState.SEALING, RoomState.ABORTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.SEALING, EnumSet.of(RoomState.FIGHTING, RoomState.ABORTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.FIGHTING, EnumSet.of(RoomState.CLEAR_COMMITTED, RoomState.ABORTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.CLEAR_COMMITTED, EnumSet.of(RoomState.OPENING, RoomState.ABORTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.OPENING, EnumSet.of(RoomState.REWARD_READY, RoomState.ABORTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.REWARD_READY, EnumSet.of(RoomState.RESETTING, RoomState.ABORTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.RESETTING, EnumSet.of(RoomState.PUBLISHED_IDLE,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.ABORTING, EnumSet.of(RoomState.RESETTING,
                RoomState.RECOVERY_REQUIRED));
        LEGAL.put(RoomState.RECOVERY_REQUIRED, EnumSet.of(RoomState.RESETTING, RoomState.ABORTING));
    }

    private RoomStateTransitionValidator() {
    }

    public static boolean isLegal(RoomState from, RoomState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        Set<RoomState> allowed = LEGAL.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Evaluates a transition fenced by the attempt generation. {@code attemptGeneration}
     * is the generation the caller believes it is acting on; {@code currentGeneration}
     * is the authoritative generation of the live room.
     */
    public static Decision evaluate(RoomState from, RoomState to,
                                    long attemptGeneration, long currentGeneration) {
        if (from == null || to == null) {
            return Decision.ILLEGAL_TRANSITION;
        }
        if (attemptGeneration != currentGeneration) {
            return Decision.STALE_GENERATION;
        }
        if (from == to) {
            return Decision.SELF_TRANSITION_IDEMPOTENT;
        }
        return isLegal(from, to) ? Decision.ALLOWED : Decision.ILLEGAL_TRANSITION;
    }

    public static boolean admits(RoomState from, RoomState to,
                                 long attemptGeneration, long currentGeneration) {
        Decision decision = evaluate(from, to, attemptGeneration, currentGeneration);
        return decision == Decision.ALLOWED || decision == Decision.SELF_TRANSITION_IDEMPOTENT;
    }

    /** Human-readable Russian explanation of a rejected transition. */
    public static String describe(RoomState from, RoomState to, Decision decision) {
        String f = from == null ? "?" : from.name();
        String t = to == null ? "?" : to.name();
        return switch (decision) {
            case ALLOWED -> "Переход " + f + " -> " + t + " разрешён.";
            case SELF_TRANSITION_IDEMPOTENT -> "Повторное событие " + f
                    + " безопасно проигнорировано (идемпотентность).";
            case STALE_GENERATION -> "Устаревшее поколение попытки: переход " + f + " -> " + t
                    + " отклонён.";
            case ILLEGAL_TRANSITION -> "Недопустимый переход состояния комнаты: " + f + " -> " + t + ".";
        };
    }
}
