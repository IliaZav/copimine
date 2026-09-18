package me.copimine.endevent.domain;

/**
 * Pure state and role selection rules for a Wave 6 Ritual Sphere caster.
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

    /**
     * Native mob AI belongs to the caster only after the first accepted hit.
     * Guarded and exposed casters are driven by the event controller while
     * they hold their raised-arm channeling pose.
     */
    public static boolean nativeAiEnabled(State state) {
        return state == State.AWAKENED_ATTACKING;
    }

    public static boolean castsSphere(State state) {
        return state == State.GUARDED_CASTING || state == State.EXPOSED_CASTING;
    }

    /** The caster owns one of the sphere's four core abilities while channeling. */
    public static boolean ownsRitualAbility(State state) {
        return state == State.GUARDED_CASTING || state == State.EXPOSED_CASTING;
    }

    /** Amplifiers contribute only while they are still channeling the sphere. */
    public static boolean contributesAmplification(State state, Role role) {
        return role == Role.AMPLIFIER && ownsRitualAbility(state);
    }

    public static boolean holdsRaisedArms(State state) {
        return castsSphere(state);
    }

    /** Select the bounded core role for a Wave 6 caster slot. */
    public static Role roleForSlot(int casterSlot) {
        if (casterSlot < 0) {
            throw new IllegalArgumentException("caster slot must be non-negative");
        }
        return switch (casterSlot) {
            case 0 -> Role.PROJECTILE_CASTER;
            case 1 -> Role.ZONE_CASTER;
            case 2 -> Role.REVERSE_CASTER;
            case 3 -> Role.CONTROL_SWAP_CASTER;
            default -> Role.AMPLIFIER;
        };
    }

    public enum State {
        GUARDED_CASTING,
        EXPOSED_CASTING,
        AWAKENED_ATTACKING
    }

    public enum Role {
        PROJECTILE_CASTER,
        ZONE_CASTER,
        REVERSE_CASTER,
        CONTROL_SWAP_CASTER,
        AMPLIFIER
    }
}
