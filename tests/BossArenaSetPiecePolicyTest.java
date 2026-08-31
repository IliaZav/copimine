import me.copimine.endevent.domain.BossArenaSetPiecePolicy;

import java.util.List;

public final class BossArenaSetPiecePolicyTest {
    public static void main(String[] args) {
        BossArenaSetPiecePolicy.Bounds bounds = new BossArenaSetPiecePolicy.Bounds(-12, 12, 60, 72, -12, 12);
        BossArenaSetPiecePolicy.Point core = new BossArenaSetPiecePolicy.Point(0, 64, 0);

        for (BossArenaSetPiecePolicy.Scene scene : BossArenaSetPiecePolicy.Scene.values()) {
            BossArenaSetPiecePolicy.Frame frame = BossArenaSetPiecePolicy.frame(scene, bounds, core, 0);
            BossArenaSetPiecePolicy.Frame clamped = BossArenaSetPiecePolicy.frame(scene, bounds, core, -25);
            require(frame.equals(clamped), scene + " elapsed ticks must clamp at zero");
            require(frame.scene() == scene, scene + " frame must retain its scene");
            require(!frame.rings().isEmpty(), scene + " must include rings");
            require(!frame.pillars().isEmpty(), scene + " must include pillars");
            require(!frame.cells().isEmpty(), scene + " must include cells");
            require(frame.allPointsInside(bounds), scene + " geometry must stay inside the arena");
            require(frame.allPointsInside(bounds), scene + " geometry must stay inside the bounds");
            require(frame.finished() == (scene == BossArenaSetPiecePolicy.Scene.BOSS_FINISH),
                    scene + " finish flag must be deterministic");
            requireThrows(UnsupportedOperationException.class,
                    () -> frame.rings().add(frame.rings().get(0)),
                    scene + " rings must be immutable");
        }

        BossArenaSetPiecePolicy.Frame escaped = new BossArenaSetPiecePolicy.Frame(
                BossArenaSetPiecePolicy.Scene.FINAL_DRAIN,
                List.of(new BossArenaSetPiecePolicy.Ring("outer", List.of(
                        new BossArenaSetPiecePolicy.Point(0, 64, 0),
                        new BossArenaSetPiecePolicy.Point(20, 64, 0)))),
                List.of(new BossArenaSetPiecePolicy.Pillar("pillar", List.of(
                        new BossArenaSetPiecePolicy.Point(0, 65, 0)))),
                List.of(new BossArenaSetPiecePolicy.Cell("cell", List.of(
                        new BossArenaSetPiecePolicy.Point(0, 64, 1)))),
                false);
        require(!escaped.allPointsInside(bounds), "allPointsInside must inspect every geometry point");
        System.out.println("BossArenaSetPiecePolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrows(Class<? extends Throwable> expected, ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + " (unexpected " + thrown.getClass().getSimpleName() + ")", thrown);
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
