import me.copimine.endevent.domain.BossAnimationId;
import me.copimine.endevent.domain.GroundedBossMotionPolicy;

public final class GroundedBossMotionPolicyTest {
    public static void main(String[] args) {
        testOrdinaryGroundedPursuitWalksWithoutVerticalCarryover();
        testOrdinaryAirbornePursuitAlsoClearsVerticalCarryover();
        testOrdinaryAirbornePursuitPreservesGravity();
        testSettledPursuitUsesIdle();
        testScriptedMotionKeepsItsVerticalState();
        System.out.println("GroundedBossMotionPolicyTest OK");
    }

    private static void testOrdinaryGroundedPursuitWalksWithoutVerticalCarryover() {
        GroundedBossMotionPolicy.Resolution walking = GroundedBossMotionPolicy.resolve(
                true, true, 0.16D, 0.18D);
        check(!walking.allowTeleport(), "ordinary pursuit cannot teleport");
        check(walking.appliedVerticalVelocity() == 0.0D,
                "grounded pursuit clears stale Y velocity");
        GroundedBossMotionPolicy.Resolution settled = GroundedBossMotionPolicy.resolve(
                true, true, 0.16D, -0.08D);
        check(settled.appliedVerticalVelocity() == 0.0D,
                "grounded pursuit does not accumulate downward velocity");
        check(walking.animation() == BossAnimationId.RUN,
                "horizontal movement selects the authored run clip");
    }

    private static void testSettledPursuitUsesIdle() {
        GroundedBossMotionPolicy.Resolution idle = GroundedBossMotionPolicy.resolve(
                true, true, 0.01D, 0.0D);
        check(idle.animation() == BossAnimationId.IDLE_BREATH,
                "settled guardian selects the authored idle clip");
    }

    private static void testOrdinaryAirbornePursuitAlsoClearsVerticalCarryover() {
        GroundedBossMotionPolicy.Resolution recovering = GroundedBossMotionPolicy.resolve(
                true, false, 0.16D, 0.42D);
        check(recovering.appliedVerticalVelocity() == 0.0D,
                "ordinary pursuit clears an airborne jump impulse");
    }

    private static void testOrdinaryAirbornePursuitPreservesGravity() {
        GroundedBossMotionPolicy.Resolution falling = GroundedBossMotionPolicy.resolve(
                true, false, 0.16D, -0.08D);
        check(falling.appliedVerticalVelocity() == -0.08D,
                "airborne pursuit preserves downward velocity so gravity can land the boss");
    }

    private static void testScriptedMotionKeepsItsVerticalState() {
        GroundedBossMotionPolicy.Resolution scripted = GroundedBossMotionPolicy.resolve(
                false, false, 0.0D, 0.35D);
        check(scripted.appliedVerticalVelocity() == 0.35D,
                "scripted motion retains its own Y state");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
