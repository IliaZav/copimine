package me.copimine.endevent.domain;

/**
 * Phase 4 (OVERLOAD) signature spell: the rotating Rift Fire Ring.
 *
 * <p>The Guardian gathers the arena's heat into a spinning ring of fire, then
 * hurls it at the party. The ring flies forward while rotating about its travel
 * axis, and detonates on the first real contact - a player, or the floor when it
 * is not stopped. The burst damages and ignites everyone it catches.</p>
 *
 * <p>This is a signature cast, so
 * {@link BossSignatureCastGatePolicy} fires it at most once per configured HP
 * threshold; the policy here owns only the shape and timing of one ring.</p>
 *
 * <p>Counterplay is authored, not implied: the gather window is a readable tell
 * ({@link #GATHER_TICKS}), the flight is slow enough to sidestep
 * ({@link #travelPerTick()}), and the actual hit test is the ring's own
 * thickness ({@link #HIT_RADIUS}), never an invisible homing sphere.</p>
 */
public final class RiftFireRingPolicy {
    /** Segments drawn around the ring; the hit volume matches this ring. */
    public static final int SEGMENTS = 14;
    /** The visible gather tell before the ring leaves the Guardian. */
    public static final int GATHER_TICKS = 30;
    /** Hard ceiling on the flight so a ring can never live forever. */
    public static final int FLIGHT_TICKS_MAX = 90;
    /** How long the detonation remains a damaging volume. */
    public static final int BURST_TICKS = 6;
    /** Ring geometry in world blocks. */
    public static final double RING_RADIUS = 2.8D;
    public static final double SEGMENT_RADIUS = 1.2D;
    /** Real hit geometry, not cosmetic. */
    public static final double HIT_RADIUS = 2.2D;
    public static final double BURST_RADIUS = 3.6D;
    /** Damage and burn. */
    public static final double IMPACT_DAMAGE = 10.0D;
    public static final double BURST_DAMAGE = 15.0D;
    public static final int IGNITE_TICKS = 60;
    /** Visual spin; the ring keeps turning the whole flight. */
    public static final double ROTATION_DEGREES_PER_TICK = 18.0D;
    public static final double SPEED_BLOCKS_PER_TICK = 0.55D;
    /** A signature cast still needs a real cooldown. */
    public static final int COOLDOWN_TICKS = 1000;

    public enum Phase {
        GATHER,
        FLIGHT,
        BURST,
        ENDED
    }

    private RiftFireRingPolicy() {
    }

    /** Lifecycle phase for one ring cast tick. */
    public static Phase phaseFor(long castTick) {
        if (castTick < 0L) {
            return Phase.ENDED;
        }
        if (castTick < GATHER_TICKS) {
            return Phase.GATHER;
        }
        if (castTick < GATHER_TICKS + FLIGHT_TICKS_MAX) {
            return Phase.FLIGHT;
        }
        if (castTick < GATHER_TICKS + FLIGHT_TICKS_MAX + BURST_TICKS) {
            return Phase.BURST;
        }
        return Phase.ENDED;
    }

    /** True only while the ring is really in flight. */
    public static boolean inFlight(long castTick) {
        return phaseFor(castTick) == Phase.FLIGHT;
    }

    /** Distance the ring centre has travelled along its locked heading. */
    public static double travelledBlocks(long flightTick) {
        long ticks = Math.max(0L, Math.min(FLIGHT_TICKS_MAX, flightTick));
        return ticks * SPEED_BLOCKS_PER_TICK;
    }

    public static double travelPerTick() {
        return SPEED_BLOCKS_PER_TICK;
    }

    /** Spin angle of the ring at a flight tick; continuous, never a jump cut. */
    public static double rotationDegrees(long flightTick) {
        long ticks = Math.max(0L, flightTick);
        return (ticks * ROTATION_DEGREES_PER_TICK) % 360.0D;
    }

    /**
     * The offset of one segment on the ring, in the ring's own plane.
     *
     * @param index      segment index
     * @param spinDegrees current ring spin
     * @return a two-element {@code {dx, dz}} offset
     */
    public static double[] segmentOffset(int index, double spinDegrees) {
        double step = 360.0D / SEGMENTS;
        double angle = Math.toRadians(index * step + spinDegrees);
        return new double[]{Math.cos(angle) * RING_RADIUS, Math.sin(angle) * RING_RADIUS};
    }

    /** A ring segment itself hits only inside its own authored radius. */
    public static boolean segmentConnects(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            return false;
        }
        return Math.hypot(dx, dz) <= SEGMENT_RADIUS + HIT_RADIUS;
    }

    /**
     * First-contact test: the ring detonates when its centre reaches a player's
     * body or the floor. {@code centreDistanceToPlayer} is the 3D distance from
     * the flying centre to the player; {@code reachedFloor} is a real block
     * probe, never inferred.
     */
    public static boolean detonates(double centreDistanceToPlayer, boolean reachedFloor) {
        if (reachedFloor) {
            return true;
        }
        return Double.isFinite(centreDistanceToPlayer) && centreDistanceToPlayer <= HIT_RADIUS;
    }

    /** The burst volume is a real radius around the detonation point. */
    public static boolean burstConnects(double distance) {
        return Double.isFinite(distance) && distance <= BURST_RADIUS;
    }

    /** A signature ring only fires again once its cooldown has elapsed. */
    public static boolean mayCast(long lastCastTick, long nowTick) {
        if (lastCastTick < 0L || nowTick < 0L) {
            return true;
        }
        return nowTick - lastCastTick >= COOLDOWN_TICKS;
    }

    /** Diagnostic label; logs must never invent a different name. */
    public static String describe() {
        return "PHASE4_RIFT_FIRE_RING segments=" + SEGMENTS
                + " gather_ticks=" + GATHER_TICKS
                + " flight_ticks_max=" + FLIGHT_TICKS_MAX
                + " ring_radius=" + RING_RADIUS
                + " hit_radius=" + HIT_RADIUS
                + " burst_radius=" + BURST_RADIUS
                + " impact_damage=" + IMPACT_DAMAGE
                + " burst_damage=" + BURST_DAMAGE
                + " ignite_ticks=" + IGNITE_TICKS
                + " cooldown_ticks=" + COOLDOWN_TICKS;
    }
}
