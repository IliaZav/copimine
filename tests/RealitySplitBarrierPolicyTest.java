import me.copimine.endevent.domain.RealitySplitBarrierPolicy;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class RealitySplitBarrierPolicyTest {
    public static void main(String[] args) {
        check(RealitySplitBarrierPolicy.cells(2).size() > 0,
                "two-player Wave 7 must have a physical separator");
        check(RealitySplitBarrierPolicy.HEIGHT >= 5,
                "Wave 7 room walls must be visibly taller than a player jump");
        check(RealitySplitBarrierPolicy.MIN_RADIUS <= 1.5D,
                "Wave 7 walls must close the central Core bypass");
        check(RealitySplitBarrierPolicy.MAX_RADIUS >= 20.0D,
                "Wave 7 walls must reach the arena edge instead of leaving an outer bypass");
        check(RealitySplitBarrierPolicy.cells(4).size()
                        <= RealitySplitBarrierPolicy.MAX_CELLS,
                "four-room separator must remain bounded");
        check(RealitySplitBarrierPolicy.WALL_HALF_WIDTH == 1,
                "room separator must have a collision-safe three-block cross-section");
        check(RealitySplitBarrierPolicy.VISUAL_CELL_SCALE >= 0.99F,
                "Wave 7 visual wall must fill each collision cell instead of leaving pillar gaps");
        check(RealitySplitBarrierPolicy.VISUAL_CELL_TRANSLATION == -0.5F,
                "Wave 7 visual wall must be centred on its collision cell");
        check(RealitySplitBarrierPolicy.cellsForBoundary(0, 4).size()
                        > 13 * RealitySplitBarrierPolicy.HEIGHT,
                "diagonal room separator must fill its cross-section instead of a one-cell chain");
        check(RealitySplitBarrierPolicy.cells(4).stream()
                        .allMatch(cell -> cell.level() >= 1
                                && cell.level() <= RealitySplitBarrierPolicy.HEIGHT),
                "wall cells must use the configured height");
        check(RealitySplitBarrierPolicy.cells(4).stream()
                        .allMatch(cell -> Math.hypot(cell.xOffset(), cell.zOffset())
                                > RealitySplitBarrierPolicy.MIN_RADIUS),
                "barriers must not touch the Core");
        check(RealitySplitBarrierPolicy.cells(4).stream()
                        .allMatch(cell -> Math.hypot(cell.xOffset(), cell.zOffset())
                                < RealitySplitBarrierPolicy.MAX_RADIUS),
                "barriers must stay inside the arena");
        check(RealitySplitBarrierPolicy.boundaryForPair(0, 1, 4) >= 0,
                "adjacent completed rooms must map to a gate");
        check(RealitySplitBarrierPolicy.boundaryForPair(0, 2, 4) < 0,
                "diagonal rooms must not remove an unrelated wall");
        check(RealitySplitBarrierPolicy.boundaryForPair(0, 1, 2) == 0,
                "two rooms use one diameter gate");
        check(RealitySplitBarrierPolicy.cellsForBoundary(0, 2).size()
                        == RealitySplitBarrierPolicy.cells(2).size(),
                "the two-room diameter must be one complete boundary");
        check(!sectorsConnectWhileEveryBoundaryIsClosed(4, 0, 1),
                "four Wave 7 rooms must not leak through the Core or arena edge");
        System.out.println("RealitySplitBarrierPolicyTest OK");
    }

    private static boolean sectorsConnectWhileEveryBoundaryIsClosed(
            int chamberCount, int firstChamber, int secondChamber) {
        Set<String> blocked = new HashSet<>();
        for (RealitySplitBarrierPolicy.Cell cell : RealitySplitBarrierPolicy.cells(chamberCount)) {
            if (cell.level() == 1) {
                blocked.add(cell.xOffset() + ":" + cell.zOffset());
            }
        }
        // The Core itself is a solid block, so an actual player cannot use its
        // cell to cross a radial barrier.
        blocked.add("0:0");
        int[] start = chamberPoint(firstChamber, chamberCount);
        int[] goal = chamberPoint(secondChamber, chamberCount);
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        frontier.add(start);
        seen.add(start[0] + ":" + start[1]);
        while (!frontier.isEmpty()) {
            int[] point = frontier.removeFirst();
            if (point[0] == goal[0] && point[1] == goal[1]) {
                return true;
            }
            for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nextX = point[0] + direction[0];
                int nextZ = point[1] + direction[1];
                if (Math.abs(nextX) > 20 || Math.abs(nextZ) > 20) {
                    continue;
                }
                String key = nextX + ":" + nextZ;
                if (!blocked.contains(key) && seen.add(key)) {
                    frontier.addLast(new int[] {nextX, nextZ});
                }
            }
        }
        return false;
    }

    private static int[] chamberPoint(int chamber, int chamberCount) {
        double angle = -Math.PI / 2.0D + (Math.PI * 2.0D * chamber / chamberCount);
        return new int[] {
                (int) Math.round(Math.cos(angle) * 10.0D),
                (int) Math.round(Math.sin(angle) * 10.0D)
        };
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
