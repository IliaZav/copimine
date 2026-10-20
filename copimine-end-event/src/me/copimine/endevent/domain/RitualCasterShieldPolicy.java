package me.copimine.endevent.domain;

/** Guard-backed shield state for one Ritual Sphere caster. */
public final class RitualCasterShieldPolicy {
    private RitualCasterShieldPolicy() {
    }

    public static State state(boolean casterAlive, int livingGuards, int expectedGuards) {
        if (!casterAlive) {
            return State.DEAD;
        }
        int maximum = Math.max(1, Math.min(3, expectedGuards));
        int living = Math.max(0, Math.min(maximum, livingGuards));
        if (living == 0) return State.BROKEN;
        if (living == maximum || (long) living * 3L > (long) maximum * 2L) {
            return State.FULL;
        }
        if ((long) living * 3L > maximum) return State.WEAKENED;
        return State.CRITICAL;
    }

    public static boolean blocksDamage(boolean casterAlive, int livingGuards, int expectedGuards) {
        State state = state(casterAlive, livingGuards, expectedGuards);
        return state == State.FULL || state == State.WEAKENED || state == State.CRITICAL;
    }

    public enum State {
        FULL,
        WEAKENED,
        CRITICAL,
        BROKEN,
        DEAD
    }
}
