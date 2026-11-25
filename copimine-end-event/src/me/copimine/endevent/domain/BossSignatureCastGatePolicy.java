package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Owner request: new signature spells fire <b>once</b> at a given HP threshold,
 * so a phase is never overloaded by the same cast landing again and again.
 *
 * <p>An encounter configures a descending list of thresholds (for example
 * {@code [0.40, 0.33, 0.26]}). Each threshold is "armed" while the Guardian's
 * health fraction is at or above it, and is spent exactly once when the health
 * drops through it. The highest still-armed, not-yet-spent threshold at or
 * below the current fraction is the one a cast satisfies.</p>
 *
 * <p>The gate is pure and additive: it never removes a threshold, never
 * re-arms one by itself, and treats a malformed threshold as absent rather than
 * guessing.</p>
 */
public final class BossSignatureCastGatePolicy {

    /** Outcome of asking whether a signature cast may fire. */
    public record Gate(boolean allowed, double threshold) {
        public static Gate refused() {
            return new Gate(false, Double.NaN);
        }
    }

    private BossSignatureCastGatePolicy() {
    }

    /**
     * The highest threshold the Guardian has already crossed - the largest
     * unconsumed threshold at or above {@code hpFraction} - or
     * {@link Double#NaN} when none qualifies.
     *
     * <p>A threshold is only "armed" once health has fallen to it, so a healthy
     * boss above every threshold fires nothing. Choosing the largest armed
     * threshold fires the earliest-crossed one first, so a fast health drop that
     * skips several thresholds still spends them in order instead of losing one.</p>
     */
    public static double firstUnconsumedThreshold(double hpFraction, List<Double> thresholds,
                                                  Set<Double> consumed) {
        if (thresholds == null || thresholds.isEmpty()) {
            return Double.NaN;
        }
        double fraction = clampFraction(hpFraction);
        Set<Double> spent = consumed == null ? Set.of() : new LinkedHashSet<>(consumed);
        double best = Double.NaN;
        for (Double threshold : thresholds) {
            if (threshold == null || !Double.isFinite(threshold)) {
                continue;
            }
            double value = clampFraction(threshold);
            // Not yet reached (health still above it), or already spent.
            if (value < fraction || spent.contains(value)) {
                continue;
            }
            if (Double.isNaN(best) || value > best) {
                best = value;
            }
        }
        return best;
    }

    /** True when at least one threshold is armed and unspent at this fraction. */
    public static boolean mayFire(double hpFraction, List<Double> thresholds, Set<Double> consumed) {
        return !Double.isNaN(firstUnconsumedThreshold(hpFraction, thresholds, consumed));
    }

    /** Full answer, carrying the threshold the cast spends when it is allowed. */
    public static Gate gate(double hpFraction, List<Double> thresholds, Set<Double> consumed) {
        double threshold = firstUnconsumedThreshold(hpFraction, thresholds, consumed);
        return Double.isNaN(threshold) ? Gate.refused() : new Gate(true, threshold);
    }

    /** Returns a new set with {@code threshold} spent; the input set is untouched. */
    public static Set<Double> consume(Set<Double> consumed, double threshold) {
        Set<Double> next = new LinkedHashSet<>(consumed == null ? Set.of() : consumed);
        if (Double.isFinite(threshold)) {
            next.add(threshold);
        }
        return next;
    }

    /** Normalises a configured threshold list: bounded 0..1, descending, deduped. */
    public static List<Double> normaliseThresholds(List<Double> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return List.of();
        }
        List<Double> values = new ArrayList<>();
        for (Double threshold : thresholds) {
            if (threshold == null || !Double.isFinite(threshold)) {
                continue;
            }
            double value = clampFraction(threshold);
            if (!values.contains(value)) {
                values.add(value);
            }
        }
        values.sort((left, right) -> Double.compare(right, left));
        return List.copyOf(values);
    }

    private static double clampFraction(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
