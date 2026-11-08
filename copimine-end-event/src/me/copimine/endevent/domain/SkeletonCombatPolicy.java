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

    /**
     * Audit elite gap: the mini-boss volley is THREE sequential fans of SIX
     * arrows, not a single three-arrow burst.
     *
     * <p>The old profile capped the count at three, so the "triple volley" the
     * tactic string promised never existed. {@link #MINI_BOSS_VOLLEYS} carries
     * the number of fans and {@link ArrowProfile#arrowsPerFan()} the size of
     * each one; the runtime fires the fans on a stagger instead of one blob.</p>
     */
    public static final int MINI_BOSS_VOLLEYS = 3;
    public static final int MINI_BOSS_ARROWS_PER_FAN = 6;

    public static ArrowProfile arrowProfile(boolean miniBoss) {
        return miniBoss
                ? new ArrowProfile(MINI_BOSS_ARROWS_PER_FAN, 3.5D, 70, "rift_salvo", MINI_BOSS_VOLLEYS)
                : new ArrowProfile(1, 5.0D, 50, "bone_tracer", 1);
    }

    public static boolean hasArrowSpell(EndRiftObjective.Objective objective,
                                        boolean miniBoss) {
        return miniBoss && objective != null
                && objective != EndRiftObjective.Objective.RIFT_CARRIERS;
    }

    /**
     * A closed Reality Split chamber is intentionally too small for an
     * unbounded ranged kite.  Keep the skeleton's bow/spell attack, but leave
     * its firing position reachable by the player assigned to that chamber.
     */
    public static boolean shouldHoldPositionInClosedChamber(
            EndRiftObjective.Objective objective) {
        return objective == EndRiftObjective.Objective.REALITY_SPLIT;
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
                                       boolean miniBoss, int slot) {
        EndRiftObjective.Objective safeObjective = objective == null
                ? EndRiftObjective.Objective.RIFT_CARRIERS : objective;
        int beat = Math.floorMod(Math.max(0, slot), 6);
        return switch (safeObjective) {
            case RIFT_CARRIERS -> beat % 2 == 0 ? Maneuver.HOLD_LINE : Maneuver.SIDE_STEP;
            case RIFT_HUNT -> beat % 2 == 0 ? Maneuver.CROSS_FIRE : Maneuver.SIDE_STEP;
            case RIFT_GATES -> beat % 3 == 0 ? Maneuver.ROTATE_COVER : Maneuver.HOLD_LINE;
            case OBELISK_ASSAULT -> beat % 2 == 0 ? Maneuver.ROTATE_COVER : Maneuver.CROSS_FIRE;
            case BLACK_FOG -> miniBoss ? Maneuver.CROSS_FIRE
                    : beat % 2 == 0 ? Maneuver.SIDE_STEP : Maneuver.FALLBACK;
            case COLLAPSE_RINGS, RITUAL_SPHERE, RIFT_RAM ->
                    beat % 2 == 0 ? Maneuver.SIDE_STEP : Maneuver.FALLBACK;
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
            case COLLAPSE_RINGS, RITUAL_SPHERE -> new WaveBehavior("ring_guard", 9.0D, 15.0D,
                    false, true, true, "держит разрыв между кольцами");
            // Wave 6 is one ram, not a ring: the skeleton keeps a wide berth so a
            // charge does not delete it, and it still backs away from the Core.
            case RIFT_RAM -> new WaveBehavior("ram_flank", 11.0D, 17.0D,
                    false, true, true, "обходит тарана с фланга");
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
                               String particlePattern, int volleys) {
        /** Backwards-compatible single-fan profile. */
        public ArrowProfile(int arrowCount, double damage, int cooldownTicks, String particlePattern) {
            this(arrowCount, damage, cooldownTicks, particlePattern, 1);
        }

        public ArrowProfile {
            // Six arrows per fan is the authored elite volley, so the bound has to
            // admit it; anything larger is still rejected.
            arrowCount = Math.max(1, Math.min(MINI_BOSS_ARROWS_PER_FAN, arrowCount));
            volleys = Math.max(1, Math.min(MINI_BOSS_VOLLEYS, volleys));
            damage = finite(damage) ? Math.max(0.0D, Math.min(20.0D, damage)) : 0.0D;
            cooldownTicks = Math.max(20, Math.min(200, cooldownTicks));
            particlePattern = particlePattern == null ? "bone_tracer"
                    : particlePattern.trim().toLowerCase(Locale.ROOT);
            if (particlePattern.isBlank()) {
                particlePattern = "bone_tracer";
            }
        }

        /** How many arrows each fan carries. */
        public int arrowsPerFan() { return arrowCount; }

        /** The whole volley: fans multiplied by their arrows. */
        public int totalArrows() { return arrowCount * volleys; }
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
