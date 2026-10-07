import java.util.LinkedHashSet;
import java.util.Set;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;
import me.copimine.endevent.domain.RealitySplitBarrierCoveragePolicy;

public final class RealitySplitBarrierCoverageTest {
    public static void main(String[] args) {
        var required = RealitySplitBarrierPolicy.cells(4);
        Set<RealitySplitBarrierPolicy.Cell> installed = new LinkedHashSet<>(required);
        check(RealitySplitBarrierCoveragePolicy.missing(required, installed::contains).isEmpty(), "six full levels close every room");
        var gap = required.stream().filter(cell -> cell.level() == 5).findFirst().orElseThrow();
        installed.remove(gap);
        check(RealitySplitBarrierCoveragePolicy.missing(required, installed::contains).equals(Set.of(gap)), "one declined upper placement is still a real hole");
        Set<RealitySplitBarrierPolicy.Cell> naturalWalls = Set.of(gap);
        check(RealitySplitBarrierCoveragePolicy.missing(required, cell -> installed.contains(cell) || naturalWalls.contains(cell)).isEmpty(), "an existing full collision block may close a cell without replacement");
        var open = RealitySplitBarrierPolicy.cellsExcludingBoundaries(4, Set.of(0));
        check(RealitySplitBarrierCoveragePolicy.missing(open, installed::contains).stream().allMatch(cell -> cell.level() == 5), "an intentionally open passage is not an accidental coverage defect");
        System.out.println("RealitySplitBarrierCoverageTest OK");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
