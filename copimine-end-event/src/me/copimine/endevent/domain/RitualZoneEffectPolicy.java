package me.copimine.endevent.domain;

/** Pure decision table for the active Wave 6 corrupted zones. */
public final class RitualZoneEffectPolicy {
    private RitualZoneEffectPolicy() {
    }

    public static Result effect(boolean prisoner,
                                boolean insideActiveZone,
                                boolean controlSwapActive) {
        if (prisoner || !insideActiveZone) {
            return new Result(false, false, false);
        }
        return new Result(true, true, !controlSwapActive);
    }

    public record Result(boolean wither, boolean slowness, boolean reverseMovement) {
    }
}
