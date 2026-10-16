import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.copimine.endevent.domain.RealitySplitBarrierMutationPolicy;

public final class RealitySplitBarrierMutationPolicyTest {
    public static void main(String[] args) {
        appliesEveryCellWhenAllWritesSucceed();
        restoresEveryAttemptedCellWhenOneWriteFails();
        restoresEveryCellWhenVisualCommitFails();
        reportsWhenRollbackCannotRestoreEveryCell();
        System.out.println("RealitySplitBarrierMutationPolicyTest OK");
    }

    private static void appliesEveryCellWhenAllWritesSucceed() {
        Map<String, String> world = world();
        var result = RealitySplitBarrierMutationPolicy.applyAtomically(
                List.of("west", "center", "east"), world::get,
                cell -> {
                    world.put(cell, "minecraft:barrier");
                    return true;
                },
                (cell, before) -> {
                    world.put(cell, before);
                    return before.equals(world.get(cell));
                });

        check(result.committed(), "all cells must commit when every write succeeds");
        check(result.rollbackComplete(), "a committed operation has no rollback work");
        check(world.equals(Map.of("west", "minecraft:barrier", "center", "minecraft:barrier",
                        "east", "minecraft:barrier")),
                "a committed wall must cover every planned cell");
    }

    private static void restoresEveryAttemptedCellWhenOneWriteFails() {
        Map<String, String> world = world();
        var result = RealitySplitBarrierMutationPolicy.applyAtomically(
                List.of("west", "center", "east"), world::get,
                cell -> {
                    world.put(cell, "minecraft:air");
                    return !cell.equals("center");
                },
                (cell, before) -> {
                    world.put(cell, before);
                    return before.equals(world.get(cell));
                });

        check(!result.committed(), "a failed cell must reject the whole opening operation");
        check(result.rollbackComplete(), "a recoverable write failure must roll back cleanly");
        check(world.equals(Map.of("west", "minecraft:barrier", "center", "minecraft:barrier",
                        "east", "minecraft:barrier")),
                "partial opening must restore every attempted cell to its prior barrier state");
    }

    private static void reportsWhenRollbackCannotRestoreEveryCell() {
        Map<String, String> world = world();
        var result = RealitySplitBarrierMutationPolicy.applyAtomically(
                List.of("west", "center", "east"), world::get,
                cell -> {
                    world.put(cell, "minecraft:air");
                    return !cell.equals("center");
                },
                (cell, before) -> {
                    if (cell.equals("center")) {
                        return false;
                    }
                    world.put(cell, before);
                    return before.equals(world.get(cell));
                });

        check(!result.committed(), "a failed cell must not commit the operation");
        check(!result.rollbackComplete(), "failed rollback must remain an explicit recovery error");
        check(result.failedCells().contains("center"),
                "the cell that could not be restored must be identified");
        check(world.get("west").equals("minecraft:barrier"),
                "successful rollback cells must still be restored");
        check(world.get("center").equals("minecraft:air"),
                "the simulated failed rollback must remain visible to recovery");
    }

    private static void restoresEveryCellWhenVisualCommitFails() {
        Map<String, String> world = world();
        var result = RealitySplitBarrierMutationPolicy.applyAtomically(
                List.of("west", "center", "east"), world::get,
                cell -> {
                    world.put(cell, "minecraft:air");
                    return true;
                },
                (cell, before) -> {
                    world.put(cell, before);
                    return before.equals(world.get(cell));
                },
                () -> false);

        check(!result.committed(), "a failed visual commit must reject the wall opening");
        check(result.rollbackComplete(), "visual failure must roll all physical cells back");
        check(world.equals(Map.of("west", "minecraft:barrier", "center", "minecraft:barrier",
                        "east", "minecraft:barrier")),
                "a visual removal failure must never leave the boundary open");
    }

    private static Map<String, String> world() {
        Map<String, String> world = new LinkedHashMap<>();
        world.put("west", "minecraft:barrier");
        world.put("center", "minecraft:barrier");
        world.put("east", "minecraft:barrier");
        return world;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
