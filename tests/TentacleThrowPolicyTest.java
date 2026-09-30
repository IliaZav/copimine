import me.copimine.endevent.domain.TentacleThrowPolicy;

import java.lang.reflect.Method;

public final class TentacleThrowPolicyTest {
    public static void main(String[] args) throws Exception {
        testReleaseDamageSupportsOnlyActiveCombatAndLocalTestShowroom();
        testReleaseDamageRemovesExactlyFiveHearts();
        testThrowTravelsAwayFromTheTentacleWithReadableLift();
        testSocketCoincidenceUsesAFiniteLongFallback();
        testSafeLandingKeepsTheStrongestReadableThrow();
        testNoSafeLandingRejectsRatherThanApplyingAWeakThrow();
        System.out.println("TentacleThrowPolicyTest OK");
    }

    private static void testReleaseDamageRemovesExactlyFiveHearts() throws Exception {
        Method healthAfter = TentacleThrowPolicy.class.getMethod("healthAfterReleaseDamage", double.class);
        double fullHealth = (double) healthAfter.invoke(null, 20.0D);
        check(Math.abs(fullHealth - 10.0D) < 0.000001D,
                "release damage removes exactly ten HP from a healthy player");
        double lowHealth = (double) healthAfter.invoke(null, 8.0D);
        check(lowHealth == 0.0D,
                "release damage is clamped at zero health");
        double invalidHealth = (double) healthAfter.invoke(null, Double.NaN);
        check(invalidHealth == 0.0D,
                "non-finite health cannot produce a non-finite release result");
    }

    private static void testReleaseDamageSupportsOnlyActiveCombatAndLocalTestShowroom() throws Exception {
        Method shouldApply = TentacleThrowPolicy.class.getMethod("shouldApplyReleaseDamage",
                boolean.class, boolean.class, boolean.class);
        check((boolean) shouldApply.invoke(null, true, true, false),
                "official active participants receive release damage during boss combat");
        check(!(boolean) shouldApply.invoke(null, true, false, false),
                "official participants outside boss combat do not receive release damage");
        check((boolean) shouldApply.invoke(null, false, false, true),
                "the local test-boss showroom must exercise real release damage");
        check(!(boolean) shouldApply.invoke(null, false, false, false),
                "ordinary inactive viewers outside the local test showroom stay protected");
    }

    private static void testThrowTravelsAwayFromTheTentacleWithReadableLift() {
        TentacleThrowPolicy.Launch away = TentacleThrowPolicy.launch(0.0D, 0.0D, 6.0D, 0.0D);
        check(away.x() > 0.0D && away.horizontalLength() >= 0.95D,
                "throw travels away with long horizontal reach");
        check(Math.abs(away.y() - 0.72D) < 0.000001D,
                "throw keeps the stronger authored lift needed to carry players toward the arena edge");
        check(TentacleThrowPolicy.estimatedHorizontalTravel(away) >= 15.0D,
                "throw carries the player a long distance toward the perimeter");
    }

    private static void testSocketCoincidenceUsesAFiniteLongFallback() {
        TentacleThrowPolicy.Launch coincident = TentacleThrowPolicy.launch(0.0D, 0.0D, 0.0D, 0.0D);
        check(coincident.horizontalLength() >= 0.95D,
                "socket coincidence cannot shorten a throw");
        check(Double.isFinite(coincident.x()) && Double.isFinite(coincident.z()),
                "fallback direction is finite");
    }

    private static void testSafeLandingKeepsTheStrongestReadableThrow() {
        TentacleThrowPolicy.Launch requested = TentacleThrowPolicy.launch(0.0D, 0.0D, 6.0D, 0.0D);
        TentacleThrowPolicy.Launch safe = TentacleThrowPolicy.strongestSafeLaunch(requested,
                candidate -> candidate.horizontalLength() >= 0.90D
                        && candidate.horizontalLength() <= 1.25D,
                0.85D);

        check(safe != null && Math.abs(safe.horizontalLength() - 1.20D) < 0.000001D,
                "choose the strongest safe sampled launch, not a tiny binary-search remainder");
    }

    private static void testNoSafeLandingRejectsRatherThanApplyingAWeakThrow() {
        TentacleThrowPolicy.Launch requested = TentacleThrowPolicy.launch(0.0D, 0.0D, 6.0D, 0.0D);
        TentacleThrowPolicy.Launch safe = TentacleThrowPolicy.strongestSafeLaunch(requested,
                candidate -> candidate.horizontalLength() < 0.85D,
                0.85D);

        check(safe == null,
                "unsafe geometry must not silently degrade a throw below the readable-impulse floor");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
