import me.copimine.endevent.domain.BlackFogTimingPolicy;

public final class BlackFogTimingPolicyTest {
    public static void main(String[] args) {
        // November 9 supersedes fixed three-cycle timing with roster profiles.
        int[] parties = {2, 5, 10, 11, 20}, targets = {5, 6, 7, 8, 10};
        for (int i = 0; i < parties.length; i++) {
            int target = BlackFogTimingPolicy.cycleCount(parties[i]);
            check(target == targets[i], "roster target");
            check(BlackFogTimingPolicy.safeZoneSeconds(0, target) == 4
                    && BlackFogTimingPolicy.safeZoneSeconds(target - 1, target) == 1, "shrinking reach window");
            check(BlackFogTimingPolicy.fogSeconds(0, target) == 18
                    && BlackFogTimingPolicy.fogSeconds(target - 1, target) == 26, "longer fog exposure");
            check(BlackFogTimingPolicy.hasRemainingCycles(target - 1, target)
                    && !BlackFogTimingPolicy.hasRemainingCycles(target, target), "exact completion");
        }
        check(BlackFogTimingPolicy.combatSeconds(0) == 25
                && BlackFogTimingPolicy.combatSeconds(9) == 35, "bounded combat segments");
        check(!BlackFogTimingPolicy.hasRemainingCycles(-1, 5), "invalid cycle rejected");
        System.out.println("BlackFogTimingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}