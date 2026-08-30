package me.copimine.endevent.domain;

/**
 * Bounded roster scaling for the live waves.
 *
 * <p>The two-player composition is the configured wave plus six additional
 * mobs.  A larger roster grows that composition smoothly until the global
 * hard cap at twenty players.  Health/damage and objective durations use
 * separate, smaller multipliers so a full server feels stronger without
 * multiplying the number of expensive entities or effects without a bound.</p>
 */
public final class WaveScalingPolicy {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int MIN_EXTRA_MOBS = 6;
    public static final double MAX_MOB_STRENGTH_MULTIPLIER = 1.30D;
    public static final double MAX_EFFECT_MULTIPLIER = 1.25D;

    private WaveScalingPolicy() {
    }

    /** Scale one configured composition while preserving every non-zero role. */
    public static WaveMechanicsPolicy.WaveCounts scale(
            WaveMechanicsPolicy.WaveCounts configured, int players, int hardCap) {
        if (configured == null || hardCap <= 0) {
            return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        }
        int[] source = {
                Math.max(0, configured.endermen()),
                Math.max(0, configured.spiders()),
                Math.max(0, configured.skeletons()),
                Math.max(0, configured.eliteEndermen()),
                Math.max(0, configured.eliteSkeletons())};
        int configuredTotal = sum(source);
        if (configuredTotal == 0) {
            return new WaveMechanicsPolicy.WaveCounts(0, 0, 0, 0, 0);
        }
        int cap = Math.max(1, hardCap);
        int target = targetTotal(configuredTotal, players, cap);
        int[] result = distribute(source, target);
        return new WaveMechanicsPolicy.WaveCounts(
                result[0], result[1], result[2], result[3], result[4]);
    }

    /** Target count is monotonic in players and reaches hardCap at twenty. */
    public static int targetTotal(int configuredTotal, int players, int hardCap) {
        int safeConfigured = Math.max(0, configuredTotal);
        int cap = Math.max(0, hardCap);
        if (safeConfigured == 0 || cap == 0) {
            return 0;
        }
        if (safeConfigured >= cap) {
            return cap;
        }
        int safeMinimum = Math.min(cap, safeConfigured + MIN_EXTRA_MOBS);
        double progress = rosterProgress(players);
        int target = (int) Math.round(safeMinimum + (cap - safeMinimum) * progress);
        return Math.max(safeConfigured, Math.min(cap, target));
    }

    /** Bounded health/attack multiplier for event mobs. */
    public static double mobStrengthMultiplier(int players) {
        return 1.0D + (MAX_MOB_STRENGTH_MULTIPLIER - 1.0D) * rosterProgress(players);
    }

    /** Bounded duration/telegraph multiplier for wave mechanics. */
    public static double effectMultiplier(int players) {
        return 1.0D + (MAX_EFFECT_MULTIPLIER - 1.0D) * rosterProgress(players);
    }

    public static int effectDurationTicks(int baseTicks, int players) {
        if (baseTicks <= 0) {
            return 0;
        }
        return Math.max(baseTicks, (int) Math.ceil(baseTicks * effectMultiplier(players)));
    }

    private static double rosterProgress(int players) {
        int safePlayers = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, players));
        return (safePlayers - MIN_PLAYERS) / (double) (MAX_PLAYERS - MIN_PLAYERS);
    }

    private static int[] distribute(int[] source, int target) {
        int total = sum(source);
        int[] result = new int[source.length];
        if (total == 0 || target == 0) {
            return result;
        }
        double ratio = target / (double) total;
        double[] desired = new double[source.length];
        int used = 0;
        for (int index = 0; index < source.length; index++) {
            desired[index] = source[index] * ratio;
            result[index] = Math.max(0, Math.min(target, (int) Math.floor(desired[index])));
            used += result[index];
        }
        while (used < target) {
            int selected = -1;
            double bestDeficit = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < source.length; index++) {
                if (source[index] <= 0) {
                    continue;
                }
                double deficit = desired[index] - result[index];
                if (selected < 0 || deficit > bestDeficit + 0.000001D) {
                    selected = index;
                    bestDeficit = deficit;
                }
            }
            if (selected < 0) {
                break;
            }
            result[selected]++;
            used++;
        }
        while (used > target) {
            int selected = -1;
            for (int index = source.length - 1; index >= 0; index--) {
                if (result[index] > 0) {
                    selected = index;
                    break;
                }
            }
            if (selected < 0) {
                break;
            }
            result[selected]--;
            used--;
        }
        return result;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += Math.max(0, value);
        }
        return total;
    }
}
