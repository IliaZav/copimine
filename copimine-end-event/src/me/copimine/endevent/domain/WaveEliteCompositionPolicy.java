package me.copimine.endevent.domain;

/** Wave-specific legacy cleanup rules that preserve ordinary Wave 7 guardians. */
public final class WaveEliteCompositionPolicy {
    private WaveEliteCompositionPolicy() { }

    /**
     * Wave 2's obsolete elite tags are unambiguous. Wave 7 also uses the guardian
     * kind for ordinary trial pressure, so those entries are removed only when
     * their adapter has a separate marker identifying them as a legacy elite.
     */
    public static boolean isObsolete(int wave, int lastWave, String kind,
                                     String eliteKind, String guardianKind,
                                     boolean markedLegacyElite) {
        return isObsolete(wave, lastWave, kind, eliteKind, guardianKind,
                markedLegacyElite, false);
    }

    /** Keep only the explicitly approved final-group Wave 2 skeleton elite. */
    public static boolean isObsolete(int wave, int lastWave, String kind,
                                     String eliteKind, String guardianKind,
                                     boolean markedLegacyElite,
                                     boolean approvedWaveTwoFinalElite) {
        if (kind == null || eliteKind == null || guardianKind == null) return false;
        if (wave == 2) return eliteKind.equals(kind) && !approvedWaveTwoFinalElite;
        if (wave != lastWave) return false;
        return eliteKind.equals(kind)
                || guardianKind.equals(kind) && markedLegacyElite;
    }
}
