import me.copimine.endevent.domain.V2FogTimingPolicy;

public final class V2FogTimingPolicyTest {
    public static void main(String[] args) {
        check(V2FogTimingPolicy.CYCLE_COUNT == 3, "three fog cycles are required");
        check(V2FogTimingPolicy.combatSeconds(0) == 40, "cycle 1 combat is 40 seconds");
        check(V2FogTimingPolicy.combatSeconds(1) == 50, "cycle 2 combat is 50 seconds");
        check(V2FogTimingPolicy.combatSeconds(2) == 60, "cycle 3 combat is 60 seconds");
        check(V2FogTimingPolicy.combatSeconds(99) == 60, "combat timing is bounded");
        check(V2FogTimingPolicy.safeZoneSeconds() == 4, "safe zone warning is 4 seconds");
        check(V2FogTimingPolicy.fogSeconds() == 3, "black fog lasts 3 seconds");
        check(!V2FogTimingPolicy.complete(2), "two cycles are incomplete");
        check(V2FogTimingPolicy.complete(3), "three cycles complete Wave 4 objective");
        System.out.println("V2FogTimingPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
