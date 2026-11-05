package me.copimine.endevent.domain;

/**
 * Wave 6 mage health curve.
 *
 * <p>The encounter is a sequence of five real fights, so a mage must survive long
 * enough to be fought. The player set the envelope explicitly: at most 800 HP and
 * never below 350. The ramp follows the mage's place in the wheel, so the first
 * mage is the lightest and the last is the heaviest, and extra players raise the
 * whole wheel without ever leaving the envelope.</p>
 */
public final class RitualCasterHealthPolicy {
    /** Absolute ceiling the player named. */
    public static final double MAX_HEALTH = 800.0D;
    /** Absolute floor the player named. */
    public static final double MIN_HEALTH = 350.0D;
    private static final int STAGES = 5;

    private RitualCasterHealthPolicy() {
    }

    /** Health for a mage at {@code stage} (0-based) with {@code players} in the party. */
    public static double maxHealthFor(int stage, int players) {
        int boundedStage = Math.max(0, Math.min(STAGES - 1, stage));
        int boundedPlayers = Math.max(1, players);
        double step = (MAX_HEALTH - MIN_HEALTH) / (STAGES - 1);
        double base = MIN_HEALTH + boundedStage * step;
        // Extra players add a measured share of the headroom above the floor, so a
        // large party never pushes a mage past the ceiling the player named.
        double partyShare = 0.12D * Math.max(0, boundedPlayers - 2);
        double scaled = base + (MAX_HEALTH - base) * partyShare;
        return Math.max(MIN_HEALTH, Math.min(MAX_HEALTH, scaled));
    }
}
