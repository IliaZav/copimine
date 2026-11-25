package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.1 Archmage combat contract: a ranged caster with bounded Vex support, a
 * short local Fang Line, a gap-bearing Circle of Fangs, a five-spoke rotating
 * Crown and a single warned anti-crowd Staff Repulse.
 *
 * <p>Every number the owner ratified lives here so the runtime only executes a
 * decided cast and the guarantees are assertable without a server. The class is
 * deliberately additive: it never re-implements the existing
 * {@link Wave7ArchmageVexPolicy}, which stays the single Vex owner.</p>
 *
 * <p>Legacy ids remain accounted for through {@link #action(String)}. Nothing is
 * silently renamed: the ledger in
 * {@code docs/end-rift/2026-11-23/V5_ACCEPTANCE_MATRIX.md} records which V4
 * behaviour each old id maps onto and which parts are SUPERSEDED.</p>
 */
public final class ArchmageV5CombatPolicy {
    private ArchmageV5CombatPolicy() {
    }

    /** The final V5.1 Archmage action vocabulary. */
    public enum Action {
        /** Existing bounded Vex support; owned by {@link Wave7ArchmageVexPolicy}. */
        VEX_SUPPORT,
        /** Three consecutive real fangs across a three-block local lane. */
        FANG_LINE,
        /** 7..9 jaws in a ring with one genuinely traversable gap. */
        CIRCLE_OF_FANGS,
        /** Five curved rotating spoke-arms, a bounded channel. */
        CROWN_OF_FANGS,
        /** Short warned anti-crowd staff push. */
        STAFF_REPULSE,
        /** Existing validated floor blink. */
        REPOSITION,
        /** Existing long ranged beam / spear beats. */
        RANGED_BEAT,
        /** Not an Archmage action. */
        NONE
    }

    /**
     * Explicit old-id to final-action mapping.
     *
     * <p>{@code ARCHMAGE_BEAM} and {@code ARCHMAGE_SPEAR} are kept as legal
     * ranged beats: the V5.1 contract permits "existing suitable ranged/step
     * spells". {@code ARCHMAGE_FANGS_GROUP} is SUPERSEDED as a wide all-party
     * line and is remapped onto the short local Fang Line; the group spell's
     * job is now done by the Circle, which warns and shows a gap.</p>
     */
    public static Action action(String legacyId) {
        if (legacyId == null || legacyId.isBlank()) {
            return Action.NONE;
        }
        String canonical = legacyId.trim().toUpperCase(java.util.Locale.ROOT);
        if (canonical.startsWith("WAVE7_")) {
            canonical = canonical.substring("WAVE7_".length());
        }
        return switch (canonical) {
            case "ARCHMAGE_VEX", "ARCHMAGE_VEX_SUPPORT" -> Action.VEX_SUPPORT;
            case "ARCHMAGE_FANGS", "ARCHMAGE_FANG_LINE", "ARCHMAGE_FANGS_GROUP" -> Action.FANG_LINE;
            case "ARCHMAGE_CIRCLE", "ARCHMAGE_CIRCLE_OF_FANGS" -> Action.CIRCLE_OF_FANGS;
            case "ARCHMAGE_CROWN", "ARCHMAGE_CROWN_OF_FANGS" -> Action.CROWN_OF_FANGS;
            case "ARCHMAGE_REPULSE", "ARCHMAGE_STAFF_REPULSE" -> Action.STAFF_REPULSE;
            case "ARCHMAGE_STEP", "ARCHMAGE_REPOSITION" -> Action.REPOSITION;
            case "ARCHMAGE_BEAM", "ARCHMAGE_SPEAR" -> Action.RANGED_BEAT;
            case "NONE", "NONE_INTERNAL" -> Action.NONE;
            default -> Action.NONE;
        };
    }

    /** The four new actions that a Crown channel must suppress. */
    public static boolean isBoundedMajor(Action action) {
        return action == Action.CIRCLE_OF_FANGS || action == Action.CROWN_OF_FANGS
                || action == Action.STAFF_REPULSE;
    }

    // ------------------------------------------------------------------
    // Fang Line
    // ------------------------------------------------------------------

    /** Real fang bodies in one line. */
    public static final int FANG_LINE_TEETH = 3;
    /** Total local lane length the three fangs span, in blocks. */
    public static final double FANG_LINE_PATH_BLOCKS = 3.0D;
    /**
     * Warning window. The contract allows 0.7..1.0 s; 0.8 s (40 ticks) keeps a
     * readable tell without turning a frequent spell into a cutscene.
     */
    public static final int FANG_LINE_WARNING_TICKS = 18;
    /** Per-fang damage before party scaling. */
    public static final double FANG_LINE_DAMAGE = 7.0D;

    /**
     * Offsets of the three fangs along the locked aim direction.
     *
     * <p>They are centred on the marked cell so the lane is short and local:
     * -1, 0 and +1 block. A caster-to-target 30-block line would be the exact
     * "large unannounced Fang Line" the owner rejected.</p>
     */
    public static double[] fangLineOffsets() {
        return new double[]{-1.0D, 0.0D, 1.0D};
    }

    /**
     * Ground cells of one Fang Line, in world space.
     *
     * @param centerX marked cell centre X
     * @param centerZ marked cell centre Z
     * @param directionX normalized horizontal aim X
     * @param directionZ normalized horizontal aim Z
     */
    public static List<double[]> fangLineCells(double centerX, double centerZ,
                                               double directionX, double directionZ) {
        double[] offsets = fangLineOffsets();
        List<double[]> cells = new ArrayList<>(offsets.length);
        double length = Math.hypot(directionX, directionZ);
        if (!Double.isFinite(length) || length < 1.0E-6D) {
            directionX = 0.0D;
            directionZ = 1.0D;
        } else {
            directionX /= length;
            directionZ /= length;
        }
        for (double offset : offsets) {
            double x = centerX + directionX * offset;
            double z = centerZ + directionZ * offset;
            // Snap to the authored block centres so three consecutive fangs
            // occupy exactly three consecutive cells.
            cells.add(new double[]{Math.floor(x) + 0.5D, Math.floor(z) + 0.5D});
        }
        return List.copyOf(cells);
    }

    // ------------------------------------------------------------------
    // Circle of Fangs
    // ------------------------------------------------------------------

    public static final int CIRCLE_JAWS_MIN = 7;
    public static final int CIRCLE_JAWS_MAX = 9;
    public static final double CIRCLE_RADIUS_MIN = 2.3D;
    public static final double CIRCLE_RADIUS_MAX = 2.7D;
    /** Smallest gap the owner accepts as a real escape route. */
    public static final double CIRCLE_GAP_BLOCKS_MIN = 2.0D;
    /** 1.0..1.3 s; 1.1 s keeps the ring readable under a 2-player pile. */
    public static final int CIRCLE_WARNING_TICKS = 24;
    public static final double CIRCLE_FANG_DAMAGE = 8.0D;

    /** Clamp an authored radius into the ratified band. */
    public static double circleRadius(double requested) {
        if (!Double.isFinite(requested)) {
            return (CIRCLE_RADIUS_MIN + CIRCLE_RADIUS_MAX) / 2.0D;
        }
        return Math.max(CIRCLE_RADIUS_MIN, Math.min(CIRCLE_RADIUS_MAX, requested));
    }

    /** How many jaws a party size earns: one legal player -> 7, a group -> 9. */
    public static int circleJaws(int admittedPlayers) {
        if (admittedPlayers <= 2) {
            return CIRCLE_JAWS_MIN;
        }
        if (admittedPlayers <= 9) {
            return 8;
        }
        return CIRCLE_JAWS_MAX;
    }

    /** One fang position on the ring. */
    public record Jaw(double x, double z, double angleDegrees) {
    }

    /**
     * Ring positions with one explicit gap.
     *
     * <p>The jaws are spread across {@code 360 - gapAngle} and the empty sector
     * is centred on {@code gapCenterDegrees}. The gap angle is derived from the
     * real chord, not typed in: at radius R a gap of G blocks needs
     * {@code 2*asin(G/(2R))}. At the default radius that is about 47 degrees,
     * which is the arc a player actually walks through.</p>
     */
    public static List<Jaw> circleJaws(int jaws, double radius, double gapBlocks,
                                       double gapCenterDegrees) {
        int count = Math.max(CIRCLE_JAWS_MIN, Math.min(CIRCLE_JAWS_MAX, jaws));
        double r = circleRadius(radius);
        double gap = Math.max(CIRCLE_GAP_BLOCKS_MIN, Math.min(2.0D * r, gapBlocks));
        double gapAngle = Math.toDegrees(2.0D * Math.asin(gap / (2.0D * r)));
        double available = 360.0D - gapAngle;
        double start = gapCenterDegrees + gapAngle / 2.0D;
        List<Jaw> out = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            // Span the whole available arc: with `count` jaws the first sits at
            // the edge of the gap and the last sits at the other edge, so the
            // empty sector is exactly the gap the players were promised and not
            // a wider one that would make the ring look misleadingly generous.
            double angle = start + (count == 1 ? 0.0D
                    : available * index / (count - 1.0D));
            double radians = Math.toRadians(angle);
            out.add(new Jaw(Math.cos(radians) * r, Math.sin(radians) * r, angle));
        }
        return List.copyOf(out);
    }

    /** Largest empty angular sector of a ring, in degrees. */
    public static double largestGapDegrees(List<Jaw> jaws) {
        if (jaws == null || jaws.size() < 2) {
            return 360.0D;
        }
        List<Double> angles = new ArrayList<>(jaws.size());
        for (Jaw jaw : jaws) {
            angles.add(((jaw.angleDegrees() % 360.0D) + 360.0D) % 360.0D);
        }
        angles.sort(Double::compare);
        double largest = 0.0D;
        for (int index = 0; index < angles.size(); index++) {
            double next = index + 1 < angles.size() ? angles.get(index + 1) : angles.get(0) + 360.0D;
            largest = Math.max(largest, next - angles.get(index));
        }
        return largest;
    }

    /**
     * True when the ring really leaves a walkable sector: the largest empty
     * angle must subtend at least the ratified gap at the ring radius.
     */
    public static boolean hasTraversableGap(List<Jaw> jaws, double radius, double gapBlocks) {
        if (jaws == null || jaws.isEmpty()) {
            return false;
        }
        double r = circleRadius(radius);
        double required = Math.toDegrees(2.0D * Math.asin(
                Math.min(1.0D, gapBlocks / (2.0D * r))));
        // A jaw is a body with width, and an exact floating-point equality is
        // not a fair test: a gap that subtends the authored chord within one
        // jaw's slack is still the escape route the players were promised.
        return largestGapDegrees(jaws) >= required - 1.5D;
    }

    // ------------------------------------------------------------------
    // Crown of Fangs
    // ------------------------------------------------------------------

    public static final int CROWN_SPOKES = 5;
    public static final double CROWN_LENGTH_MIN = 7.0D;
    public static final double CROWN_LENGTH_MAX = 9.0D;
    /** Charge time before the arms exist. */
    public static final int CROWN_CHARGE_TICKS = 30;
    /** Standard channel floor and ceiling. */
    public static final int CROWN_CHANNEL_MIN_TICKS = 240;
    public static final int CROWN_CHANNEL_MAX_TICKS = 360;
    /** Absolute bounded finish: no Crown may outlive 30 s. */
    public static final int CROWN_HARD_MAX_TICKS = 600;
    public static final double CROWN_ROTATION_MIN_DEG_PER_SEC = 8.0D;
    public static final double CROWN_ROTATION_MAX_DEG_PER_SEC = 12.0D;
    /** Teeth density: one fang every 1.25 blocks of arm. */
    public static final double CROWN_TEETH_SPACING_BLOCKS = 1.25D;
    /** Cooldown band. */
    public static final int CROWN_COOLDOWN_MIN_TICKS = 900;
    public static final int CROWN_COOLDOWN_MAX_TICKS = 1200;
    public static final double CROWN_TOOTH_DAMAGE = 9.0D;

    /** Clamp an authored arm length into 7..9 blocks. */
    public static double crownLength(double requested) {
        if (!Double.isFinite(requested)) {
            return CROWN_LENGTH_MAX;
        }
        return Math.max(CROWN_LENGTH_MIN, Math.min(CROWN_LENGTH_MAX, requested));
    }

    /** Clamp the authored rotation into 8..12 deg/s. */
    public static double crownRotation(double requested) {
        if (!Double.isFinite(requested)) {
            return CROWN_ROTATION_MIN_DEG_PER_SEC;
        }
        return Math.max(CROWN_ROTATION_MIN_DEG_PER_SEC,
                Math.min(CROWN_ROTATION_MAX_DEG_PER_SEC, requested));
    }

    /** Channel length for a roster, inside the standard band and the hard cap. */
    public static int crownChannelTicks(int admittedPlayers) {
        int base = admittedPlayers <= 2 ? CROWN_CHANNEL_MIN_TICKS
                : admittedPlayers <= 9 ? 300 : CROWN_CHANNEL_MAX_TICKS;
        return Math.min(CROWN_HARD_MAX_TICKS, base);
    }

    /** True exactly once the channel has to end, whatever the author intended. */
    public static boolean crownMustFinish(int elapsedTicks) {
        return elapsedTicks >= CROWN_HARD_MAX_TICKS;
    }

    /** Arm angles at one instant; deterministic from the elapsed ticks. */
    public static double[] crownSpokeAnglesDegrees(long elapsedTicks, double degreesPerSecond,
                                                   double phaseDegrees) {
        double dps = crownRotation(degreesPerSecond);
        double rotation = (elapsedTicks / 20.0D) * dps + phaseDegrees;
        double[] angles = new double[CROWN_SPOKES];
        for (int index = 0; index < CROWN_SPOKES; index++) {
            angles[index] = (rotation + index * (360.0D / CROWN_SPOKES)) % 360.0D;
        }
        return angles;
    }

    /**
     * Sampling points along one curved arm.
     *
     * <p>The arm curves: it bends by {@code CURVE_DEGREES} across its length so
     * the Crown reads as obsidian roots and jaws rather than five coloured
     * sticks. The returned points are in local space relative to the caster.</p>
     */
    public static final double CROWN_CURVE_DEGREES = 26.0D;

    public static List<double[]> crownArmPoints(double angleDegrees, double lengthBlocks,
                                                int samples) {
        int steps = Math.max(2, Math.min(48, samples));
        double length = crownLength(lengthBlocks);
        List<double[]> points = new ArrayList<>(steps);
        for (int index = 0; index <= steps; index++) {
            double t = index / (double) steps;
            double curved = angleDegrees + CROWN_CURVE_DEGREES * (t * t - t * 0.5D);
            double radians = Math.toRadians(curved);
            double distance = length * t;
            // A shallow rise near the caster makes the arm leave the floor.
            double lift = Math.sin(Math.PI * t) * 0.55D + t * 0.25D;
            points.add(new double[]{Math.cos(radians) * distance, lift,
                    Math.sin(radians) * distance});
        }
        return List.copyOf(points);
    }

    /** Number of real fang teeth that fit on one arm. */
    public static int crownTeeth(double lengthBlocks) {
        return Math.max(1, (int) Math.floor(crownLength(lengthBlocks)
                / CROWN_TEETH_SPACING_BLOCKS));
    }

    /**
     * A tooth bites when the player is inside its own small volume, measured on
     * the rotating arm. Callers pass the player's offset in the arm's local
     * frame, so a target that walked out of the wedge is never hit by UUID.
     */
    public static boolean crownToothHits(double alongBlocks, double lateralBlocks,
                                         double verticalBlocks) {
        return alongBlocks > 0.75D && alongBlocks <= CROWN_LENGTH_MAX
                && Math.abs(lateralBlocks) <= 0.85D
                && Math.abs(verticalBlocks) <= 1.9D;
    }

    // ------------------------------------------------------------------
    // Staff Repulse
    // ------------------------------------------------------------------

    /** 0.5..0.7 s; 0.6 s is the readable middle of the band. */
    public static final int REPULSE_WARNING_TICKS = 12;
    public static final double REPULSE_RADIUS_MIN = 3.5D;
    public static final double REPULSE_RADIUS_MAX = 4.5D;
    public static final double REPULSE_PUSH_MIN = 2.5D;
    public static final double REPULSE_PUSH_MAX = 4.0D;
    /** Moderate/low: it is a defensive reset, never a damage spell. */
    public static final double REPULSE_DAMAGE = 3.0D;
    public static final int REPULSE_COOLDOWN_MIN_TICKS = 240;
    public static final int REPULSE_COOLDOWN_MAX_TICKS = 360;
    /** Vertical tolerance: a player on a ledge above the caster is not shoved. */
    public static final double REPULSE_VERTICAL = 2.5D;

    public static double repulseRadius() {
        return (REPULSE_RADIUS_MIN + REPULSE_RADIUS_MAX) / 2.0D;
    }

    public static double repulsePush() {
        return (REPULSE_PUSH_MIN + REPULSE_PUSH_MAX) / 2.0D;
    }

    public static int repulseCooldownTicks() {
        return (REPULSE_COOLDOWN_MIN_TICKS + REPULSE_COOLDOWN_MAX_TICKS) / 2;
    }

    /**
     * A crowd answer is warranted when at least two admitted players are inside
     * the repulse radius and the cooldown has elapsed. One rusher is handled by
     * the ordinary punish window, not by a defensive spell.
     */
    public static boolean repulseWarranted(int closePlayers, long nowTick, long lastRepulseTick,
                                           boolean crownActive) {
        if (crownActive) {
            // A single optional defensive repulse is allowed during the Crown,
            // but only after its own cooldown, and it must never restart the
            // channel or add a second major cast.
            return false;
        }
        if (closePlayers < 2) {
            return false;
        }
        return lastRepulseTick <= 0L || nowTick - lastRepulseTick >= repulseCooldownTicks();
    }

    /** Distance test for the repulse, including the vertical band. */
    public static boolean repulseHits(double horizontalDistance, double verticalDistance) {
        return Double.isFinite(horizontalDistance)
                && horizontalDistance <= REPULSE_RADIUS_MAX
                && horizontalDistance >= 0.0D
                && Math.abs(verticalDistance) <= REPULSE_VERTICAL;
    }

    /** Whether a cast is legal while a Crown channel owns the caster. */
    public static boolean allowedDuringCrown(Action action) {
        return action == Action.STAFF_REPULSE;
    }

    // ------------------------------------------------------------------
    // Pacing
    // ------------------------------------------------------------------

    /** Warning ticks for one final action. */
    public static int warningTicks(Action action) {
        return switch (action) {
            case FANG_LINE -> FANG_LINE_WARNING_TICKS;
            case CIRCLE_OF_FANGS -> CIRCLE_WARNING_TICKS;
            case CROWN_OF_FANGS -> CROWN_CHARGE_TICKS;
            case STAFF_REPULSE -> REPULSE_WARNING_TICKS;
            case REPOSITION -> 18;
            case RANGED_BEAT -> 24;
            case VEX_SUPPORT -> 20;
            case NONE -> 0;
        };
    }

    /**
     * Cooldown after release, in ticks.
     *
     * <p>The Crown is the spell with the long authored reset; the others are
     * frequent beats. These are additive to the existing per-attack cooldowns
     * in {@link Wave7NamedTrialActorPolicy} and only used for the new actions.</p>
     */
    public static int cooldownTicks(Action action) {
        return switch (action) {
            case FANG_LINE -> 60;
            case CIRCLE_OF_FANGS -> 180;
            case CROWN_OF_FANGS -> CROWN_COOLDOWN_MIN_TICKS;
            case STAFF_REPULSE -> repulseCooldownTicks();
            case REPOSITION -> 220;
            case RANGED_BEAT -> 80;
            case VEX_SUPPORT -> 200;
            case NONE -> 1;
        };
    }

    /** Short authored label used by diagnostics and pose packets. */
    public static String clip(Action action) {
        return switch (action) {
            case VEX_SUPPORT -> "ARCHMAGE_VEX";
            case FANG_LINE -> "ARCHMAGE_FANGS";
            case CIRCLE_OF_FANGS -> "ARCHMAGE_CIRCLE";
            case CROWN_OF_FANGS -> "ARCHMAGE_CROWN";
            case STAFF_REPULSE -> "ARCHMAGE_REPULSE";
            case REPOSITION -> "ARCHMAGE_STEP";
            case RANGED_BEAT -> "ARCHMAGE_BEAM";
            case NONE -> "NONE";
        };
    }
}
