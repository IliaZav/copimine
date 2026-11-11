import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy;
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy.Ability;

/**
 * The elite marksman that holds the Wave 6 final pillar.
 *
 * <p>This is the only skeleton-on-a-pillar mechanic in the event, and it must
 * never become a static turret, an invisible archer or an unreachable orphan
 * left standing on a pillar that has collapsed underneath it. The two volleys
 * must also be genuinely different attacks rather than one animation played
 * twice with a different number of arrows.
 */
public final class WaveSixPillarSkeletonPolicyTest {
    private static int checks;

    private WaveSixPillarSkeletonPolicyTest() {
    }

    public static void main(String[] args) {
        theTwoVolleysAreDistinct();
        noTargetNoShot();
        cooldownsGateEachVolleyIndependently();
        everyDirectionIsReal();
        degenerateAimNeverFiresIntoSelf();
        itMovesInsteadOfStandingStill();
        theDropHappensOnceForSurvivors();
        noOrphanMayBeLeftBehind();
        System.out.println("WaveSixPillarSkeletonPolicyTest: PASS (" + checks + " checks)");
    }

    private static void theTwoVolleysAreDistinct() {
        check("the triple shot fires three arrows",
                WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_ARROWS == 3);
        check("the heavy volley fires at least one arrow",
                WaveSixPillarSkeletonPolicy.BONE_RAIN_ARROWS >= 1);
        check("the volleys spread differently",
                WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_SPREAD_DEGREES
                        != WaveSixPillarSkeletonPolicy.BONE_RAIN_ARC_DEGREES);
        check("the volleys fly at different speeds",
                WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_SPEED
                        != WaveSixPillarSkeletonPolicy.BONE_RAIN_SPEED);
        check("the volleys are on different cooldowns",
                WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_COOLDOWN_TICKS
                        != WaveSixPillarSkeletonPolicy.BONE_RAIN_COOLDOWN_TICKS);
        check("the marksman is a real killable actor with real health",
                WaveSixPillarSkeletonPolicy.HEALTH > 0.0D);
    }

    private static void noTargetNoShot() {
        check("an untargeted marksman never fires",
                WaveSixPillarSkeletonPolicy.nextAbility(500, 0, 0, false) == Ability.NONE);
        check("a negative tick never fires",
                WaveSixPillarSkeletonPolicy.nextAbility(-1, 0, 0, true) == Ability.NONE);
    }

    private static void cooldownsGateEachVolleyIndependently() {
        check("a fresh marksman opens with the heavy volley",
                WaveSixPillarSkeletonPolicy.nextAbility(0, -1, -1, true) == Ability.BONE_RAIN);
        check("nothing fires while both are recharging",
                WaveSixPillarSkeletonPolicy.nextAbility(
                        WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_COOLDOWN_TICKS - 1,
                        0, 0, true) == Ability.NONE);
        check("the triple shot takes over once its own cooldown lapses",
                WaveSixPillarSkeletonPolicy.nextAbility(
                        WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_COOLDOWN_TICKS,
                        0, 0, true) == Ability.TRIPLE_SHOT);
        // The heavy volley must not monopolise the fight: when both are ready it
        // wins, which is what makes the pair alternate rather than repeat.
        check("both ready prefers the heavy volley, so the fight alternates",
                WaveSixPillarSkeletonPolicy.nextAbility(10_000, 0, 0, true) == Ability.BONE_RAIN);
        check("a future timestamp is treated as never used",
                WaveSixPillarSkeletonPolicy.nextAbility(10, 9_999, 9_999, true) == Ability.BONE_RAIN);
    }

    private static void everyDirectionIsReal() {
        double[][] triple = WaveSixPillarSkeletonPolicy.tripleShotDirections(0.0D, 1.0D);
        check("the triple shot has one direction per arrow",
                triple.length == WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_ARROWS);
        for (double[] dir : triple) {
            check("a triple shot direction is a unit vector", isUnit(dir));
        }
        check("the middle arrow aims exactly at the target",
                Math.abs(triple[1][0]) < 1.0E-9D && Math.abs(triple[1][1] - 1.0D) < 1.0E-9D);
        check("the flanking arrows fan to opposite sides",
                triple[0][0] * triple[2][0] < 0.0D);
        check("the fan is symmetric about the aim",
                Math.abs(triple[0][0] + triple[2][0]) < 1.0E-9D);

        double[][] bone = WaveSixPillarSkeletonPolicy.boneRainDirections(1.0D, 0.0D);
        check("the heavy volley has one direction per arrow",
                bone.length == WaveSixPillarSkeletonPolicy.BONE_RAIN_ARROWS);
        for (double[] dir : bone) {
            check("a heavy volley direction is a unit vector", isUnit(dir));
        }
        check("the heavy volley spreads wider than the triple shot",
                Math.abs(bone[0][1]) > Math.abs(triple[0][0]));
    }

    private static void degenerateAimNeverFiresIntoSelf() {
        check("a zero-length aim produces no arrows",
                WaveSixPillarSkeletonPolicy.spread(0.0D, 0.0D, 3, 7.0D).length == 0);
        check("a non-finite aim produces no arrows",
                WaveSixPillarSkeletonPolicy.spread(Double.NaN, 0.0D, 3, 7.0D).length == 0);
        check("an infinite aim produces no arrows",
                WaveSixPillarSkeletonPolicy.spread(Double.POSITIVE_INFINITY, 0.0D, 3, 7.0D).length == 0);
        check("asking for zero arrows yields zero",
                WaveSixPillarSkeletonPolicy.spread(0.0D, 1.0D, 0, 7.0D).length == 0);
    }

    private static void itMovesInsteadOfStandingStill() {
        check("a new marksman picks a spot",
                WaveSixPillarSkeletonPolicy.shouldReposition(0, -1));
        check("it holds briefly before moving",
                !WaveSixPillarSkeletonPolicy.shouldReposition(
                        WaveSixPillarSkeletonPolicy.REPOSITION_TICKS - 1, 0));
        check("then it moves",
                WaveSixPillarSkeletonPolicy.shouldReposition(
                        WaveSixPillarSkeletonPolicy.REPOSITION_TICKS, 0));
        check("a negative tick never repositions",
                !WaveSixPillarSkeletonPolicy.shouldReposition(-5, 0));
        for (int step = 0; step < 64; step++) {
            double[] offset = WaveSixPillarSkeletonPolicy.patrolOffset(step);
            double radius = Math.hypot(offset[0], offset[1]);
            check("the patrol stays on the platform",
                    radius <= WaveSixPillarSkeletonPolicy.PATROL_RADIUS + 1.0E-9D);
            check("the patrol actually displaces the marksman", radius > 0.0D);
        }
        double[] first = WaveSixPillarSkeletonPolicy.patrolOffset(1);
        double[] second = WaveSixPillarSkeletonPolicy.patrolOffset(2);
        check("consecutive patrol spots differ",
                Math.hypot(first[0] - second[0], first[1] - second[1]) > 0.05D);
    }

    private static void theDropHappensOnceForSurvivors() {
        check("no drop before the pillar collapses",
                !WaveSixPillarSkeletonPolicy.shouldFall(6, true, false));
        check("a survivor drops when the final pillar is destroyed",
                WaveSixPillarSkeletonPolicy.shouldFall(7, true, false));
        check("a dead marksman never drops",
                !WaveSixPillarSkeletonPolicy.shouldFall(7, false, false));
        check("the drop happens exactly once",
                !WaveSixPillarSkeletonPolicy.shouldFall(7, true, true));
        check("nothing drops on an ordinary impact",
                !WaveSixPillarSkeletonPolicy.shouldFall(3, true, false));
    }

    private static void noOrphanMayBeLeftBehind() {
        check("a marksman still on a destroyed pillar blocks completion",
                !WaveSixPillarSkeletonPolicy.encounterMayComplete(true, true));
        check("a marksman fighting on the floor does not block completion",
                WaveSixPillarSkeletonPolicy.encounterMayComplete(true, false));
        check("no marksman does not block completion",
                WaveSixPillarSkeletonPolicy.encounterMayComplete(false, false));
    }

    private static boolean isUnit(double[] direction) {
        return Math.abs(Math.hypot(direction[0], direction[1]) - 1.0D) < 1.0E-9D;
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + name);
        checks++;
    }
}
