import me.copimine.endevent.domain.RealitySplitBarrierPolicy;

public final class RealitySplitBarrierPolicyTest {
    public static void main(String[] args) {
        check(RealitySplitBarrierPolicy.cells(2).size() > 0,
                "two-player Wave 7 must have a physical separator");
        check(RealitySplitBarrierPolicy.cells(4).size()
                        <= RealitySplitBarrierPolicy.MAX_CELLS,
                "four-room separator must remain bounded");
        check(RealitySplitBarrierPolicy.WALL_HALF_WIDTH == 1,
                "room separator must have a collision-safe three-block cross-section");
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
        System.out.println("RealitySplitBarrierPolicyTest OK");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
