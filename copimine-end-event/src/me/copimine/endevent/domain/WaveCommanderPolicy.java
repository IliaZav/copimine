package me.copimine.endevent.domain;

/** Small, bounded commander policy shared by the objective controller and tests. */
public final class WaveCommanderPolicy {
    public static final double AURA_RADIUS_BLOCKS = 10.0D;
    public static final int AURA_DURATION_TICKS = 40;
    public static final int AURA_AMPLIFIER = 0; // Strength I.

    private WaveCommanderPolicy() {
    }

    /** Current objectives that can carry one commander aura. */
    public static boolean supportsCommander(EndRiftObjective.Objective objective) {
        return objective == EndRiftObjective.Objective.RIFT_GATES
                || objective == EndRiftObjective.Objective.OBELISK_ASSAULT
                || objective == EndRiftObjective.Objective.BLACK_FOG
                || objective == EndRiftObjective.Objective.COLLAPSE_RINGS
                || objective == EndRiftObjective.Objective.REALITY_SPLIT;
    }

    /** Descriptive compatibility for code that asks whether an objective is difficult. */
    public static boolean isDifficultObjective(EndRiftObjective.Objective objective) {
        return supportsCommander(objective);
    }

    public static boolean shouldAssign(EndRiftObjective.Objective objective,
                                       boolean elite, boolean alreadyAssigned) {
        return supportsCommander(objective) && elite && !alreadyAssigned;
    }

    public static String displayName(String baseName) {
        String safe = baseName == null || baseName.isBlank() ? "Страж Разлома" : baseName.trim();
        return "Командир волны · " + safe;
    }
}
