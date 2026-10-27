import me.copimine.endevent.domain.Wave5PreFogRefillPolicy;

public final class Wave5PreFogRefillPolicyTest {
    public static void main(String[] args) {
        check(Wave5PreFogRefillPolicy.shouldRefill(5, true, 0, 10_000L, 0L),
                "an empty Wave 5 combat segment refills before any fog cycle");
        check(!Wave5PreFogRefillPolicy.shouldRefill(5, true, 1, 10_000L, 0L),
                "live mobs prevent overlapping refill packs");
        check(!Wave5PreFogRefillPolicy.shouldRefill(5, false, 0, 10_000L, 0L),
                "safe and fog phases do not refill combat mobs");
        check(!Wave5PreFogRefillPolicy.shouldRefill(4, true, 0, 10_000L, 0L),
                "other waves never enter the Wave 5 refill path");
        check(!Wave5PreFogRefillPolicy.shouldRefill(5, true, 0, 9_999L, 10_000L),
                "the retry deadline prevents a refill storm");
        System.out.println("Wave5PreFogRefillPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
