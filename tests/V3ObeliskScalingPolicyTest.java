import me.copimine.endevent.domain.V3ObeliskScalingPolicy;
import me.copimine.endevent.domain.V3ObeliskPlacementPolicy;

public final class V3ObeliskScalingPolicyTest {
    public static void main(String[] args) {
        assertProfile(2, 4, 3, 12, 3, 1);
        assertProfile(4, 4, 3, 12, 3, 1);
        assertProfile(5, 4, 4, 16, 4, 2);
        assertProfile(7, 4, 4, 16, 4, 2);
        assertProfile(8, 5, 4, 20, 5, 2);
        assertProfile(10, 5, 4, 20, 5, 2);
        assertProfile(11, 5, 5, 25, 6, 3);
        assertProfile(15, 5, 5, 25, 6, 3);
        assertProfile(16, 6, 5, 30, 8, 3);
        assertProfile(20, 6, 5, 30, 8, 3);
        assertProfile(25, 6, 5, 30, 8, 3);
        check(V3ObeliskScalingPolicy.obeliskCount(1) == 0,
                "fewer than two players must not create obelisks");
        check(V3ObeliskPlacementPolicy.hasReasonableSpacing(
                        V3ObeliskPlacementPolicy.candidates(6)),
                "six deterministic obelisk anchors must be separated");
        System.out.println("V3ObeliskScalingPolicyTest PASS");
    }

    private static void assertProfile(int players, int obelisks, int health,
                                      int hits, int mobs, int cap) {
        V3ObeliskScalingPolicy.Profile profile =
                V3ObeliskScalingPolicy.profileForPlayers(players);
        check(profile.obeliskCount() == obelisks, "obelisks for " + players);
        check(profile.health() == health, "health for " + players);
        check(profile.requiredHits() == hits, "hits for " + players);
        check(profile.mobCount() == mobs, "mobs for " + players);
        check(profile.fireballCap() == cap, "fire cap for " + players);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
