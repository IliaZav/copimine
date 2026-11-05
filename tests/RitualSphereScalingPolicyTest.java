import me.copimine.endevent.domain.RitualSphereScalingPolicy;

/**
 * Guard-count expectations for the Wave 6 ritual sphere.
 *
 * <p>Superseded 2026-10-31: the earlier table used a fixed three guards per
 * caster (5/10/15). User requirement W6-009 replaces it with a roster ramp of
 * one guard at a 2-4 party, +1 per four players, up to the five-per-mage
 * ceiling, so the expected totals become 5/10/15/20/25. The caster count,
 * volley size, zone count and cooldown expectations are unchanged.</p>
 */
public final class RitualSphereScalingPolicyTest {
    public static void main(String[] args) {
        check(RitualSphereScalingPolicy.forPlayers(1).casterCount() == 5,
                "solo Wave 6 must still spawn exactly five ritual casters");
        check(RitualSphereScalingPolicy.forPlayers(20).casterCount() == 5,
                "large-party Wave 6 must still spawn exactly five ritual casters");
        checkProfile(2, 5, 5, 3, 1, 14);
        checkProfile(3, 5, 5, 4, 1, 14);
        checkProfile(4, 5, 5, 5, 1, 14);
        checkProfile(5, 5, 10, 5, 1, 14);
        checkProfile(8, 5, 10, 7, 1, 14);
        checkProfile(9, 5, 15, 7, 2, 14);
        checkProfile(12, 5, 15, 7, 2, 14);
        checkProfile(13, 5, 20, 7, 2, 14);
        checkProfile(16, 5, 20, 7, 2, 14);
        checkProfile(17, 5, 25, 7, 3, 14);
        checkProfile(20, 5, 25, 7, 3, 14);
        RitualSphereScalingPolicy.Profile capped = RitualSphereScalingPolicy.forPlayers(999);
        check(capped.participants() == 20, "participants must be capped at twenty");
        check(capped.guardCount() == capped.casterCount() * 5,
                "large-party caster groups must use the five-guard ceiling");
        check(RitualSphereScalingPolicy.forPlayers(2).projectilesPerVolley() == 3,
                "solo and duo preserve the reduced guard pressure while retaining three projectiles");
        check(RitualSphereScalingPolicy.pressureMobsForPlayers(2).total() == 4,
                "duo ritual needs ordinary combat pressure alongside its elite");
        var twoPlayerPressure = RitualSphereScalingPolicy.pressureMobsForPlayers(2);
        check(twoPlayerPressure.eliteEndermen() == 1 && twoPlayerPressure.eliteSkeletons() == 0
                        && twoPlayerPressure.endermen() + twoPlayerPressure.spiders()
                        + twoPlayerPressure.skeletons() == 3,
                "duo pressure has three ordinary mobs and one visually distinct elite");
        check(RitualSphereScalingPolicy.pressureMobsForPlayers(6).total() == 8,
                "six-player ritual pressure grows independently from caster count");
        var fivePlayerPressure = RitualSphereScalingPolicy.pressureMobsForPlayers(5);
        check(fivePlayerPressure.eliteEndermen() == 1 && fivePlayerPressure.eliteSkeletons() == 0,
                "five-player pressure keeps a single distinct elite");
        check(RitualSphereScalingPolicy.pressureMobsForPlayers(6).eliteEndermen() == 1
                        && me.copimine.endevent.domain.EliteAbilityPolicy.activeCastForWave(
                        6, me.copimine.endevent.domain.EliteAbilityPolicy.Role.ENDERMAN)
                        == me.copimine.endevent.domain.EliteAbilityPolicy.Ability.VOID_BURST,
                "the six-player pressure Enderman is assigned the Void Burst live spell");
        check(RitualSphereScalingPolicy.pressureMobsForPlayers(20).total() == 8,
                "full ritual pressure remains bounded");
        var sixPlayerPressure = RitualSphereScalingPolicy.pressureMobsForPlayers(6);
        check(sixPlayerPressure.eliteEndermen() == 1 && sixPlayerPressure.eliteSkeletons() == 0,
                "Wave 6 pressure includes one distinct elite while keeping ordinary mobs");
        var twentyPlayerPressure = RitualSphereScalingPolicy.pressureMobsForPlayers(20);
        check(twentyPlayerPressure.eliteEndermen() == 1 && twentyPlayerPressure.eliteSkeletons() == 0
                        && twentyPlayerPressure.total() == 8,
                "large parties receive one bounded Void Burst elite plus ordinary mobs");
        System.out.println("RitualSphereScalingPolicyTest OK");
    }

    private static void checkProfile(int players, int casters, int guards,
                                     int volleys, int zones, int cooldown) {
        RitualSphereScalingPolicy.Profile profile = RitualSphereScalingPolicy.forPlayers(players);
        check(profile.casterCount() == casters, players + " caster count");
        check(profile.guardCount() == guards, players + " guard count");
        check(profile.projectilesPerVolley() == volleys, players + " volley size");
        check(profile.simultaneousZones() == zones, players + " zone count");
        check(profile.majorCooldownSeconds() == cooldown, players + " cooldown");
        int guardsPerCaster = profile.guardCount() / profile.casterCount();
        check(profile.guardCount() == profile.casterCount() * guardsPerCaster
                        && guardsPerCaster >= 1 && guardsPerCaster <= 5,
                players + " must have one to five guards per caster");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
