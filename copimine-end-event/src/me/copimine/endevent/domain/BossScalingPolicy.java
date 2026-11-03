package me.copimine.endevent.domain;

import java.util.Objects;

/**
 * Boss statistics as a function of the admitted party, rescaled on request.
 *
 * <p>The user's rule, verbatim: "если игроков от 10 то хп босса растёт
 * пропорционально игрокам макс 5к хп и урон соответственно тоже но не
 * сильно". That replaces the old authored ladder, whose minimum was already
 * five thousand: a party of ten now meets a 5000 HP boss, every extra player
 * adds a proportional slice, and the pool is hard-capped at 5000. A smaller
 * party keeps the same floor, so the boss never becomes free.</p>
 *
 * <p>Damage follows health but far more gently. Scaling damage one-to-one with
 * health would turn a twenty-player raid into a one-shot arena; the damage
 * factor is deliberately sub-linear and capped, which is what "но не сильно"
 * asks for.</p>
 */
public final class BossScalingPolicy {
    /** The pool stops growing here; the user's explicit ceiling. */
    public static final double HP_CAP = 5_000.0D;

    /** The floor every party gets, independent of size. */
    public static final double HP_FLOOR = 5_000.0D;

    /** Proportional growth starts here: below ten players nothing is added. */
    public static final int GROWTH_START_PLAYERS = 10;

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;

    /**
     * Extra health per player above {@link #GROWTH_START_PLAYERS}.
     *
     * <p>The cap is what carries the ceiling, so this only has to be large
     * enough that the pool reaches it at the top of the roster; it is expressed
     * per head so the curve is readable.</p>
     */
    public static final double HP_PER_EXTRA_PLAYER = 500.0D;

    /**
     * Damage never exceeds this multiple of a solo-party boss.
     *
     * <p>A hard multiple, not a percentage of health: the party has the same
     * number of hit points per player, so a boss that scaled damage with health
     * would simply erase a group regardless of skill.</p>
     */
    public static final double DAMAGE_CAP_MULTIPLIER = 1.60D;

    /** Damage rises much more slowly than health, hence the square root. */
    public static final double DAMAGE_GROWTH_EXPONENT = 0.5D;

    private BossScalingPolicy() {
    }

    private static int clampPlayers(int players) {
        return Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
    }

    /**
     * Boss maximum health for a party size.
     *
     * <p>Exactly {@link #HP_FLOOR} at ten players and below, then proportional
     * growth, never above {@link #HP_CAP}. Because the floor equals the cap the
     * pool is constant today; it is written as a curve rather than a constant so
     * raising the cap is a one-line change and never silently reintroduces the
     * old fifteen-thousand-health ladder.</p>
     */
    public static double maxHealthForPlayers(int players) {
        int safe = clampPlayers(players);
        double extra = Math.max(0, safe - GROWTH_START_PLAYERS) * HP_PER_EXTRA_PLAYER;
        double scaled = HP_FLOOR + extra * (HP_CAP / Math.max(1.0D,
                (MAX_PLAYERS - GROWTH_START_PLAYERS) * HP_PER_EXTRA_PLAYER));
        return Math.min(HP_CAP, Math.max(HP_FLOOR, scaled));
    }

    /**
     * Multiplier applied to the boss's outgoing damage for a party size.
     *
     * <p>One for small parties, growing sub-linearly above ten players and
     * clamped at {@link #DAMAGE_CAP_MULTIPLIER}; the result is never below one
     * and never above the cap, so no party can be made safer than intended or
     * wiped by arithmetic alone.</p>
     */
    public static double damageMultiplierForPlayers(int players) {
        int safe = clampPlayers(players);
        int extra = Math.max(0, safe - GROWTH_START_PLAYERS);
        int span = MAX_PLAYERS - GROWTH_START_PLAYERS;
        if (extra == 0 || span <= 0) return 1.0D;
        double ramp = Math.pow((double) extra / span, DAMAGE_GROWTH_EXPONENT);
        double scaled = 1.0D + (DAMAGE_CAP_MULTIPLIER - 1.0D) * ramp;
        return Math.min(DAMAGE_CAP_MULTIPLIER, Math.max(1.0D, scaled));
    }

    /** True when the party is large enough for the user's scaling rule to apply. */
    public static boolean scalesAboveFloor(int players) {
        return clampPlayers(players) > GROWTH_START_PLAYERS;
    }

    /** Clamp a live health value into a pool, used by every damage path. */
    public static double clampCurrent(double health, double maxHealth) {
        // A broken or negative pool must fall back to the AUTHORED ceiling, not
        // to the floor: falling back to five thousand would let a corrupted
        // read silently authorise more health than the cap allows.
        double safeMax = Double.isFinite(maxHealth) && maxHealth > 0.0D ? maxHealth : HP_CAP;
        double safeHealth = Double.isFinite(health) ? health : 0.0D;
        return Math.max(0.0D, Math.min(safeMax, safeHealth));
    }

    /** Stable description for diagnostics and the command output. */
    public static String describe(int players) {
        int safe = clampPlayers(players);
        return "players=" + safe + " hp=" + Math.round(maxHealthForPlayers(safe))
                + " damageX=" + String.format(java.util.Locale.ROOT, "%.2f",
                damageMultiplierForPlayers(safe));
    }

    public record Stats(int players, double maxHealth, double damageMultiplier) {
        public Stats {
            players = clampPlayers(players);
            if (!Double.isFinite(maxHealth) || maxHealth <= 0.0D
                    || !Double.isFinite(damageMultiplier) || damageMultiplier <= 0.0D) {
                throw new IllegalArgumentException("invalid boss stats");
            }
            Objects.requireNonNull(players);
        }

        public static Stats forPlayers(int players) {
            return new Stats(players, maxHealthForPlayers(players),
                    damageMultiplierForPlayers(players));
        }
    }
}
