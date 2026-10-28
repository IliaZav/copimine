package me.copimine.endevent.domain;

/**
 * Deterministic rules for the ARENA_INFERNO cast.
 *
 * The inferno is a real magma floor: every unprotected solid floor cell burns,
 * so there is no coordinate-hash "safe sector" puzzle. Players survive by
 * leaving the burning cells. Each steam vent runs its own bounded life cycle:
 * a warning first, exactly one eruption tick that launches and injures the
 * players still standing on it, a short recovery puff, and then removal.
 */
public final class ArenaInfernoPolicy {
    public static final int VENT_COUNT = 3;
    public static final int VENT_PERIOD_TICKS = 60;
    /** Warning window that must elapse before a vent deals damage: 0.8 s. */
    public static final int VENT_WARNING_TICKS = 16;
    /** Recovery puff after the single eruption tick; it never damages again. */
    public static final int VENT_RECOVER_TICKS = 10;
    public static final int IGNITE_TICKS = 40;
    public static final int MAX_ARMOR_WEAR = 40;

    public enum VentPhase {
        WARN,
        ERUPT,
        RECOVER,
        REMOVE
    }

    private ArenaInfernoPolicy() {
    }

    /** Do not combine magma/vents with a new relocation volley or hard restraint. */
    public static boolean allowsConcurrent(EndRiftAiPolicy.BossSpell spell) {
        return spell != null && spell != EndRiftAiPolicy.BossSpell.RIFT_ARROWS
                && spell != EndRiftAiPolicy.BossSpell.VOID_MARK
                && spell != EndRiftAiPolicy.BossSpell.AMBUSH_GRAB
                && spell != EndRiftAiPolicy.BossSpell.ARENA_INFERNO;
    }

    /**
     * Splits the vent cadence into warning, the one eruption tick, the recovery
     * puff and removal. Warning always comes first, so a vent never hurts a
     * player who had no chance to step off, and the eruption is a single tick
     * instead of a repeated damage window.
     */
    public static VentPhase ventPhase(int eventTick) {
        int slot = Math.floorMod(eventTick, VENT_PERIOD_TICKS);
        if (slot < VENT_WARNING_TICKS) {
            return VentPhase.WARN;
        }
        if (slot == VENT_WARNING_TICKS) {
            return VentPhase.ERUPT;
        }
        if (slot <= VENT_WARNING_TICKS + VENT_RECOVER_TICKS) {
            return VentPhase.RECOVER;
        }
        return VentPhase.REMOVE;
    }

    /** True only on the single tick where the vent really erupts. */
    public static boolean ventDamages(VentPhase phase) {
        return phase == VentPhase.ERUPT;
    }

    /** Bounded simultaneous vents: one per four participants, never more than three. */
    public static int ventCountForRoster(int roster) {
        int bounded = Math.max(0, Math.min(20, roster));
        return Math.max(1, Math.min(VENT_COUNT, (bounded + 3) / 4));
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
