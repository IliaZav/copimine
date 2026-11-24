package me.copimine.dungeon.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Generation-fenced gate transition validator (spec OV4.5). Monotonic and
 * idempotent: a late/duplicate callback from an older generation is rejected,
 * and an occupied doorway routes to BLOCKED_BY_ENTITY instead of an unsafe
 * close.
 */
public final class GateStateTransitionValidator {

    private static final Map<GateState, Set<GateState>> LEGAL = new EnumMap<>(GateState.class);

    static {
        LEGAL.put(GateState.CLOSED, EnumSet.of(GateState.OPENING, GateState.CLOSING,
                GateState.RECOVERY_REQUIRED));
        LEGAL.put(GateState.OPENING, EnumSet.of(GateState.OPEN, GateState.CLOSING,
                GateState.BLOCKED_BY_ENTITY, GateState.RECOVERY_REQUIRED));
        LEGAL.put(GateState.OPEN, EnumSet.of(GateState.CLOSING, GateState.RECOVERY_REQUIRED));
        LEGAL.put(GateState.CLOSING, EnumSet.of(GateState.CLOSED, GateState.OPENING,
                GateState.BLOCKED_BY_ENTITY, GateState.RECOVERY_REQUIRED));
        LEGAL.put(GateState.BLOCKED_BY_ENTITY, EnumSet.of(GateState.CLOSING, GateState.OPENING,
                GateState.RECOVERY_REQUIRED));
        LEGAL.put(GateState.RECOVERY_REQUIRED, EnumSet.of(GateState.CLOSED, GateState.OPEN));
    }

    private GateStateTransitionValidator() {
    }

    public static boolean isLegal(GateState from, GateState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        Set<GateState> allowed = LEGAL.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static boolean admits(GateState from, GateState to,
                                 long attemptGeneration, long currentGeneration) {
        if (from == null || to == null || attemptGeneration != currentGeneration) {
            return false;
        }
        return isLegal(from, to);
    }
}
