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
        MELEE_PRESSURE,
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
        /**
         * Wave 1 pressure body. It hunts the player directly instead of holding a
         * ring around the carrier: escorting made the whole wave orbit a moving
         * anchor, which is what the player saw as Endermen running back and forth.
         */
        WAVE_ONE_STALKER,
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
        return bossPlan(phase, cycle, targetDistance, targetOnCore, false);
    }

    /** Cocoon ownership is explicit: health alone cannot identify the grounded finale. */
    public static BossPlan bossPlan(BossPhase phase, int cycle,
                                    double targetDistance, boolean targetOnCore,
                                    boolean meleeFury) {
        BossPhase safe = phase == null ? BossPhase.AWAKENING : phase;
        int beat = Math.floorMod(cycle, 4);
        double direction = beat % 2 == 0 ? 1.0D : -1.0D;
        if (targetOnCore) {
            return new BossPlan(BossTactic.CREATE_SPACE, 8.0D, true, true, direction);
        }
        // Keep a real contact opportunity instead of abandoning it for the
        // next orbit beat. The adapter resolves this band from the PLAYER,
        // never from Core; it remains subject to the arena/floor validator.
        boolean contact = Double.isFinite(targetDistance) && targetDistance >= 0.0D
                && targetDistance <= BossContactStrikePolicy.MELEE_SWIPE_RANGE;
        if (safe == BossPhase.LAST_SEAL && meleeFury
                || safe != BossPhase.LAST_SEAL && contact) {
            return new BossPlan(BossTactic.MELEE_PRESSURE, MIN_BOSS_DISTANCE,
                    true, false, direction);
        }
        // Alternate a deliberate close approach with the phase's ranged job.
        // Previously every early-phase destination stayed beyond swipe range.
        if (safe != BossPhase.LAST_SEAL && (beat == 0 || beat == 2)) {
            return new BossPlan(BossTactic.MELEE_PRESSURE, MIN_BOSS_DISTANCE,
                    true, false, direction);
        }
        return switch (safe) {
            case AWAKENING -> new BossPlan(BossTactic.RING_ORBIT, 6.5D,
                    true, true, direction);
            case HUNT -> switch (Math.floorMod(cycle, 3)) {
                case 1 -> new BossPlan(BossTactic.RING_ORBIT, 7.5D,
                        true, true, direction);
                case 2 -> new BossPlan(BossTactic.CROSSCUT, 5.2D,
                        true, false, -direction);
                default -> new BossPlan(BossTactic.FLANK, 4.5D,
                        true, false, direction);
            };
            case RIFT -> beat == 1
                    ? new BossPlan(BossTactic.FLANK, 7.0D,
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
        if ((objective == EndRiftObjective.Objective.COLLAPSE_RINGS
                || objective == EndRiftObjective.Objective.RITUAL_SPHERE
                || objective == EndRiftObjective.Objective.REALITY_SPLIT)
                && ("COMMANDER".equals(normalized) || "ELITE".equals(normalized))) {
            return MobTactic.ELITE_HUNTER;
        }
        // Elite is a strength tier, not an objective. Keep the wave's job.
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
            case COLLAPSE_RINGS, RITUAL_SPHERE -> MobTactic.RING_GUARD;
            // Wave 6 mobs support the ram: they keep their distance from the
            // charge line instead of holding a ring the ram does not use.
            case RIFT_RAM -> MobTactic.FOG_SCOUT;
            case REALITY_SPLIT -> MobTactic.CHAMBER_BLADE;
        };
    }

    public static MobManeuver maneuverFor(EndRiftObjective.Objective objective,
                                          String role, int slot) {
        // A combat role owns a stable intention. Movement changes when the
        // target, objective, or hazard state changes, not on a wall-clock beat.
        int beat = Math.floorMod(Math.max(0, slot), 6);
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
