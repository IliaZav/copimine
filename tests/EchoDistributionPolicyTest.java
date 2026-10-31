import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.EchoDistributionPolicy;

/**
 * ECHO-001 / ECHO-002: every admitted participant gets exactly one real copy
 * and fights immediately. Small groups share the ordinary region; from ten
 * participants the roster splits between ordinary and limbo with a difference
 * of at most one.
 */
public final class EchoDistributionPolicyTest {
    public static void main(String[] args) {
        smallGroupsAllStayInTheOrdinaryRegion();
        splitMatchesTheApprovedTable();
        splitIsBalancedWithinOneForEveryRosterSize();
        assignmentIsStableAndDeterministic();
        everyAdmittedPlayerGetsExactlyOneCopy();
        rejoinNeverMovesAParticipantToAnEasierRealm();
        System.out.println("EchoDistributionPolicyTest OK");
    }

    private static void smallGroupsAllStayInTheOrdinaryRegion() {
        for (int players = 2; players <= 9; players++) {
            var plan = EchoDistributionPolicy.assign(sample(players));
            int ordinary = plan.playersIn(EchoDistributionPolicy.Region.ORDINARY);
            int limbo = plan.playersIn(EchoDistributionPolicy.Region.LIMBO);
            check(ordinary == players, players + " participants all fight in the ordinary region");
            check(limbo == 0, "the limbo region stays unused below ten participants");
            check(plan.totalCopies() == players, "every participant has exactly one copy");
        }
    }

    private static void splitMatchesTheApprovedTable() {
        checkSplit(10, 5, 5);
        checkSplit(11, 6, 5);
        checkSplit(15, 8, 7);
        checkSplit(20, 10, 10);
    }

    private static void checkSplit(int players, int ordinary, int limbo) {
        var plan = EchoDistributionPolicy.assign(sample(players));
        check(plan.playersIn(EchoDistributionPolicy.Region.ORDINARY) == ordinary,
                players + " participants put " + ordinary + " in the ordinary region");
        check(plan.playersIn(EchoDistributionPolicy.Region.LIMBO) == limbo,
                players + " participants put " + limbo + " in the limbo region");
        check(plan.totalCopies() == players, "a split roster still has one copy per participant");
    }

    private static void splitIsBalancedWithinOneForEveryRosterSize() {
        for (int players = 2; players <= 20; players++) {
            var plan = EchoDistributionPolicy.assign(sample(players));
            int ordinary = plan.playersIn(EchoDistributionPolicy.Region.ORDINARY);
            int limbo = plan.playersIn(EchoDistributionPolicy.Region.LIMBO);
            check(ordinary + limbo == players, "nobody is dropped from the roster");
            if (players >= 10) {
                // The balance requirement applies to the split, not to the
                // small-group case where the limbo region is deliberately unused.
                check(Math.abs(ordinary - limbo) <= 1,
                        "the split differs by at most one at " + players + " participants");
                check(ordinary > 0 && limbo > 0, "both regions are actually used from ten players");
            }
        }
    }

    private static void assignmentIsStableAndDeterministic() {
        var roster = sample(11);
        var first = EchoDistributionPolicy.assign(roster);
        // Same roster in a different arrival order must give the same split.
        var shuffled = new ArrayList<>(roster);
        java.util.Collections.rotate(shuffled, 4);
        var second = EchoDistributionPolicy.assign(shuffled);
        check(first.regionOf(roster.get(0)) == second.regionOf(roster.get(0)),
                "assignment does not depend on arrival order");
        for (UUID id : roster) {
            check(first.regionOf(id) == second.regionOf(id),
                    "each participant keeps the same region for an identical roster");
        }
    }

    private static void everyAdmittedPlayerGetsExactlyOneCopy() {
        var roster = sample(20);
        var plan = EchoDistributionPolicy.assign(roster);
        check(plan.totalCopies() == 20, "twenty participants get twenty copies");
        Set<UUID> owners = new HashSet<>();
        for (UUID id : roster) {
            check(plan.hasCopyFor(id), "every admitted participant owns a copy");
            owners.add(plan.ownerOf(id));
        }
        check(owners.size() == 20, "no copy is shared between two participants");
        check(owners.equals(new HashSet<>(roster)), "every copy belongs to a real participant");
    }

    private static void rejoinNeverMovesAParticipantToAnEasierRealm() {
        var roster = sample(11);
        var plan = EchoDistributionPolicy.assign(roster);
        UUID participant = roster.get(0);
        var region = plan.regionOf(participant);
        // Reconciling the same participant again keeps the frozen assignment.
        var again = plan.withReturning(participant);
        check(again.regionOf(participant) == region,
                "a death or rejoin does not move a participant to an easier realm");
        check(again.totalCopies() == plan.totalCopies(),
                "a rejoin does not add a second copy");
    }

    private static List<UUID> sample(int count) {
        List<UUID> roster = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Stable, distinguishable ids that are not in sorted order.
            roster.add(UUID.nameUUIDFromBytes(("participant-" + (count - i)).getBytes()));
        }
        return roster;
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}