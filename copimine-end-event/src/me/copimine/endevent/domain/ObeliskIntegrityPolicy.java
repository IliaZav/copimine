package me.copimine.endevent.domain;

/**
 * Server-independent health and hit admission rules for the Wave 4
 * physical obelisk.  Health is deliberately integer-only: a projectile is
 * either one accepted hit or it is not a hit at all.
 */
public final class ObeliskIntegrityPolicy {
    private ObeliskIntegrityPolicy() {
    }

    public enum State {
        FULL,
        DAMAGED,
        CRITICAL,
        DESTROYED
    }

    public enum RejectReason {
        ACCEPTED,
        ALREADY_CONSUMED,
        ALREADY_DESTROYED,
        NOT_EVENT_PROJECTILE,
        WRONG_PROJECTILE_KIND,
        NOT_REFLECTED,
        STALE_GENERATION,
        NON_PARTICIPANT_REFLECTOR,
        TARGET_INACTIVE
    }

    /**
     * Uses cross multiplication instead of floating point thresholds.  The
     * boundaries are exactly those in the contract.
     */
    public static State state(int health, int maximumHealth) {
        int maximum = Math.max(1, maximumHealth);
        int current = Math.max(0, Math.min(maximum, health));
        if (current == 0) {
            return State.DESTROYED;
        }
        if (current * 3 > maximum * 2) {
            return State.FULL;
        }
        if (current * 3 > maximum) {
            return State.DAMAGED;
        }
        return State.CRITICAL;
    }

    public static HitDecision tryReflectedHit(int currentHealth, int maximumHealth,
                                               boolean eventOwned,
                                               ObeliskProjectilePolicy.Kind kind,
                                               boolean reflected,
                                               boolean generationMatches,
                                               boolean officialReflector,
                                               boolean targetActive,
                                               boolean alreadyConsumed) {
        int maximum = Math.max(1, maximumHealth);
        int current = Math.max(0, Math.min(maximum, currentHealth));
        if (alreadyConsumed) {
            return rejected(current, maximum, RejectReason.ALREADY_CONSUMED);
        }
        if (current == 0) {
            return rejected(current, maximum, RejectReason.ALREADY_DESTROYED);
        }
        if (!eventOwned) {
            return rejected(current, maximum, RejectReason.NOT_EVENT_PROJECTILE);
        }
        if (kind != ObeliskProjectilePolicy.Kind.OBELISK_RIFT_FIREBALL) {
            return rejected(current, maximum, RejectReason.WRONG_PROJECTILE_KIND);
        }
        if (!reflected) {
            return rejected(current, maximum, RejectReason.NOT_REFLECTED);
        }
        if (!generationMatches) {
            return rejected(current, maximum, RejectReason.STALE_GENERATION);
        }
        if (!officialReflector) {
            return rejected(current, maximum, RejectReason.NON_PARTICIPANT_REFLECTOR);
        }
        if (!targetActive) {
            return rejected(current, maximum, RejectReason.TARGET_INACTIVE);
        }
        int remaining = current - 1;
        return new HitDecision(remaining, true, remaining == 0,
                state(remaining, maximum), RejectReason.ACCEPTED);
    }

    private static HitDecision rejected(int health, int maximum, RejectReason reason) {
        return new HitDecision(health, false, false, state(health, maximum), reason);
    }

    public record HitDecision(int remainingHealth, boolean accepted, boolean destroyed,
                              State state, RejectReason reason) {
    }
}
