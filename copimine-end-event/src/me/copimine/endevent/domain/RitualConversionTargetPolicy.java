package me.copimine.endevent.domain;

/** Eligibility boundary for the Wave 6 prisoner's temporary Turncoat ability. */
public final class RitualConversionTargetPolicy {
    private RitualConversionTargetPolicy() {
    }

    public static boolean canConvert(String kind, int wave,
                                     boolean currentSessionOwned,
                                     boolean waveCommander,
                                     boolean bossOrElite,
                                     boolean objectiveEntity,
                                     boolean alreadyConverted) {
        boolean ordinaryEventMob = "WAVE_MOB".equals(kind)
                || "RITUAL_GUARD".equals(kind);
        return ordinaryEventMob && wave == 6 && currentSessionOwned
                && !waveCommander && !bossOrElite && !objectiveEntity
                && !alreadyConverted;
    }
}
