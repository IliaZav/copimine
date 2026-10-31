import me.copimine.endevent.domain.BlackFogTimingPolicy;

public final class BlackFogTimingPolicyTest {
    public static void main(String[] args) {
        // Superseded 2026-10-31 (WP-C): Wave 5 runs exactly FIVE fog cycles.
        check(BlackFogTimingPolicy.CYCLE_COUNT == 5, "Wave 5 must run exactly five fog cycles");
        check(BlackFogTimingPolicy.safeZoneSeconds() == 8,
                "safe zone must remain visible long enough to reach it");
        check(BlackFogTimingPolicy.fogSeconds() == 3,
                "fog penalty window must remain bounded");
        check(BlackFogTimingPolicy.combatSeconds(0) == 40
                        && BlackFogTimingPolicy.combatSeconds(1) == 50
                        && BlackFogTimingPolicy.combatSeconds(2) == 60
                        && BlackFogTimingPolicy.combatSeconds(3) == 60
                        && BlackFogTimingPolicy.combatSeconds(4) == 60,
                "combat windows must keep their staged timings");
        check(BlackFogTimingPolicy.complete(5), "five cycles must complete the objective");
        check(!BlackFogTimingPolicy.complete(4), "four cycles must not complete the objective");
        check(BlackFogTimingPolicy.hasRemainingCycles(0)
                        && BlackFogTimingPolicy.hasRemainingCycles(4)
                        && !BlackFogTimingPolicy.hasRemainingCycles(5)
                        && !BlackFogTimingPolicy.hasRemainingCycles(-1),
                "completed or invalid fog cycles must not start another safe-zone cycle");
        System.out.println("BlackFogTimingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}