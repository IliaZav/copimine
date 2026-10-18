import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.Wave7EchoDuelCellPolicy;
import me.copimine.endevent.domain.Wave7EchoDuelCellBarrierPolicy;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;

public final class Wave7EchoDuelCellPolicyTest {
    public static void main(String[] args) {
        deterministicOwnerCellsPartitionOneRoom();
        sharedWallsRemainUntilBothAdjacentDuelsComplete();
        completedOwnersCanUseOnlyConnectedClearedCells();
        boundaryAndOwnerChecksFailClosed();
        invalidAssignmentsAreRejected();
        System.out.println("Wave7EchoDuelCellPolicyTest OK");
    }

    private static void sharedWallsRemainUntilBothAdjacentDuelsComplete() {
        List<UUID> owners = List.of(id(1), id(2), id(3));
        var barriers = Wave7EchoDuelCellBarrierPolicy.plan(1, 4, owners);
        check(barriers.size() == owners.size() - 1,
                "each neighboring pair gets one owner-specific shared wall");
        var first = barriers.get(0);
        check(first.lowerOwner().equals(id(1)) && first.upperOwner().equals(id(2)),
                "shared walls follow stable ordered owner cells");
        check(Wave7EchoDuelCellBarrierPolicy.remaining(barriers, Set.of(id(1))).equals(barriers),
                "a wall stays closed while its other owner still has a live Echo");
        check(Wave7EchoDuelCellBarrierPolicy.remaining(barriers, Set.of(id(1), id(2))).size() == 1,
                "a shared passage opens only after both adjacent owners complete");
        check(Wave7EchoDuelCellBarrierPolicy.remaining(barriers, Set.of(id(1), id(2), id(3))).isEmpty(),
                "all internal walls open after every duel in the room completes");

        var blocks = Wave7EchoDuelCellBarrierPolicy.cells(first, RealitySplitBarrierPolicy.HEIGHT);
        var floorPath = blocks.stream().filter(cell -> cell.level() == 1).toList();
        check(!floorPath.isEmpty() && blocks.size() == floorPath.size() * RealitySplitBarrierPolicy.HEIGHT,
                "the wall fills the supported vertical height");
        for (int index = 1; index < floorPath.size(); index++) {
            var previous = floorPath.get(index - 1);
            var current = floorPath.get(index);
            int manhattan = Math.abs(current.xOffset() - previous.xOffset())
                    + Math.abs(current.zOffset() - previous.zOffset());
            check(manhattan == 1, "rasterized owner wall has no diagonal corner gaps");
        }
        var activeWalls = Wave7EchoDuelCellBarrierPolicy.cells(barriers, Set.of(id(1)),
                RealitySplitBarrierPolicy.HEIGHT);
        check(activeWalls.containsAll(blocks), "unfinished neighbor keeps the entire shared wall active");
        check(!Wave7EchoDuelCellBarrierPolicy.cells(barriers, Set.of(id(1), id(2), id(3)),
                        RealitySplitBarrierPolicy.HEIGHT).isEmpty() == false,
                "fully completed Echo room has no remaining internal collision walls");
        expectRejected(() -> Wave7EchoDuelCellBarrierPolicy.cells(first, 9),
                "over-height wall is rejected");
    }

    private static void deterministicOwnerCellsPartitionOneRoom() {
        List<UUID> owners = List.of(id(5), id(2), id(4), id(1), id(3));
        var cells = Wave7EchoDuelCellPolicy.plan(1, 4, owners);
        var reversed = Wave7EchoDuelCellPolicy.plan(1, 4,
                List.of(id(3), id(1), id(4), id(2), id(5)));
        check(cells.equals(reversed), "owner cell assignment is stable across roster iteration order");
        check(cells.size() == owners.size(), "each Echo owner receives one cell");
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            check(cell.owner().equals(id(index + 1)), "cells are assigned in canonical owner order");
            check(cell.ordinal() == index && cell.ownerCount() == owners.size(),
                    "cell identity includes its stable position and room population");
            double x = Math.cos(cell.centerAngleRadians()) * 10.0D;
            double z = Math.sin(cell.centerAngleRadians()) * 10.0D;
            long containing = cells.stream().filter(candidate -> candidate.contains(x, z)).count();
            check(cell.contains(x, z) && containing == 1,
                    "each owner's center point belongs to exactly its own cell");
        }
    }

    private static void completedOwnersCanUseOnlyConnectedClearedCells() {
        var owners = List.of(id(1), id(2), id(3));
        var cells = Wave7EchoDuelCellPolicy.plan(1, 4, owners);
        var barriers = Wave7EchoDuelCellBarrierPolicy.plan(1, 4, owners);
        var completed = Set.of(id(1), id(2));
        var first = cells.get(0);
        var second = cells.get(1);
        var third = cells.get(2);
        double firstX = Math.cos(first.centerAngleRadians()) * 10.0D;
        double firstZ = Math.sin(first.centerAngleRadians()) * 10.0D;
        double secondX = Math.cos(second.centerAngleRadians()) * 10.0D;
        double secondZ = Math.sin(second.centerAngleRadians()) * 10.0D;
        double thirdX = Math.cos(third.centerAngleRadians()) * 10.0D;
        double thirdZ = Math.sin(third.centerAngleRadians()) * 10.0D;

        check(Wave7EchoDuelCellBarrierPolicy.allowsOwnerLocation(
                        cells, barriers, completed, id(1), firstX, firstZ),
                "a completed owner remains inside their own cell");
        check(Wave7EchoDuelCellBarrierPolicy.allowsOwnerLocation(
                        cells, barriers, completed, id(1), secondX, secondZ),
                "adjacent completed owners may use their opened shared passage");
        check(!Wave7EchoDuelCellBarrierPolicy.allowsOwnerLocation(
                        cells, barriers, completed, id(1), thirdX, thirdZ),
                "a completed owner cannot enter a cell whose owner still fights");
        check(!Wave7EchoDuelCellBarrierPolicy.allowsOwnerLocation(
                        cells, barriers, completed, id(3), firstX, firstZ),
                "an active owner cannot cross into cleared cells while their own duel is live");
        check(Wave7EchoDuelCellBarrierPolicy.allowsOwnerTransition(
                        cells, barriers, completed, id(1), firstX, firstZ, secondX, secondZ),
                "movement across a persisted open separator is admitted");
        check(!Wave7EchoDuelCellBarrierPolicy.allowsOwnerTransition(
                        cells, barriers, completed, id(1), firstX, firstZ, thirdX, thirdZ),
                "a completed owner cannot skip across a live neighbor's wall");
    }

    private static void boundaryAndOwnerChecksFailClosed() {
        var cells = Wave7EchoDuelCellPolicy.plan(0, 4, List.of(id(1), id(2)));
        var first = cells.get(0);
        check(Wave7EchoDuelCellPolicy.allowsOwnerAccess(first, first.owner()),
                "only the assigned owner may enter their Echo cell");
        check(!Wave7EchoDuelCellPolicy.allowsOwnerAccess(first, id(2)),
                "a different owner cannot enter this duel cell");
        check(!Wave7EchoDuelCellPolicy.allowsOwnerAccess(first, null),
                "missing owner identity cannot enter a duel cell");
        var second = cells.get(1);
        double fromX = Math.cos(first.centerAngleRadians()) * 10.0D;
        double fromZ = Math.sin(first.centerAngleRadians()) * 10.0D;
        double neighborX = Math.cos(second.centerAngleRadians()) * 10.0D;
        double neighborZ = Math.sin(second.centerAngleRadians()) * 10.0D;
        check(Wave7EchoDuelCellPolicy.allowsTransition(first, id(1),
                        fromX, fromZ, fromX + 0.1D, fromZ + 0.1D),
                "owner may move while both endpoints remain in their cell");
        check(!Wave7EchoDuelCellPolicy.allowsTransition(first, id(1),
                        fromX, fromZ, neighborX, neighborZ),
                "owner cannot cross into an adjacent live duel cell");
        check(!Wave7EchoDuelCellPolicy.allowsTransition(first, id(2),
                        fromX, fromZ, fromX, fromZ),
                "a neighboring owner cannot enter this cell");
        var wall = first.upperBoundary();
        double midX = (wall.innerX() + wall.outerX()) / 2.0D;
        double midZ = (wall.innerZ() + wall.outerZ()) / 2.0D;
        check(cells.stream().noneMatch(cell -> cell.contains(midX, midZ)),
                "an owner-cell separator belongs to neither neighboring combat zone");
        check(first.lowerBoundary().length() > 0 && first.upperBoundary().length() > 0,
                "cell exposes both physical separator segments for a later world adapter");
    }

    private static void invalidAssignmentsAreRejected() {
        expectRejected(() -> Wave7EchoDuelCellPolicy.plan(-1, 4, List.of(id(1))),
                "negative room rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.plan(4, 4, List.of(id(1))),
                "room outside chamber count rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.plan(0, 0, List.of(id(1))),
                "empty chamber count rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.plan(0, 4, List.of(id(1), id(1))),
                "duplicate duel owner rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.plan(0, 4, java.util.Arrays.asList(id(1), null)),
                "null owner rejected");
        expectRejected(() -> Wave7EchoDuelCellPolicy.plan(0, 4,
                        java.util.stream.IntStream.range(1, 22).mapToObj(Wave7EchoDuelCellPolicyTest::id).toList()),
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
