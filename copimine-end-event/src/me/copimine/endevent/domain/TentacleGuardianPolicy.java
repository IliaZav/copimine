package me.copimine.endevent.domain;

/**
 * Bounded server policy for the permanent guardians used by the boss's
 * LAST_SEAL stage.
 *
 * The policy deliberately contains no Bukkit types.  The plugin adapter owns
 * entities and effects; this class owns the health budget, shield window and
 * deterministic scheduling rules that must survive a restart or a change in
 * the number of active players.
 */
public final class TentacleGuardianPolicy {
    public static final int DAMAGE_WINDOW_TICKS = 300; // 15 seconds
    public static final int RESPAWN_DELAY_TICKS = 800; // 40 seconds
    public static final int ATTACK_INTERVAL_TICKS = 220;
    public static final int ATTACK_STAGGER_TICKS = 20;
    public static final int MIN_TOTAL_HEALTH = 120;
    public static final int MAX_TOTAL_HEALTH = 336;

    private TentacleGuardianPolicy() {
    }

    /**
     * Keep a small two-player budget while adding bounded pressure for a full
     * party.  The cap is independent of the number of entities in the room.
     */
    public static int totalHealthBudget(int players) {
        int safePlayers = Math.max(0, Math.min(20, players));
        if (safePlayers == 0) {
            return 0;
        }
        return Math.min(MAX_TOTAL_HEALTH,
                MIN_TOTAL_HEALTH + Math.max(0, safePlayers - 2) * 12);
    }

    public static int perGuardianHealth(int players) {
        int count = TentacleScalingPolicy.permanentFor(players, BossPhase.LAST_SEAL);
        if (count <= 0) {
            return 0;
        }
        int total = totalHealthBudget(players);
        return (total + count - 1) / count;
    }

    public static boolean shielded(BossPhase stage, int livingGuardians,
                                   boolean damageWindowOpen) {
        return shielded(stage, livingGuardians, damageWindowOpen, false);
    }

    /**
     * Keep the seal closed during the single-tick guardian initialization
     * boundary.  Stage changes and the central runtime tick are both main
     * thread operations, but a player damage event can arrive between them;
     * allowing that gap would let a full party kill the boss before its
     * permanent guardians exist.
     */
    public static boolean shielded(BossPhase stage, int livingGuardians,
                                   boolean damageWindowOpen,
                                   boolean guardiansPendingInitialization) {
        return stage == BossPhase.LAST_SEAL
                && !damageWindowOpen
                && (livingGuardians > 0 || guardiansPendingInitialization);
    }

    public static boolean damageWindowOpen(BossPhase stage, int livingGuardians,
                                           long startedTick, long nowTick) {
        return damageWindowOpen(stage, livingGuardians, startedTick, nowTick,
                DAMAGE_WINDOW_TICKS);
    }

    public static boolean damageWindowOpen(BossPhase stage, int livingGuardians,
                                           long startedTick, long nowTick,
                                           int durationTicks) {
        int safeDuration = Math.max(1, durationTicks);
        return stage == BossPhase.LAST_SEAL
                && livingGuardians <= 0
                && startedTick >= 0L
                && nowTick >= startedTick
                && nowTick - startedTick < safeDuration;
    }

    public static boolean respawnDue(long nowTick, long respawnTick,
                                     boolean frozen) {
        return !frozen && respawnTick >= 0L && nowTick >= respawnTick;
    }

    /**
     * A slot with no respawn schedule is the initial Last Seal spawn and is
     * ready immediately. A slot with a schedule must wait for that schedule
     * unless the guardian is still alive.
     */
    public static boolean slotReadyForSpawn(double health, Long respawnTick,
                                            long nowTick, boolean frozen) {
        if (Double.isFinite(health) && health > 0.0D) {
            return true;
        }
        return respawnTick == null
                || respawnDue(nowTick, respawnTick, frozen);
    }

    public static long firstAttackTick(long spawnTick, int slot) {
        return spawnTick + 60L + Math.max(0, slot) * ATTACK_STAGGER_TICKS;
    }

    public static boolean attackDue(long nowTick, long nextAttackTick,
                                    boolean busy) {
        return !busy && nextAttackTick >= 0L && nowTick >= nextAttackTick;
    }

    public static boolean canBeginAttack(TentacleAnimationPolicy.Kind kind,
                                         TentacleAnimationPolicy.State state) {
        return kind == TentacleAnimationPolicy.Kind.PERMANENT
                && (TentacleAnimationPolicy.canonical(state)
                == TentacleAnimationPolicy.State.SHIELD_CHANNEL
                || TentacleAnimationPolicy.canonical(state)
                == TentacleAnimationPolicy.State.READY);
    }

    public static String healthVisualState(double current, double maximum) {
        if (!Double.isFinite(current) || !Double.isFinite(maximum)
                || maximum <= 0.0D || current <= 0.0D) {
            return "DEAD";
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, current / maximum));
        if (ratio <= 1.0D / 3.0D) {
            return "CRITICAL";
        }
        if (ratio <= 2.0D / 3.0D) {
            return "DAMAGED";
        }
        return "FULL";
    }
}
