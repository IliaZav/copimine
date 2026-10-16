package me.copimine.endevent.domain;

/** Wave-scoped pressure handling for authored exceptions to the common profile. */
public final class WavePressurePolicy {
    private static final int WAVE_FIVE = 5;
    private static final int APPROVED_WAVE_FIVE_ELITE_ENDERMEN = 6;
    private static final int APPROVED_WAVE_FIVE_ELITE_SKELETONS = 2;

    private WavePressurePolicy() { }

    /**
     * Apply the normal profile, with only the approved two-player Wave 5 roster
     * exception. The exception has fixed elite role caps, drops ordinary mobs,
     * and remains below the absolute entity hard cap.
     */
    public static WaveMechanicsPolicy.WaveCounts clamp(
            int wave, WaveMechanicsPolicy.WaveCounts requested, int livingPlayers) {
        WaveMechanicsPolicy.WaveCounts pressureLimited =
                WaveMechanicsPolicy.clampToPressure(requested, livingPlayers);
        if (wave != WAVE_FIVE || livingPlayers != 2 || requested == null) {
            return pressureLimited;
        }

        int eliteEndermen = Math.min(APPROVED_WAVE_FIVE_ELITE_ENDERMEN,
                Math.max(0, requested.eliteEndermen()));
        int eliteSkeletons = Math.min(APPROVED_WAVE_FIVE_ELITE_SKELETONS,
                Math.max(0, requested.eliteSkeletons()));
        int eliteTotal = eliteEndermen + eliteSkeletons;
        if (eliteTotal > PressureBudgetController.profileForPlayers(livingPlayers).activePressure()) {
            return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, eliteEndermen, eliteSkeletons);
        }
        return new WaveMechanicsPolicy.WaveCounts(
                pressureLimited.endermen(), pressureLimited.spiders(), pressureLimited.skeletons(),
                eliteEndermen, eliteSkeletons);
    }

    /** True only for the exact approved eight-elite two-player Wave 5 roster. */
    public static boolean isApprovedTwoPlayerException(
            int wave, int livingPlayers, WaveMechanicsPolicy.WaveCounts active) {
        return wave == WAVE_FIVE && livingPlayers == 2 && active != null
                && active.endermen() == 0 && active.spiders() == 0 && active.skeletons() == 0
                && active.eliteEndermen() == APPROVED_WAVE_FIVE_ELITE_ENDERMEN
                && active.eliteSkeletons() == APPROVED_WAVE_FIVE_ELITE_SKELETONS
                && active.total() == 8 && active.total() <= PressureBudgetController.HARD_CAP;
    }
}
