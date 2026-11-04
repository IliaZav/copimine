import me.copimine.endevent.domain.WaveDamagePolicy;

public final class WaveDamagePolicyTest {
    public static void main(String[] args) {
        check(close(WaveDamagePolicy.reduce(8.0D, 4.0D), 4.0D),
                "wave damage must be reduced by four");
        check(close(WaveDamagePolicy.reduce(4.0D, 4.0D), 0.0D),
                "reduction must clamp at zero");
        check(close(WaveDamagePolicy.reduce(-2.0D, 4.0D), 0.0D),
                "invalid base damage must clamp at zero");
        // The curve used to subtract a flat 4.0 and clamp at 1.0. An Enderman's
        // base attack is 7.0, so ordinary wave bodies hit for 3.0 and were fully
        // absorbed by armour: every swing read as final=0.0 in production.
        check(close(WaveDamagePolicy.minimumCombatDamage(7.0D, 4.0D), 7.0D * 0.8D),
                "an ordinary Enderman must hit harder than a flat subtraction allowed");
        check(WaveDamagePolicy.minimumCombatDamage(7.0D, 4.0D) > 3.0D,
                "the wave curve must never fall back to the removed flat subtraction");
        check(close(WaveDamagePolicy.minimumCombatDamage(4.0D, 4.0D), 3.2D),
                "a small attack must keep most of its value");
        check(close(WaveDamagePolicy.minimumCombatDamage(12.0D, 4.0D), 12.0D * 0.8D),
                "the curve must scale with the attack instead of subtracting");
        // The weakest attacks still scale with the curve; the readable minimum is
        // applied AFTER the share is removed, so a 1.0D swing resolves to the
        // committed floor instead of being inflated or swallowed.
        check(close(WaveDamagePolicy.minimumCombatDamage(4.0D, 4.0D),
                        Math.max(WaveDamagePolicy.MINIMUM_HIT_DAMAGE, 4.0D * 0.8D)),
                "a small attack keeps the larger of its scaled value and the floor");
        check(close(WaveDamagePolicy.minimumCombatDamage(1.0D, 4.0D), WaveDamagePolicy.MINIMUM_HIT_DAMAGE),
                "the weakest attack resolves to the readable minimum instead of vanishing");
        check(close(WaveDamagePolicy.minimumCombatDamage(0.0D, 4.0D), WaveDamagePolicy.MINIMUM_HIT_DAMAGE),
                "a zero attack must still leave a readable minimum hit");
        check(close(WaveDamagePolicy.minimumCombatDamage(Double.NaN, 4.0D), WaveDamagePolicy.MINIMUM_HIT_DAMAGE),
                "non-finite wave damage must fail closed to the minimum hit");
        check(close(WaveDamagePolicy.minimumCombatDamage(20.0D, 0.0D), 20.0D),
                "no configured reduction means no scaling");
        check(WaveDamagePolicy.minimumCombatDamage(50.0D, 1_000.0D)
                        <= 50.0D * (1.0D - WaveDamagePolicy.MAX_REDUCTION_FRACTION) + 0.000001D,
                "the reduction fraction must stay capped for any configured value");
        System.out.println("WaveDamagePolicyTest OK");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
