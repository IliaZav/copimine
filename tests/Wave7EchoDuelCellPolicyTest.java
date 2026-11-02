import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.Wave7EchoDuelCellPolicy;

/**
 * ECHO-002/ECHO-005: the personal Echo is ONE shared combat region.
 *
 * <p>Owners keep a stable lane identity (spawn point, actor lookup key) but no
 * longer own an exclusive walled cell. Pair isolation is the server owner-pair
 * rule, so this test only checks geometry: stable lanes, an exactly covered
 * ring, region-wide movement for admitted owners, and fail-closed boundaries.
 */
public final class Wave7EchoDuelCellPolicyTest {
    public static void main(String[] args) {
        lanesAreStableAndCoverOneRegion();
        everyAdmittedOwnerMayUseTheWholeRegion();
        regionBoundariesFailClosed();
        invalidAssignmentsAreRejected();
        System.out.println("Wave7EchoDuelCellPolicyTest OK");
    }

    private static void lanesAreStableAndCoverOneRegion() {
        List<UUID> owners = List.of(id(5), id(2), id(4), id(1), id(3));
        var cells = Wave7EchoDuelCellPolicy.sharedRegionPlan(1, owners.size(), owners);
        var reversed = Wave7EchoDuelCellPolicy.sharedRegionPlan(1, owners.size(),
                List.of(id(3), id(1), id(4), id(2), id(5)));
        check(cells.equals(reversed), "lane assignment is stable across roster iteration order");
        check(cells.size() == owners.size(), "each Echo owner receives one lane");
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            check(cell.owner().equals(id(index + 1)), "lanes follow canonical owner order");
            check(cell.ordinal() == index && cell.ownerCount() == owners.size(),
                    "lane identity includes its stable position and roster size");
            double x = Math.cos(cell.centerAngleRadians()) * 10.0D;
            double z = Math.sin(cell.centerAngleRadians()) * 10.0D;
            long containing = cells.stream().filter(candidate -> candidate.contains(x, z)).count();
            check(cell.contains(x, z) && containing == 1,
                    "each owner's start point belongs to exactly their own lane");
        }
        for (int index = 0; index + 1 < cells.size(); index++) {
            double lowerEdge = cells.get(index).centerAngleRadians() + cells.get(index).halfWidthRadians();
            double upperEdge = cells.get(index + 1).centerAngleRadians() - cells.get(index + 1).halfWidthRadians();
            check(Math.abs(lowerEdge - upperEdge) < 1.0E-9D,
                    "neighbouring lanes share exactly one edge; no dead corridor between duels");
        }
        // A point just inside a shared edge belongs to the region. The exact
        // edge is a measure-zero line, so a body standing on it is the only
        // case that may be refused.
        var first = cells.get(0);
        double edge = first.centerAngleRadians() + first.halfWidthRadians() - 1.0E-4D;
        double edgeX = Math.cos(edge) * 10.0D;
        double edgeZ = Math.sin(edge) * 10.0D;
        check(Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, id(1), edgeX, edgeZ),
                "the lane edge belongs to the shared region");
    }

    private static void everyAdmittedOwnerMayUseTheWholeRegion() {
        var owners = List.of(id(1), id(2), id(3));
        var cells = Wave7EchoDuelCellPolicy.sharedRegionPlan(1, owners.size(), owners);
        for (var lane : cells) {
            for (var other : cells) {
                double x = Math.cos(other.centerAngleRadians()) * 10.0D;
                double z = Math.sin(other.centerAngleRadians()) * 10.0D;
                check(Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, lane.owner(), x, z),
                        "an admitted owner may cross into a neighbour's lane");
            }
        }
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, id(9), 10.0D, 0.0D),
                "a player outside the admitted roster has no region access");
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, null, 10.0D, 0.0D),
                "missing owner identity has no region access");
    }

    private static void regionBoundariesFailClosed() {
        var cells = Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 2, List.of(id(1), id(2)));
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, id(1), 0.0D, 0.0D),
                "the Core column is outside the shared combat region");
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, id(1), 26.0D, 0.0D),
                "outside the authored ring is outside the shared region");
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, id(1),
                        Double.NaN, 0.0D),
                "non-finite positions are rejected");
        check(!Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(List.of(), id(1), 10.0D, 0.0D),
                "an empty plan admits nobody");
        var secondLane = cells.get(1);
        check(Wave7EchoDuelCellPolicy.allowsSharedRegionAccess(cells, id(2),
                        Math.cos(secondLane.centerAngleRadians()) * 10.0D,
                        Math.sin(secondLane.centerAngleRadians()) * 10.0D),
                "the second owner still uses the whole region");
        Set<UUID> seen = Set.copyOf(ownersOf(cells));
        check(seen.equals(Set.of(id(1), id(2))), "the plan carries exactly the admitted owners");
    }

    private static List<UUID> ownersOf(List<Wave7EchoDuelCellPolicy.Cell> cells) {
        return cells.stream().map(Wave7EchoDuelCellPolicy.Cell::owner).toList();
    }

    private static void invalidAssignmentsAreRejected() {
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(4, 1, List.of(id(1))),
                "room outside chamber count rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 0, List.of()),
                "empty roster rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 2, List.of(id(1))),
                "roster smaller than the declared size rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 2, List.of(id(1), id(1))),
                "duplicate duel owner rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 2,
                        java.util.Arrays.asList(id(1), null)),
                "null owner rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.sharedRegionPlan(0, 21,
                        java.util.stream.IntStream.range(1, 22)
                                .mapToObj(Wave7EchoDuelCellPolicyTest::id).toList()),
                "oversized owner set rejected");
    }

    private static UUID id(long value) { return new UUID(0L, value); }

    private static void expectRejected(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException expected) { }
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
