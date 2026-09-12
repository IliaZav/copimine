package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * Player-only, objective-aware skeleton behavior.  No target decision here
 * can select another mob, and every movement beat remains bounded to its
 * current objective's arena/chamber.
 */
public final class SkeletonCombatPolicy {
    private SkeletonCombatPolicy() {
    }

    public static boolean canTargetPlayersOnly(String targetType, boolean combatTarget) {
        return combatTarget && "PLAYER".equalsIgnoreCase(
                targetType == null ? "" : targetType.trim());
    }

    public static ArrowProfile arrowProfile(boolean miniBoss) {
        return miniBoss
                ? new ArrowProfile(3, 8.0D, 70, "rift_salvo")
                : new ArrowProfile(1, 5.0D, 50, "bone_tracer");
    }

    public static boolean hasArrowSpell(EndRiftObjective.Objective objective,
                                        boolean miniBoss) {
        return miniBoss && objective != null
                && objective != EndRiftObjective.Objective.RIFT_CARRIERS;
    }

    public static boolean shouldPrioritizeMarkedTarget(EndRiftObjective.Objective objective,
                                                       boolean focusMarkedPlayer,
                                                       boolean markedTargetEligible) {
        return objective == EndRiftObjective.Objective.RIFT_HUNT
                && focusMarkedPlayer && markedTargetEligible;
    }

    public enum Maneuver {
        HOLD_LINE,
        SIDE_STEP,
        FALLBACK,
        CROSS_FIRE,
        ROTATE_COVER,
        CHAMBER_STEP
    }

    public static Maneuver maneuverFor(EndRiftObjective.Objective objective,
                                       boolean miniBoss, int cycle, int slot) {
        EndRiftObjective.Objective safeObjective = objective == null
                ? EndRiftObjective.Objective.RIFT_CARRIERS : objective;
        int beat = Math.floorMod(Math.max(0, cycle) + Math.max(0, slot), 6);
        return switch (safeObjective) {
            case RIFT_CARRIERS -> beat % 2 == 0 ? Maneuver.HOLD_LINE : Maneuver.SIDE_STEP;
            case RIFT_HUNT -> beat % 2 == 0 ? Maneuver.CROSS_FIRE : Maneuver.SIDE_STEP;
            case RIFT_GATES -> beat % 3 == 0 ? Maneuver.ROTATE_COVER : Maneuver.HOLD_LINE;
            case OBELISK_ASSAULT -> beat % 2 == 0 ? Maneuver.ROTATE_COVER : Maneuver.CROSS_FIRE;
            case BLACK_FOG -> switch (beat % 3) {
                case 0 -> Maneuver.SIDE_STEP;
                case 1 -> Maneuver.FALLBACK;
                default -> Maneuver.CROSS_FIRE;
            };
            case COLLAPSE_RINGS -> beat % 2 == 0 ? Maneuver.SIDE_STEP : Maneuver.FALLBACK;
            case REALITY_SPLIT -> miniBoss ? Maneuver.CHAMBER_STEP : Maneuver.HOLD_LINE;
        };
    }

    public static WaveBehavior behaviorFor(EndRiftObjective.Objective objective,
                                           boolean miniBoss) {
        EndRiftObjective.Objective safeObjective = objective == null
                ? EndRiftObjective.Objective.RIFT_CARRIERS : objective;
        WaveBehavior base = switch (safeObjective) {
            case RIFT_CARRIERS -> new WaveBehavior("carrier_screen", 9.0D, 15.0D,
                    false, true, false, "держит коридор к носителю");
            case RIFT_HUNT -> new WaveBehavior("marked_pursuit", 8.0D, 14.0D,
                    true, false, false, "перекрывает отход отмеченной цели");
            case RIFT_GATES -> new WaveBehavior("gate_line", 8.0D, 13.0D,
                    false, true, false, "меняет огневую линию у врат");
            case OBELISK_ASSAULT -> new WaveBehavior("obelisk_cover", 9.0D, 15.0D,
                    false, true, false, "прикрывает обелиск с дальней позиции");
            case BLACK_FOG -> new WaveBehavior("fog_scout", 10.0D, 16.0D,
                    false, false, true, "отступает из чёрного тумана");
            case COLLAPSE_RINGS -> new WaveBehavior("ring_guard", 9.0D, 15.0D,
                    false, true, true, "держит разрыв между кольцами");
            case REALITY_SPLIT -> new WaveBehavior("chamber_fireline", 8.0D, 14.0D,
                    false, true, true, "не выходит из своей комнаты");
        };
        if (!miniBoss) {
            return base;
        }
        return new WaveBehavior(base.id(), Math.max(7.0D, base.minimumRange() - 1.0D),
                Math.max(base.minimumRange() + 2.0D, base.maximumRange() - 1.0D),
                base.focusMarkedPlayer(), base.guardsObjective(), base.hazardAware(),
                "командный тройной залп; " + base.tactic());
    }

    public record WaveBehavior(String id, double minimumRange, double maximumRange,
                               boolean focusMarkedPlayer, boolean guardsObjective,
                               boolean hazardAware, String tactic) {
        public WaveBehavior {
            id = id == null || id.isBlank() ? "fireline" : id.trim().toLowerCase(Locale.ROOT);
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
        return Double.isFinite(value);
    }
}
