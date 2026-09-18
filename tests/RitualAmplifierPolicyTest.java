import me.copimine.endevent.domain.RitualAmplifierPolicy;
import me.copimine.endevent.domain.RitualSphereScalingPolicy;

public final class RitualAmplifierPolicyTest {
    public static void main(String[] args) {
        var p9 = RitualSphereScalingPolicy.forPlayers(9);
        check(p9.projectilesPerVolley() == 3, "9-player base table must stay unchanged");
        check(RitualAmplifierPolicy.projectileCount(p9, 1) == 4,
                "one live amplifier must add one sphere projectile");

        var p20 = RitualSphereScalingPolicy.forPlayers(20);
        check(p20.projectilesPerVolley() == 5, "20-player base table must stay unchanged");
        check(RitualAmplifierPolicy.projectileCount(p20, 2) == 7,
                "two live amplifiers must raise the bounded projectile count to seven");
        check(RitualAmplifierPolicy.projectileCount(p20, 99) == 7,
                "amplifier projectile count must remain bounded");

        check(RitualAmplifierPolicy.effectiveIntensity(0, 2) == 2,
                "two channeling amplifiers must contribute bounded ritual intensity");
        check(RitualAmplifierPolicy.projectileDamageMultiplier(10, 2)
                        <= RitualSphereScalingPolicy.MAX_PROJECTILE_DAMAGE_MULTIPLIER,
                "amplifiers must not exceed projectile damage cap");
        check(RitualAmplifierPolicy.effectDurationMultiplier(10, 2)
                        <= RitualSphereScalingPolicy.MAX_EFFECT_DURATION_MULTIPLIER,
                "amplifiers must not exceed duration cap");

        long baseline = RitualSphereScalingPolicy.majorCooldownMillis(p9, 0);
        long amplified = RitualAmplifierPolicy.majorCooldownMillis(p9, 0, 1);
        check(amplified < baseline,
                "a live amplifier must reduce the bounded core ability cooldown");

        System.out.println("RitualAmplifierPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
