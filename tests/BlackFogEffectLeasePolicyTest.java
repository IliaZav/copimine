import me.copimine.endevent.domain.BlackFogEffectLeasePolicy;

public final class BlackFogEffectLeasePolicyTest {
    public static void main(String[] args) {
        check(BlackFogEffectLeasePolicy.remainingDuration(200, 100L, 140L) == 160,
                "restoration must subtract real server ticks spent under fog");
        check(BlackFogEffectLeasePolicy.remainingDuration(20, 100L, 140L) == 0,
                "expired previous effect must not be restarted");
        check(BlackFogEffectLeasePolicy.remainingDuration(-1, 100L, 400L) == -1,
                "infinite effect duration is preserved");
        check(!BlackFogEffectLeasePolicy.mayReplace(2, 200, 2), "equal effect belongs to its original source");
        check(!BlackFogEffectLeasePolicy.mayReplace(3, 40, 2), "stronger effect must survive");
        check(!BlackFogEffectLeasePolicy.mayReplace(0, -1, 2), "infinite effect must survive");
        check(BlackFogEffectLeasePolicy.mayReplace(0, 200, 2), "weaker finite effect can be temporarily overlaid");
        check(BlackFogEffectLeasePolicy.LEASE_TICKS <= 40,
                "abrupt stop must leave a bounded, short debuff, not fifteen seconds of blindness");
        System.out.println("BlackFogEffectLeasePolicyTest OK");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
