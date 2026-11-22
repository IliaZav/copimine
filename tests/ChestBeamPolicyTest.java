import java.util.HashSet;
import java.util.Set;
import me.copimine.endevent.domain.ChestBeamPolicy;

/**
 * The owner's approved signature ultimate (specification sections 7 and 9H) is a
 * real, dodgeable group mechanic, not a spherical area-of-effect. This probe
 * pins the geometry, the roster-bounded lane budget, the single damage window
 * and the refuse rules that keep the mechanic fair on a live server.
 */
public final class ChestBeamPolicyTest {
    private static int checks;

    private ChestBeamPolicyTest() {
    }

    public static void main(String[] args) {
        laneBudgetScalesWithRosterAndStaysBounded();
        lanesAreDistinctAndSeeded();
        theRayIsFiniteAndDirectional();
        hitTestIsASweptCapsule();
        theDamageWindowIsSingleAndOrdered();
        castIsRefusedWithoutAFairStage();
        theCooldownIsHonest();
        aimIsAlwaysNormalised();
        laneRailsMarkTheRealRadius();
        System.out.println("ChestBeamPolicyTest: PASS (" + checks + " checks)");
    }

    private static void laneBudgetScalesWithRosterAndStaysBounded() {
        check(ChestBeamPolicy.laneCount(0) == ChestBeamPolicy.SOLO_LANES, "empty roster still gets the solo lane");
        check(ChestBeamPolicy.laneCount(1) == ChestBeamPolicy.SOLO_LANES, "solo gets exactly one lane");
        check(ChestBeamPolicy.laneCount(2) == ChestBeamPolicy.SOLO_LANES, "a duo gets exactly one lane");
        check(ChestBeamPolicy.laneCount(5) == 2, "a small group gets two lanes");
        check(ChestBeamPolicy.laneCount(8) == 2, "the small-group ceiling is two lanes");
        check(ChestBeamPolicy.laneCount(20) == ChestBeamPolicy.MAX_LANES, "a full roster gets the authored maximum");
        check(ChestBeamPolicy.laneCount(500) == ChestBeamPolicy.MAX_LANES, "the lane count is bounded");
    }

    private static void lanesAreDistinctAndSeeded() {
        for (int seed = 0; seed < 24; seed++) {
            int[] picked = ChestBeamPolicy.selectLaneIndices(20, ChestBeamPolicy.MAX_LANES, seed);
            check(picked.length == ChestBeamPolicy.MAX_LANES, "a full roster always locks the full lane budget");
            Set<Integer> distinct = new HashSet<>();
            for (int index : picked) {
                check(index >= 0 && index < 20, "a lane index stays inside the roster");
                check(distinct.add(index), "a lane index is never reused");
            }
        }
        // A lane budget larger than the roster cannot invent players.
        int[] two = ChestBeamPolicy.selectLaneIndices(2, 3, 7);
        check(two.length == 2, "the lane budget is clamped to the roster size");
        check(two[0] != two[1], "two players never share one lane");
        // Lower lane budget than group size must still produce distinct picks.
        int[] bounded = ChestBeamPolicy.selectLaneIndices(20, 2, 5);
        check(bounded.length == 2 && bounded[0] != bounded[1], "a bounded budget still locks distinct lanes");
        check(ChestBeamPolicy.selectLaneIndices(0, 3, 1).length == 0, "no roster means no lanes");
        check(ChestBeamPolicy.selectLaneIndices(5, 0, 1).length == 0, "no budget means no lanes");
        // The rotation reaches every player; no player is structurally never chosen.
        Set<Integer> reached = new HashSet<>();
        for (int seed = 0; seed < 20; seed++) {
            for (int index : ChestBeamPolicy.selectLaneIndices(20, 1, seed)) {
                reached.add(index);
            }
        }
        check(reached.size() == 20, "every roster slot can be a beam lane over time");
    }

    private static void theRayIsFiniteAndDirectional() {
        ChestBeamPolicy.Aim aim = ChestBeamPolicy.aimAt(0, 0, 0, 0, 0, 10);
        check(near(aim.dx(), 0) && near(aim.dy(), 0) && near(aim.dz(), 1), "the aim points at the target");
        check(near(aim.tipZ(), ChestBeamPolicy.BEAM_RANGE), "the tip is exactly one finite range away");
        check(near(aim.tipX(), 0) && near(aim.tipY(), 0), "the tip stays on the locked direction");
        // A zero-length aim is normalised to something drawable instead of NaN.
        ChestBeamPolicy.Aim degenerate = ChestBeamPolicy.aimAt(1, 1, 1, 1, 1, 1);
        check(near(degenerate.dx(), 0) && near(degenerate.dy(), 0) && near(degenerate.dz(), 1),
                "a degenerate aim falls back to a unit direction");
    }

    private static void hitTestIsASweptCapsule() {
        ChestBeamPolicy.Aim aim = ChestBeamPolicy.aimAt(0, 0, 0, 0, 0, 1);
        check(ChestBeamPolicy.beamHits(aim, 0, 0, 5), "a point on the lane axis is hit");
        check(ChestBeamPolicy.beamHits(aim, 1.2, 0, 5), "a point just inside the lane radius is hit");
        check(!ChestBeamPolicy.beamHits(aim, 3.0, 0, 5), "a point outside the lane radius is safe");
        check(!ChestBeamPolicy.beamHits(aim, 0, 0, -2), "a point behind the core is never hit");
        check(!ChestBeamPolicy.beamHits(aim, 0, 0, ChestBeamPolicy.BEAM_RANGE + 1),
                "the ray is finite: nothing past the range is hit");
        // A point inside the capsule but beyond the range along the axis is safe.
        check(!ChestBeamPolicy.beamHits(aim, 0, 0, ChestBeamPolicy.BEAM_RANGE + 0.5),
                "the swept capsule is capped at the authored range");
        check(near(ChestBeamPolicy.perpendicular(aim, 2, 0, 5), 2), "perpendicular distance is the lateral offset");
        check(near(ChestBeamPolicy.along(aim, 0, 0, 5), 5), "along distance measures travel from the core");
        check(Double.isNaN(ChestBeamPolicy.along(null, 1, 2, 3)), "a missing aim yields no along distance");
        check(!ChestBeamPolicy.beamHits(null, 0, 0, 1), "a missing aim never hits");
    }

    private static void theDamageWindowIsSingleAndOrdered() {
        int charge = ChestBeamPolicy.CHARGE_TICKS;
        int fire = ChestBeamPolicy.FIRE_TICKS;
        check(ChestBeamPolicy.chargingAt(0), "tick zero is charging");
        check(ChestBeamPolicy.chargingAt(charge - 1), "the last charge tick is charging");
        check(!ChestBeamPolicy.chargingAt(charge), "once locked the charge is over");
        check(ChestBeamPolicy.damagesAt(charge), "the first fire tick damages");
        check(ChestBeamPolicy.damagesAt(charge + fire - 1), "the last fire tick damages");
        check(!ChestBeamPolicy.damagesAt(charge - 1), "the charge does not damage");
        check(!ChestBeamPolicy.damagesAt(charge + fire), "damage stops at the authored fire length");
        check(ChestBeamPolicy.aftermathAt(charge + fire), "the aftermath begins after the fire");
        check(!ChestBeamPolicy.aftermathAt(charge + fire - 1), "the aftermath does not overlap the fire");
        check(!ChestBeamPolicy.aftermathAt(ChestBeamPolicy.TOTAL_TICKS), "the ultimate ends at its authored length");
        // The three stages must be a strict, non-overlapping partition.
        for (int tick = 0; tick < ChestBeamPolicy.TOTAL_TICKS; tick++) {
            int stages = (ChestBeamPolicy.chargingAt(tick) ? 1 : 0)
                    + (ChestBeamPolicy.damagesAt(tick) ? 1 : 0)
                    + (ChestBeamPolicy.aftermathAt(tick) ? 1 : 0);
            check(stages == 1, "tick " + tick + " belongs to exactly one stage");
        }
        check(ChestBeamPolicy.TOTAL_TICKS == charge + fire + ChestBeamPolicy.AFTERMATH_TICKS,
                "the total is the sum of its stages");
        check(ChestBeamPolicy.damagesAt(charge) && !ChestBeamPolicy.damagesAt(charge + fire),
                "the damage window is a single finite interval, not the whole cast");
    }

    private static void castIsRefusedWithoutAFairStage() {
        check(ChestBeamPolicy.mayCast(true, false), "a legal center with no firefront may cast");
        check(!ChestBeamPolicy.mayCast(false, false), "no legal center stand point refuses the cast");
        check(!ChestBeamPolicy.mayCast(true, true), "a lethal full-arena firefront refuses the cast");
        check(!ChestBeamPolicy.mayCast(false, true), "both blockers together refuse the cast");
    }

    private static void theCooldownIsHonest() {
        check(ChestBeamPolicy.onCooldown(100, 100 + ChestBeamPolicy.COOLDOWN_TICKS),
                "the ultimate is unavailable right after it fires");
        check(!ChestBeamPolicy.onCooldown(100 + ChestBeamPolicy.COOLDOWN_TICKS,
                        100 + ChestBeamPolicy.COOLDOWN_TICKS),
                "the cooldown expires on its boundary tick");
        check(!ChestBeamPolicy.onCooldown(0, 0), "a zero cooldown is never on cooldown");
        check(ChestBeamPolicy.onCooldown(0, 1), "a future expiry is on cooldown");
    }

    private static void aimIsAlwaysNormalised() {
        for (double[] raw : new double[][]{
                {3, 4, 0}, {0, 0, -2}, {1, 1, 1}, {10, 0, 0}, {0, 7, 24}}) {
            ChestBeamPolicy.Aim aim = ChestBeamPolicy.aimAt(0, 0, 0, raw[0], raw[1], raw[2]);
            double length = Math.sqrt(aim.dx() * aim.dx() + aim.dy() * aim.dy() + aim.dz() * aim.dz());
            check(near(length, 1.0D), "the aim direction is a unit vector");
            check(Double.isFinite(aim.tipX()) && Double.isFinite(aim.tipY()) && Double.isFinite(aim.tipZ()),
                    "the finite tip is always a real point");
        }
    }

    /**
     * The two edge traces drawn by the presentation layer (specification 9H.02,
     * shot 3) must sit exactly on the swept-capsule boundary of the very lane
     * the server damages with. A rail that drifted inward would understate the
     * hit; a rail that drifted outward would overstate it. Either way the
     * warning would lie, so this pins the geometry.
     */
    private static void laneRailsMarkTheRealRadius() {
        ChestBeamPolicy.Aim aim = ChestBeamPolicy.aimAt(0, 0, 0, 0, 0, 10);
        ChestBeamPolicy.Aim left = ChestBeamPolicy.rail(aim, -ChestBeamPolicy.BEAM_RADIUS, 0);
        ChestBeamPolicy.Aim right = ChestBeamPolicy.rail(aim, ChestBeamPolicy.BEAM_RADIUS, 0);
        check(left != null && right != null, "a lane always produces both rails");
        // Same direction: a rail is a translation, never a re-aim.
        check(near(left.dx(), aim.dx()) && near(left.dy(), aim.dy()) && near(left.dz(), aim.dz()),
                "the left rail keeps the locked direction");
        check(near(right.dx(), aim.dx()) && near(right.dy(), aim.dy()) && near(right.dz(), aim.dz()),
                "the right rail keeps the locked direction");
        // Every point along a rail sits exactly on the capsule surface.
        check(near(ChestBeamPolicy.perpendicular(aim, left.ox(), left.oy(), left.oz()),
                        ChestBeamPolicy.BEAM_RADIUS),
                "the left rail lies on the hit boundary");
        check(near(ChestBeamPolicy.perpendicular(aim, right.ox(), right.oy(), right.oz()),
                        ChestBeamPolicy.BEAM_RADIUS),
                "the right rail lies on the hit boundary");
        // A point drawn between the rails is inside the lane; one outside is safe.
        check(ChestBeamPolicy.beamHits(aim, 0, 0, 5), "the space between the rails is the danger lane");
        check(!ChestBeamPolicy.beamHits(aim, ChestBeamPolicy.BEAM_RADIUS + 0.5D, 0, 5),
                "outside the rails is safe");
        // The rails are on opposite sides of the axis.
        check(!near(left.ox(), right.ox()) || !near(left.oz(), right.oz()),
                "the two rails are distinct edges, not the same line");
        // A near-vertical lane still produces a well conditioned frame.
        ChestBeamPolicy.Aim up = ChestBeamPolicy.aimAt(0, 0, 0, 0, 10, 0);
        ChestBeamPolicy.Aim upRail = ChestBeamPolicy.rail(up, ChestBeamPolicy.BEAM_RADIUS, 0);
        check(upRail != null && near(ChestBeamPolicy.perpendicular(up, upRail.ox(), upRail.oy(),
                        upRail.oz()), ChestBeamPolicy.BEAM_RADIUS),
                "a vertical lane still marks its radius");
        check(ChestBeamPolicy.rail(null, 1, 0) == null, "a missing lane has no rail");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-9D;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }
}
