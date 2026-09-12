import me.copimine.endevent.domain.TentacleScalingPolicy;
import me.copimine.endevent.domain.BossPhase;

public final class TentacleScalingPolicyTest {
    public static void main(String[] args) {
        testPermanentGuardiansOnlyExistInTheLastSeal();
        check(TentacleScalingPolicy.permanentFor(2, BossPhase.LAST_SEAL) == 2,
                "a duo must have two permanent tentacles in the last seal");
        check(TentacleScalingPolicy.permanentFor(3, BossPhase.LAST_SEAL) == 3,
                "three players must have three permanent tentacles");
        check(TentacleScalingPolicy.permanentFor(5, BossPhase.LAST_SEAL) == 4,
                "five players must have four permanent tentacles");
        check(TentacleScalingPolicy.permanentFor(8, BossPhase.LAST_SEAL) == 5,
                "eight players must have five permanent tentacles");
        check(TentacleScalingPolicy.permanentFor(11, BossPhase.LAST_SEAL) == 6,
                "eleven players must have six permanent tentacles");
        check(TentacleScalingPolicy.permanentFor(16, BossPhase.LAST_SEAL) == 8,
                "sixteen players must have eight permanent tentacles");
        check(TentacleScalingPolicy.permanentFor(20, BossPhase.LAST_SEAL) == 8,
                "permanent tentacles must cap at eight");
        check(TentacleScalingPolicy.permanentFor(20, BossPhase.LAST_SEAL)
                        <= TentacleScalingPolicy.MAX_PERMANENT,
                "permanent cap must be hard");
        check(TentacleScalingPolicy.temporaryFor(2, BossPhase.HUNT) == 0,
                "temporary grabs must not start before the late phases");
        check(TentacleScalingPolicy.temporaryFor(2, BossPhase.LAST_SEAL) == 2,
                "late-phase duo must have two temporary grab slots");
        check(TentacleScalingPolicy.temporaryFor(5, BossPhase.LAST_SEAL) == 3,
                "five players must have three temporary grab slots");
        check(TentacleScalingPolicy.temporaryFor(20, BossPhase.LAST_SEAL) == 6,
                "temporary grab slots must cap at six");
        check(TentacleScalingPolicy.permanentFor(0, BossPhase.LAST_SEAL) == 0,
                "empty roster must not spawn tentacles");
        System.out.println("TentacleScalingPolicyTest OK");
    }

    private static void testPermanentGuardiansOnlyExistInTheLastSeal() {
        for (BossPhase stage : BossPhase.values()) {
            if (stage == BossPhase.LAST_SEAL) {
                continue;
            }
            check(TentacleScalingPolicy.permanentFor(20, stage) == 0,
                    "permanent guardian tentacles must be absent before LAST_SEAL: " + stage);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
