import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import me.copimine.endevent.domain.RealitySplitBarrierPolicy;

public final class RealitySplitBarrierRecoveryTest {
    public static void main(String[] args) {
        List<RealitySplitBarrierPolicy.Cell> all = RealitySplitBarrierPolicy.cells(4);
        List<RealitySplitBarrierPolicy.Cell> boundaryZero =
                RealitySplitBarrierPolicy.cellsForBoundary(0, 4);
        List<RealitySplitBarrierPolicy.Cell> rebuilt = invokeClosedCells(4, Set.of(0));

        check(rebuilt.size() < all.size(),
                "rebuild must not recreate an already open Wave 7 boundary");
        check(rebuilt.size() == all.size() - boundaryZero.size(),
                "rebuild must retain every closed boundary and only omit the open one");
        check(rebuilt.stream().noneMatch(boundaryZero::contains),
                "rebuild must leave every cell of an open boundary passable");
        check(rebuilt.containsAll(RealitySplitBarrierPolicy.cellsForBoundary(1, 4)),
                "rebuild must retain a different closed boundary");
        System.out.println("RealitySplitBarrierRecoveryTest OK");
    }

    @SuppressWarnings("unchecked")
    private static List<RealitySplitBarrierPolicy.Cell> invokeClosedCells(
            int chamberCount, Set<Integer> openBoundaries) {
        try {
            Method method = RealitySplitBarrierPolicy.class.getMethod(
                    "cellsExcludingBoundaries", int.class, Set.class);
            return (List<RealitySplitBarrierPolicy.Cell>) method.invoke(
                    null, chamberCount, openBoundaries);
        } catch (NoSuchMethodException error) {
            throw new AssertionError(
                    "rebuild policy must expose cellsExcludingBoundaries", error);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new AssertionError("rebuild policy invocation failed", error);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
