import me.copimine.endevent.domain.EventMusicLoopPolicy;

/**
 * W2-02: the event music bed restarted and stacked on itself.
 *
 * <p>Two causes, both arithmetic: a start was only suppressed while a loop
 * task happened to exist (so a non-looping bed restarted on every call and a
 * bed whose task had died restarted once more), and the repeat was scheduled
 * with the track duration as its period without a wall-clock gate, so jitter
 * re-sent the ogg before the previous copy finished.</p>
 */
public final class EventMusicLoopPolicyTest {
    private static int checks;

    private EventMusicLoopPolicyTest() {
    }

    public static void main(String[] args) {
        theLiveBedIsStartedOnce();
        aDifferentBedReplacesTheOldOne();
        aStaleTaskCannotResurrectARetiredBed();
        theRepeatIsNeverEarly();
        aLateRepeatIsAccepted();
        aNonLoopingBedIsNeverRearmed();
        theVictoryTailOnlyStopsRearming();
        loopPeriodIsBounded();
        System.out.println("EventMusicLoopPolicyTest: PASS (" + checks + " checks)");
    }

    private static void theLiveBedIsStartedOnce() {
        check("the active bed is recognised as already playing",
                EventMusicLoopPolicy.isAlreadyPlaying("copimine:wave_2", "copimine:wave_2"));
        check("an empty active bed is never 'already playing'",
                !EventMusicLoopPolicy.isAlreadyPlaying("", "copimine:wave_2"));
        check("a blank requested bed is never 'already playing'",
                !EventMusicLoopPolicy.isAlreadyPlaying("copimine:wave_2", ""));
        check("a null requested bed is never 'already playing'",
                !EventMusicLoopPolicy.isAlreadyPlaying("copimine:wave_2", null));
    }

    private static void aDifferentBedReplacesTheOldOne() {
        check("a new bed is not already playing",
                !EventMusicLoopPolicy.isAlreadyPlaying("copimine:wave_2", "copimine:wave_3"));
        check("a new bed does not claim ownership of the old task",
                !EventMusicLoopPolicy.owns("copimine:wave_2", "copimine:wave_3"));
    }

    private static void aStaleTaskCannotResurrectARetiredBed() {
        check("the task that owns the live bed may act",
                EventMusicLoopPolicy.owns("copimine:wave_2", "copimine:wave_2"));
        check("a task for a stopped bed may not act",
                !EventMusicLoopPolicy.owns("copimine:wave_2", ""));
        check("a task cannot act when ownership was cleared",
                !EventMusicLoopPolicy.owns("", ""));
    }

    private static void theRepeatIsNeverEarly() {
        check("a repeat before the due time is refused",
                !EventMusicLoopPolicy.shouldRepeat(1_000L, 2_000L));
        check("a repeat one millisecond early is refused",
                !EventMusicLoopPolicy.shouldRepeat(1_999L, 2_000L));
    }

    private static void aLateRepeatIsAccepted() {
        check("a repeat exactly on the due time is accepted",
                EventMusicLoopPolicy.shouldRepeat(2_000L, 2_000L));
        check("a late repeat is accepted, because a gap is better than a stack",
                EventMusicLoopPolicy.shouldRepeat(2_500L, 2_000L));
    }

    private static void aNonLoopingBedIsNeverRearmed() {
        check("loop-seconds 0 has no period", EventMusicLoopPolicy.loopPeriodTicks(0) == 0L);
        check("a negative loop length is treated as no loop",
                EventMusicLoopPolicy.loopPeriodTicks(-5) == 0L);
        check("loop-seconds 0 has no delay", EventMusicLoopPolicy.loopDelayMillis(0) == 0L);
    }

    private static void theVictoryTailOnlyStopsRearming() {
        check("the victory bed keeps its tail while it owns the speakers",
                EventMusicLoopPolicy.keepTailOnPhaseExit(true, "copimine:victory", "copimine:victory"));
        check("a non-victory bed is torn down on phase exit",
                !EventMusicLoopPolicy.keepTailOnPhaseExit(false, "copimine:wave_2", "copimine:wave_2"));
        check("the victory bed does not keep a tail it no longer owns",
                !EventMusicLoopPolicy.keepTailOnPhaseExit(true, "copimine:victory", "copimine:wave_3"));
    }

    private static void loopPeriodIsBounded() {
        for (int seconds = 1; seconds <= 90; seconds++) {
            long period = EventMusicLoopPolicy.loopPeriodTicks(seconds);
            if (period < 20L) {
                throw new AssertionError("period below the scheduler floor for " + seconds);
            }
            if (period != Math.max(20L, seconds * 20L)) {
                throw new AssertionError("period is not the track length in ticks for " + seconds);
            }
            long delay = EventMusicLoopPolicy.loopDelayMillis(seconds);
            if (delay <= seconds * 1_000L) {
                throw new AssertionError("re-arm has no safety margin for " + seconds);
            }
        }
        checks++;
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + name);
        checks++;
    }
}
