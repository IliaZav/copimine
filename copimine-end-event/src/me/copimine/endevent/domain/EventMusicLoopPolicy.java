package me.copimine.endevent.domain;

/**
 * Single-owner policy for the one looping event music bed.
 *
 * <p>The player report was "the music restarts and overlaps". Two separate
 * defects produced it, and both are arithmetic, so both live here:</p>
 *
 * <ol>
 *   <li><b>Two owners.</b> The track could be started from a per-tick objective
 *   path, a phase transition and a boss handoff. The old start guard only
 *   matched when a loop task happened to exist, so a track configured with
 *   {@code loop-seconds: 0} restarted on every call, and anything that set the
 *   active track id without stopping the previous loop left the previous bed
 *   re-arming forever underneath the new one. A bed is identified by its sound
 *   id alone: either it owns the speakers or it does not.</li>
 *   <li><b>Re-arming too early.</b> The loop task was scheduled with the track
 *   duration as its period but, for non-boss beds, fired without checking the
 *   wall clock. Jitter therefore re-sent the ogg before the previous instance
 *   had finished. Repeat is now gated on a due time, so a loop can arrive late
 *   (a short gap at worst) but never early (a stacked second copy).</li>
 * </ol>
 *
 * <p>Pure arithmetic only: no scheduler, no sound API, no state.</p>
 */
public final class EventMusicLoopPolicy {
    /**
     * Silence kept between two instances of the same bed.
     *
     * <p>The server sends the start, the client begins playing it a network
     * round-trip later, and the ogg is exactly as long as {@code loop-seconds}.
     * Re-arming at exactly that offset therefore starts the second copy while
     * the first is still finishing. A short deliberate gap is inaudible on an
     * ambient bed and is what guarantees no stacking.</p>
     */
    public static final long RETRIGGER_SAFETY_MILLIS = 300L;

    private EventMusicLoopPolicy() {
    }

    /** Whether {@code requestedTrackId} is a bed that is already sounding. */
    public static boolean isAlreadyPlaying(String activeTrackId, String requestedTrackId) {
        return requestedTrackId != null && !requestedTrackId.isBlank()
                && requestedTrackId.equals(activeTrackId);
    }

    /** Whether the loop task captured for {@code capturedTrackId} still owns the bed. */
    public static boolean owns(String capturedTrackId, String activeTrackId) {
        return isAlreadyPlaying(activeTrackId, capturedTrackId);
    }

    /**
     * Period in ticks for polling the repeat gate, or {@code 0} when the bed
     * must not be re-armed at all ({@code loop-seconds: 0} means "play once").
     */
    public static long loopPeriodTicks(int loopSeconds) {
        if (loopSeconds <= 0) return 0L;
        return Math.max(20L, loopSeconds * 20L);
    }

    /** Delay before the first re-arm, in milliseconds; {@code 0} when no loop. */
    public static long loopDelayMillis(int loopSeconds) {
        if (loopSeconds <= 0) return 0L;
        return loopSeconds * 1_000L + RETRIGGER_SAFETY_MILLIS;
    }

    /**
     * Whether the loop task may send the bed again now.
     *
     * <p>Never before the due time. A late send is a gap; an early send is the
     * overlap the player reported.</p>
     */
    public static boolean shouldRepeat(long nowMillis, long dueMillis) {
        return nowMillis >= dueMillis;
    }

    /**
     * Whether a bed may keep sounding after the phase stops being a music
     * phase.
     *
     * <p>Only the victory bed, and only while it still owns the speakers: that
     * track is deliberately allowed to finish over the unlock instead of being
     * cut the instant the phase changes. Every other exit is a real teardown and
     * stops the bed. A tail merely stops re-arming; it never restarts the bed.</p>
     */
    public static boolean keepTailOnPhaseExit(boolean victoryBed,
                                              String capturedTrackId,
                                              String activeTrackId) {
        return victoryBed && owns(capturedTrackId, activeTrackId);
    }
}
