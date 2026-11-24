import me.copimine.endevent.domain.BoundedDiagnosticCounterPolicy;
import me.copimine.endevent.domain.BoundedDiagnosticCounterPolicy.Decision;

/**
 * N23-P1-14 / specification 13: repeated INFO/WARN spam must be batched through
 * key-based BOUNDED counters.
 *
 * The contract the gate now enforces: one line per key per window, the emit
 * after a window carries the exact suppressed total, an ordinary first line is
 * byte-identical to the pre-throttle output (empty suffix), and the key budget
 * can never grow without limit.
 */
public final class BoundedDiagnosticCounterPolicyTest {
    private static int failures = 0;

    public static void main(String[] args) {
        firstOccurrenceEmitsImmediately();
        windowBatchesTheCount();
        countsSurviveAcrossManyWindows();
        distinctKeysAreIndependent();
        quietKeysDoNotEmit();
        keyBudgetIsBounded();
        nullAndBlankFailClosed();
        clockStepBackwardsStartsAFreshWindow();

        if (failures > 0) {
            throw new AssertionError(failures + " BoundedDiagnosticCounterPolicy checks failed");
        }
        System.out.println("BoundedDiagnosticCounterPolicyTest OK");
    }

    private static void firstOccurrenceEmitsImmediately() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 8);
        Decision first = policy.decide("WAVE_MOB_ENVIRONMENT_DAMAGE_BLOCKED", 0L);
        check(first.emit(), "the first occurrence must be reported, not swallowed");
        check(first.suppressedBefore() == 0L, "the first line has nothing suppressed before it");
        check(first.suffix().isEmpty(), "an ordinary first line must keep its original bytes");
    }

    private static void windowBatchesTheCount() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 8);
        policy.decide("env", 0L);
        for (int i = 1; i <= 7; i++) {
            Decision suppressed = policy.decide("env", i * 100L);
            check(!suppressed.emit(), "a repeat inside the window must be suppressed");
        }
        // Exactly one more occurrence at/after the window boundary emits and
        // reports the seven suppressed inside the closed window.
        Decision reopen = policy.decide("env", 1_000L);
        check(reopen.emit(), "the window boundary must reopen the key");
        check(reopen.suppressedBefore() == 7L,
                "the reopening line must report the exact suppressed total, got " + reopen.suppressedBefore());
        check(" suppressed=7".equals(reopen.suffix()), "suffix must carry the batch total");
    }

    private static void countsSurviveAcrossManyWindows() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(100L, 8);
        long emitted = 0L;
        long reported = 0L;
        long total = 0L;
        for (long t = 0L; t < 1_000L; t += 10L) {
            total++;
            Decision decision = policy.decide("hot", t);
            if (decision.emit()) {
                emitted++;
                reported += decision.suppressedBefore();
            }
        }
        // Close the final open window with one more occurrence, otherwise the
        // tail of the stream is legitimately still buffered.
        total++;
        Decision flush = policy.decide("hot", 10_000L);
        check(flush.emit(), "a far-future occurrence must close the final window");
        emitted++;
        reported += flush.suppressedBefore();
        // Every occurrence is either emitted once or counted into a later
        // emission, so emitted + reported equals the exact total. No occurrence
        // may vanish.
        check(emitted + reported == total,
                "no occurrence may be lost: emitted=" + emitted + " reported=" + reported + " total=" + total);
        check(emitted <= 12L, "a 100ms window over 1000ms must emit O(10) lines, got " + emitted);
        check(emitted < total, "the throttle must actually reduce line count for a hot key");
    }

    private static void distinctKeysAreIndependent() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 8);
        Decision a = policy.decide("cloud_rejected", 0L);
        Decision b = policy.decide("env_blocked", 0L);
        check(a.emit() && b.emit(), "a new key must not be silenced by another key's window");
        Decision aAgain = policy.decide("cloud_rejected", 10L);
        check(!aAgain.emit(), "each key owns its own window");
        Decision bAgain = policy.decide("env_blocked", 10L);
        check(!bAgain.emit(), "the second key also suppresses inside its own window");
    }

    private static void quietKeysDoNotEmit() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 8);
        policy.decide("rare", 0L);
        Decision later = policy.decide("rare", 10_000L);
        check(later.emit(), "a quiet key emits once per occurrence, not once per window");
        check(later.suppressedBefore() == 0L,
                "a key with nothing suppressed must not claim a batch");
    }

    private static void keyBudgetIsBounded() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 4);
        for (int i = 0; i < 4; i++) {
            check(policy.decide("key-" + i, 0L).emit(), "keys inside the budget emit");
        }
        check(policy.trackedKeys() == 4, "the budget must be exactly tracked");
        for (int i = 4; i < 40; i++) {
            policy.decide("key-" + i, 0L);
        }
        check(policy.trackedKeys() <= 5,
                "the key set must stay bounded (budget + one overflow bucket), got " + policy.trackedKeys());
    }

    private static void nullAndBlankFailClosed() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 4);
        Decision nullKey = policy.decide(null, 0L);
        check(nullKey.emit(), "a null key must still be reported under a stable placeholder");
        // null and "" both resolve to the same placeholder, so a blank key is a
        // repeat inside the window rather than a second unbounded bucket.
        check(!policy.decide("", 0L).emit(), "a blank key shares the null placeholder");
        check(policy.trackedKeys() == 1, "null and blank must not create two buckets");
        check(!policy.decide("", 5L).emit(), "empty keys stay inside the placeholder window");
    }

    private static void clockStepBackwardsStartsAFreshWindow() {
        BoundedDiagnosticCounterPolicy policy = new BoundedDiagnosticCounterPolicy(1_000L, 4);
        policy.decide("clock", 5_000L);
        check(!policy.decide("clock", 5_100L).emit(), "inside the window it suppresses");
        // A backwards clock (NTP step, monotonic-to-wall switch) must not pin the
        // key shut forever: trading clarity for a permanently silent channel.
        Decision afterRewind = policy.decide("clock", 100L);
        check(afterRewind.emit(), "a backwards clock step must reopen the key, not mute it");
        check(afterRewind.suppressedBefore() == 1L, "the suppressed count carries through the rewind");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }
}
