package me.copimine.endevent.domain;

/**
 * Ownership of boss locomotion during a cast.
 *
 * <p>The 11-November contract (10.2) replaces the old "stop the body for the
 * whole cast" behaviour. A cast is either <em>mobile</em> - the boss keeps
 * legal locomotion and keeps evaluating targets while the spell resolves - or
 * <em>stationary</em>, where the body is locked only for the authored heavy
 * animation segment and never for the whole clip.</p>
 *
 * <p>This class is the single authority for that split. The Bukkit adapter
 * must not decide it independently, otherwise two controllers fight over the
 * pathfinder every tick and the boss freezes.</p>
 */
public final class BossCastMobilityPolicy {

    /**
     * Hard ceiling for any stationary lock. The authored ground slam reaches
     * its impact at 110 ticks, so the bound sits just above the heaviest
     * authored segment and refuses a lock that spans a whole 9 s clip.
     *
     * <p>NOTE: every current authored lock is already below this cap, so the
     * clamp is a fail-safe for a future longer clip rather than a behaviour a
     * test can observe today. The probe pins the invariant it guarantees (no
     * lock exceeds the cap and every lock ends inside its clip) instead of
     * pretending the clamp itself is exercised.</p>
     */
    public static final int MAX_STATIONARY_LOCK_TICKS = 120;

    /**
     * How quickly a lost cast callback is treated as stale. The longest
     * authored telegraph plus its recovery tail is well inside this budget,
     * so a legitimate cast is never cut short, while a dropped task cannot
     * hold the body indefinitely.
     */
    public static final long STALE_CAST_MILLIS = 20_000L;

    private BossCastMobilityPolicy() {
    }

    /**
     * A mobile cast leaves locomotion to the movement controller: projectiles,
     * beams and summons never freeze the boss in place.
     */
    public static boolean allowsLocomotion(EndRiftAiPolicy.BossSpell spell) {
        if (spell == null) {
            return true;
        }
        return switch (spell) {
            case RIFT_PROJECTILE, RIFT_ARROWS, VOID_BLAST, VOID_MARK,
                 BOSS_FIREBALL, SUMMON_SERVANTS, SMALL_KAGUNE -> true;
            // AMBUSH_GRAB burrows the body under the floor, so it cannot drift
            // while the grab resolves. Its lock is still bounded and short.
            case MELEE_SWIPE, CHEST_STRIKE, GROUND_SLAM,
                 ARENA_INFERNO, FINAL_STRIKE, AMBUSH_GRAB -> false;
        };
    }

    /**
     * Target evaluation continues even during a locked authored segment: the
     * boss is rooted, not blinded. Losing perception is how the creature ends
     * up swinging at an empty patch of floor.
     */
    public static boolean allowsTargetEvaluation(EndRiftAiPolicy.BossSpell spell) {
        return spell != null;
    }

    /**
     * Whether the cast requires disabling the vanilla AI flag.
     *
     * <p>Never. Disabling {@code setAI}/{@code setAware} was the freeze root
     * cause: nothing re-enabled it when the cast ended. Locomotion is owned by
     * the movement controller and a lock is expressed as a stopped path plus a
     * cleared horizontal velocity, both of which expire with the window.</p>
     */
    public static boolean forbidsNativeAi(EndRiftAiPolicy.BossSpell spell) {
        return false;
    }

    /**
     * Length of the authored lock window in server ticks. Mobile casts lock
     * nothing. Stationary casts lock only up to their authored contact moment,
     * bounded by {@link #MAX_STATIONARY_LOCK_TICKS}.
     */
    public static int lockedTicks(EndRiftAiPolicy.BossSpell spell) {
        if (spell == null || allowsLocomotion(spell)) {
            return 0;
        }
        int authored = BossContactStrikePolicy.contactMarkerTicks(spell);
        if (authored <= 0) {
            // Non-contact heavy casts (Inferno, Final Strike) have no contact
            // keyframe of their own, so they keep a short authored tell rather
            // than an unbounded hold.
            authored = HEAVY_TELL_TICKS;
        }
        return Math.max(1, Math.min(MAX_STATIONARY_LOCK_TICKS, authored));
    }

    /** Short authored tell used by heavy casts that have no contact keyframe. */
    public static final int HEAVY_TELL_TICKS = 40;

    /**
     * Whether the body may be held right now. Pure function of the clock, so
     * a lost callback cannot extend the lock: the window simply expires.
     */
    public static boolean shouldHold(EndRiftAiPolicy.BossSpell spell,
                                     long castStartedMillis, long elapsedTicks) {
        if (spell == null || allowsLocomotion(spell)) {
            return false;
        }
        if (!Double.isFinite(castStartedMillis) && castStartedMillis != 0L) {
            return false;
        }
        if (elapsedTicks < 0L) {
            return false;
        }
        return elapsedTicks < lockedTicks(spell);
    }

    /**
     * A cast timestamp older than the whole budget means the callback that was
     * supposed to end it never ran. Such a cast must be released, not held.
     */
    public static boolean stale(long castStartedMillis, long nowMillis) {
        if (castStartedMillis <= 0L || nowMillis < castStartedMillis) {
            return false;
        }
        return nowMillis - castStartedMillis > STALE_CAST_MILLIS;
    }

    /**
     * Whether the movement controller may act after the lock window closed.
     * A stationary cast is stationary only while it is authored to be.
     */
    public static boolean allowsLocomotionAfterLock(EndRiftAiPolicy.BossSpell spell) {
        return spell != null;
    }

    /**
     * The single recovery contract. Every cast release must apply all of it,
     * otherwise a stale flag leaves the boss immobile forever.
     */
    public static Recovery recovery() {
        return Recovery.INSTANCE;
    }

    public record Recovery(boolean restoreAi, boolean restoreAware,
                           boolean restoreGravity, boolean clearPath,
                           boolean restoreNavigation,
                           boolean resumeTargetEvaluation) {
        static final Recovery INSTANCE =
                new Recovery(true, true, true, true, true, true);
    }
}
