package me.copimine.endevent.domain;

/**
 * Close-range anti-camping for a real Wave 4 obelisk.
 *
 * <p>W4-009 [U]: sustained close pressure must trigger a repelling impulse, so a
 * player cannot stand beside one obelisk and hold attack indefinitely. The
 * engineering defaults are fixed by W4-010 and are deliberately small and
 * readable: a three-block close radius, a one-second warning, and a nine-second
 * per-obelisk cooldown.</p>
 *
 * <p>W4-012 [A] keeps the obelisk vulnerable: this policy never grants an
 * invulnerability window, so a correct reflected hit is still accepted while the
 * warning is playing. W4-013 [A] makes the impulse fail closed when no safe
 * free space exists rather than throwing a player through terrain.</p>
 *
 * <p>Pure: no Bukkit types, no entity access. The adapter decides what "safe
 * free space" means for the live world.</p>
 */
public final class ObeliskRepelPolicy {
    /** W4-010: players this close for long enough are pushed away. */
    public static final double CLOSE_RADIUS_BLOCKS = 3.0D;
    /** W4-010: readable warning before the impulse lands. */
    public static final long WARNING_TICKS = 20L;
    /** W4-010: per-obelisk cooldown, so the impulse cannot be spammed. */
    public static final long COOLDOWN_TICKS = 180L;
    /** Sustained pressure required before a warning may start. */
    public static final long SUSTAINED_PRESSURE_TICKS = 40L;
    /** Horizontal speed applied to an accepted impulse. */
    public static final double HORIZONTAL_IMPULSE = 1.05D;
    /** Slight lift so the displaced player clears the surface instead of scraping. */
    public static final double VERTICAL_IMPULSE = 0.42D;
    /** A live obelisk is never invulnerable; this exists only to make that explicit. */
    public static final boolean GRANTS_INVULNERABILITY = false;

    private ObeliskRepelPolicy() {
    }

    /** Why an impulse did or did not happen; every branch is a logged outcome. */
    public enum Decision {
        /** No sustained close pressure yet. */
        IDLE,
        /** Pressure started; the warning sound and surface cue are playing. */
        WARNING,
        /** Impulse accepted and applied. */
        REPELLED,
        /** A warning is running but the player left the close radius. */
        WARNING_ABORTED,
        /** Cooldown from the previous impulse has not elapsed. */
        COOLDOWN,
        /** No safe free space; the impulse is skipped rather than unsafe. */
        NO_SAFE_SPACE,
        /** The obelisk is destroyed, so it cannot repel anything. */
        OBELISK_DESTROYED
    }

    /** Per-obelisk timers; the adapter owns one instance per live obelisk. */
    public record Timers(long pressureSinceTick, long warningStartedTick, long lastRepelledTick) {
        public static final Timers IDLE_TIMERS = new Timers(-1L, -1L, -1L);

        public Timers {
            if (pressureSinceTick < -1L || warningStartedTick < -1L || lastRepelledTick < -1L) {
                throw new IllegalArgumentException("obelisk repel timers must be non-negative or unset");
            }
        }
    }

    /**
     * Advances one obelisk for one tick.
     *
     * @param timers        previous timers for this obelisk
     * @param tick          current event tick
     * @param insideClose   an eligible player is inside the close radius
     * @param hasSafeSpace  the adapter proved a safe free-space displacement exists
     * @param obeliskAlive  the obelisk still stands
     * @return the decision plus the timers to store back
     */
    public static Result step(Timers timers, long tick, boolean insideClose,
                              boolean hasSafeSpace, boolean obeliskAlive) {
        Timers current = timers == null ? Timers.IDLE_TIMERS : timers;
        if (tick < 0L) throw new IllegalArgumentException("tick must be non-negative");
        if (!obeliskAlive) {
            return new Result(Decision.OBELISK_DESTROYED, Timers.IDLE_TIMERS);
        }
        if (!insideClose) {
            // Leaving the radius aborts a pending warning and clears pressure;
            // a returning player starts the count again from zero.
            boolean warned = current.warningStartedTick() >= 0L;
            return new Result(warned ? Decision.WARNING_ABORTED : Decision.IDLE,
                    new Timers(-1L, -1L, current.lastRepelledTick()));
        }
        if (current.warningStartedTick() >= 0L) {
            if (tick - current.warningStartedTick() < WARNING_TICKS) {
                return new Result(Decision.WARNING, current);
            }
            if (!hasSafeSpace) {
                return new Result(Decision.NO_SAFE_SPACE, new Timers(-1L, -1L, tick));
            }
            return new Result(Decision.REPELLED, new Timers(-1L, -1L, tick));
        }
        if (current.lastRepelledTick() >= 0L
                && tick - current.lastRepelledTick() < COOLDOWN_TICKS) {
            return new Result(Decision.COOLDOWN, current);
        }
        long since = current.pressureSinceTick() >= 0L ? current.pressureSinceTick() : tick;
        if (tick - since < SUSTAINED_PRESSURE_TICKS) {
            return new Result(Decision.IDLE, new Timers(since, -1L, current.lastRepelledTick()));
        }
        if (!hasSafeSpace) {
            return new Result(Decision.NO_SAFE_SPACE, new Timers(since, -1L, current.lastRepelledTick()));
        }
        return new Result(Decision.WARNING, new Timers(since, tick, current.lastRepelledTick()));
    }

    /** Decision plus the timers the adapter must persist for the next tick. */
    public record Result(Decision decision, Timers timers) {
        public Result {
            if (decision == null || timers == null) {
                throw new IllegalArgumentException("obelisk repel result is incomplete");
            }
        }
    }
}