import me.copimine.endevent.domain.RiftFireballPolicy;

public final class RiftFireballScalingPolicyTest {
    public static void main(String[] args) {
        testDamageStartsAtGhastLevelAndHasSmallBoundedGrowth();
        testDurationsStayReadableAndBounded();
        testIntensityStaysAtTheExplicitImpactContract();
        System.out.println("RiftFireballScalingPolicyTest OK");
    }

    private static void testDamageStartsAtGhastLevelAndHasSmallBoundedGrowth() {
        double two = RiftFireballPolicy.scaledFireballDamage(6.0D, 2);
        double five = RiftFireballPolicy.scaledFireballDamage(6.0D, 5);
        double ten = RiftFireballPolicy.scaledFireballDamage(6.0D, 10);
        double twenty = RiftFireballPolicy.scaledFireballDamage(6.0D, 20);
        check(close(two, 6.0D), "two players must keep the 6.0 base damage");
        check(two < five && five < ten && ten < twenty,
                "fireball damage must grow monotonically with the active party");
        check(twenty <= RiftFireballPolicy.MAX_SCALED_DAMAGE,
                "twenty players must stay below the non-one-shot damage cap");
        check(RiftFireballPolicy.scaledFireballDamage(40.0D, 20)
                        == RiftFireballPolicy.MAX_SCALED_DAMAGE,
                "unsafe configured damage must fail closed at the hard cap");
    }

    private static void testDurationsStayReadableAndBounded() {
        RiftFireballPolicy.EffectProfile base =
                RiftFireballPolicy.scaledFireballEffects(6.0D, 40, 60, 2);
        RiftFireballPolicy.EffectProfile scaled =
                RiftFireballPolicy.scaledFireballEffects(6.0D, 40, 60, 20);
        check(base.blindnessTicks() == 40 && base.weaknessTicks() == 60
                        && base.nauseaTicks() == 60 && base.slownessTicks() == 60,
                "two players must keep the requested base durations");
        check(scaled.blindnessTicks() == base.blindnessTicks()
                        && scaled.weaknessTicks() == base.weaknessTicks()
                        && scaled.nauseaTicks() == base.nauseaTicks()
                        && scaled.slownessTicks() == base.slownessTicks(),
                "party size must not turn one fireball into an unbounded control chain");
        for (int players = 2; players <= 20; players++) {
            RiftFireballPolicy.EffectProfile profile =
                    RiftFireballPolicy.scaledFireballEffects(6.0D, 40, 60, players);
            check(profile.blindnessTicks() <= 200 && profile.weaknessTicks() <= 200
                            && profile.nauseaTicks() <= 200 && profile.slownessTicks() <= 200,
                    "scaled effects must stay within the ten-second bound");
        }
    }

    private static void testIntensityStaysAtTheExplicitImpactContract() {
        RiftFireballPolicy.EffectProfile base =
                RiftFireballPolicy.scaledFireballEffects(6.0D, 40, 60, 2);
        RiftFireballPolicy.EffectProfile scaled =
                RiftFireballPolicy.scaledFireballEffects(6.0D, 40, 60, 20);
        check(scaled.weaknessAmplifier() == 0 && scaled.nauseaAmplifier() == 1
                        && scaled.slownessAmplifier() == 0,
                "fireball debuffs must keep the explicit Bukkit amplifiers");
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.0001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
