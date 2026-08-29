package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * Deterministic combat decisions shared by the live Bukkit controller and
 * the policy tests.  The adapter still owns collision and world checks; this
 * class only describes why an entity should move and what job it is doing.
 */
public final class CombatTacticsPolicy {
    public static final double MIN_BOSS_DISTANCE = 3.5D;

    private CombatTacticsPolicy() {
    }

    public enum BossTactic {
        RING_ORBIT,
        FLANK,
        CROSSCUT,
        PHANTOM_FEINT,
        ABSORPTION_RETREAT,
        CATASTROPHE_PRESSURE
    }

    public enum MobTactic {
        ASSAULT,
        MARKED_HUNTER,
        PORTAL_GUARD,
        RAIDER_RUSH,
        CORE_BREAKER,
        ARTILLERY_SCREEN,
        STORM_HUNTER,
        ELITE_HUNTER
    }

    /** Short deterministic movement beats used between target rotations. */
    public enum MobManeuver {
        HOLD_LINE,
        SIDE_STEP,
        FALLBACK,
        CROSS_FIRE,
        PINCH
    }

    /**
     * Return the current boss posture.  A cycle is supplied by the runtime
     * controller rather than using wall-clock randomness, so a reconnect or
     * a lag spike cannot make the boss choose an unsafe or repeated route.
     */
    public static BossPlan bossPlan(BossStage stage, int cycle,
                                    double targetDistance, boolean targetOnCore) {
        BossStage safeStage = stage == null ? BossStage.AWAKENING : stage;
        int safeCycle = Math.max(0, cycle);
        if (targetOnCore) {
            return new BossPlan(BossTactic.ABSORPTION_RETREAT, 8.0D, true,
                    true, Math.floorMod(safeCycle, 2) == 0 ? 1.0D : -1.0D);
        }
        return switch (safeStage) {
            case AWAKENING -> new BossPlan(BossTactic.RING_ORBIT, 6.5D, true,
                    false, safeCycle % 2 == 0 ? 1.0D : -1.0D);
            case HUNTER -> switch (Math.floorMod(safeCycle, 3)) {
                case 1 -> new BossPlan(BossTactic.PHANTOM_FEINT, 7.5D, true,
                        false, safeCycle % 2 == 0 ? 1.0D : -1.0D);
                case 2 -> new BossPlan(BossTactic.CROSSCUT, 5.2D, true,
                        false, safeCycle % 2 == 0 ? -1.0D : 1.0D);
                default -> new BossPlan(BossTactic.FLANK, 4.5D, true,
                        false, safeCycle % 2 == 0 ? 1.0D : -1.0D);
            };
            case DISTORTION -> Math.floorMod(safeCycle, 3) == 1
                    ? new BossPlan(BossTactic.PHANTOM_FEINT, 7.0D, true,
                    false, safeCycle % 2 == 0 ? -1.0D : 1.0D)
                    : new BossPlan(BossTactic.CROSSCUT, 6.0D, true,
                    false, safeCycle % 2 == 0 ? -1.0D : 1.0D);
            case ABSORPTION -> new BossPlan(BossTactic.ABSORPTION_RETREAT, 8.0D, true,
                    true, safeCycle % 2 == 0 ? 1.0D : -1.0D);
            case CATASTROPHE -> new BossPlan(BossTactic.CATASTROPHE_PRESSURE,
                    Math.max(MIN_BOSS_DISTANCE, Math.min(4.0D,
                            finite(targetDistance) && targetDistance > 0.0D ? targetDistance : 4.0D)),
                    true, false, safeCycle % 2 == 0 ? 1.0D : -1.0D);
        };
    }

    /** Assign one stable job to a wave entity. */
    public static MobTactic waveTactic(int wave, String role, int slot) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (wave == 5 && "ELITE".equals(normalized)) {
            return MobTactic.STORM_HUNTER;
        }
        if ("RAIDER".equals(normalized)) {
            return MobTactic.RAIDER_RUSH;
        }
        if ("BREAKER".equals(normalized)) {
            return MobTactic.CORE_BREAKER;
        }
        if ("ARTILLERY".equals(normalized)) {
            return MobTactic.ARTILLERY_SCREEN;
        }
        if ("ELITE".equals(normalized)) {
            return MobTactic.ELITE_HUNTER;
        }
        return switch (Math.max(1, wave)) {
            case 2 -> MobTactic.MARKED_HUNTER;
            case 3 -> MobTactic.PORTAL_GUARD;
            default -> slot % 2 == 0 ? MobTactic.ASSAULT : MobTactic.ELITE_HUNTER;
        };
    }

    /**
     * Choose a bounded movement beat for one mob.  The slot is stable for a
     * generation, while cycle changes the readable response every few
     * seconds; no wall-clock randomness can make the mob leave the arena.
     */
    public static MobManeuver waveManeuver(int wave, String role, int cycle, int slot) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        int safeWave = Math.max(1, Math.min(6, wave));
        int beat = Math.floorMod(Math.max(0, cycle) + Math.max(0, slot), 4);
        if ("ARTILLERY".equals(normalized)) {
            return beat % 2 == 0 ? MobManeuver.CROSS_FIRE : MobManeuver.HOLD_LINE;
        }
        if ("BREAKER".equals(normalized)) {
            return beat == 2 ? MobManeuver.FALLBACK : MobManeuver.PINCH;
        }
        if (safeWave == 5 || "ELITE".equals(normalized)) {
            return switch (beat) {
                case 0 -> MobManeuver.SIDE_STEP;
                case 1 -> MobManeuver.CROSS_FIRE;
                case 2 -> MobManeuver.FALLBACK;
                default -> MobManeuver.PINCH;
            };
        }
        return switch (safeWave) {
            case 2 -> beat % 2 == 0 ? MobManeuver.PINCH : MobManeuver.SIDE_STEP;
            case 3 -> beat == 1 ? MobManeuver.SIDE_STEP : MobManeuver.HOLD_LINE;
            case 4 -> beat % 2 == 0 ? MobManeuver.CROSS_FIRE : MobManeuver.FALLBACK;
            default -> beat == 2 ? MobManeuver.SIDE_STEP : MobManeuver.HOLD_LINE;
        };
    }

    public record BossPlan(BossTactic tactic, double preferredDistance,
                           boolean shouldReposition, boolean preferOuterRing,
                           double orbitDirection) {
        public BossPlan {
            tactic = tactic == null ? BossTactic.RING_ORBIT : tactic;
            preferredDistance = finite(preferredDistance)
                    ? Math.max(MIN_BOSS_DISTANCE, preferredDistance) : MIN_BOSS_DISTANCE;
            orbitDirection = orbitDirection < 0.0D ? -1.0D : 1.0D;
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
