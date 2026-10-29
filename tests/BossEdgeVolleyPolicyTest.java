import me.copimine.endevent.domain.BossEdgeVolleyPolicy;
import me.copimine.endevent.domain.BossPhase;

public final class BossEdgeVolleyPolicyTest {
    public static void main(String[] args) {
        require(BossEdgeVolleyPolicy.SALVO_COUNT == 5,
                "the edge volley must fire exactly five salvos");
        require(BossEdgeVolleyPolicy.COOLDOWN_MILLIS >= 60_000L
                        && BossEdgeVolleyPolicy.COOLDOWN_MILLIS <= 90_000L,
                "the edge volley cooldown must stay in the authored 60-90 s window");
        for (BossPhase phase : new BossPhase[]{
                BossPhase.RIFT, BossPhase.OVERLOAD, BossPhase.RAGE}) {
            require(BossEdgeVolleyPolicy.allowsPhase(phase),
                    phase + " must allow the edge volley");
        }
        for (BossPhase phase : new BossPhase[]{
                BossPhase.AWAKENING, BossPhase.HUNT, BossPhase.LAST_SEAL}) {
            require(!BossEdgeVolleyPolicy.allowsPhase(phase),
                    phase + " must not allow the edge volley");
        }
        require(BossEdgeVolleyPolicy.onCooldown(1_000L, 2_000L),
                "an early recast must be gated by the cooldown");
        require(!BossEdgeVolleyPolicy.onCooldown(2_000L, 2_000L),
                "the cooldown must expire at the deadline");
        require(BossEdgeVolleyPolicy.decide(true, true, false)
                        == BossEdgeVolleyPolicy.Decision.START,
                "an allowed phase, an expired cooldown and a safe corner must start the volley");
        require(BossEdgeVolleyPolicy.decide(true, false, false)
                        == BossEdgeVolleyPolicy.Decision.NO_CORNER,
                "a missing safe corner must abort the volley before any salvo is scheduled");
        require(BossEdgeVolleyPolicy.decide(true, true, true)
                        == BossEdgeVolleyPolicy.Decision.GATED,
                "a volley inside the cooldown must stay gated");
        require(BossEdgeVolleyPolicy.decide(false, true, false)
                        == BossEdgeVolleyPolicy.Decision.GATED,
                "a phase that does not allow the volley must stay gated");
        require(BossEdgeVolleyPolicy.relocated(0.0D) && BossEdgeVolleyPolicy.relocated(7.5D),
                "a boss that reached the corner must count as repositioned");
        require(!BossEdgeVolleyPolicy.relocated(20.0D),
                "a boss that never reached the corner must count as a failed reposition");
        require(!BossEdgeVolleyPolicy.relocated(Double.NaN),
                "an unknown distance must count as a failed reposition");
        require(BossEdgeVolleyPolicy.ARRIVAL_AIM_TICKS >= 10L,
                "the boss needs a readable aim window before the first salvo");
        require(BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, true, true, true),
                "a live RIFT boss with a legal target keeps firing its salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.LAST_SEAL, true, true, true),
                "entering LAST_SEAL must cancel the remaining salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, false, true, true),
                "a finished encounter must cancel the remaining salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, true, false, true),
                "a dead boss must cancel the remaining salvos");
        require(!BossEdgeVolleyPolicy.salvoAllowed(BossPhase.RIFT, true, true, false),
                "an illegal target must cancel the remaining salvos");

        // Spec 3.2 sampling policy: ~4-block radius observed for ~2-3 s.
        require(BossEdgeVolleyPolicy.CLUSTER_RADIUS_BLOCKS > 0.0D
                        && BossEdgeVolleyPolicy.CLUSTER_RADIUS_BLOCKS <= 6.0D,
                "the cluster radius must stay near the authored ~4 blocks");
        require(BossEdgeVolleyPolicy.CLUSTER_DWELL_MILLIS >= 1_500L
                        && BossEdgeVolleyPolicy.CLUSTER_DWELL_MILLIS <= 3_500L,
                "the dwell must stay inside the authored 2-3 s window");

        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        java.util.UUID c = java.util.UUID.randomUUID();
        java.util.UUID d = java.util.UUID.randomUUID();

        // A party split 3+1 away from the boss: the trio is the majority.
        java.util.List<BossEdgeVolleyPolicy.ClusterPoint> majority = java.util.List.of(
                new BossEdgeVolleyPolicy.ClusterPoint(a, 20.0D, 20.0D),
                new BossEdgeVolleyPolicy.ClusterPoint(b, 21.5D, 20.5D),
                new BossEdgeVolleyPolicy.ClusterPoint(c, 20.5D, 22.0D),
                new BossEdgeVolleyPolicy.ClusterPoint(d, -30.0D, -30.0D));
        BossEdgeVolleyPolicy.Cluster cluster = BossEdgeVolleyPolicy.majorityCluster(majority, 0.0D, 0.0D);
        require(cluster != null && cluster.members().size() == 3
                        && cluster.members().contains(a) && cluster.members().contains(b)
                        && cluster.members().contains(c) && !cluster.members().contains(d),
                "a 3+1 split must resolve to the majority trio, never the lone outlier");

        // One far outlier is not the whole formation.
        require(BossEdgeVolleyPolicy.majorityCluster(
                        java.util.List.of(new BossEdgeVolleyPolicy.ClusterPoint(a, 40.0D, 0.0D)), 0.0D, 0.0D) == null,
                "a single distant player must not count as a majority cluster");
        // An evenly split party has no majority.
        require(BossEdgeVolleyPolicy.majorityCluster(java.util.List.of(
                        new BossEdgeVolleyPolicy.ClusterPoint(a, 20.0D, 20.0D),
                        new BossEdgeVolleyPolicy.ClusterPoint(b, -20.0D, -20.0D)), 0.0D, 0.0D) == null,
                "a two-way split has no majority cluster");
        // A majority hugging the boss is close pressure, not a far cluster.
        require(BossEdgeVolleyPolicy.majorityCluster(java.util.List.of(
                        new BossEdgeVolleyPolicy.ClusterPoint(a, 1.0D, 1.0D),
                        new BossEdgeVolleyPolicy.ClusterPoint(b, 2.0D, 1.5D),
                        new BossEdgeVolleyPolicy.ClusterPoint(c, 1.5D, 2.0D)), 0.0D, 0.0D) == null,
                "a cluster standing on the boss must not trigger the corner volley");
        require(BossEdgeVolleyPolicy.majorityCluster(java.util.List.of(), 0.0D, 0.0D) == null,
                "an empty roster has no cluster");

        // The dwell gate: never true on the first observation.
        BossEdgeVolleyPolicy.ClusterDwell dwell = new BossEdgeVolleyPolicy.ClusterDwell();
        long start = 1_000_000L;
        require(!dwell.observe(true, start), "the dwell must not fire on a single tick");
        require(!dwell.observe(true, start + BossEdgeVolleyPolicy.CLUSTER_DWELL_MILLIS - 1L),
                "the dwell must not fire before it has been held long enough");
        require(dwell.observe(true, start + BossEdgeVolleyPolicy.CLUSTER_DWELL_MILLIS),
                "the dwell must fire once held for the full window");
        require(!dwell.observe(false, start + BossEdgeVolleyPolicy.CLUSTER_DWELL_MILLIS + 1L),
                "any break must reset the dwell");
        require(!dwell.observe(true, start + BossEdgeVolleyPolicy.CLUSTER_DWELL_MILLIS + 50L),
                "a reset dwell must start counting again from scratch");
        dwell.reset();
        require(!dwell.satisfied(), "reset must clear a satisfied dwell");
        System.out.println("BossEdgeVolleyPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}