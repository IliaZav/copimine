import me.copimine.endevent.domain.ArchmageV5CombatPolicy;
import me.copimine.endevent.domain.ArchmageV5CombatPolicy.Action;
import me.copimine.endevent.domain.ArchmageV5CombatPolicy.Jaw;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.1 Archmage contract: short local fang lane, gap-bearing Circle, bounded
 * five-spoke Crown and one warned anti-crowd Repulse.
 */
public final class ArchmageV5CombatPolicyTest {
    public static void main(String[] args) {
        legacyIdsMapOntoFinalActions();
        fangLineIsShortAndLocal();
        circleAlwaysHasATraversableGap();
        crownIsFiveBoundedRotatingSpokes();
        repulseIsOneWarnedCrowdReset();
        timingsStayInsideTheRatifiedBands();
        System.out.println("ArchmageV5CombatPolicyTest OK");
    }

    private static void legacyIdsMapOntoFinalActions() {
        check(ArchmageV5CombatPolicy.action("ARCHMAGE_FANGS") == Action.FANG_LINE,
                "ARCHMAGE_FANGS stays legal and becomes the short Fang Line");
        check(ArchmageV5CombatPolicy.action("WAVE7_ARCHMAGE_FANGS_GROUP") == Action.FANG_LINE,
                "the old all-party group line is explicitly SUPERSEDED by the local lane");
        check(ArchmageV5CombatPolicy.action("ARCHMAGE_BEAM") == Action.RANGED_BEAT,
                "the existing beam remains a legal ranged beat");
        check(ArchmageV5CombatPolicy.action("ARCHMAGE_SPEAR") == Action.RANGED_BEAT,
                "the existing spear remains a legal ranged beat");
        check(ArchmageV5CombatPolicy.action("ARCHMAGE_STEP") == Action.REPOSITION,
                "the existing step is the validated reposition");
        check(ArchmageV5CombatPolicy.action("ARCHMAGE_CROWN") == Action.CROWN_OF_FANGS,
                "the Crown has its own final action");
        check(ArchmageV5CombatPolicy.action("ARCHMAGE_REPULSE") == Action.STAFF_REPULSE,
                "the Repulse has its own final action");
        check(ArchmageV5CombatPolicy.action(null) == Action.NONE,
                "a missing id is not silently treated as an attack");
    }

    private static void fangLineIsShortAndLocal() {
        check(ArchmageV5CombatPolicy.FANG_LINE_TEETH == 3,
                "the lane is exactly three real fangs");
        check(Math.abs(ArchmageV5CombatPolicy.FANG_LINE_PATH_BLOCKS - 3.0D) < 1.0E-9D,
                "the lane spans three blocks, not a caster-to-target line");
        List<double[]> cells = ArchmageV5CombatPolicy.fangLineCells(10.5D, 10.5D, 1.0D, 0.0D);
        check(cells.size() == 3, "three ground cells");
        check(Math.abs(cells.get(0)[0] - 9.5D) < 1.0E-9D
                        && Math.abs(cells.get(1)[0] - 10.5D) < 1.0E-9D
                        && Math.abs(cells.get(2)[0] - 11.5D) < 1.0E-9D,
                "the three cells are consecutive and centred on the marked cell");
        double span = cells.get(2)[0] - cells.get(0)[0];
        check(Math.abs(span - 2.0D) < 1.0E-9D,
                "three consecutive block centres span exactly two blocks of travel");
        List<double[]> turned = ArchmageV5CombatPolicy.fangLineCells(0.5D, 0.5D, 0.0D, -1.0D);
        check(Math.abs(turned.get(0)[1] - 1.5D) < 1.0E-9D
                        && Math.abs(turned.get(2)[1] + 0.5D) < 1.0E-9D,
                "the lane follows the locked aim axis in every direction");
    }

    private static void circleAlwaysHasATraversableGap() {
        for (int jaws = ArchmageV5CombatPolicy.CIRCLE_JAWS_MIN;
                jaws <= ArchmageV5CombatPolicy.CIRCLE_JAWS_MAX; jaws++) {
            for (double radius = ArchmageV5CombatPolicy.CIRCLE_RADIUS_MIN;
                    radius <= ArchmageV5CombatPolicy.CIRCLE_RADIUS_MAX; radius += 0.1D) {
                List<Jaw> ring = ArchmageV5CombatPolicy.circleJaws(jaws, radius,
                        ArchmageV5CombatPolicy.CIRCLE_GAP_BLOCKS_MIN, 0.0D);
                check(ring.size() == jaws, "the ring has exactly the requested jaw count");
                check(ArchmageV5CombatPolicy.hasTraversableGap(ring, radius,
                                ArchmageV5CombatPolicy.CIRCLE_GAP_BLOCKS_MIN),
                        "a 2-block gap must remain walkable at every legal radius");
            }
        }
        check(ArchmageV5CombatPolicy.circleJaws(2, 2.5D, 2.0D, 0.0D).size()
                        == ArchmageV5CombatPolicy.CIRCLE_JAWS_MIN,
                "a two-player party still gets the minimum seven jaws");
        check(ArchmageV5CombatPolicy.circleJaws(20, 2.5D, 2.0D, 0.0D).size()
                        == ArchmageV5CombatPolicy.CIRCLE_JAWS_MAX,
                "a large party gets nine jaws");
        List<Jaw> closed = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            closed.add(new Jaw(2.5D, 0.0D, index * (360.0D / 16.0D)));
        }
        check(!ArchmageV5CombatPolicy.hasTraversableGap(closed, 2.5D, 2.0D),
                "a dense ring with every sector occupied is rejected: no closed prison");
    }

    private static void crownIsFiveBoundedRotatingSpokes() {
        check(ArchmageV5CombatPolicy.CROWN_SPOKES == 5,
                "the Crown is exactly five spoke-arms");
        check(ArchmageV5CombatPolicy.crownChannelTicks(2)
                        >= ArchmageV5CombatPolicy.CROWN_CHANNEL_MIN_TICKS,
                "the standard channel is at least twelve seconds");
        check(ArchmageV5CombatPolicy.crownChannelTicks(20)
                        <= ArchmageV5CombatPolicy.CROWN_CHANNEL_MAX_TICKS,
                "the standard channel is at most eighteen seconds");
        check(ArchmageV5CombatPolicy.crownMustFinish(ArchmageV5CombatPolicy.CROWN_HARD_MAX_TICKS),
                "no Crown may outlive thirty seconds");
        check(!ArchmageV5CombatPolicy.crownMustFinish(
                        ArchmageV5CombatPolicy.CROWN_HARD_MAX_TICKS - 1),
                "the forced finish is exactly at the bound");
        check(ArchmageV5CombatPolicy.crownLength(1.0D)
                        == ArchmageV5CombatPolicy.CROWN_LENGTH_MIN,
                "arm length is clamped up to seven blocks");
        check(ArchmageV5CombatPolicy.crownLength(99.0D)
                        == ArchmageV5CombatPolicy.CROWN_LENGTH_MAX,
                "arm length is clamped down to nine blocks");
        double[] first = ArchmageV5CombatPolicy.crownSpokeAnglesDegrees(0L, 12.0D, 0.0D);
        double[] later = ArchmageV5CombatPolicy.crownSpokeAnglesDegrees(200L, 12.0D, 0.0D);
        check(first.length == 5, "five arms at any instant");
        for (int index = 1; index < first.length; index++) {
            check(Math.abs((first[index] - first[index - 1]) - 72.0D) < 1.0E-6D,
                    "arms are evenly spaced by 72 degrees");
        }
        check(Math.abs(later[0] - first[0] - 120.0D) < 1.0E-6D,
                "ten seconds at twelve degrees per second advance the arms deterministically");
        check(ArchmageV5CombatPolicy.crownRotation(99.0D)
                        == ArchmageV5CombatPolicy.CROWN_ROTATION_MAX_DEG_PER_SEC,
                "rotation is clamped into eight..twelve degrees per second");
        List<double[]> arm = ArchmageV5CombatPolicy.crownArmPoints(0.0D, 8.0D, 24);
        check(arm.size() == 25, "the arm is sampled as a continuous curve");
        check(Math.abs(arm.get(arm.size() - 1)[0] - 8.0D) < 1.5D,
                "the arm reaches roughly its authored length");
        check(arm.get(arm.size() / 2)[1] > arm.get(0)[1],
                "the arm rises off the ground instead of being a flat ring");
        check(ArchmageV5CombatPolicy.crownTeeth(7.0D) >= 5,
                "a short arm still carries a readable row of teeth");
        check(ArchmageV5CombatPolicy.crownToothHits(3.0D, 0.0D, 0.0D),
                "a tooth bites a player on the arm");
        check(!ArchmageV5CombatPolicy.crownToothHits(3.0D, 1.5D, 0.0D),
                "a player in the moving safe wedge is not bitten");
    }

    private static void repulseIsOneWarnedCrowdReset() {
        check(ArchmageV5CombatPolicy.repulseRadius()
                        >= ArchmageV5CombatPolicy.REPULSE_RADIUS_MIN
                        && ArchmageV5CombatPolicy.repulseRadius()
                        <= ArchmageV5CombatPolicy.REPULSE_RADIUS_MAX,
                "the repulse radius is inside 3.5..4.5");
        check(ArchmageV5CombatPolicy.repulsePush()
                        >= ArchmageV5CombatPolicy.REPULSE_PUSH_MIN
                        && ArchmageV5CombatPolicy.repulsePush()
                        <= ArchmageV5CombatPolicy.REPULSE_PUSH_MAX,
                "the push is inside 2.5..4");
        check(ArchmageV5CombatPolicy.repulseHits(4.4D, 0.0D),
                "a rusher inside the radius is pushed");
        check(!ArchmageV5CombatPolicy.repulseHits(4.6D, 0.0D),
                "a player who stepped out during the windup is safe");
        check(!ArchmageV5CombatPolicy.repulseHits(2.0D, 3.0D),
                "a player on a high ledge above the caster is not hit");
        check(!ArchmageV5CombatPolicy.repulseWarranted(1, 10_000L, 0L, false),
                "one rusher does not trigger a defensive spell");
        check(ArchmageV5CombatPolicy.repulseWarranted(2, 10_000L, 0L, false),
                "a crowd on cooldown-ready triggers the repulse");
        check(!ArchmageV5CombatPolicy.repulseWarranted(4, 1_000L, 900L, false),
                "the repulse is refused before its cooldown elapses");
        check(!ArchmageV5CombatPolicy.repulseWarranted(4, 10_000L, 0L, true),
                "the Crown owns the cast slot");
    }

    private static void timingsStayInsideTheRatifiedBands() {
        check(ArchmageV5CombatPolicy.FANG_LINE_WARNING_TICKS >= 14
                        && ArchmageV5CombatPolicy.FANG_LINE_WARNING_TICKS <= 20,
                "the Fang Line warning is 0.7..1.0 s");
        check(ArchmageV5CombatPolicy.CIRCLE_WARNING_TICKS >= 20
                        && ArchmageV5CombatPolicy.CIRCLE_WARNING_TICKS <= 26,
                "the Circle warning is 1.0..1.3 s");
        check(ArchmageV5CombatPolicy.REPULSE_WARNING_TICKS >= 10
                        && ArchmageV5CombatPolicy.REPULSE_WARNING_TICKS <= 14,
                "the Repulse warning is 0.5..0.7 s");
        check(ArchmageV5CombatPolicy.CROWN_COOLDOWN_MIN_TICKS >= 900
                        && ArchmageV5CombatPolicy.CROWN_COOLDOWN_MAX_TICKS <= 1200,
                "the Crown cooldown is 45..60 s");
        check(ArchmageV5CombatPolicy.cooldownTicks(Action.FANG_LINE) > 0,
                "each new action owns a cooldown");
        check(ArchmageV5CombatPolicy.warningTicks(Action.NONE) == 0,
                "a non-action has no tell");
        check(!ArchmageV5CombatPolicy.allowedDuringCrown(Action.CIRCLE_OF_FANGS)
                        && !ArchmageV5CombatPolicy.allowedDuringCrown(Action.FANG_LINE),
                "no incompatible cast may interrupt the Crown");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
