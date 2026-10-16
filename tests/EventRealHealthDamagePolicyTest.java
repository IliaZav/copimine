import me.copimine.endevent.domain.EventRealHealthDamagePolicy;

public final class EventRealHealthDamagePolicyTest {
    public static void main(String[] args) {
        assertDamage(40.0D, 2.44D, 37.56D, "one hit subtracts the computed final damage");
        assertDamage(37.56D, 2.082D, 35.478D, "a later hit composes from current entity health");

        EventRealHealthDamagePolicy.Result threePlayers = EventRealHealthDamagePolicy.applySeries(
                5_000.0D, 5_000.0D, 10.0D, 14.0D, 8.0D);
        assertClose(4_968.0D, threePlayers.remainingHealth(),
                "same-tick sequential attacks may not overwrite each other");
        assertClose(32.0D, threePlayers.appliedDamage(), "all computed final damage is retained");

        EventRealHealthDamagePolicy.Result bounded = EventRealHealthDamagePolicy.apply(
                2.0D, 40.0D, 6.0D);
        assertClose(0.0D, bounded.remainingHealth(), "damage cannot make real health negative");
        assertClose(2.0D, bounded.appliedDamage(), "lethal damage is bounded by remaining health");

        EventRealHealthDamagePolicy.Result ignored = EventRealHealthDamagePolicy.apply(
                40.0D, 40.0D, -2.0D);
        assertClose(40.0D, ignored.remainingHealth(), "negative damage is ignored");
        assertClose(0.0D, ignored.appliedDamage(), "negative damage applies nothing");
        System.out.println("EventRealHealthDamagePolicyTest OK");
    }

    private static void assertDamage(double current, double incoming, double expected, String message) {
        EventRealHealthDamagePolicy.Result result = EventRealHealthDamagePolicy.apply(current, 40.0D, incoming);
        assertClose(expected, result.remainingHealth(), message);
    }

    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.00001D) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
