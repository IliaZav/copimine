package me.copimine.endevent.domain;

/**
 * Pure state and attack selection rules for a Wave 6 Ritual Sphere caster.
 *
 * <p>The guard gate is evaluated before the damage flag on purpose.  A caster
 * whose shield is still supplied by at least one living guard remains a
 * ritualist even if a third-party damage event was attempted.  Once the last
 * guard is gone, the first accepted hit makes that caster an attacker for the
 * rest of the encounter.</p>
 */
public final class RitualCasterTacticsPolicy {
    private RitualCasterTacticsPolicy() {
    }

    public static State state(boolean guardAlive, boolean damaged) {
        if (guardAlive) {
            return State.GUARDED_CASTING;
        }
        return damaged ? State.AWAKENED_ATTACKING : State.EXPOSED_CASTING;
    }

    public static boolean canTargetPlayers(State state) {
        return state == State.AWAKENED_ATTACKING;
    }

    public static boolean castsSphere(State state) {
        return state == State.GUARDED_CASTING || state == State.EXPOSED_CASTING;
    }

    public static boolean holdsRaisedArms(State state) {
        return castsSphere(state);
    }

    /** Select one stable, unique tactic for each Wave 6 caster slot. */
    public static Attack attackForSlot(int casterSlot) {
        if (casterSlot < 0) {
            throw new IllegalArgumentException("caster slot must be non-negative");
        }
        return Attack.values()[Math.floorMod(casterSlot, Attack.values().length)];
    }

    public enum State {
        GUARDED_CASTING,
        EXPOSED_CASTING,
        AWAKENED_ATTACKING
    }

    public enum Attack {
        SPHERE_BARRAGE("sphere-barrage"),
        RIFT_MARK("rift-mark"),
        REVERSE_PULL("reverse-pull"),
        CONTROL_SWAP("control-swap"),
        VOID_LANCE("void-lance"),
        RIFT_SPIKES("rift-spikes");

        private final String id;

        Attack(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
