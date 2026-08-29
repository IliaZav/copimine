package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * Pure combat contract for End Rift skeletons.  Bukkit owns the entity and
 * projectile lifecycle; this policy keeps target filtering and the two arrow
 * profiles deterministic and bounded.
 */
public final class SkeletonCombatPolicy {
    private SkeletonCombatPolicy() {
    }

    /** Skeletons may only acquire a living, event-eligible player target. */
    public static boolean canTargetPlayersOnly(String targetType, boolean combatTarget) {
        return combatTarget && "PLAYER".equalsIgnoreCase(
                targetType == null ? "" : targetType.trim());
    }

    public static ArrowProfile arrowProfile(boolean miniBoss) {
        return miniBoss
                ? new ArrowProfile(3, 8.0D, 70, "rift_salvo")
                : new ArrowProfile(1, 5.0D, 50, "bone_tracer");
    }

    public static boolean hasArrowSpell(boolean miniBoss, int wave) {
        return miniBoss && wave >= 3;
    }

    /**
     * A live marked player is an immediate tactical override in Hunt (Wave
     * II).  The controller still validates the Bukkit player before calling
     * this method, so an offline/dead mark can never become a target.
     */
    public static boolean shouldPrioritizeMarkedTarget(int wave,
                                                       boolean focusMarkedPlayer,
                                                       boolean markedTargetEligible) {
        return wave == 2 && focusMarkedPlayer && markedTargetEligible;
    }

    /** The short movement beat used while a skeleton keeps its firing lane. */
    public enum Maneuver {
        HOLD_LINE,
        SIDE_STEP,
        FALLBACK,
        CROSS_FIRE
    }

    public static Maneuver maneuverForWave(int wave, boolean miniBoss, int cycle, int slot) {
        int safeWave = Math.max(1, Math.min(6, wave));
        int beat = Math.floorMod(Math.max(0, cycle) + Math.max(0, slot), 4);
        if (safeWave == 4) {
            return beat % 2 == 0 ? Maneuver.CROSS_FIRE : Maneuver.HOLD_LINE;
        }
        if (safeWave == 5 || safeWave == 6) {
            return switch (beat) {
                case 0 -> Maneuver.SIDE_STEP;
                case 1 -> Maneuver.CROSS_FIRE;
                case 2 -> Maneuver.FALLBACK;
                default -> miniBoss ? Maneuver.SIDE_STEP : Maneuver.HOLD_LINE;
            };
        }
        if (safeWave == 2) {
            return beat % 2 == 0 ? Maneuver.CROSS_FIRE : Maneuver.SIDE_STEP;
        }
        if (safeWave == 3) {
            return beat == 1 ? Maneuver.SIDE_STEP : Maneuver.HOLD_LINE;
        }
        return beat == 2 ? Maneuver.SIDE_STEP : Maneuver.HOLD_LINE;
    }

    /**
     * Return the readable battlefield job for a skeleton in one wave.  The
     * Bukkit controller uses this as a compact, deterministic input for
     * navigation and logging; it never changes the player-only target rule.
     */
    public static WaveBehavior behaviorForWave(int wave, boolean miniBoss) {
        int safeWave = Math.max(1, Math.min(6, wave));
        WaveBehavior base = switch (safeWave) {
            case 1 -> new WaveBehavior("bone_line", 9.0D, 15.0D,
                    false, false, false, "одна точная стрела по игроку");
            case 2 -> new WaveBehavior("marked_hunt", 8.0D, 14.0D,
                    true, false, false, "фокус по отмеченной цели");
            case 3 -> new WaveBehavior("portal_guard", 8.0D, 13.0D,
                    false, true, false, "держит линию у портала");
            case 4 -> new WaveBehavior("tower_artillery", 9.0D, 15.0D,
                    false, true, false, "прикрывает ядро с дальней позиции");
            case 5 -> new WaveBehavior("storm_kite", 10.0D, 16.0D,
                    false, false, true, "обходит опасные клетки и отступает");
            default -> new WaveBehavior("final_volley", 8.0D, 14.0D,
                    false, false, true, "закрывает отход залпом");
        };
        if (!miniBoss) {
            return base;
        }
        return new WaveBehavior(base.id(),
                Math.max(7.0D, base.minimumRange() - 1.0D),
                Math.max(base.minimumRange() + 2.0D, base.maximumRange() - 1.0D),
                base.focusMarkedPlayer(), base.guardsObjective(), base.hazardAware(),
                "тройной залп Разлома; " + base.tactic());
    }

    public record WaveBehavior(String id, double minimumRange, double maximumRange,
                               boolean focusMarkedPlayer, boolean guardsObjective,
                               boolean hazardAware, String tactic) {
        public WaveBehavior {
            id = id == null || id.isBlank() ? "bone_line" : id.trim().toLowerCase(Locale.ROOT);
            minimumRange = finite(minimumRange) ? Math.max(5.0D, minimumRange) : 8.0D;
            maximumRange = finite(maximumRange)
                    ? Math.max(minimumRange + 1.0D, maximumRange) : minimumRange + 4.0D;
            tactic = tactic == null || tactic.isBlank() ? "держит огневую линию" : tactic.trim();
        }
    }

    public record ArrowProfile(int arrowCount, double damage, int cooldownTicks,
                               String particlePattern) {
        public ArrowProfile {
            arrowCount = Math.max(1, Math.min(3, arrowCount));
            damage = finite(damage) ? Math.max(0.0D, Math.min(20.0D, damage)) : 0.0D;
            cooldownTicks = Math.max(20, Math.min(200, cooldownTicks));
            particlePattern = particlePattern == null ? "bone_tracer"
                    : particlePattern.trim().toLowerCase(Locale.ROOT);
            if (particlePattern.isBlank()) {
                particlePattern = "bone_tracer";
            }
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
