import me.copimine.endevent.domain.BlackFogTimingPolicy;

public final class BlackFogTimingPolicyTest {
    public static void main(String[] args) {
        check(BlackFogTimingPolicy.CYCLE_COUNT == 3, "Wave 5 must keep three cycles");
        check(BlackFogTimingPolicy.safeZoneSeconds() == 8,
                "safe zone must remain visible long enough to reach it");
        check(BlackFogTimingPolicy.fogSeconds() == 3,
                "fog penalty window must remain bounded");
        check(BlackFogTimingPolicy.combatSeconds(0) == 40
                        && BlackFogTimingPolicy.combatSeconds(1) == 50
                        && BlackFogTimingPolicy.combatSeconds(2) == 60,
                "combat windows must keep their staged timings");
        check(BlackFogTimingPolicy.complete(3), "three cycles must complete the objective");
        check(!BlackFogTimingPolicy.complete(2), "two cycles must not complete the objective");
        System.out.println("BlackFogTimingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
