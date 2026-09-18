import me.copimine.endevent.domain.RitualSphereScalingPolicy;

public final class RitualSphereScalingPolicyTest {
    public static void main(String[] args) {
        checkProfile(2, 4, 12, 1, 1, 0, 13);
        checkProfile(4, 4, 12, 1, 1, 1, 13);
        checkProfile(5, 4, 12, 2, 1, 1, 12);
        checkProfile(8, 4, 12, 2, 1, 1, 12);
        checkProfile(9, 5, 15, 3, 2, 2, 11);
        checkProfile(12, 5, 15, 3, 2, 2, 11);
        checkProfile(13, 5, 15, 4, 2, 2, 10);
        checkProfile(16, 5, 15, 4, 2, 2, 10);
        checkProfile(17, 6, 18, 5, 3, 3, 9);
        checkProfile(20, 6, 18, 5, 3, 3, 9);
        RitualSphereScalingPolicy.Profile capped = RitualSphereScalingPolicy.forPlayers(999);
        check(capped.participants() == 20, "participants must be capped at twenty");
        check(capped.guardCount() == capped.casterCount() * 3,
                "every caster must have exactly three guards");
        check(RitualSphereScalingPolicy.intensityForSuccessfulDrains(100)
                        == RitualSphereScalingPolicy.MAX_INTENSITY,
                "ritual intensity must be bounded");
        check(RitualSphereScalingPolicy.projectileDamageMultiplier(0) == 1.0D,
                "zero drains must keep baseline projectile damage");
        check(RitualSphereScalingPolicy.projectileDamageMultiplier(100)
                        <= RitualSphereScalingPolicy.MAX_PROJECTILE_DAMAGE_MULTIPLIER,
                "projectile multiplier must be bounded");
        System.out.println("RitualSphereScalingPolicyTest OK");
    }

    private static void checkProfile(int players, int casters, int guards,
                                     int volleys, int zones, int pairs, int cooldown) {
        RitualSphereScalingPolicy.Profile profile = RitualSphereScalingPolicy.forPlayers(players);
        check(profile.casterCount() == casters, players + " caster count");
        check(profile.guardCount() == guards, players + " guard count");
        check(profile.projectilesPerVolley() == volleys, players + " volley size");
        check(profile.simultaneousZones() == zones, players + " zone count");
        check(profile.controlSwapPairs() == pairs, players + " swap pair count");
        check(profile.majorCooldownSeconds() == cooldown, players + " cooldown");
        check(profile.guardCount() == profile.casterCount() * 3,
                players + " must preserve three guards per caster");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
