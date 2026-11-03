package me.copimine.endevent.domain;

/**
 * How the paired Markmen move, hide and provoke.
 *
 * <p>The user asked for skeletons that are "реально неуловимыми быстрыми
 * скрытными и очень противными" and that must still work as a pair. Those four
 * words pull against each other: a fast invisible actor that also repels is not
 * a fight, it is a wall. This policy keeps them asurable by making every one of
 * the annoying behaviours a bounded window with a readable tell, and by tying
 * the escape move to the PARTNER — the one thing the pair cannot do alone.</p>
 *
 * <p>Everything here is pure: ticks in, offsets out.</p>
 */
public final class Wave7SkeletonElusivenessPolicy {
    /** They move between hides far more often than a normal mob repositions. */
    public static final int REPOSITION_INTERVAL_TICKS = 8;

    /** A hide is brief: long enough to lose them, short enough to punish late. */
    public static final int VANISH_TICKS = 34;

    /** A vanished archer cannot shoot and cannot be hit; the tell is the smoke. */
    public static final int VANISH_RECOVERY_TICKS = 10;

    /** Between two hides there must be a real window to shoot them. */
    public static final int VANISH_COOLDOWN_TICKS = 110;

    /**
     * After reappearing they cannot hide again for this long, so a party that
     * keeps pressure on one skeleton eventually gets a full damage window.
     */
    public static final int REAPPEAR_GRACE_TICKS = 40;

    /** Sprint gait while repositioning, ordinary walk while lining up a shot. */
    public static final double SPEED_SPRINT = 0.42D;
    public static final double SPEED_LINEUP = 0.10D;

    /**
     * The provoke move: on a hit the pair swaps targets and kicks up ash.
     *
     * <p>Bounded so it cannot become a permanent stun, and only usable when
     * the partner is alive — an alone survivor has no symbiosis to lean on.</p>
     */
    public static final int GALL_TICKS = 24;
    public static final int GALL_COOLDOWN_TICKS = 90;

    /** How far a hide may drift from the lane the pair agreed on. */
    public static final double HIDE_DRIFT_BLOCKS = 4.5D;

    private Wave7SkeletonElusivenessPolicy() {
    }

    /**
     * Whether the actor may hide now.
     *
     * <p>Refused while casting: vanishing mid-draw would hide the telegraph the
     * contract requires every attack to show.</p>
     */
    public static boolean mayVanish(long nowTick, long lastVanishTick, boolean casting,
                                    boolean visibleRecently) {
        if (casting) return false;
        if (lastVanishTick < 0L) return true;
        long since = nowTick - lastVanishTick;
        if (since < 0L) return false;
        // The grace window is what makes the pair beatable: after they come back
        // the party has a guaranteed stretch of real targets.
        return since >= VANISH_COOLDOWN_TICKS + REAPPEAR_GRACE_TICKS;
    }

    /** True while the hide is still running. */
    public static boolean hidden(long nowTick, long vanishStartTick) {
        return vanishStartTick >= 0L && nowTick - vanishStartTick < VANISH_TICKS;
    }

    /** True during the brief, no-longer-hidden but not-yet-firing window. */
    public static boolean recovering(long nowTick, long vanishStartTick) {
        if (vanishStartTick < 0L) return false;
        long elapsed = nowTick - vanishStartTick;
        return elapsed >= VANISH_TICKS && elapsed < VANISH_TICKS + VANISH_RECOVERY_TICKS;
    }

    /** A hidden or recovering skeleton never fires; the tell must be seen. */
    public static boolean mayAttack(long nowTick, long vanishStartTick) {
        return !hidden(nowTick, vanishStartTick) && !recovering(nowTick, vanishStartTick);
    }

    /** Gait speed for the current stance. */
    public static double speed(boolean repositioning, boolean vanished) {
        if (vanished) return SPEED_SPRINT;
        return repositioning ? SPEED_SPRINT : SPEED_LINEUP;
    }

    /**
     * Lateral drift for a hide, mirrored per flank so the pair never piles into
     * the same pocket of cover.
     */
    public static double hideDriftBlocks(Wave7SkeletonPairPolicy.Flank flank, long nowTick) {
        double sign = flank == Wave7SkeletonPairPolicy.Flank.RIGHT ? 1.0D : -1.0D;
        // A slow oscillation rather than random: deterministic hides are
        // repeatable for a test and still feel evasive in play.
        double phase = (nowTick % 80L) / 80.0D * 2.0D * Math.PI;
        return sign * Math.cos(phase) * HIDE_DRIFT_BLOCKS;
    }

    /**
     * The pair's provocation: when one is hit it drags attention to the partner
     * and kicks up ash, which is annoying without removing player agency.
     */
    public static boolean mayProvoke(long nowTick, long lastProvokeTick, boolean partnerAlive) {
        if (!partnerAlive) return false;
        if (lastProvokeTick < 0L) return true;
        long since = nowTick - lastProvokeTick;
        return since >= GALL_COOLDOWN_TICKS;
    }

    /** Why a hide or provoke was refused, for diagnostics. */
    public static String refusal(boolean casting, long nowTick, long lastVanishTick) {
        if (casting) return "casting";
        long since = lastVanishTick < 0L ? Long.MAX_VALUE / 2L : nowTick - lastVanishTick;
        if (since < VANISH_COOLDOWN_TICKS) return "cooldown";
        if (since < VANISH_COOLDOWN_TICKS + REAPPEAR_GRACE_TICKS) return "grace";
        return "ready";
    }
}
