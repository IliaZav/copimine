import me.copimine.endevent.domain.BlackFogTimingPolicy;

public final class BlackFogTimingPolicyTest {
    public static void main(String[] args) {
        // The approved contract is THREE fog cycles. Every expectation below is
        // written against that count, not the superseded five-cycle design.
        check(BlackFogTimingPolicy.CYCLE_COUNT == 3, "Wave 5 must run exactly three fog cycles");
        check(BlackFogTimingPolicy.safeZoneSeconds() == 8,
                "safe zone must remain visible long enough to reach it");
        // SUPERSEDED 2026-11-03 (user request): the fog penalty window was 3 s
        // and the user asked for exactly +7 s on top of it, i.e. 10 s. The old
        // assertion pinned the OLD value, so it is restated against the new one
        // rather than deleted. The window is still bounded: the assertion now
        // states the new bound explicitly.
        check(BlackFogTimingPolicy.fogSeconds() == 10,
                "fog penalty window must be the requested 10 s (3 s + 7 s)");
        check(BlackFogTimingPolicy.fogSeconds() > BlackFogTimingPolicy.safeZoneSeconds(),
                "the fog must outlast the safe zone or the penalty is cosmetic");
        check(BlackFogTimingPolicy.combatSeconds(0) == 40
                        && BlackFogTimingPolicy.combatSeconds(1) == 50
                        && BlackFogTimingPolicy.combatSeconds(2) == 60,
                "combat windows must keep their staged 40/50/60 s timings");
        check(BlackFogTimingPolicy.complete(3), "three cycles must complete the objective");
        check(!BlackFogTimingPolicy.complete(2), "two cycles must not complete the objective");
        check(BlackFogTimingPolicy.hasRemainingCycles(0)
                        && BlackFogTimingPolicy.hasRemainingCycles(2)
                        && !BlackFogTimingPolicy.hasRemainingCycles(3)
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