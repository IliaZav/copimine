import me.copimine.endevent.domain.GateOpeningPlan;

import java.util.List;

public final class GateOpeningPlanTest {
    public static void main(String[] args) {
        testLayersAreInclusiveAndDescending();
        testLayersExposeEveryCoordinateForSelectionPreview();
        testWorldAndVolumeAreBounded();
        System.out.println("GateOpeningPlanTest OK");
    }

    private static void testLayersAreInclusiveAndDescending() {
        GateOpeningPlan plan = GateOpeningPlan.from(
                new GateOpeningPlan.Point("CopiMine_the_end", 3, 4, 7),
                new GateOpeningPlan.Point("CopiMine_the_end", 4, 6, 8),
                12);

        check(plan.volume() == 12, "gate volume must include both endpoints");
        check(plan.layersDescending().stream().map(GateOpeningPlan.Layer::y).toList()
                        .equals(List.of(6, 5, 4)),
                "gate layers must be removed from greatest Y to least Y");
        for (GateOpeningPlan.Layer layer : plan.layersDescending()) {
            check(layer.blocks().equals(List.of(
                            new GateOpeningPlan.Point("CopiMine_the_end", 3, layer.y(), 7),
                            new GateOpeningPlan.Point("CopiMine_the_end", 3, layer.y(), 8),
                            new GateOpeningPlan.Point("CopiMine_the_end", 4, layer.y(), 7),
                            new GateOpeningPlan.Point("CopiMine_the_end", 4, layer.y(), 8))),
                    "each layer must have deterministic X/Z order and remain inside the cuboid");
        }
    }

    private static void testWorldAndVolumeAreBounded() {
        expectIllegalArgument(() -> GateOpeningPlan.from(
                        new GateOpeningPlan.Point("world_a", 0, 0, 0),
                        new GateOpeningPlan.Point("world_b", 0, 0, 0), 1),
                "cross-world gate points must be rejected");
                expectIllegalArgument(() -> GateOpeningPlan.from(
                        new GateOpeningPlan.Point("world", 0, 0, 0),
                        new GateOpeningPlan.Point("world", 2, 1, 1), 11),
                "gate volume above the configured ceiling must be rejected");
    }

    private static void testLayersExposeEveryCoordinateForSelectionPreview() {
        GateOpeningPlan plan = GateOpeningPlan.from(
                new GateOpeningPlan.Point("world", 1, 10, 1),
                new GateOpeningPlan.Point("world", 3, 11, 3),
                18);

        long coordinates = plan.layersDescending().stream()
                .flatMap(layer -> layer.blocks().stream())
                .count();
        check(coordinates == plan.volume(),
                "selection preview must receive every coordinate the staged opening processes");
        check(plan.layersDescending().stream()
                        .flatMap(layer -> layer.blocks().stream())
                        .anyMatch(point -> point.x() == 2 && point.y() == 10 && point.z() == 2),
                "selection preview must include an interior block, not only the cuboid boundary");
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
