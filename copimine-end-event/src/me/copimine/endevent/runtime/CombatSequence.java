package me.copimine.endevent.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Audit ECHO error 10: the ordered history of what a participant actually did.
 *
 * <p>The profile used to keep counters and a short raw window, so two players
 * with clearly different fights produced the same copy and a restart erased
 * everything that shaped it. This class keeps the ORDER of significant actions
 * in a compressed form: consecutive repeats of the same action collapse into a
 * count, so a long fight stays small without losing its shape. It round-trips
 * through {@link #encode()}/{@link #decode(String)} so the history survives a
 * server restart.</p>
 */
public final class CombatSequence {
    /** Hard bound on stored runs; older runs are dropped first. */
    static final int MAX_RUNS = 256;
    /** Hard bound on one run's repetition count. */
    static final long MAX_RUN_COUNT = 1_000_000L;

    /** One compressed run: the same action repeated {@code count} times. */
    public record Run(int wave, String action, long count) {
        public Run {
            Objects.requireNonNull(action, "action");
            if (wave < 1 || wave > 7) throw new IllegalArgumentException("invalid wave in combat run");
            if (count < 1 || count > MAX_RUN_COUNT) {
                throw new IllegalArgumentException("invalid combat run count");
            }
        }
    }

    private final ArrayDeque<Run> runs = new ArrayDeque<>();

    /** Append one observed action, merging it into the previous run when it repeats. */
    public void record(int wave, String action, int value) {
        if (action == null || action.isBlank()) return;
        // A zero-valued observation carries no behaviour (an idle sample, a
        // refused action) and would only bloat the history with noise.
        if (value == 0) return;
        Run last = runs.peekLast();
        if (last != null && last.wave() == wave && last.action().equals(action)) {
            runs.removeLast();
            runs.addLast(new Run(wave, action, Math.min(MAX_RUN_COUNT, last.count() + 1L)));
            return;
        }
        if (runs.size() >= MAX_RUNS) runs.removeFirst();
        runs.addLast(new Run(wave, action, 1L));
    }

/**
     * Append already-decoded runs, used by the profile restore path.
     *
     * <p>Kept separate from {@link #record} because a restore must not re-merge
     * neighbours: the stored payload is already the final shape.</p>
     */
    public void appendAll(List<Run> restored) {
        if (restored == null) return;
        for (Run run : restored) {
            if (run == null) continue;
            while (runs.size() >= MAX_RUNS) runs.removeFirst();
            runs.addLast(run);
        }
    }

    /** The ordered runs, oldest first. */
    public List<Run> runs() { return List.copyOf(runs); }

    public int size() { return runs.size(); }

    public boolean isEmpty() { return runs.isEmpty(); }

    /** How many significant actions in total were folded in. */
    public long totalActions() {
        long total = 0L;
        for (Run run : runs) total += run.count();
        return total;
    }

    /**
     * How often the action switched between two consecutive runs.
     *
     * <p>This is the shape signal the copy needs: a player who alternated
     * sword and bow looks different from one who used one weapon throughout,
     * even when the per-action totals are identical.</p>
     */
    public int transitions() {
        int switches = 0;
        String previous = null;
        for (Run run : runs) {
            if (previous != null && !previous.equals(run.action())) switches++;
            previous = run.action();
        }
        return switches;
    }

    /** Stable one-line encoding: {@code wave:action:count} joined by {@code ;}. */
    public String encode() {
        List<String> parts = new ArrayList<>(runs.size());
        for (Run run : runs) {
            parts.add(run.wave() + ":" + encodeToken(run.action()) + ":" + run.count());
        }
        return String.join(";", parts);
    }

    /** Restore a sequence, or an empty one when the payload is unusable. */
    public static CombatSequence decode(String payload) {
        CombatSequence sequence = new CombatSequence();
        if (payload == null || payload.isBlank()) return sequence;
        for (String part : payload.split(";")) {
            if (part.isBlank()) continue;
            String[] fields = part.split(":", 3);
            if (fields.length != 3) continue;
            try {
                // SUPERSEDED: this used to call record(wave, action, count).
                // record() IGNORES the value as a magnitude -- it only uses it to
                // decide whether the observation is non-zero, then increments the
                // previous run by exactly one. So a stored run "1:SWING:9" came
                // back as "1:SWING:1": the copy lost its whole behavioural shape
                // on every restart, and a player who swung nine times looked
                // identical to one who swung once. Counts are restored verbatim.
                int wave = Integer.parseInt(fields[0]);
                int count = (int) Math.min(Integer.MAX_VALUE, Long.parseLong(fields[2]));
                String action = decodeToken(fields[1]);
                if (action.isBlank() || count < 1) continue;
                sequence.appendAll(List.of(new Run(wave, action,
                        (int) Math.min(MAX_RUN_COUNT, count))));
            } catch (RuntimeException malformed) {
                // A damaged payload must not fail the whole restore.
            }
        }
        return sequence;
    }

    private static String encodeToken(String action) {
        return action.replace("%", "%25").replace(":", "%3A").replace(";", "%3B");
    }

    private static String decodeToken(String token) {
        return token.replace("%3B", ";").replace("%3A", ":").replace("%25", "%");
    }
}
