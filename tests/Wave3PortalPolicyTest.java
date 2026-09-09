import me.copimine.endevent.domain.Wave3PortalPolicy;

public final class Wave3PortalPolicyTest {
    public static void main(String[] args) {
        check(Wave3PortalPolicy.PORTAL_COUNT == 3, "Wave 3 must have exactly three portals");
        check(Wave3PortalPolicy.pusherCount(0) == 0, "empty pack has no pushers");
        check(Wave3PortalPolicy.pusherCount(1) == 1, "one mob yields one pusher");
        check(Wave3PortalPolicy.pusherCount(12) == 2, "a pack is capped at two pushers");
        check(Wave3PortalPolicy.isPusher(0) && Wave3PortalPolicy.isPusher(1),
                "the first two pack slots are pushers");
        check(!Wave3PortalPolicy.isPusher(2), "the third pack slot is not a pusher");
        System.out.println("Wave3PortalPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
