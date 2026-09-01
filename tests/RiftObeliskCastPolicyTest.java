import me.copimine.endevent.domain.BossStage;
import me.copimine.endevent.domain.RiftObeliskCastPolicy;

public final class RiftObeliskCastPolicyTest {
    public static void main(String[] args) {
        testOnlyUnusedDistortionFightCanStartObelisks();
        testDestroyedObelisksStayConsumedForTheFight();
        testNewFightCanStartAFreshSet();
        System.out.println("RiftObeliskCastPolicyTest OK");
    }

    private static void testOnlyUnusedDistortionFightCanStartObelisks() {
        check(RiftObeliskCastPolicy.canStart(
                        BossStage.DISTORTION, true, false, false),
                "an unused DISTORTION fight must allow the first obelisk cast");
        check(!RiftObeliskCastPolicy.canStart(
                        BossStage.AWAKENING, true, false, false),
                "obelisk cast must be restricted to DISTORTION");
        check(!RiftObeliskCastPolicy.canStart(
                        BossStage.DISTORTION, false, false, false),
                "disabled obelisk configuration must fail closed");
        check(!RiftObeliskCastPolicy.canStart(
                        BossStage.DISTORTION, true, false, true),
                "a second active obelisk set must not overlap the first one");
    }

    private static void testDestroyedObelisksStayConsumedForTheFight() {
        check(!RiftObeliskCastPolicy.canStart(
                        BossStage.DISTORTION, true, true, false),
                "destroyed obelisks must not be respawned in the same boss fight");
        check(!RiftObeliskCastPolicy.canStart(
                        BossStage.DISTORTION, true, true, true),
                "the one-shot guard must also win while stale active state exists");
    }

    private static void testNewFightCanStartAFreshSet() {
        check(RiftObeliskCastPolicy.canStart(
                        BossStage.DISTORTION, true, false, false),
                "a new boss fight must receive a fresh one-shot allowance");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
