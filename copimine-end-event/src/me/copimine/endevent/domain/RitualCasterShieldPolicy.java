package me.copimine.endevent.domain;

/** Guard-backed shield state for one Ritual Sphere caster. */
public final class RitualCasterShieldPolicy {
    private RitualCasterShieldPolicy() {
    }

    public static State state(boolean casterAlive, int livingGuards) {
        if (!casterAlive) {
            return State.DEAD;
        }
        return switch (Math.max(0, Math.min(3, livingGuards))) {
            case 3 -> State.FULL;
            case 2 -> State.WEAKENED;
            case 1 -> State.CRITICAL;
            default -> State.BROKEN;
        };
    }

    public static boolean blocksDamage(boolean casterAlive, int livingGuards) {
        State state = state(casterAlive, livingGuards);
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
