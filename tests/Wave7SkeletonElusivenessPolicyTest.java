import me.copimine.endevent.domain.Wave7SkeletonElusivenessPolicy;
import me.copimine.endevent.domain.Wave7SkeletonPairPolicy;

/** The paired Markmen: fast, slippery, provoking — and still asurable. */
public final class Wave7SkeletonElusivenessPolicyTest {
    private static int checks;

    private Wave7SkeletonElusivenessPolicyTest() {
    }

    public static void main(String[] args) {
        theFirstHideIsAlwaysAllowed();
        aHideIsShortAndRepeats();
        castingBlocksTheHide();
        aVanishedSkeletonDoesNotShoot();
        theGraceWindowGuaranteesAShotWindow();
        flankDriftIsMirroredAndBounded();
        speedRewardsTheSlipperyStance();
        provokingNeedsALivingPartner();
        refusalsAreNamed();
        System.out.println("Wave7SkeletonElusivenessPolicyTest: PASS (" + checks + " checks)");
    }

    private static void theFirstHideIsAlwaysAllowed() {
        check("a fresh skeleton may hide",
                Wave7SkeletonElusivenessPolicy.mayVanish(100L, -1L, false, false));
        check("a fresh skeleton that is casting may not hide",
                !Wave7SkeletonElusivenessPolicy.mayVanish(100L, -1L, true, false));
    }

    private static void aHideIsShortAndRepeats() {
        check("the hide is short enough to read", Wave7SkeletonElusivenessPolicy.VANISH_TICKS <= 40);
        check("the hide still exists", Wave7SkeletonElusivenessPolicy.VANISH_TICKS >= 10);
        long start = 500L;
        check("hidden at the start",
                Wave7SkeletonElusivenessPolicy.hidden(start, start));
        check("hidden one tick before the end",
                Wave7SkeletonElusivenessPolicy.hidden(
                        start + Wave7SkeletonElusivenessPolicy.VANISH_TICKS - 1L, start));
        check("visible on the reveal tick",
                !Wave7SkeletonElusivenessPolicy.hidden(
                        start + Wave7SkeletonElusivenessPolicy.VANISH_TICKS, start));
        // The pair must be able to hide again while a fight is still running.
        check("a hide repeats within a short fight",
                Wave7SkeletonElusivenessPolicy.VANISH_COOLDOWN_TICKS < 200);
    }

    private static void castingBlocksTheHide() {
        long since = Wave7SkeletonElusivenessPolicy.VANISH_COOLDOWN_TICKS
                + Wave7SkeletonElusivenessPolicy.REAPPEAR_GRACE_TICKS + 5L;
        check("casting is refused", !Wave7SkeletonElusivenessPolicy.mayVanish(since, 0L, true, false));
        check("not casting is allowed", Wave7SkeletonElusivenessPolicy.mayVanish(since, 0L, false, false));
    }

    private static void aVanishedSkeletonDoesNotShoot() {
        long start = 1_000L;
        check("hidden cannot fire", !Wave7SkeletonElusivenessPolicy.mayAttack(start + 1L, start));
        check("recovering cannot fire", !Wave7SkeletonElusivenessPolicy.mayAttack(
                start + Wave7SkeletonElusivenessPolicy.VANISH_TICKS + 1L, start));
        check("fires once the reveal finished", Wave7SkeletonElusivenessPolicy.mayAttack(
                start + Wave7SkeletonElusivenessPolicy.VANISH_TICKS
                        + Wave7SkeletonElusivenessPolicy.VANISH_RECOVERY_TICKS, start));
        check("a never-hidden skeleton fires", Wave7SkeletonElusivenessPolicy.mayAttack(50L, -1L));
    }

    private static void theGraceWindowGuaranteesAShotWindow() {
        long last = 1_000L;
        long cooldownEnd = last + Wave7SkeletonElusivenessPolicy.VANISH_COOLDOWN_TICKS;
        check("still cooling down", !Wave7SkeletonElusivenessPolicy.mayVanish(cooldownEnd - 1L, last, false, false));
        check("inside the grace window the pair cannot re-hide",
                !Wave7SkeletonElusivenessPolicy.mayVanish(cooldownEnd + 1L, last, false, false));
        check("after the grace window it may hide again",
                Wave7SkeletonElusivenessPolicy.mayVanish(
                        cooldownEnd + Wave7SkeletonElusivenessPolicy.REAPPEAR_GRACE_TICKS, last, false, false));
        // The grace window is the whole reason the pair is killable.
        check("the grace window is real", Wave7SkeletonElusivenessPolicy.REAPPEAR_GRACE_TICKS > 0);
    }

    private static void flankDriftIsMirroredAndBounded() {
        for (long tick = 0L; tick < 200L; tick += 7L) {
            double left = Wave7SkeletonElusivenessPolicy.hideDriftBlocks(
                    Wave7SkeletonPairPolicy.Flank.LEFT, tick);
            double right = Wave7SkeletonElusivenessPolicy.hideDriftBlocks(
                    Wave7SkeletonPairPolicy.Flank.RIGHT, tick);
            check("drift is mirrored at " + tick, Math.abs(left + right) < 1e-9);
            check("drift stays bounded at " + tick,
                    Math.abs(left) <= Wave7SkeletonElusivenessPolicy.HIDE_DRIFT_BLOCKS + 1e-9);
        }
        check("the drift is deterministic",
                Wave7SkeletonElusivenessPolicy.hideDriftBlocks(Wave7SkeletonPairPolicy.Flank.LEFT, 13L)
                        == Wave7SkeletonElusivenessPolicy.hideDriftBlocks(
                                Wave7SkeletonPairPolicy.Flank.LEFT, 13L));
    }

    private static void speedRewardsTheSlipperyStance() {
        check("sprinting is faster than lining up", Wave7SkeletonElusivenessPolicy.speed(true, false)
                > Wave7SkeletonElusivenessPolicy.speed(false, false));
        check("vanished bodies keep the sprint",
                Wave7SkeletonElusivenessPolicy.speed(true, true) == Wave7SkeletonElusivenessPolicy.SPEED_SPRINT);
        check("lining up is slow enough to be hit",
                Wave7SkeletonElusivenessPolicy.speed(false, false) < 0.2D);
    }

    private static void provokingNeedsALivingPartner() {
        check("a live partner allows the first provoke",
                Wave7SkeletonElusivenessPolicy.mayProvoke(100L, -1L, true));
        check("a dead partner forbids it", !Wave7SkeletonElusivenessPolicy.mayProvoke(100L, -1L, false));
        check("the provoke cools down", !Wave7SkeletonElusivenessPolicy.mayProvoke(110L, 100L, true));
        check("the provoke comes back",
                Wave7SkeletonElusivenessPolicy.mayProvoke(
                        100L + Wave7SkeletonElusivenessPolicy.GALL_COOLDOWN_TICKS, 100L, true));
    }

    private static void refusalsAreNamed() {
        check("casting is named", "casting".equals(Wave7SkeletonElusivenessPolicy.refusal(true, 100L, 90L)));
        check("cooldown is named",
                "cooldown".equals(Wave7SkeletonElusivenessPolicy.refusal(false, 100L, 95L)));
        check("grace is named", "grace".equals(Wave7SkeletonElusivenessPolicy.refusal(
                false, 100L + Wave7SkeletonElusivenessPolicy.VANISH_COOLDOWN_TICKS, 100L)));
        check("ready is named", "ready".equals(Wave7SkeletonElusivenessPolicy.refusal(false, 10_000L, 100L)));
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
    }
}
