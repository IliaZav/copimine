package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Master contract 9G.07: when the LAST permanent defender dies the cocoon does
 * not simply vanish. One authored split sequence plays -- veins pulse, the
 * segmented shell cracks from the centre outward, large shell plates fly away on
 * ballistic arcs, a low-pressure shock draws dust outward, and the Guardian is
 * revealed behind the breaking shell before its real descent begins.
 *
 * <p>Every number lives here so the sequence can be asserted without a server.
 * The runtime only executes the plan: it manufactures display plates, moves them
 * along {@link #offset}, draws the authored particle passes and finally removes
 * them. Nothing in this class mutates a block, deals damage, or decides when the
 * fall itself starts -- {@link CocoonFallPolicy} still owns the descent clock,
 * so the shatter can never delay the landing hitbox.</p>
 */
public final class CocoonShatterPolicy {

    /** Authored order; index order is the contract order and never reordered. */
    public enum Stage {
        /** Faint internal glow gathers and violet veins brighten. */
        VEINS_PULSE,
        /** The shell cracks from the centre outward in a growing star. */
        SHELL_CRACK,
        /** Large plates break free and fly on ballistic arcs. */
        PLATE_BURST,
        /** A low-pressure dust shock draws outward from the Core. */
        SHOCK_DUST,
        /** Only the revealed Guardian remains; the last plates fade out. */
        REVEAL
    }

    /** Whole split sequence, measured from the first visual tick. */
    public static final int TOTAL_TICKS = 52;
    public static final int VEINS_TICKS = 8;
    public static final int CRACK_TICKS = 8;
    public static final int BURST_TICKS = 18;
    public static final int DUST_TICKS = 8;
    public static final int REVEAL_TICKS = TOTAL_TICKS - VEINS_TICKS - CRACK_TICKS
            - BURST_TICKS - DUST_TICKS;

    /** Bounded plate budget: substantial pieces, never a confetti storm. */
    public static final int PLATE_COUNT = 18;
    /** The shell's authored radius and vertical half-height, before scaling. */
    public static final double SHELL_RADIUS = 1.9D;
    public static final double SHELL_HALF_HEIGHT = 1.75D;
    /** Downward acceleration applied to every plate, in blocks per tick^2. */
    public static final double GRAVITY = 0.055D;
    /** No plate may travel further than this from the shell centre. */
    public static final double MAX_RADIUS = 7.5D;

    /** One authored shell plate with a deterministic trajectory. */
    public record Plate(int index, double yawDegrees, double elevationDegrees,
                        double speed, double spinDegrees, double size, int releaseDelay) { }

    /** A sampled plate transform for one tick, relative to the shell centre. */
    public record Offset(double x, double y, double z,
                         double pitchDegrees, double yawDegrees, double rollDegrees) { }

    private CocoonShatterPolicy() {
    }

    /**
     * The fixed plate layout. Deterministic so a restart, a second viewer and a
     * test all see the same pieces: no {@code Random} in the contract.
     */
    public static List<Plate> plates() {
        List<Plate> plates = new ArrayList<>(PLATE_COUNT);
        for (int i = 0; i < PLATE_COUNT; i++) {
            // Even yaw coverage with a small authored wobble, three elevation
            // bands so the shell opens at the top, waist and base at once.
            double yaw = (360.0D / PLATE_COUNT) * i + (i % 3) * 6.0D;
            double elevation = -26.0D + (i * 37 % 72);
            double speed = 0.11D + (i % 4) * 0.032D;
            double spin = 95.0D + (i % 5) * 42.0D;
            double size = 0.34D + (i % 3) * 0.065D;
            int delay = i % 4;
            plates.add(new Plate(i, yaw, elevation, speed, spin, size, delay));
        }
        return List.copyOf(plates);
    }

    /** Stage for an absolute tick; negative ticks are still the first beat. */
    public static Stage stageAt(int elapsedTicks) {
        int tick = Math.max(0, elapsedTicks);
        if (tick < VEINS_TICKS) {
            return Stage.VEINS_PULSE;
        }
        tick -= VEINS_TICKS;
        if (tick < CRACK_TICKS) {
            return Stage.SHELL_CRACK;
        }
        tick -= CRACK_TICKS;
        if (tick < BURST_TICKS) {
            return Stage.PLATE_BURST;
        }
        tick -= BURST_TICKS;
        if (tick < DUST_TICKS) {
            return Stage.SHOCK_DUST;
        }
        return Stage.REVEAL;
    }

    /** True once the plate has been released by its authored stagger. */
    public static boolean released(Plate plate, int elapsedTicks) {
        return plate != null && elapsedTicks >= plate.releaseDelay();
    }

    /**
     * Sample the plate's ballistic motion. Before release it sits on the shell
     * surface; after release it travels outward, arcs under gravity and spins.
     * The travel distance is clamped to {@link #MAX_RADIUS} so a long lifetime
     * can never throw a display out of the arena budget.
     */
    public static Offset offset(Plate plate, int elapsedTicks) {
        if (plate == null) {
            return new Offset(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        double yaw = Math.toRadians(plate.yawDegrees());
        double elevation = Math.toRadians(plate.elevationDegrees());
        double cosElevation = Math.cos(elevation);
        // Resting position on the shell: a point on an ellipsoid.
        double baseX = Math.cos(yaw) * cosElevation * SHELL_RADIUS;
        double baseZ = Math.sin(yaw) * cosElevation * SHELL_RADIUS;
        double baseY = Math.sin(elevation) * SHELL_HALF_HEIGHT;
        int age = Math.max(0, elapsedTicks - plate.releaseDelay());
        if (age == 0) {
            return new Offset(baseX, baseY, baseZ,
                    plate.yawDegrees() * 0.25D, plate.yawDegrees(), 0.0D);
        }
        double outward = plate.speed() * age;
        double x = baseX + Math.cos(yaw) * cosElevation * outward;
        double z = baseZ + Math.sin(yaw) * cosElevation * outward;
        double y = baseY + Math.sin(elevation) * outward
                + 0.16D * age - 0.5D * GRAVITY * age * age;
        // Keep the piece inside the authored budget even after a long resume.
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal > MAX_RADIUS) {
            double scale = MAX_RADIUS / horizontal;
            x *= scale;
            z *= scale;
        }
        double spin = plate.spinDegrees() * age * 0.5D;
        return new Offset(x, y, z, spin, plate.yawDegrees() + spin, spin * 0.5D);
    }

    /** 0..1 crack growth used by the authored crack particle pass. */
    public static double crackProgress(int elapsedTicks) {
        int tick = Math.max(0, elapsedTicks) - VEINS_TICKS;
        return Math.clamp(tick / (double) Math.max(1, CRACK_TICKS), 0.0D, 1.0D);
    }

    /** Expanding dust-shock radius in blocks; 0 before the dust beat. */
    public static double shockRadius(int elapsedTicks) {
        Stage stage = stageAt(elapsedTicks);
        if (stage != Stage.SHOCK_DUST && stage != Stage.REVEAL) {
            return 0.0D;
        }
        int tick = Math.max(0, elapsedTicks) - VEINS_TICKS - CRACK_TICKS - BURST_TICKS;
        double progress = Math.clamp(tick / (double) Math.max(1, DUST_TICKS), 0.0D, 1.0D);
        return 0.8D + 6.2D * (1.0D - Math.pow(1.0D - progress, 2.0D));
    }

    /** Triangular vein brightness used while the shell is still closed. */
    public static double veinPulse(int elapsedTicks) {
        int tick = Math.max(0, elapsedTicks);
        double period = Math.max(1.0D, VEINS_TICKS * 2.0D);
        double phase = (tick % period) / period;
        return 1.0D - Math.abs(2.0D * phase - 1.0D);
    }

    /** 1 while the plates are visible, easing to 0 across the reveal beat. */
    public static double revealFade(int elapsedTicks) {
        int revealStart = VEINS_TICKS + CRACK_TICKS + BURST_TICKS + DUST_TICKS;
        if (elapsedTicks <= revealStart) {
            return 1.0D;
        }
        return Math.clamp(1.0D - (elapsedTicks - revealStart) / (double) Math.max(1, REVEAL_TICKS),
                0.0D, 1.0D);
    }

    /** True exactly on the tick the plates are released, for the one-shot sound. */
    public static boolean burstStartsAt(int elapsedTicks) {
        return elapsedTicks == VEINS_TICKS + CRACK_TICKS;
    }

    /** True exactly on the tick the dust shock begins, for its one-shot cue. */
    public static boolean shockStartsAt(int elapsedTicks) {
        return elapsedTicks == VEINS_TICKS + CRACK_TICKS + BURST_TICKS;
    }

    /** The split is over and only the revealed Guardian remains. */
    public static boolean finishedAt(int elapsedTicks) {
        return elapsedTicks >= TOTAL_TICKS;
    }
}
