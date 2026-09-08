import me.copimine.endevent.domain.TentacleScalingPolicy;
import me.copimine.endevent.domain.V2BossStage;

public final class TentacleScalingPolicyTest {
    public static void main(String[] args) {
        check(TentacleScalingPolicy.permanentFor(2, V2BossStage.RIFT) >= 2,
                "a duo must see readable permanent tentacle pressure");
        check(TentacleScalingPolicy.permanentFor(20, V2BossStage.LAST_SEAL) == 8,
                "permanent tentacles must cap at eight");
        check(TentacleScalingPolicy.permanentFor(20, V2BossStage.LAST_SEAL)
                        <= TentacleScalingPolicy.MAX_PERMANENT,
                "permanent cap must be hard");
        check(TentacleScalingPolicy.temporaryFor(2, V2BossStage.HUNT) == 0,
                "temporary grabs must not start before the late phases");
        check(TentacleScalingPolicy.temporaryFor(20, V2BossStage.LAST_SEAL)
                        <= TentacleScalingPolicy.MAX_TEMPORARY,
                "temporary grab cap must be hard");
        check(TentacleScalingPolicy.permanentFor(0, V2BossStage.LAST_SEAL) == 0,
                "empty roster must not spawn tentacles");
        System.out.println("TentacleScalingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
