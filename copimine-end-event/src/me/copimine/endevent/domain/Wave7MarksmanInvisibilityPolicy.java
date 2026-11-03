package me.copimine.endevent.domain;

/**
 * MARK-005 / MARK-006: the Mobile Marksman may vanish for five seconds to change
 * position.
 *
 * <p>Vanishing is deliberately not cheap to implement and equally deliberately
 * not powerful. It never grants immunity, never permits firing from cover, and
 * never repeats on demand: a refused vanish keeps the actor visible and
 * attacking rather than silently doing nothing.
 */
public final class Wave7MarksmanInvisibilityPolicy {
    /**
     * REPINNED (2026-11-03): three seconds four at twenty ticks per second.
     *
     * <p>SUPERSEDED the five-second window. The user asked for skeletons that
     * are "реально неуловимыми ... и очень противными": a shorter, more
     * frequent hide reads as slippery instead of a long boring walk in the
     * dark. Together with the shorter cooldown below the pair is genuinely hard
     * to pin, while every window still ends and every window still shows a
     * reveal.</p>
     */
    public static final long INVISIBLE_TICKS = 34L;
    /** The same window expressed as a recorded wall-clock deadline. */
    public static final long INVISIBLE_MILLIS = 1_700L;
    /**
     * Readable reveal before the attack resumes. Ten ticks is half a second:
     * long enough for a player to register it, far short of a whole second of
     * free repositioning.
     */
    public static final long REVEAL_TICKS = 10L;
    /**
     * A real finite cooldown so vanish cannot be chained back to back.
     *
     * <p>REPINNED (2026-11-03): 110 ticks instead of 200. Two hides in a short
     * fight are what makes the pair feel evasive; the grace window in
     * {@code Wave7SkeletonElusivenessPolicy} is what stops that from becoming
     * an unkillable stun-lock.</p>
     */
    public static final long COOLDOWN_TICKS = 110L;

    private Wave7MarksmanInvisibilityPolicy() {
    }

    /**
     * One visible vanish window, addressed in ticks relative to its start.
     *
     * @param startedAtTick        tick the vanish began
     * @param revealStartsAt       tick the hidden window ends and the reveal begins
     * @param attackResumesAt      first tick an attack is legal again
     * @param cooldownUntil        absolute tick the next vanish becomes available
     * @param offlineTicks         ticks the actor spent unloaded inside this window
     */
    public record Window(long startedAtTick, long revealStartsAt, long attackResumesAt,
                         long cooldownUntil, long offlineTicks) {
        public Window {
            if (startedAtTick < 0L || revealStartsAt <= startedAtTick
                    || attackResumesAt < revealStartsAt || cooldownUntil < attackResumesAt
                    || offlineTicks < 0L) {
                throw new IllegalArgumentException("invalid Marksman invisibility window");
            }
        }

        /** True while the model is hidden, excluding the reveal. */
        public boolean hiddenAt(long tick) {
            return tick >= startedAtTick && tick < revealStartsAt;
        }

        /**
         * An attack is legal only once the reveal has played out. This is what
         * stops a point-blank full fan on the reveal tick itself.
         */
        public boolean mayAttack(long tick) {
            return tick >= attackResumesAt;
        }
    }

    /** Invisibility is a positional tool, never a damage shield. */
    public static boolean immuneWhileHidden() {
        return false;
    }

    /**
     * Starts a vanish if the cooldown allows one, or returns {@code null} when
     * the vanish is refused. A refusal is a normal outcome: the caller keeps the
     * actor visible and attacking.
     */
    public static Window vanish(long now, long cooldownUntil) {
        if (now < 0L || !readyAt(now, cooldownUntil)) {
            return null;
        }
        long reveal = now + INVISIBLE_TICKS;
        long resumes = reveal + REVEAL_TICKS;
        return new Window(now, reveal, resumes, resumes + COOLDOWN_TICKS, 0L);
    }

    /** A cooldown of {@code cooldownUntil <= 0} means the ability was never used. */
    public static boolean readyAt(long now, long cooldownUntil) {
        return now >= 0L && now >= cooldownUntil;
    }

    /**
     * Rebuilds a persisted window after a restart.
     *
     * <p>The deadline stays absolute, so a restart never refreshes the vanish.
     * The window is re-anchored on the resume tick and only keeps the time that
     * is actually left: a restart part-way through does not hand out a fresh
     * five seconds. If the deadline already passed while the actor was
     * unloaded the window is over, which is what stops a server restart from
     * leaving a permanently invisible root behind.
     */
    public static Window resume(long persistedDeadlineMillis, long nowMillis) {
        if (persistedDeadlineMillis <= 0L || nowMillis < 0L) {
            return null;
        }
        long remaining = persistedDeadlineMillis - nowMillis;
        if (remaining <= 0L) {
            return null;
        }
        // Anchor the window so that it reveals exactly on the persisted deadline:
        // a restart part-way through keeps only the time that is actually left.
        long reveal = persistedDeadlineMillis;
        long resumes = reveal + REVEAL_TICKS;
        long offlineTicks = Math.max(0L, INVISIBLE_MILLIS - remaining) / 50L;
        return new Window(nowMillis, reveal, resumes, resumes + COOLDOWN_TICKS, offlineTicks);
    }
}