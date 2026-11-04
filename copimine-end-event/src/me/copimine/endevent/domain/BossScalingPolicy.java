package me.copimine.endevent.domain;

import java.util.Objects;

/**
 * Boss statistics as a function of the admitted party, rescaled on request.
 *
 * <p>The user's rule, verbatim: "у босса минимум 20к хп должно быть" and,
 * for growth, "если игроков от 10 то хп босса растёт пропорционально игрокам
 * ... и урон соответственно тоже но не сильно". The old numbers were
 * self-defeating: the floor and the cap were BOTH five thousand, so the pool
 * was a constant in disguise and no party size ever changed it. The floor is
 * now twenty thousand, the pool grows proportionally above ten players and
 * stops at forty thousand, and every party keeps at least the floor.</p>
 *
 * <p>Damage follows health but far more gently. Scaling damage one-to-one with
 * health would turn a twenty-player raid into a one-shot arena; the damage
 * factor is deliberately sub-linear and capped, which is what "но не сильно"
 * asks for.</p>
 */
public final class BossScalingPolicy {
    /** The pool stops growing here. */
    public static final double HP_CAP = 40_000.0D;

    /**
     * The floor every party gets, independent of size: the user's explicit
     * minimum. A smaller party must never meet a weaker boss.
     */
    public static final double HP_FLOOR = 20_000.0D;

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
    public static final double HP_PER_EXTRA_PLAYER = 2_000.0D;

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
     * growth of {@link #HP_PER_EXTRA_PLAYER} per head, never above
     * {@link #HP_CAP}. Ten players meet twenty thousand health, twenty meet
     * forty thousand, and neither the floor nor the cap can drift into each
     * other the way the old five-thousand pair did.</p>
     */
    public static double maxHealthForPlayers(int players) {
        int safe = clampPlayers(players);
        double extra = Math.max(0, safe - GROWTH_START_PLAYERS) * HP_PER_EXTRA_PLAYER;
        double scaled = HP_FLOOR + extra;
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
        // to the floor: a corrupted read must never authorise more health than
        // the cap allows.
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
