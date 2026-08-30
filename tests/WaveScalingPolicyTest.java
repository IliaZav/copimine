import me.copimine.endevent.domain.WaveMechanicsPolicy;
import me.copimine.endevent.domain.WaveScalingPolicy;

public final class WaveScalingPolicyTest {
    public static void main(String[] args) {
        WaveMechanicsPolicy.WaveCounts base = new WaveMechanicsPolicy.WaveCounts(7, 10, 5, 0, 0);
        WaveMechanicsPolicy.WaveCounts twoPlayers = WaveScalingPolicy.scale(base, 2, 56);
        WaveMechanicsPolicy.WaveCounts twentyPlayers = WaveScalingPolicy.scale(base, 20, 56);

        check(twoPlayers.total() == base.total() + 6,
                "two-player wave must add six mobs to the configured composition");
        check(twentyPlayers.total() == 56,
                "twenty-player wave must reach, but never exceed, the global cap");
        int previous = twoPlayers.total();
        for (int players = 3; players <= 20; players++) {
            int current = WaveScalingPolicy.scale(base, players, 56).total();
            check(current >= previous, "mob count must not decrease as the roster grows");
            previous = current;
        }
        check(WaveScalingPolicy.mobStrengthMultiplier(2) == 1.0D,
                "minimum roster must keep the baseline mob strength");
        check(close(WaveScalingPolicy.mobStrengthMultiplier(20), 1.30D),
                "large roster must get a bounded strength increase");
        check(close(WaveScalingPolicy.effectMultiplier(20), 1.25D),
                "large roster must get a bounded effect increase");
        check(WaveScalingPolicy.effectDurationTicks(220, 20) == 275,
                "large roster effect duration must be scaled deterministically");
        System.out.println("WaveScalingPolicyTest OK");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
