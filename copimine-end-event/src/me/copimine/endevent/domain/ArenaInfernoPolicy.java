package me.copimine.endevent.domain;

/**
 * Deterministic rules for the ARENA_INFERNO cast.
 *
 * The inferno is a real magma floor: every unprotected solid floor cell burns,
 * so there is no coordinate-hash "safe sector" puzzle. Players survive by
 * leaving the burning cells, and the steam vents that launch them give a
 * warning window before they damage anyone standing on them.
 */
public final class ArenaInfernoPolicy {
    public static final int VENT_COUNT = 3;
    public static final int VENT_PERIOD_TICKS = 60;
    /** Warning window that must elapse before a vent deals damage. */
    public static final int VENT_WARNING_TICKS = 20;
    public static final int VENT_DAMAGE_TICKS = 10;
    public static final int IGNITE_TICKS = 40;
    public static final int MAX_ARMOR_WEAR = 40;

    public enum VentPhase {
        IDLE,
        WARNING,
        DAMAGE
    }

    private ArenaInfernoPolicy() {
    }

    /**
     * Splits the vent cadence into a warning phase, a damage phase and the idle
     * remainder. Warning always comes first, so a vent never hurts a player who
     * had no chance to step off.
     */
    public static VentPhase ventPhase(int eventTick) {
        int slot = Math.floorMod(eventTick, VENT_PERIOD_TICKS);
        if (slot < VENT_WARNING_TICKS) {
            return VentPhase.WARNING;
        }
        if (slot < VENT_WARNING_TICKS + VENT_DAMAGE_TICKS) {
            return VentPhase.DAMAGE;
        }
        return VentPhase.IDLE;
    }

    /** Evenly spaced vent cells across the burning floor. */
    public static int ventStride(int cellCount) {
        return Math.max(1, cellCount / VENT_COUNT);
    }

    public static boolean armorWearAllowed(int wornPieces) {
        return wornPieces >= 0 && wornPieces < MAX_ARMOR_WEAR;
    }

    /** Every unprotected solid floor cell burns; the core and the pads stay intact. */
    public static boolean igniteCell(boolean protectedCell, boolean solidFloor, boolean liquid,
                                     boolean alreadyMagma) {
        return !protectedCell && solidFloor && !liquid && !alreadyMagma;
    }
}