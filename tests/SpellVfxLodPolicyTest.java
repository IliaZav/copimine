import me.copimine.endevent.domain.SpellVfxLodPolicy;

/**
 * N23-P1-07: the per-viewer cost ladder for the server-emitted spell
 * presentation particles.
 *
 * <p>The interim emitter ships {@code O(samples * viewers * frames)} cosmetic
 * points. V6 demands a large measured reduction and forbids starving combat or
 * gameplay packets for art, so these checks pin the pure contract the emitter
 * and the future signed cast packet both depend on: a deterministic
 * distance/party LOD, a bounded per-viewer count that never erases an effect,
 * a monotonic per-frame budget, and a shipped total that is provably below the
 * unbounded cost on a raid-sized party.</p>
 */
public final class SpellVfxLodPolicyTest {

    public static void main(String[] args) {
        lodIsMonotonicInDistance();
        lodTightensAsThePartyGrows();
        strideAndPerViewerCountAreBounded();
        frameBudgetIsBoundedAndMonotonic();
        shippedTotalNeverExceedsTheBudgetAndSavesOnAraid();
        degenerateInputsAreSafeAndClamped();
        describeIsStable();
        System.out.println("SpellVfxLodPolicyTest OK");
    }

    private static void lodIsMonotonicInDistance() {
        check(SpellVfxLodPolicy.lodForViewer(0.0D, 1) == SpellVfxLodPolicy.LOD_FULL,
                "a viewer on the origin gets full detail");
        check(SpellVfxLodPolicy.lodForViewer(SpellVfxLodPolicy.NEAR_BLOCKS, 1)
                        == SpellVfxLodPolicy.LOD_FULL,
                "the near band is inclusive");
        check(SpellVfxLodPolicy.lodForViewer(SpellVfxLodPolicy.NEAR_BLOCKS + 0.5D, 1)
                        == SpellVfxLodPolicy.LOD_REDUCED,
                "just past the near band is reduced");
        check(SpellVfxLodPolicy.lodForViewer(SpellVfxLodPolicy.MID_BLOCKS, 1)
                        == SpellVfxLodPolicy.LOD_REDUCED,
                "the mid band is inclusive");
        check(SpellVfxLodPolicy.lodForViewer(SpellVfxLodPolicy.MID_BLOCKS + 0.5D, 1)
                        == SpellVfxLodPolicy.LOD_SILHOUETTE,
                "past the mid band is silhouette only");
        int previous = SpellVfxLodPolicy.LOD_FULL;
        for (double d = 0.0D; d <= 200.0D; d += 0.5D) {
            int lod = SpellVfxLodPolicy.lodForViewer(d, 4);
            check(lod >= previous, "detail never sharpens as a viewer moves away");
            previous = lod;
        }
    }

    private static void lodTightensAsThePartyGrows() {
        check(SpellVfxLodPolicy.lodForViewer(9.0D, 1) == SpellVfxLodPolicy.LOD_FULL,
                "a solo player 9 blocks out is still full detail");
        check(SpellVfxLodPolicy.lodForViewer(9.0D, 16) == SpellVfxLodPolicy.LOD_REDUCED,
                "the same distance is reduced in a sixteen-player party");
        check(SpellVfxLodPolicy.lodForViewer(22.0D, 1) == SpellVfxLodPolicy.LOD_REDUCED,
                "a solo player 22 blocks out is reduced");
        check(SpellVfxLodPolicy.lodForViewer(22.0D, 16) == SpellVfxLodPolicy.LOD_SILHOUETTE,
                "the same distance is silhouette only in a large party");
        for (double d = 0.0D; d <= 60.0D; d += 0.5D) {
            check(SpellVfxLodPolicy.lodForViewer(d, 20) >= SpellVfxLodPolicy.lodForViewer(d, 1),
                    "a larger party is never assigned finer detail at the same distance");
        }
    }

    private static void strideAndPerViewerCountAreBounded() {
        check(SpellVfxLodPolicy.sampleStride(SpellVfxLodPolicy.LOD_FULL) == 1,
                "full detail walks every point");
        check(SpellVfxLodPolicy.sampleStride(SpellVfxLodPolicy.LOD_REDUCED) == 2,
                "reduced detail walks every second point");
        check(SpellVfxLodPolicy.sampleStride(SpellVfxLodPolicy.LOD_SILHOUETTE) == 4,
                "silhouette detail walks every fourth point");
        check(SpellVfxLodPolicy.sampleStride(-99) == 1, "a too-fine LOD is clamped to full");
        check(SpellVfxLodPolicy.sampleStride(99) == 4, "a too-coarse LOD is clamped to silhouette");
        for (int base : new int[]{1, 7, 24, 96}) {
            for (double d = 0.0D; d <= 120.0D; d += 3.0D) {
                for (int players : new int[]{1, 5, 20}) {
                    int points = SpellVfxLodPolicy.pointsForViewer(base, d, players);
                    check(points >= 1, "an effect is never erased for a viewer in range");
                    check(points <= base, "a viewer never spends more than the authored count");
                }
            }
        }
        check(SpellVfxLodPolicy.pointsForViewer(0, 5.0D, 10) == 0,
                "a zero-point geometry spends nothing");
        check(SpellVfxLodPolicy.pointsForViewer(96, 5.0D, 20)
                        >= SpellVfxLodPolicy.pointsForViewer(96, 40.0D, 20),
                "a closer viewer never gets fewer points than a far one");
    }

    private static void frameBudgetIsBoundedAndMonotonic() {
        int previous = 0;
        for (int players = 1; players <= 64; players++) {
            int budget = SpellVfxLodPolicy.framePointBudget(players);
            check(budget >= SpellVfxLodPolicy.FRAME_BUDGET_MIN,
                    "even a solo party can ship the authored floor");
            check(budget <= SpellVfxLodPolicy.FRAME_BUDGET_CAP,
                    "the frame budget never exceeds the authored cap");
            check(budget >= previous, "the budget never shrinks as the party grows");
            previous = budget;
        }
        check(SpellVfxLodPolicy.framePointBudget(1) == SpellVfxLodPolicy.FRAME_BUDGET_MIN,
                "a solo party gets exactly the authored floor");
        check(SpellVfxLodPolicy.framePointBudget(0) == SpellVfxLodPolicy.framePointBudget(1),
                "an empty party is clamped to a solo party");
        check(SpellVfxLodPolicy.framePointBudget(Integer.MAX_VALUE) == SpellVfxLodPolicy.FRAME_BUDGET_CAP,
                "a huge party is clamped to the cap, never beyond");
    }

    private static void shippedTotalNeverExceedsTheBudgetAndSavesOnAraid() {
        double[] smallParty = {4.0D, 6.0D, 8.0D, 10.0D, 12.0D};
        for (int players = 1; players <= 40; players++) {
            int shipped = SpellVfxLodPolicy.totalPoints(96, smallParty, players);
            check(shipped <= SpellVfxLodPolicy.framePointBudget(players),
                    "a frame can never ship more than its budget");
            check(shipped <= SpellVfxLodPolicy.unboundedPoints(96, smallParty),
                    "the shipped total is never above the unbounded total");
        }
        double[] raid = new double[20];
        for (int i = 0; i < raid.length; i++) {
            raid[i] = 5.0D;
        }
        int shipped = SpellVfxLodPolicy.totalPoints(96, raid, raid.length);
        int unbounded = SpellVfxLodPolicy.unboundedPoints(96, raid);
        check(unbounded == 1920, "the unbounded raid cost is the naive product");
        check(shipped == SpellVfxLodPolicy.framePointBudget(raid.length),
                "a dense raid is shipped exactly at its budget, no more");
        check(shipped * 4 <= unbounded, "the raid reduction is at least four to one");
        // A lone far viewer still receives the bounded silhouette, never zero.
        check(SpellVfxLodPolicy.totalPoints(96, new double[]{100.0D}, 1) >= 1,
                "even a far lone viewer keeps the effect");
    }

    private static void degenerateInputsAreSafeAndClamped() {
        check(SpellVfxLodPolicy.clampLod(-5) == SpellVfxLodPolicy.LOD_FULL,
                "a too-fine LOD is clamped to full");
        check(SpellVfxLodPolicy.clampLod(99) == SpellVfxLodPolicy.LOD_SILHOUETTE,
                "a too-coarse LOD is clamped to silhouette");
        check(SpellVfxLodPolicy.lodForViewer(Double.NaN, 4) == SpellVfxLodPolicy.LOD_REDUCED,
                "a non-finite distance degrades to the reduced band, never throws");
        check(SpellVfxLodPolicy.lodForViewer(-50.0D, 4) == SpellVfxLodPolicy.LOD_FULL,
                "a negative distance is treated as on top of the effect");
        check(SpellVfxLodPolicy.totalPoints(96, null, 10) == 0, "null distances ship nothing");
        check(SpellVfxLodPolicy.totalPoints(96, new double[0], 10) == 0, "no viewers ship nothing");
        check(SpellVfxLodPolicy.unboundedPoints(96, null) == 0, "a null distance set has no cost");
        check(SpellVfxLodPolicy.pointsForViewer(-1, 5.0D, 10) == 0, "a negative base ships nothing");
    }

    private static void describeIsStable() {
        double[] distances = {5.0D, 30.0D};
        String text = SpellVfxLodPolicy.describe(10, 96, distances);
        check(text.contains("budget="), "the describe label carries the budget");
        check(text.contains("shipped="), "the describe label carries the shipped cost");
        check(text.contains("saved="), "the describe label carries the saving");
        check(SpellVfxLodPolicy.describe(10, 96, distances).equals(text),
                "the describe label is stable for the same inputs");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
