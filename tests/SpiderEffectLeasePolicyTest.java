package me.copimine.endevent.domain;

public final class SpiderEffectLeasePolicyTest {
    public static void main(String[] args) {
        preservesAndRestoresWeakerFiniteEffects();
        preservesEqualStrongerAndInfiniteEffects();
        overlappingHitsKeepTheFirstSnapshotAndOwner();
        staleGenerationCannotTakeOverAnExistingLease();
        foreignReplacementSurvivesRelease();
        sameSignatureForeignRefreshSurvivesRelease();
        expiredOriginalIsNotRestarted();
        System.out.println("SpiderEffectLeasePolicyTest OK");
    }

    private static void preservesAndRestoresWeakerFiniteEffects() {
        var owner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var previous = new SpiderEffectLeasePolicy.Effect(0, 200, false, true, true);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);

        var begin = SpiderEffectLeasePolicy.applyOrRefresh(owner, null, previous, 100L, applied);
        check(begin.action() == SpiderEffectLeasePolicy.ApplyAction.APPLIED,
                "a weaker finite effect can be temporarily replaced");
        check(begin.lease().original().equals(previous), "the original effect is saved");
        check(begin.lease().owner().equals(owner), "the lease records event and generation ownership");

        var naturallyTicked = new SpiderEffectLeasePolicy.Effect(2, 80, false, true, true);
        var release = SpiderEffectLeasePolicy.release(owner, begin.lease(), naturallyTicked, 140L, true);
        check(release.action() == SpiderEffectLeasePolicy.ReleaseAction.REMOVED_OWNED_EFFECT,
                "release removes the still-owned spider effect");
        check(release.restoredEffect().equals(new SpiderEffectLeasePolicy.Effect(0, 160, false, true, true)),
                "release restores the original with elapsed ticks subtracted");
    }

    private static void preservesEqualStrongerAndInfiniteEffects() {
        var owner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);
        for (var prior : new SpiderEffectLeasePolicy.Effect[] {
                new SpiderEffectLeasePolicy.Effect(2, 200, false, true, true),
                new SpiderEffectLeasePolicy.Effect(3, 40, false, true, true),
                new SpiderEffectLeasePolicy.Effect(0, -1, false, true, true)}) {
            var result = SpiderEffectLeasePolicy.applyOrRefresh(owner, null, prior, 100L, applied);
            check(result.action() == SpiderEffectLeasePolicy.ApplyAction.PRESERVED_EXISTING,
                    "equal, stronger, and infinite prior effects remain untouched");
            check(result.lease() == null, "a preserved prior effect is not claimed by the spider lease");
        }
    }

    private static void overlappingHitsKeepTheFirstSnapshotAndOwner() {
        var owner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var previous = new SpiderEffectLeasePolicy.Effect(1, 200, true, false, true);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);
        var first = SpiderEffectLeasePolicy.applyOrRefresh(owner, null, previous, 100L, applied);
        var refreshedEffect = new SpiderEffectLeasePolicy.Effect(2, 90, false, true, true);

        var overlap = SpiderEffectLeasePolicy.applyOrRefresh(owner, first.lease(), refreshedEffect, 130L, applied);
        check(overlap.action() == SpiderEffectLeasePolicy.ApplyAction.REFRESHED,
                "a matching active lease is refreshed by another hit");
        check(overlap.lease().original().equals(previous), "overlap does not replace the first snapshot");
        check(overlap.lease().capturedAtTick() == 100L, "overlap keeps the first capture tick");
        check(overlap.lease().appliedAtTick() == 130L, "overlap records the latest application tick");
        check(overlap.lease().owner().equals(owner), "overlap stays owned by the same generation");
    }

    private static void staleGenerationCannotTakeOverAnExistingLease() {
        var originalOwner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var newOwner = new SpiderEffectLeasePolicy.Owner("event-a", 5L);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);
        var first = SpiderEffectLeasePolicy.applyOrRefresh(originalOwner, null, null, 100L, applied);

        var stale = SpiderEffectLeasePolicy.applyOrRefresh(newOwner, first.lease(), applied, 110L, applied);
        check(stale.action() == SpiderEffectLeasePolicy.ApplyAction.OWNER_MISMATCH,
                "a new generation must release the old lease before applying its own effect");
        check(stale.lease().owner().equals(originalOwner), "owner mismatch keeps the old lease available for cleanup");
    }

    private static void foreignReplacementSurvivesRelease() {
        var owner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);
        var lease = SpiderEffectLeasePolicy.applyOrRefresh(owner, null, null, 100L, applied).lease();
        var foreign = new SpiderEffectLeasePolicy.Effect(3, 300, true, false, false);

        var release = SpiderEffectLeasePolicy.release(owner, lease, foreign, 110L, true);
        check(release.action() == SpiderEffectLeasePolicy.ReleaseAction.FOREIGN_EFFECT_RETAINED,
                "release notices that another source replaced the owned effect");
        check(!release.removeCurrentEffect(), "foreign effect is not removed");
        check(release.restoredEffect() == null, "the old snapshot is not restored over the foreign effect");
    }

    private static void sameSignatureForeignRefreshSurvivesRelease() {
        var owner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);
        var lease = SpiderEffectLeasePolicy.applyOrRefresh(owner, null, null, 100L, applied).lease();
        var foreignRefresh = new SpiderEffectLeasePolicy.Effect(2, 60, false, true, true);

        var release = SpiderEffectLeasePolicy.release(owner, lease, foreignRefresh, 110L, true);
        check(release.action() == SpiderEffectLeasePolicy.ReleaseAction.FOREIGN_EFFECT_RETAINED,
                "a same-signature effect refreshed by another source must not be removed");
        check(!release.removeCurrentEffect(), "foreign same-signature effect remains active");
        check(release.restoredEffect() == null, "do not replace the foreign same-signature effect with a snapshot");
    }

    private static void expiredOriginalIsNotRestarted() {
        var owner = new SpiderEffectLeasePolicy.Owner("event-a", 4L);
        var previous = new SpiderEffectLeasePolicy.Effect(0, 20, false, true, true);
        var applied = new SpiderEffectLeasePolicy.Effect(2, 120, false, true, true);
        var lease = SpiderEffectLeasePolicy.applyOrRefresh(owner, null, previous, 100L, applied).lease();

        var naturallyTicked = new SpiderEffectLeasePolicy.Effect(2, 80, false, true, true);
        var release = SpiderEffectLeasePolicy.release(owner, lease, naturallyTicked, 140L, true);
        check(release.removeCurrentEffect(), "the still-owned spider effect is removed");
        check(release.restoredEffect() == null, "an expired original effect is not restarted");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
