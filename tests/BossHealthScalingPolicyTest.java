import me.copimine.endevent.domain.BossHealthScalingPolicy;
import me.copimine.endevent.domain.BossStage;
import me.copimine.endevent.domain.BossStagePolicy;

public final class BossHealthScalingPolicyTest {
    public static void main(String[] args) {
        testPlayerBands();
        testConfiguredThresholdsScaleWithTheFrozenMaxHealth();
        testStagesUsePercentagesForLargeBossPools();
        System.out.println("BossHealthScalingPolicyTest OK");
    }

    private static void testPlayerBands() {
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(1), 5000.0D),
                "the base boss pool must be 5000 HP");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(2), 5000.0D),
                "the two-player event must start at 5000 HP");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(7), 9166.666666666666D),
                "the seventh player must be part of the smooth ramp to 10000 HP");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(8), 10000.0D),
                "eight players must start the 10000 HP band");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(9), 10833.333333333334D),
                "the ninth player must add one linear step");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(19), 19166.666666666668D),
                "the nineteenth player must stay below the cap");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(20), 20000.0D),
                "twenty players must reach the 20000 HP cap");
        check(close(BossHealthScalingPolicy.maxHealthForPlayers(32), 20000.0D),
                "players above the configured cap must not increase boss HP");
    }

    private static void testConfiguredThresholdsScaleWithTheFrozenMaxHealth() {
        double max = BossHealthScalingPolicy.maxHealthForPlayers(20);
        check(close(BossHealthScalingPolicy.scaleFromBase(2500.0D, 5000.0D, max), 10000.0D),
                "the half-health threshold must remain 50 percent");
        check(close(BossHealthScalingPolicy.scaleFromBase(500.0D, 5000.0D, max), 2000.0D),
                "the Judgment threshold must remain 10 percent");
        check(close(BossHealthScalingPolicy.scaleFromBase(650.0D, 5000.0D, max), 2600.0D),
                "the final release pool must keep its configured proportion");
        check(BossHealthScalingPolicy.scaleFromBase(650.0D, 5000.0D, max) <= max,
                "a scaled threshold must never exceed the boss max");
    }

    private static void testStagesUsePercentagesForLargeBossPools() {
        double max = BossHealthScalingPolicy.maxHealthForPlayers(20);
        check(BossStagePolicy.stageFor(16000.0D, max, false) == BossStage.HUNTER,
                "80 percent of the large pool must enter Hunter");
        check(BossStagePolicy.stageFor(12000.0D, max, false) == BossStage.DISTORTION,
                "60 percent of the large pool must enter Distortion");
        check(BossStagePolicy.stageFor(8000.0D, max, false) == BossStage.ABSORPTION,
                "40 percent of the large pool must enter Absorption");
        check(BossStagePolicy.stageFor(4000.0D, max, false) == BossStage.CATASTROPHE,
                "20 percent of the large pool must enter Catastrophe");
        check(BossStagePolicy.transition(BossStage.CATASTROPHE, 2000.0D, max, false).triggerJudgment(),
                "10 percent of the large pool must trigger Judgment");
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
