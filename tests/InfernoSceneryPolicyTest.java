import me.copimine.endevent.domain.InfernoSceneryPolicy;
import me.copimine.endevent.domain.InfernoSceneryPolicy.Layer;

import java.util.List;

/**
 * N23-P1-09: the authored Phase 4 (OVERLOAD) overhead infernal scenery.
 *
 * <p>V5.1 requires Phase 4 to be an infernal <b>world</b> with vertical drama:
 * a sky vortex, two counter-drifting ember storms at different heights, fire
 * arches on the arena rim, an overhead energy cloud and one legible fire front.
 * These checks pin the pure geometry contract the runtime and the future signed
 * cast packet both depend on: a bounded, monotonic per-frame point budget, an
 * LOD ladder that drops secondary layers before the silhouette, a deterministic
 * seeded orbit that never allocates per frame, and per-layer geometry that can
 * never exceed its own authored point count.</p>
 */
public final class InfernoSceneryPolicyTest {

    public static void main(String[] args) {
        budgetIsBoundedAndMonotonic();
        lodLadderKeepsTheSilhouetteFirst();
        perLayerPointsAreBoundedByTheLayer();
        frameNeverExceedsThePartyBudget();
        orbitIsDeterministicAndFinite();
        layerPointsAreBoundedAndEmptyWhenDropped();
        describeIsStable();
        System.out.println("InfernoSceneryPolicyTest OK");
    }

    private static void budgetIsBoundedAndMonotonic() {
        int previous = 0;
        for (int players = 1; players <= 64; players++) {
            int budget = InfernoSceneryPolicy.budgetFor(players);
            check(budget >= InfernoSceneryPolicy.BUDGET_MIN,
                    "even a solo party can render the floor of the budget");
            check(budget <= InfernoSceneryPolicy.BUDGET_CAP,
                    "the scenery budget never exceeds the authored cap");
            check(budget >= previous, "the budget never shrinks as the party grows");
            previous = budget;
        }
        check(InfernoSceneryPolicy.budgetFor(1) == InfernoSceneryPolicy.BUDGET_MIN,
                "a solo party gets exactly the authored floor");
        check(InfernoSceneryPolicy.budgetFor(0) == InfernoSceneryPolicy.budgetFor(1),
                "an empty party is clamped to a solo party");
        check(InfernoSceneryPolicy.budgetFor(Integer.MAX_VALUE) == InfernoSceneryPolicy.BUDGET_CAP,
                "a huge party is clamped to the cap, never beyond");
        check(InfernoSceneryPolicy.BUDGET_CAP <= 128,
                "the whole scene stays cheap enough for a server-side emitter");
    }

    private static void lodLadderKeepsTheSilhouetteFirst() {
        for (Layer layer : Layer.values()) {
            check(InfernoSceneryPolicy.drawsAt(layer, InfernoSceneryPolicy.LOD_FULL),
                    "every authored layer draws at full detail");
        }
        check(InfernoSceneryPolicy.drawsAt(Layer.SKY_VORTEX, InfernoSceneryPolicy.LOD_SILHOUETTE),
                "the sky vortex silhouette survives the coarsest LOD");
        check(InfernoSceneryPolicy.drawsAt(Layer.FIRE_FRONT, InfernoSceneryPolicy.LOD_SILHOUETTE),
                "the one legible fire front survives the coarsest LOD");
        check(!InfernoSceneryPolicy.drawsAt(Layer.FIRE_ARCH, InfernoSceneryPolicy.LOD_SILHOUETTE),
                "the rim arches are the first thing dropped");
        check(InfernoSceneryPolicy.LOD_SILHOUETTE >= InfernoSceneryPolicy.LOD_FULL
                        && InfernoSceneryPolicy.LOD_REDUCED >= InfernoSceneryPolicy.LOD_FULL,
                "LOD constants keep full as the finest setting");
        // An out-of-range LOD is clamped rather than throwing.
        check(InfernoSceneryPolicy.drawsAt(Layer.SKY_VORTEX, -5), "a too-fine LOD is clamped up");
        check(!InfernoSceneryPolicy.drawsAt(Layer.FIRE_ARCH, 99), "a too-coarse LOD is clamped down");
        check(!InfernoSceneryPolicy.drawsAt(null, InfernoSceneryPolicy.LOD_FULL), "a null layer never draws");
    }

    private static void perLayerPointsAreBoundedByTheLayer() {
        for (Layer layer : Layer.values()) {
            for (int players : new int[]{1, 5, 20, 64}) {
                for (int lod = InfernoSceneryPolicy.LOD_FULL; lod <= InfernoSceneryPolicy.LOD_SILHOUETTE; lod++) {
                    int points = InfernoSceneryPolicy.pointsFor(layer, players, lod);
                    check(points >= 0, "a layer never spends a negative number of points");
                    check(points <= layer.pointCount(),
                            "a layer never exceeds its own authored point count");
                    if (!InfernoSceneryPolicy.drawsAt(layer, lod)) {
                        check(points == 0, "a dropped layer spends nothing");
                    }
                }
            }
        }
        for (int lod = InfernoSceneryPolicy.LOD_FULL; lod <= InfernoSceneryPolicy.LOD_SILHOUETTE; lod++) {
            check(InfernoSceneryPolicy.pointsFor(null, 10, lod) == 0,
                    "a null layer has no points at any LOD");
        }
    }

    private static void frameNeverExceedsThePartyBudget() {
        for (int players : new int[]{1, 2, 5, 10, 20, 40, 200}) {
            for (int lod = InfernoSceneryPolicy.LOD_FULL; lod <= InfernoSceneryPolicy.LOD_SILHOUETTE; lod++) {
                int frame = InfernoSceneryPolicy.framePoints(players, lod);
                check(frame <= InfernoSceneryPolicy.budgetFor(players),
                        "the frame can never spend more than the party budget");
                check(frame >= 0, "a frame is never negative");
            }
        }
        check(InfernoSceneryPolicy.framePoints(10, InfernoSceneryPolicy.LOD_FULL)
                        >= InfernoSceneryPolicy.framePoints(10, InfernoSceneryPolicy.LOD_SILHOUETTE),
                "a coarser LOD never costs more points than a finer one");
    }

    private static void orbitIsDeterministicAndFinite() {
        Layer layer = Layer.SKY_VORTEX;
        double[] first = InfernoSceneryPolicy.orbitPoint(
                layer, 3, 16, 7, 1.0D, 100.5D, 64.0D, -220.5D);
        double[] again = InfernoSceneryPolicy.orbitPoint(
                layer, 3, 16, 7, 1.0D, 100.5D, 64.0D, -220.5D);
        check(first != null && again != null, "a valid orbit sample is produced");
        check(first[0] == again[0] && first[1] == again[1] && first[2] == again[2],
                "the orbit is a pure function of its inputs");
        for (double value : first) {
            check(Double.isFinite(value), "every orbit coordinate is finite");
        }
        double dx = first[0] - 100.5D;
        double dz = first[2] - (-220.5D);
        check(Math.abs(Math.sqrt(dx * dx + dz * dz) - layer.radius()) < 1.0E-6D,
                "the orbit point sits exactly on the layer radius");
        check(Math.abs(first[1] - (64.0D + layer.height())) <= layer.radius(),
                "the vertical wave stays near the layer height");
        check(InfernoSceneryPolicy.orbitPoint(null, 0, 16, 0, 1.0D, 0.0D, 0.0D, 0.0D) == null,
                "a null layer has no orbit point");
        check(InfernoSceneryPolicy.orbitPoint(layer, 0, 0, 0, 1.0D, 0.0D, 0.0D, 0.0D) == null,
                "zero points has no orbit point");
        check(InfernoSceneryPolicy.orbitPoint(layer, 0, 16, 0, 1.0D, Double.NaN, 0.0D, 0.0D) == null,
                "a non-finite centre is refused");
    }

    private static void layerPointsAreBoundedAndEmptyWhenDropped() {
        for (Layer layer : Layer.values()) {
            List<double[]> points = InfernoSceneryPolicy.layerPoints(
                    layer, 10, InfernoSceneryPolicy.LOD_FULL, 4, 0.0D, 0.0D, 0.0D);
            check(points.size() == InfernoSceneryPolicy.pointsFor(
                            layer, 10, InfernoSceneryPolicy.LOD_FULL),
                    "the emitted list is exactly the bounded point count");
            for (double[] point : points) {
                check(point != null && point.length == 3, "each emitted point is a finite triple");
                for (double value : point) {
                    check(Double.isFinite(value), "an emitted coordinate is finite");
                }
            }
        }
        check(InfernoSceneryPolicy.layerPoints(
                        Layer.FIRE_ARCH, 10, InfernoSceneryPolicy.LOD_SILHOUETTE, 0, 0.0D, 0.0D, 0.0D)
                        .isEmpty(),
                "a dropped layer emits an empty list, never null");
    }

    private static void describeIsStable() {
        String text = InfernoSceneryPolicy.describe(10, InfernoSceneryPolicy.LOD_FULL);
        check(text.contains("budget="), "the describe label carries the budget");
        check(text.contains("lod="), "the describe label carries the LOD");
        check(text.contains("frame_points="), "the describe label carries the frame cost");
        check(InfernoSceneryPolicy.describe(10, InfernoSceneryPolicy.LOD_FULL).equals(text),
                "the describe label is stable for the same inputs");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
