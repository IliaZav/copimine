import java.util.List;
import me.copimine.endevent.domain.BossCastState;
import me.copimine.endevent.domain.BossDamagePolicy;
import me.copimine.endevent.domain.BossVirtualHealthPolicy;

public final class BossVirtualHealthPolicyTest {
    public static void main(String[] args) {
        testMultipleHitsAccumulateWithoutLostUpdates();
        testMixedHitsAccumulateInOneTick();
        testExhaustedMultiplierIsAppliedOncePerHit();
        testHealthIsClampedToTheAuthoritativePool();
        testLethalConcurrentSequenceStopsAtZero();
        System.out.println("BossVirtualHealthPolicyTest OK");
    }

    private static void testMultipleHitsAccumulateWithoutLostUpdates() {
        double result = BossVirtualHealthPolicy.applyHits(5000.0D,
                List.of(10.0D, 14.0D, 8.0D), 5000.0D);
        check(result == 4968.0D, "10 + 14 + 8 damage must leave 4968 virtual HP");
    }

    private static void testMixedHitsAccumulateInOneTick() {
        double result = BossVirtualHealthPolicy.applyHits(5000.0D,
                List.of(10.0D, 7.5D, 14.0D, 8.0D, 2.0D), 5000.0D);
        check(result == 4958.5D, "melee/projectile hits in one tick must all be retained");
    }

    private static void testExhaustedMultiplierIsAppliedOncePerHit() {
        double first = BossDamagePolicy.applyIncomingDamage(10.0D, BossCastState.EXHAUSTED);
        double second = BossDamagePolicy.applyIncomingDamage(14.0D, BossCastState.EXHAUSTED);
        double result = BossVirtualHealthPolicy.applyHits(5000.0D, List.of(first, second), 5000.0D);
        check(result == 4964.0D, "EXHAUSTED must apply x1.5 once to each independent hit");
        check(BossVirtualHealthPolicy.applyHit(5000.0D, 10.0D, BossCastState.EXHAUSTED, 5000.0D)
                        .appliedDamage() == 15.0D,
                "single EXHAUSTED hit must be multiplied exactly once");
    }

    private static void testHealthIsClampedToTheAuthoritativePool() {
        check(BossVirtualHealthPolicy.applyHit(100.0D, 40.0D, BossCastState.NONE, 5000.0D)
                        .remainingHealth() == 60.0D,
                "ordinary hit must subtract from current authoritative HP");
        check(BossVirtualHealthPolicy.applyHit(100.0D, 200.0D, BossCastState.NONE, 5000.0D)
                        .remainingHealth() == 0.0D,
                "lethal hit must clamp at zero");
        check(BossVirtualHealthPolicy.applyHit(100.0D, Double.NaN, BossCastState.NONE, 5000.0D)
                        .appliedDamage() == 0.0D,
                "non-finite hit must fail closed");
    }

    private static void testLethalConcurrentSequenceStopsAtZero() {
        double result = BossVirtualHealthPolicy.applyHits(20.0D,
                List.of(7.0D, 8.0D, 12.0D), 5000.0D);
        check(result == 0.0D, "several near-simultaneous lethal hits must not resurrect HP");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
