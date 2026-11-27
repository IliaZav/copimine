package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * The single mapping from a boss phase to its configured music key.
 *
 * <p>Owner request: "enable Phase 4 music". The track already exists
 * ({@code copimine:end_rift/boss_overload}); the risk is the key being derived
 * by string fiddling. {@link BossPhase#LAST_SEAL} would lower-case to
 * {@code last_seal} and silently miss the configured {@code boss-last-seal}
 * entry, leaving the final phase on the previous track. This policy makes every
 * key explicit and testable so a phase can never lose its music to a spelling
 * accident.</p>
 */
public final class BossPhaseMusicPolicy {

    private BossPhaseMusicPolicy() {
    }

    /** The config {@code music.phase} key for a boss phase, never null. */
    public static String musicKey(BossPhase phase) {
        if (phase == null) {
            return "boss-awakening";
        }
        return switch (phase) {
            case AWAKENING -> "boss-awakening";
            case HUNT -> "boss-hunt";
            case RIFT -> "boss-rift";
            case OVERLOAD -> "boss-overload";
            case RAGE -> "boss-rage";
            case LAST_SEAL -> "boss-last-seal";
        };
    }

    /**
     * Resolve a phase name as it arrives over a packet/log.
     *
     * <p>Accepts both {@code LAST_SEAL} and {@code last-seal}/{@code last_seal}
     * spellings so an older producer cannot strand the finale on silence.</p>
     */
    public static String musicKey(String phaseId) {
        if (phaseId == null || phaseId.isBlank()) {
            return "boss-awakening";
        }
        String normalised = phaseId.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_');
        for (BossPhase phase : BossPhase.values()) {
            if (phase.name().equals(normalised)) {
                return musicKey(phase);
            }
        }
        return "boss-awakening";
    }

    /** True when this phase is the Phase 4 inferno, which must own its track. */
    public static boolean isPhaseFour(BossPhase phase) {
        return phase == BossPhase.OVERLOAD;
    }

    /** Diagnostic label; logs must never invent a different key. */
    public static String describe(BossPhase phase) {
        return "BOSS_PHASE_MUSIC phase=" + (phase == null ? "?" : phase.name())
                + " key=" + musicKey(phase);
    }
}
