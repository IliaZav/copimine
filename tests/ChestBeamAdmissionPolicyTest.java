import me.copimine.endevent.domain.ChestBeamAdmissionPolicy;
import me.copimine.endevent.domain.ChestBeamAdmissionPolicy.BodySample;
import me.copimine.endevent.domain.ChestBeamAdmissionPolicy.Decision;

/**
 * N23-P1-04: the signature ultimate must resolve its locked lane's damage
 * against the same body point it samples for solid cover, and it must separate
 * an attempted hit from an accepted one so a refused damage event can never be
 * labelled a successful hit — while still not retrying that player every tick.
 */
public final class ChestBeamAdmissionPolicyTest {

    public static void main(String[] args) {
        damageableSampleIsTorsoNotEye();
        coverFollowsTheDamageableBody();
        headOnlyVisibilityDoesNotBecomeABodyHit();
        inLaneClearBodyIsAdmitted();
        outOfLaneIsNeverAnAttempt();
        alreadyAttemptedIsNeverRetried();
        coveredBodyIsNeverAdmitted();
        precedenceIsStable();
        onlyAttemptIsAdmitted();
        System.out.println("ChestBeamAdmissionPolicyTest OK");
    }

    private static void damageableSampleIsTorsoNotEye() {
        check(ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE == BodySample.TORSO,
                "the damaged body sample must be the torso, not the eye");
    }

    private static void coverFollowsTheDamageableBody() {
        // A wall shields the torso but the head peeks over it: the shot is
        // covered because the body the beam damages is behind solid cover.
        check(ChestBeamAdmissionPolicy.covered(
                        ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE, true, false),
                "a body behind cover must stay covered even with a visible head");
        check(ChestBeamAdmissionPolicy.covered(
                        ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE, true, true),
                "a fully blocked body is covered");
        check(!ChestBeamAdmissionPolicy.covered(
                        ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE, false, false),
                "an open body is not covered");
    }

    private static void headOnlyVisibilityDoesNotBecomeABodyHit() {
        // A slab at head height only blocks the eye; the torso is still open, so
        // the body sample does not treat it as cover.
        check(!ChestBeamAdmissionPolicy.covered(
                        ChestBeamAdmissionPolicy.DAMAGEABLE_SAMPLE, false, true),
                "a head-height slab must not cover the damageable torso");
        // The rejected rule: sampling the eye would have let the shielded body be
        // hit. This documents that the eye is not the authoritative sample.
        check(!ChestBeamAdmissionPolicy.covered(BodySample.EYE, true, false),
                "the eye sample would wrongly clear a body-covered target");
        check(ChestBeamAdmissionPolicy.covered(BodySample.EYE, false, true),
                "the eye sample blocks only on the eye ray");
    }

    private static void inLaneClearBodyIsAdmitted() {
        check(ChestBeamAdmissionPolicy.decide(true, false, false) == Decision.ATTEMPT,
                "an in-lane, uncovered, un-attempted body is admitted");
        check(ChestBeamAdmissionPolicy.admitted(Decision.ATTEMPT),
                "ATTEMPT must be the admitted decision");
    }

    private static void outOfLaneIsNeverAnAttempt() {
        check(ChestBeamAdmissionPolicy.decide(false, false, false) == Decision.SKIP_OUT_OF_LANE,
                "a body outside every locked lane is skipped");
        check(ChestBeamAdmissionPolicy.decide(false, true, true) == Decision.SKIP_OUT_OF_LANE,
                "being out of lane dominates the other reasons");
    }

    private static void alreadyAttemptedIsNeverRetried() {
        check(ChestBeamAdmissionPolicy.decide(true, false, true) == Decision.SKIP_ATTEMPTED,
                "a body already attempted this beam must not be retried");
        // A refused (e.g. protected) damage event must not become a per-tick
        // re-roll: the attempt is remembered even though no hit was accepted.
        check(ChestBeamAdmissionPolicy.decide(true, true, true) == Decision.SKIP_ATTEMPTED,
                "an attempted body stays attempted even if it is now covered");
    }

    private static void coveredBodyIsNeverAdmitted() {
        check(ChestBeamAdmissionPolicy.decide(true, true, false) == Decision.SKIP_COVERED,
                "a covered body is skipped, never hit through terrain");
    }

    private static void precedenceIsStable() {
        check(ChestBeamAdmissionPolicy.decide(false, true, true) == Decision.SKIP_OUT_OF_LANE,
                "order: out-of-lane before attempted before covered");
        check(ChestBeamAdmissionPolicy.decide(true, true, true) == Decision.SKIP_ATTEMPTED,
                "order: attempted before covered");
    }

    private static void onlyAttemptIsAdmitted() {
        for (Decision decision : Decision.values()) {
            check(ChestBeamAdmissionPolicy.admitted(decision) == (decision == Decision.ATTEMPT),
                    "only ATTEMPT is admitted, got " + decision);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
