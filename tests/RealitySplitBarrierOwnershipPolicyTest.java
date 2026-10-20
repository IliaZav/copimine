import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import me.copimine.endevent.domain.RealitySplitBarrierOwnershipPolicy;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy.Cell;

public final class RealitySplitBarrierOwnershipPolicyTest {
    public static void main(String[] args) {
        Cell sharedCell = new Cell(0, 1, 1);
        Cell unrelatedCell = new Cell(2, 3, 1);
        Map<Integer, Set<Cell>> boundaries = new LinkedHashMap<>();
        boundaries.put(0, Set.of(sharedCell));

        check(!RealitySplitBarrierOwnershipPolicy.isNeededByOtherBoundary(
                        boundaries, 0, sharedCell),
                "the boundary being opened must not keep its own cells closed");
        check(!RealitySplitBarrierOwnershipPolicy.isNeededByOtherBoundary(
                        boundaries, 0, unrelatedCell),
                "a cell absent from every boundary must not be retained");

        boundaries.put(1, Set.of(sharedCell));
        check(RealitySplitBarrierOwnershipPolicy.isNeededByOtherBoundary(
                        boundaries, 1, sharedCell),
                "a crossing cell must remain closed while another boundary owns it");
        System.out.println("RealitySplitBarrierOwnershipPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
