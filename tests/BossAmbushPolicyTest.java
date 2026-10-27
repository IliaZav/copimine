import me.copimine.endevent.domain.BossAmbushPolicy;
import me.copimine.endevent.domain.BossPhase;

/**
 * Behavioural contract for the LAST_SEAL under-player ambush: a valid
 * committed target is always captured, while every refusal is a concrete
 * safety decision instead of a random miss.
 */
public final class BossAmbushPolicyTest {

    public static void main(String[] args) {
        commitsAValidTarget();
        refusesOutsideTheLastSeal();
        refusesAnInvalidOrRestrainedTarget();
        refusesUnsafeGround();
        appliesRecentTargetPressure();
        keepsCommitmentOnlyWhileLegal();
        System.out.println("BossAmbushPolicyTest OK");
    }

    private static void commitsAValidTarget() {
        check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, true, false, true,
                        1_000_000L, 0L) == BossAmbushPolicy.Decision.COMMIT,
                "a legal target in LAST_SEAL must be committed to the grab");
        // Repeating the same inputs must stay deterministic: there is no
        // random success roll in the committed variant.
        for (int attempt = 0; attempt < 64; attempt++) {
            check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, true, false, true,
                            1_000_000L, 0L) == BossAmbushPolicy.Decision.COMMIT,
                    "the committed ambush must not roll a random miss");
        }
    }

    private static void refusesOutsideTheLastSeal() {
        for (BossPhase phase : new BossPhase[]{BossPhase.AWAKENING, BossPhase.HUNT,
                BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            check(BossAmbushPolicy.decide(phase, true, false, true, 1_000L, 0L)
                            == BossAmbushPolicy.Decision.NOT_LAST_SEAL,
                    "the ambush belongs to LAST_SEAL only: " + phase);
        }
    }

    private static void refusesAnInvalidOrRestrainedTarget() {
        check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, false, false, true, 1_000L, 0L)
                        == BossAmbushPolicy.Decision.NO_TARGET,
                "an invalid target must cancel the cast");
        check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, true, true, true, 1_000L, 0L)
                        == BossAmbushPolicy.Decision.ALREADY_RESTRAINED,
                "a target already held by another apparition must cancel the cast");
    }

    private static void refusesUnsafeGround() {
        check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, true, false, false, 1_000L, 0L)
                        == BossAmbushPolicy.Decision.UNSAFE_GROUND,
                "the apparition must not spawn without a solid floor");
    }

    private static void appliesRecentTargetPressure() {
        check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, true, false, true,
                        1_000L + BossAmbushPolicy.RECENT_TARGET_MILLIS, 1_000L)
                        == BossAmbushPolicy.Decision.COMMIT,
                "the pressure window ends exactly at the configured gap");
        check(BossAmbushPolicy.decide(BossPhase.LAST_SEAL, true, false, true,
                        1_000L + BossAmbushPolicy.RECENT_TARGET_MILLIS - 1L, 1_000L)
                        == BossAmbushPolicy.Decision.RECENT_TARGET,
                "a player grabbed moments ago must not be hard-controlled again");
        check(!BossAmbushPolicy.recentlyGrabbed(5_000L, 0L),
                "a target that was never grabbed carries no pressure");
        check(!BossAmbushPolicy.recentlyGrabbed(1_000L, 9_000L),
                "a clock that moved backwards must not trap a target forever");
    }

    private static void keepsCommitmentOnlyWhileLegal() {
        check(BossAmbushPolicy.keepsCommitment(true, true, true),
                "a still-legal committed target keeps the guaranteed grab");
        check(!BossAmbushPolicy.keepsCommitment(false, true, true),
                "a target that became illegal is a safe cancellation");
        check(!BossAmbushPolicy.keepsCommitment(true, false, true),
                "a generation change cancels the commitment");
        check(!BossAmbushPolicy.keepsCommitment(true, true, false),
                "boss death cancels the commitment");
        check(BossAmbushPolicy.GROUND_WARNING_TICKS > 0
                        && BossAmbushPolicy.MAX_CAST_TICKS > BossAmbushPolicy.GROUND_WARNING_TICKS,
                "the cast needs a readable warning inside a bounded lifetime");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}