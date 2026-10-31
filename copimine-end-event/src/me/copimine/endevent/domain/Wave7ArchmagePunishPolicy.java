package me.copimine.endevent.domain;

/**
 * MAGE7-005: the Rift Archmage's anti-camping tools, and the window that keeps
 * them fair.
 *
 * <p>A caster that repels and steps away on every approach is unkillable by
 * melee. The contract's shape is the opposite: a bounded warned repel and a
 * safe Step are allowed, and every major cast must leave a real window in which
 * neither of them fires, so a melee player who survives the cast gets to
 * connect.
 */
public final class Wave7ArchmagePunishPolicy {
    /** Length of the window opened by a major cast. */
    public static final long PUNISH_TICKS = 60L;
    /** The repel is telegraphed before it pushes. */
    public static final long REPEL_WARNING_TICKS = 20L;
    /** Bounded repel distance; far enough to reset spacing, not to eject a player. */
    public static final double REPEL_MAX_DISTANCE = 4.5D;
    /** Repel cooldown, independent of Step. */
    public static final long REPEL_COOLDOWN_TICKS = 160L;
    /** Step cooldown, so approaches cannot chain-teleport the mage. */
    public static final long STEP_COOLDOWN_TICKS = 120L;
    /** Sentinel for "this tool has never been used". */
    public static final long NEVER = Long.MIN_VALUE;

    private Wave7ArchmagePunishPolicy() {
    }

    /**
     * The window a major cast leaves behind.
     *
     * @param opensAt  tick the cast resolved
     * @param closesAt exclusive end of the punish window
     */
    public record PunishWindow(long opensAt, long closesAt) {
        public PunishWindow {
            if (opensAt < 0L || closesAt <= opensAt) {
                throw new IllegalArgumentException("invalid Archmage punish window");
            }
        }

        public boolean openAt(long tick) {
            return tick >= opensAt && tick < closesAt;
        }
    }

    public static PunishWindow afterMajorCast(long castEndsAtTick) {
        return new PunishWindow(castEndsAtTick, castEndsAtTick + PUNISH_TICKS);
    }

    /** Step is refused inside the punish window, otherwise it is a normal tactical option. */
    public static boolean mayStep(long tick, PunishWindow window) {
        return window == null || !window.openAt(tick);
    }

    public static boolean offCooldown(long tick, long lastUsedTick) {
        return lastUsedTick == NEVER || tick - lastUsedTick >= STEP_COOLDOWN_TICKS;
    }

    /** The repel is also suppressed inside the punish window. */
    public static boolean mayRepel(long tick, long lastRepelTick, PunishWindow window) {
        if (window != null && window.openAt(tick)) {
            return false;
        }
        return lastRepelTick == NEVER || tick - lastRepelTick >= REPEL_COOLDOWN_TICKS;
    }
}