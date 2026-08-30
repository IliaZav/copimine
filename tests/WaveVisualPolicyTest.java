import me.copimine.endevent.domain.WaveVisualPolicy;

public final class WaveVisualPolicyTest {
    public static void main(String[] args) {
        WaveVisualPolicy.Frame start = WaveVisualPolicy.frame(1, 0, false);
        WaveVisualPolicy.Frame middle = WaveVisualPolicy.frame(1, WaveVisualPolicy.OPENING_TICKS / 2, false);
        WaveVisualPolicy.Frame end = WaveVisualPolicy.frame(1, WaveVisualPolicy.OPENING_TICKS, false);

        check(start.outerRadius() < middle.outerRadius(), "wavefront must expand during the first half");
        check(middle.outerRadius() < end.outerRadius(), "wavefront must expand during the second half");
        check(end.outerRadius() <= WaveVisualPolicy.MAX_RADIUS_BLOCKS,
                "wavefront must stay inside the combat radius");
        check(end.outerPoints() <= WaveVisualPolicy.MAX_RING_POINTS,
                "wavefront particle point count must stay bounded");
        check(start.floorY() == 0.08D, "wavefront must stay just above the floor");
        check(WaveVisualPolicy.active(0), "wavefront must be active at tick zero");
        check(!WaveVisualPolicy.active(WaveVisualPolicy.OPENING_TICKS),
                "wavefront task must stop after the opening frame");

        WaveVisualPolicy.Frame safe = WaveVisualPolicy.frame(999, -10, false);
        check(safe.outerRadius() >= 0.0D && safe.outerPoints() >= 1,
                "invalid inputs must be clamped to a safe frame");
        System.out.println("WaveVisualPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
