import java.util.Optional;
import java.util.UUID;
import me.copimine.endevent.domain.Wave7SkeletonPairPolicy;
import me.copimine.endevent.domain.Wave7SkeletonPairPolicy.Flank;
import me.copimine.endevent.domain.Wave7SkeletonPairPolicy.JointReadiness;
import me.copimine.endevent.domain.Wave7SkeletonPairPolicy.Member;
import me.copimine.endevent.domain.Wave7SkeletonPairPolicy.Order;

/**
 * Two Wave 7 skeletons must behave as a pair, not as two solos that collide.
 *
 * <p>The user asked for exactly this: "скелетам нормальную работу в паре, чтобы
 * они не мешали друг другу и у них был симбиоз", plus one extra co-op skill
 * they cast together. These checks pin the three rules that make that true —
 * fixed lanes, alternating casts, and a joint crossfire with a real charge.</p>
 */
public final class Wave7SkeletonPairPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        flanksAreStableAndOpposite();
        lanesNeverCollideWithTheTargetLine();
        partnersAlternateInsteadOfVolleyingTogether();
        aCoveringPartnerNeverCasts();
        jointAttackNeedsTheWholePair();
        jointAttackRespectsItsCooldown();
        aHiddenPartnerBlocksTheJointAttack();
        crossfireLinesCrossInFrontOfTheTarget();
        refusalReasonsAreReported();
        System.out.println(failures == 0 ? "Wave7SkeletonPairPolicyTest: PASS"
                : "Wave7SkeletonPairPolicyTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static void flanksAreStableAndOpposite() {
        UUID first = new UUID(2L, 7L);
        UUID second = new UUID(3L, 7L);
        check("flank is deterministic", Wave7SkeletonPairPolicy.flankOf(first)
                == Wave7SkeletonPairPolicy.flankOf(first));
        check("odd and even ids take opposite flanks",
                Wave7SkeletonPairPolicy.flankOf(first) != Wave7SkeletonPairPolicy.flankOf(second));
        boolean rejected = false;
        try {
            Wave7SkeletonPairPolicy.flankOf(null);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check("a missing actor is refused", rejected);
    }

    private static void lanesNeverCollideWithTheTargetLine() {
        double left = Wave7SkeletonPairPolicy.laneOffsetBlocks(Flank.LEFT);
        double right = Wave7SkeletonPairPolicy.laneOffsetBlocks(Flank.RIGHT);
        check("lanes are mirrored", Math.abs(left + right) < 1.0E-9D);
        check("lanes are not zero", left != 0.0D && right != 0.0D);
        check("lanes are wide enough to not stack",
                Math.abs(left - right) >= 4.0D);
    }

    private static void partnersAlternateInsteadOfVolleyingTogether() {
        Member first = member("a", Flank.LEFT, false, true, 0L);
        Member partnerCasting = member("b", Flank.RIGHT, true, true, 0L);
        check("a free member covers while the partner casts",
                Wave7SkeletonPairPolicy.orderFor(first, partnerCasting, readiness(false)) == Order.COVER);
        Member partnerFree = member("b", Flank.RIGHT, false, false, 0L);
        check("with nobody casting a member casts",
                Wave7SkeletonPairPolicy.orderFor(first, partnerFree, readiness(false)) == Order.CAST);
        check("a busy member keeps casting",
                Wave7SkeletonPairPolicy.orderFor(partnerCasting, first, readiness(false)) == Order.CAST);
    }

    private static void aCoveringPartnerNeverCasts() {
        Member blind = member("a", Flank.LEFT, false, false, 0L);
        Member partner = member("b", Flank.RIGHT, false, false, 0L);
        check("no line of sight means hold the lane, never a blind cast",
                Wave7SkeletonPairPolicy.orderFor(blind, partner, readiness(false)) == Order.HOLD_LANE);
        Member hidden = member("a", Flank.LEFT, false, false, 0L);
        // hidden is encoded through the busy/casting flag pair in member()
        Member hiddenSelf = new Member(hidden.actorId(), Flank.LEFT, 4.0D, true, false, true, 0L);
        check("a hidden member holds the lane",
                Wave7SkeletonPairPolicy.orderFor(hiddenSelf, partner, readiness(false)) == Order.HOLD_LANE);
    }

    private static void jointAttackNeedsTheWholePair() {
        Member first = member("a", Flank.LEFT, false, true, 0L);
        Member second = member("b", Flank.RIGHT, false, true, 0L);
        check("a charged, close, free pair is ready",
                Wave7SkeletonPairPolicy.jointReadiness(first, second,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L).ready());
        check("an uncharged pair is not ready",
                !Wave7SkeletonPairPolicy.jointReadiness(first, second,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS - 1L, 0L).ready());
        Member far = new Member(new UUID(9L, 9L), Flank.RIGHT,
                (Wave7SkeletonPairPolicy.JOINT_MAX_DISTANCE_BLOCKS + 5.0D)
                        * (Wave7SkeletonPairPolicy.JOINT_MAX_DISTANCE_BLOCKS + 5.0D),
                true, false, false, 0L);
        check("a distant partner stops the joint attack",
                !Wave7SkeletonPairPolicy.jointReadiness(first, far,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L).ready());
        Member sameActor = member("a", Flank.RIGHT, false, true, 0L);
        check("the same actor twice is not a pair",
                !Wave7SkeletonPairPolicy.jointReadiness(first, sameActor,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L).ready());
        check("the joint attack outranks a solo cast",
                Wave7SkeletonPairPolicy.orderFor(first, second,
                        Wave7SkeletonPairPolicy.jointReadiness(first, second,
                                Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L)) == Order.JOINT_CROSSFIRE);
    }

    private static void jointAttackRespectsItsCooldown() {
        Member first = member("a", Flank.LEFT, false, true, 0L);
        Member second = member("b", Flank.RIGHT, false, true, 0L);
        long last = 1000L;
        JointReadiness tooSoon = Wave7SkeletonPairPolicy.jointReadiness(first, second,
                last + Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS - 1L, last);
        check("the joint attack cannot be spammed", !tooSoon.ready());
        JointReadiness onTime = Wave7SkeletonPairPolicy.jointReadiness(first, second,
                last + Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, last);
        check("the joint attack returns after its cooldown", onTime.ready());
    }

    private static void aHiddenPartnerBlocksTheJointAttack() {
        Member first = member("a", Flank.LEFT, false, true, 0L);
        Member hidden = new Member(new UUID(3L, 3L), Flank.RIGHT, 9.0D, true, false, true, 0L);
        check("a hidden partner cannot join the joint attack",
                !Wave7SkeletonPairPolicy.jointReadiness(first, hidden,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L).ready());
        Member busy = member("b", Flank.RIGHT, true, true, 0L);
        check("a casting partner cannot join either",
                !Wave7SkeletonPairPolicy.jointReadiness(first, busy,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L).ready());
    }

    private static void crossfireLinesCrossInFrontOfTheTarget() {
        double left = Wave7SkeletonPairPolicy.crossfireOffsetDegrees(Flank.LEFT);
        double right = Wave7SkeletonPairPolicy.crossfireOffsetDegrees(Flank.RIGHT);
        check("crossfire is mirrored", Math.abs(left + right) < 1.0E-9D);
        check("crossfire is not a single line", left != 0.0D && right != 0.0D);
        check("crossfire stays readable", Math.abs(left) <= 20.0D);
    }

    private static void refusalReasonsAreReported() {
        Member first = member("a", Flank.LEFT, false, true, 0L);
        Member second = member("b", Flank.RIGHT, false, true, 0L);
        check("a ready pair has no refusal", Wave7SkeletonPairPolicy.refusalReason(
                Wave7SkeletonPairPolicy.jointReadiness(first, second,
                        Wave7SkeletonPairPolicy.JOINT_COOLDOWN_TICKS, -1L)).isEmpty());
        check("charging is reported", "charging".equals(Wave7SkeletonPairPolicy.refusalReason(
                Wave7SkeletonPairPolicy.jointReadiness(first, second, 1L, 0L)).orElse("")));
        Optional<String> none = Wave7SkeletonPairPolicy.refusalReason(null);
        check("a missing readiness is reported", none.isPresent());
    }

    private static JointReadiness readiness(boolean ready) {
        return new JointReadiness(ready, true, true, true, 0L);
    }

    private static Member member(String id, Flank flank, boolean busy, boolean lineOfSight, long charge) {
        return new Member(UUID.nameUUIDFromBytes(id.getBytes()), flank, 9.0D, lineOfSight, busy,
                false, charge);
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
