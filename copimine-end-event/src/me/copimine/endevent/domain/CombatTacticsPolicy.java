package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * Deterministic, objective-aware combat intent for the current event.
 * Entities remain Bukkit-owned; this policy only returns bounded decisions
 * that are safe to apply on the server thread.
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
        CREATE_SPACE,
        RANGED_PRESSURE,
        CONTROL,
        SUMMON
    }

    public enum MobTactic {
        ASSAULT,
        CARRIER_ESCORT,
        MARKED_HUNTER,
        GATE_DEFENDER,
        OBELISK_GUARD,
        FOG_SCOUT,
        RING_GUARD,
        CHAMBER_BLADE,
        SKELETON_FIRELINE,
        ELITE_HUNTER
    }

    public enum MobManeuver {
        HOLD_LINE,
        SIDE_STEP,
        FALLBACK,
        CROSS_FIRE,
        PINCH,
        CIRCLE_OBJECTIVE,
        BREAK_LINE
    }

    public static BossPlan bossPlan(BossPhase phase, int cycle,
                                    double targetDistance, boolean targetOnCore) {
        BossPhase safe = phase == null ? BossPhase.AWAKENING : phase;
        int beat = Math.floorMod(cycle, 4);
        double direction = beat % 2 == 0 ? 1.0D : -1.0D;
        if (targetOnCore) {
            return new BossPlan(BossTactic.CREATE_SPACE, 8.0D, true, true, direction);
        }
        return switch (safe) {
            case AWAKENING -> new BossPlan(BossTactic.RING_ORBIT, 6.5D,
                    true, true, direction);
            case HUNT -> switch (Math.floorMod(cycle, 3)) {
                case 1 -> new BossPlan(BossTactic.PHANTOM_FEINT, 7.5D,
                        true, true, direction);
                case 2 -> new BossPlan(BossTactic.CROSSCUT, 5.2D,
                        true, false, -direction);
                default -> new BossPlan(BossTactic.FLANK, 4.5D,
                        true, false, direction);
            };
            case RIFT -> beat == 1
                    ? new BossPlan(BossTactic.PHANTOM_FEINT, 7.0D,
                    true, true, -direction)
                    : new BossPlan(BossTactic.CROSSCUT, 6.0D,
                    true, true, direction);
            case OVERLOAD -> new BossPlan(BossTactic.CONTROL, 6.0D,
                    true, true, direction);
            case RAGE -> new BossPlan(BossTactic.RANGED_PRESSURE, 4.5D,
                    true, false, direction);
            case LAST_SEAL -> new BossPlan(BossTactic.CONTROL, 5.0D,
                    true, false, -direction);
        };
    }

    /** Stable job assignment for the current objective graph. */
    public static MobTactic tacticFor(EndRiftObjective.Objective objective,
                                      String role, int slot) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if ("COMMANDER".equals(normalized) || "ELITE".equals(normalized)) {
            return MobTactic.ELITE_HUNTER;
        }
        if ("CARRIER_ESCORT".equals(normalized)) {
            return MobTactic.CARRIER_ESCORT;
        }
        if ("GATE_DEFENDER".equals(normalized)) {
            return MobTactic.GATE_DEFENDER;
        }
        if ("OBELISK_GUARD".equals(normalized)) {
            return MobTactic.OBELISK_GUARD;
        }
        if ("RING_GUARD".equals(normalized)) {
            return MobTactic.RING_GUARD;
        }
        if ("CHAMBER_BLADE".equals(normalized)) {
            return MobTactic.CHAMBER_BLADE;
        }
        EndRiftObjective.Objective safe = objective == null
                ? EndRiftObjective.Objective.RIFT_CARRIERS : objective;
        return switch (safe) {
            case RIFT_CARRIERS -> MobTactic.CARRIER_ESCORT;
            case RIFT_HUNT -> MobTactic.MARKED_HUNTER;
            case RIFT_GATES -> MobTactic.GATE_DEFENDER;
            case OBELISK_ASSAULT -> MobTactic.OBELISK_GUARD;
            case BLACK_FOG -> MobTactic.FOG_SCOUT;
            case COLLAPSE_RINGS -> MobTactic.RING_GUARD;
            case REALITY_SPLIT -> MobTactic.CHAMBER_BLADE;
        };
    }

    public static MobManeuver maneuverFor(EndRiftObjective.Objective objective,
                                          String role, int cycle, int slot) {
        int beat = Math.floorMod(Math.max(0, cycle) + Math.max(0, slot), 6);
        MobTactic tactic = tacticFor(objective, role, slot);
        if (tactic == MobTactic.OBELISK_GUARD || tactic == MobTactic.GATE_DEFENDER) {
            return beat % 2 == 0 ? MobManeuver.CIRCLE_OBJECTIVE : MobManeuver.CROSS_FIRE;
        }
        if (tactic == MobTactic.FOG_SCOUT || tactic == MobTactic.RING_GUARD) {
            return switch (beat % 3) {
                case 0 -> MobManeuver.SIDE_STEP;
                case 1 -> MobManeuver.FALLBACK;
                default -> MobManeuver.PINCH;
            };
        }
        if (tactic == MobTactic.CHAMBER_BLADE) {
            return beat % 2 == 0 ? MobManeuver.BREAK_LINE : MobManeuver.PINCH;
        }
        return beat % 2 == 0 ? MobManeuver.HOLD_LINE : MobManeuver.SIDE_STEP;
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
        return Double.isFinite(value);
    }
}
