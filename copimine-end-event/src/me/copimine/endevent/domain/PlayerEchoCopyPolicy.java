package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * V6 §963 / owner request: Phase 5 RAGE Echoes must reproduce the <b>target
 * player's own recent combat action</b>, not merely the boss's, so a victim can
 * recognise their own weapon/action silhouette. Each copy is a semi-transparent
 * player-model duplicate of the locked target, and a minority of copies instead
 * fire the Guardian's chest beam.
 *
 * <p>Pure: it maps a recorded player action to a copyable family and decides the
 * chest-beam roll. The server still owns the target lock and the damage; this
 * policy only chooses what the copy looks like and does.</p>
 */
public final class PlayerEchoCopyPolicy {

    /** Semi-transparent player copies; the client renders the alpha. */
    public static final double COPY_ALPHA = 0.55D;
    /** A lone player sees chest-beam copies more often; a raid rarely. */
    public static final double CHEST_BEAM_CHANCE_SOLO = 0.50D;
    public static final double CHEST_BEAM_CHANCE_RAID = 0.20D;
    /** Only these recorded actions map to a copyable family. */
    public static final String ACTION_MELEE = "MELEE";
    public static final String ACTION_RANGED = "RANGED";
    public static final String ACTION_AREA = "AREA";

    private PlayerEchoCopyPolicy() {
    }

    /** The copy-family a recorded player action is reproduced as. */
    public static String familyForPlayerAction(String action) {
        String key = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case ACTION_RANGED -> EndRiftAiPolicy.BossSpell.CHEST_STRIKE.name();
            case ACTION_AREA -> EndRiftAiPolicy.BossSpell.GROUND_SLAM.name();
            case ACTION_MELEE -> EndRiftAiPolicy.BossSpell.MELEE_SWIPE.name();
            default -> EndRiftAiPolicy.BossSpell.MELEE_SWIPE.name();
        };
    }

    /** Chance a copy is the Guardian's chest beam instead of the player action. */
    public static double chestBeamChance(int players) {
        int safe = Math.max(1, Math.min(20, players));
        // Linear from solo to raid, clamped to the raid floor.
        double t = (safe - 1) / 19.0D;
        return CHEST_BEAM_CHANCE_SOLO + (CHEST_BEAM_CHANCE_RAID - CHEST_BEAM_CHANCE_SOLO) * t;
    }

    /** Deterministic roll test; {@code roll01} must already be a stable 0..1 value. */
    public static boolean rollsChestBeam(int players, double roll01) {
        if (!Double.isFinite(roll01)) {
            return false;
        }
        return roll01 < chestBeamChance(players);
    }

    /**
     * The family a copy for this target uses.
     *
     * @param playerAction the target player's recorded recent action
     * @param players      admitted participant count
     * @param roll01       a deterministic 0..1 roll for this copy
     */
    public static String chooseFamily(String playerAction, int players, double roll01) {
        if (rollsChestBeam(players, roll01)) {
            return EndRiftAiPolicy.BossSpell.CHEST_STRIKE.name();
        }
        return familyForPlayerAction(playerAction);
    }

    /** A copy is a semi-transparent duplicate, never an opaque twin. */
    public static boolean isSemiTransparent() {
        return true;
    }

    /** The copy mirrors the target's equipment silhouette. */
    public static boolean copiesEquipment() {
        return true;
    }

    /** A copy never gains the boss's global full-arena spells as a payload. */
    public static boolean usesGlobalSpells() {
        return false;
    }

    public static String describe(int players, String playerAction, String family, double roll01) {
        return "PLAYER_ECHO_COPY players=" + players
                + " action=" + (playerAction == null ? "?" : playerAction)
                + " family=" + (family == null ? "?" : family)
                + " roll=" + String.format(Locale.ROOT, "%.3f", roll01)
                + " chest_beam_chance=" + String.format(Locale.ROOT, "%.3f", chestBeamChance(players))
                + " alpha=" + COPY_ALPHA
                + " semi_transparent=" + isSemiTransparent()
                + " global_spells=" + usesGlobalSpells();
    }
}
