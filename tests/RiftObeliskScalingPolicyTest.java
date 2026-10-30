import me.copimine.endevent.domain.ObeliskScalingPolicy;

public final class RiftObeliskScalingPolicyTest {
    public static void main(String[] args) {
        check(ObeliskScalingPolicy.profileForPlayers(1).obeliskCount() == 0,
                "one player is not a valid assault party");
        int[] supportedPlayerCounts = {2, 4, 5, 7, 8, 10, 11, 15, 16, 20, 21};
        for (int players : supportedPlayerCounts) {
            ObeliskScalingPolicy.Profile profile = ObeliskScalingPolicy.profileForPlayers(players);
            check(profile.health() == 3,
                    "every active obelisk must have exactly three integrity HP for " + players + " players");
            check(profile.requiredHits() == profile.obeliskCount() * 3,
                    "required hits must be the sum of three integrity HP per obelisk for " + players + " players");
        }
        check(ObeliskScalingPolicy.profileForPlayers(2).obeliskCount() == 4,
                "two players use the bounded four-obelisk profile");
        check(ObeliskScalingPolicy.profileForPlayers(10).obeliskCount() == 5,
                "ten players use five obelisks");
        check(ObeliskScalingPolicy.profileForPlayers(20).obeliskCount() == 6,
                "twenty players use six obelisks");
        int[] players = {2, 5, 8, 11, 16};
        int[] expectedMobCounts = {6, 8, 10, 12, 16};
        for (int index = 0; index < players.length; index++) {
            check(ObeliskScalingPolicy.profileForPlayers(players[index]).mobCount()
                            == expectedMobCounts[index],
                    "Wave 4 doubles its bounded pressure pack for " + players[index] + " players");
        }
        check(ObeliskScalingPolicy.profileForPlayers(2).fireballCap() == 20,
                "four towers need five bounded projectile/sequence slots each at the minimum roster");
        check(ObeliskScalingPolicy.profileForPlayers(10).fireballCap() == 25,
                "five towers reserve enough projectile capacity for each tower");
        check(ObeliskScalingPolicy.profileForPlayers(20).fireballCap() == 30,
                "six towers retain their 160-tick projectile lifetime within the hard cap");
        check(ObeliskScalingPolicy.MAX_OBELISKS == 6,
                "Wave 4 hard cap is six physical obelisks");
        System.out.println("RiftObeliskScalingPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
