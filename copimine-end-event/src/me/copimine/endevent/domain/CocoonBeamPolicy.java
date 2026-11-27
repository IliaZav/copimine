package me.copimine.endevent.domain;

/**
 * Server timing and steering for the Last Seal cocoon's single-target beam.
 *
 * <p>This is the owner's dedicated Phase 6 ability and is deliberately
 * separate from the normal-phase {@link ChestBeamPolicy} ultimate: it has its
 * own cast identity, its own reservation, and its own (much longer) timing.</p>
 *
 * <p>Timing is authoritative server ticks at 20 TPS: a charge wind-up, exactly
 * 400 active firing ticks (20 real seconds), a short recovery, then a 2400-tick
 * (120 real second) cooldown that only starts once the beam has actually
 * terminated. Charging and recovery are therefore not part of the 400 active
 * ticks.</p>
 */
public final class CocoonBeamPolicy {
    /** Chest energy accumulation before release. Not part of the active window. */
    public static final int CHARGE_TICKS = 26;
    /** Active beam: exactly 20 seconds at 20 TPS (400 server ticks). */
    public static final int FIRE_TICKS = 400;
    /** Post-beam recovery: visuals decay, the Guardian settles. */
    public static final int RECOVERY_TICKS = 12;
    /** One accepted contact per 20 ticks of real contact (initial balance). */
    public static final int HIT_INTERVAL_TICKS = 20;
    /** Cooldown starts when the beam terminates, not when it was cast. */
    public static final int COOLDOWN_TICKS = 2400;
    /** Half-width of the swept beam volume. */
    public static final double RADIUS = 1.85D;
    /** Finite reach; the beam never covers the whole world. */
    public static final double MAX_REACH = 118D;
    /** Initial balance: 4 HP per accepted contact, not the old 9 HP burst. */
    public static final double DAMAGE = 4.0D;

    private CocoonBeamPolicy() { }

    /** Charge follows faster than the released beam, which can be outrun sideways. */
    public static double maxTurn(long elapsedTicks, boolean charging) {
        return Math.toRadians(Math.clamp(elapsedTicks, 0, 5) * (charging ? 2.2D : .85D));
    }

    /** Add inward speed without cancelling outward input or affecting lateral motion. */
    public static double pullIncrement(double inwardSpeed) {
        if (!Double.isFinite(inwardSpeed)) return 0D;
        return Math.clamp(.18D - inwardSpeed, 0D, .032D);
    }
}
