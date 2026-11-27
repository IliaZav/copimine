package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4 (OVERLOAD) signature spell No.1 — the Infernal Forge ("Адская кузня").
 *
 * <p>The Guardian pulls the arena's fire inward: molten streams run across the
 * floor to the body, the magma patches cool, and bright fire cracks appear on
 * its shell. Once the heat is gathered it enters a short <b>overheat</b>: a few
 * enhanced physical strikes, each releasing a directed fire impulse, closed by a
 * two-limb ground slam. Then the overheated shell breaks and the Guardian is
 * briefly <b>vulnerable</b>.</p>
 *
 * <p>Counterplay is the whole point: dodge the strike series, then punish the
 * recovery window. The spell is medium difficulty and deliberately summons
 * <em>no</em> extra mobs ({@link #spawnsExtraMobs()}), so the fight is not
 * overloaded.</p>
 */
public final class InfernalForgePolicy {
    /** The pull-in gather tell; the arena visibly drains toward the body. */
    public static final int GATHER_TICKS = 60;
    /** Enhanced strikes in the overheat series. */
    public static final int STRIKE_COUNT = 3;
    /** Spacing of the strike series. */
    public static final int STRIKE_INTERVAL_TICKS = 24;
    /** A short windup before each impulse, so every one is dodgeable. */
    public static final int IMPULSE_WINDUP_TICKS = 8;
    /** Directed fire impulse geometry. */
    public static final double IMPULSE_REACH_BLOCKS = 14.0D;
    public static final double IMPULSE_HALF_ANGLE_DEGREES = 35.0D;
    public static final double IMPULSE_DAMAGE = 10.0D;
    /** The closing two-limb slam. */
    public static final int SLAM_WINDUP_TICKS = 20;
    public static final double SLAM_RADIUS = 4.5D;
    public static final double SLAM_DAMAGE = 18.0D;
    /** The recovery window: the broken shell is briefly easier to break. */
    public static final int VULNERABLE_TICKS = 80;
    public static final double VULNERABILITY_DAMAGE_MULTIPLIER = 2.0D;
    /** A signature cast still needs a real cooldown. */
    public static final int COOLDOWN_TICKS = 1600;

    public enum Phase {
        GATHER,
        OVERHEAT,
        SLAM,
        VULNERABLE,
        ENDED
    }

    private InfernalForgePolicy() {
    }

    // --- timeline -----------------------------------------------------------

    /** Tick the {@code index}-th (0-based) enhanced strike lands. */
    public static long strikeTick(int index) {
        int bounded = Math.max(0, Math.min(STRIKE_COUNT - 1, index));
        return GATHER_TICKS + (long) bounded * STRIKE_INTERVAL_TICKS;
    }

    /** Tick the impulse of the {@code index}-th strike is released. */
    public static long impulseTick(int index) {
        return strikeTick(index) + IMPULSE_WINDUP_TICKS;
    }

    /** Every strike tick of the series, in order. */
    public static List<Long> strikeTicks() {
        List<Long> ticks = new ArrayList<>(STRIKE_COUNT);
        for (int index = 0; index < STRIKE_COUNT; index++) {
            ticks.add(strikeTick(index));
        }
        return List.copyOf(ticks);
    }

    public static long slamWindupTick() {
        return strikeTick(STRIKE_COUNT - 1) + STRIKE_INTERVAL_TICKS;
    }

    public static long slamImpactTick() {
        return slamWindupTick() + SLAM_WINDUP_TICKS;
    }

    public static long vulnerableStartTick() {
        return slamImpactTick() + 1L;
    }

    public static long vulnerableEndTick() {
        return vulnerableStartTick() + VULNERABLE_TICKS;
    }

    public static long totalTicks() {
        return vulnerableEndTick();
    }

    public static Phase phaseFor(long tick) {
        if (tick < 0L || tick >= totalTicks()) {
            return tick < 0L ? Phase.GATHER : Phase.ENDED;
        }
        if (tick < GATHER_TICKS) {
            return Phase.GATHER;
        }
        if (tick < slamWindupTick()) {
            return Phase.OVERHEAT;
        }
        if (tick < vulnerableStartTick()) {
            return Phase.SLAM;
        }
        return Phase.VULNERABLE;
    }

    // --- damage/counterplay -------------------------------------------------

    /** True only inside the recovery window, when the shell is broken. */
    public static boolean isVulnerable(long tick) {
        return phaseFor(tick) == Phase.VULNERABLE;
    }

    /** Damage taken while the shell is broken; 1.0 outside the window. */
    public static double incomingDamageMultiplier(long tick) {
        return isVulnerable(tick) ? VULNERABILITY_DAMAGE_MULTIPLIER : 1.0D;
    }

    /**
     * A directed impulse connects inside its reach and within its facing cone.
     *
     * @param distance   horizontal distance from the Guardian
     * @param angleDeg   absolute angle between the body facing and the target
     */
    public static boolean impulseConnects(double distance, double angleDeg) {
        if (!Double.isFinite(distance) || !Double.isFinite(angleDeg)) {
            return false;
        }
        return distance <= IMPULSE_REACH_BLOCKS
                && Math.abs(angleDeg) <= IMPULSE_HALF_ANGLE_DEGREES;
    }

    /** The closing slam is a radial ground hit. */
    public static boolean slamConnects(double distance) {
        return Double.isFinite(distance) && distance <= SLAM_RADIUS;
    }

    /** Medium difficulty by construction: this spell never adds bodies. */
    public static boolean spawnsExtraMobs() {
        return false;
    }

    /** A signature forge only fires again once its cooldown has elapsed. */
    public static boolean mayCast(long lastCastTick, long nowTick) {
        if (lastCastTick < 0L || nowTick < 0L) {
            return true;
        }
        return nowTick - lastCastTick >= COOLDOWN_TICKS;
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe() {
        return "PHASE4_INFERNAL_FORGE gather_ticks=" + GATHER_TICKS
                + " strikes=" + STRIKE_COUNT
                + " strike_interval_ticks=" + STRIKE_INTERVAL_TICKS
                + " impulse_reach=" + IMPULSE_REACH_BLOCKS
                + " impulse_half_angle=" + IMPULSE_HALF_ANGLE_DEGREES
                + " slam_impact_tick=" + slamImpactTick()
                + " vulnerable_ticks=" + VULNERABLE_TICKS
                + " vulnerability_multiplier=" + VULNERABILITY_DAMAGE_MULTIPLIER
                + " extra_mobs=" + spawnsExtraMobs()
                + " cooldown_ticks=" + COOLDOWN_TICKS;
    }
}
