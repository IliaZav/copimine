import me.copimine.endevent.domain.RealitySplitPlayerKnockbackPolicy;

public final class RealitySplitPlayerKnockbackPolicyTest {
    public static void main(String[] args) {
        check(RealitySplitPlayerKnockbackPolicy.shouldSuppress(
                        7, true, true, 1_000L, 1_249L),
                "an accepted Wave 7 same-room mob hit suppresses player knockback");
        check(!RealitySplitPlayerKnockbackPolicy.shouldSuppress(
                        7, true, true, 1_250L, 1_250L),
                "the short knockback window expires at its deadline");
        check(!RealitySplitPlayerKnockbackPolicy.shouldSuppress(
                        6, true, true, 1_000L, 1_249L),
                "earlier waves retain their normal player knockback");
        check(!RealitySplitPlayerKnockbackPolicy.shouldSuppress(
                        7, false, true, 1_000L, 1_249L),
                "natural mobs cannot use the Wave 7 suppression rule");
        check(!RealitySplitPlayerKnockbackPolicy.shouldSuppress(
                        7, true, false, 1_000L, 1_249L),
                "a cross-room hit cannot arm player knockback suppression");
        check(!RealitySplitPlayerKnockbackPolicy.shouldSuppress(
                        7, true, true, 1_250L, 1_249L),
                "an expired marker cannot suppress a later knockback event");
        System.out.println("RealitySplitPlayerKnockbackPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
