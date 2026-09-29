import me.copimine.endevent.domain.RealitySplitBarrierPolicy;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class RealitySplitBarrierPolicyTest {
    public static void main(String[] args) {
        check(RealitySplitBarrierPolicy.cells(2).size() > 0,
                "two-player Wave 7 must have a physical separator");
        check(RealitySplitBarrierPolicy.HEIGHT >= 5
                        && RealitySplitBarrierPolicy.HEIGHT <= 6,
                "Wave 7 room walls must be roughly five to six blocks high");
        check(RealitySplitBarrierPolicy.FINAL_SEAL_HEIGHT >= 8,
                "the later boss seal must retain its taller central containment");
        check(RealitySplitBarrierPolicy.MIN_RADIUS <= 1.5D,
                "Wave 7 walls must close the central Core bypass");
        check(RealitySplitBarrierPolicy.MAX_RADIUS >= 30.0D,
                "Wave 7 walls must reach the arena edge instead of leaving an outer bypass");
        check(RealitySplitBarrierPolicy.cells(4).size()
                        <= RealitySplitBarrierPolicy.MAX_CELLS,
                "four-room separator must remain bounded");
        check(RealitySplitBarrierPolicy.finalSealCells(4).stream()
                        .anyMatch(cell -> cell.xOffset() == 0 && cell.zOffset() == 0
                                && cell.level() == RealitySplitBarrierPolicy.FINAL_SEAL_FIRST_COVERED_LEVEL),
                "final seal must cover the first block above the boss clearance");
        check(RealitySplitBarrierPolicy.finalSealCells(4).stream()
                        .noneMatch(cell -> cell.xOffset() == 0 && cell.zOffset() == 0
                                && cell.level() <= RealitySplitBarrierPolicy.FINAL_SEAL_CORE_CLEARANCE_LEVELS),
                "final seal must leave the Core and boss feet clearance open");
        check(RealitySplitBarrierPolicy.FINAL_SEAL_CORE_CLEARANCE_LEVELS == 1,
                "final seal must leave only the Core block below the boss open");
        check(RealitySplitBarrierPolicy.finalSealCells(4).stream()
                        .anyMatch(cell -> cell.xOffset() == 0 && cell.zOffset() == 0
                                && cell.level() == 2),
                "final seal must cover the first block above the boss standing level");
        check(RealitySplitBarrierPolicy.finalSealCells(4).stream()
                        .anyMatch(cell -> cell.xOffset() != 0 && cell.zOffset() != 0
                                && cell.level() == RealitySplitBarrierPolicy.FINAL_SEAL_HEIGHT),
                "the later boss seal must preserve the full-height radial containment");
        long perimeterColumns = RealitySplitBarrierPolicy.finalSealCells(4).stream()
                .filter(cell -> cell.level() == 1)
                .count();
        check(RealitySplitBarrierPolicy.finalSealCells(4).size()
                        == perimeterColumns * RealitySplitBarrierPolicy.FINAL_SEAL_HEIGHT
                                + RealitySplitBarrierPolicy.FINAL_SEAL_HEIGHT
                                - RealitySplitBarrierPolicy.FINAL_SEAL_CORE_CLEARANCE_LEVELS,
                "final seal core cover must add exactly the clearance-safe central column");
        check(RealitySplitBarrierPolicy.WALL_HALF_WIDTH == 0,
                "room separator must be one block wide");
        check(RealitySplitBarrierPolicy.VISUAL_CELL_SCALE >= 0.99F,
                "Wave 7 visual wall must fill each collision cell instead of leaving pillar gaps");
        check(RealitySplitBarrierPolicy.VISUAL_CELL_TRANSLATION == -0.5F,
                "Wave 7 visual wall must be centred on its collision cell");
        check(RealitySplitBarrierPolicy.cellsForBoundary(0, 4).size()
                        <= (int) (2 * RealitySplitBarrierPolicy.MAX_RADIUS)
                                * RealitySplitBarrierPolicy.HEIGHT,
                "room separator must not expand into a multi-block wall");
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
