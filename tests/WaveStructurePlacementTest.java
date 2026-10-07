import me.copimine.endevent.domain.ObeliskGeometryPolicy;
import me.copimine.endevent.domain.PortalCapturePolicy;
import me.copimine.endevent.domain.PortalPresentationPolicy;

public final class WaveStructurePlacementTest {
    public static void main(String[] args) {
        for (int layer = 0; layer < 5; layer++) {
            double height = ObeliskGeometryPolicy.visualHeightBlocks(layer);
            double centre = ObeliskGeometryPolicy.visualCenterY(layer);
            near(centre - height / 2, 0, "obelisk unit model floor");
            near(centre + height / 2, layer + 1, "obelisk crown");
        }
        check(ObeliskGeometryPolicy.VISUAL_WIDTH_BLOCKS >= 3,
                "visible model must cover the physical footprint");
        double priorMembrane = 10;
        for (int progress : new int[]{0, 1250, 2500, 3750}) {
            var state = new PortalCapturePolicy.PortalState(false, progress, progress, progress);
            var frame = PortalPresentationPolicy.frame(state, true, -1, progress);
            var solid = PortalPresentationPolicy.placement(frame, false, PortalPresentationPolicy.Layer.FRAME);
            var inner = PortalPresentationPolicy.placement(frame, false, PortalPresentationPolicy.Layer.INNER);
            near(solid.translateY() - solid.scale(), 0, "portal frame stays on floor during capture");
            near(solid.scale() * 2, 4.48, "frame does not falsely shrink capture boundary");
            near(inner.translateY() + inner.scale() * .25, 2.8, "membrane contracts towards its centre");
            check(inner.scale() < priorMembrane, "every quarter reduces membrane energy");
            priorMembrane = inner.scale();
        }
        check(PortalCapturePolicy.contains(0, 0, 0), "centre");
        check(PortalCapturePolicy.contains(2.5, 1.5, 0), "actual edge and jump tolerance");
        check(!PortalCapturePolicy.contains(2.5001, 0, 0), "outside edge");
        check(!PortalCapturePolicy.contains(0, 1.5001, 0), "wrong elevation");
        check(!PortalCapturePolicy.contains(Double.NaN, 0, 0), "invalid coordinate");
        System.out.println("WaveStructurePlacementTest OK");
    }
    static void near(double value, double expected, String message) {
        check(Math.abs(value - expected) < 1e-5, message + ": " + value);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
