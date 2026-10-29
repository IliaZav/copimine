package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The corner volley the boss unlocks from RIFT onwards: it repositions to the
 * arena corner farthest from the players and fires exactly five salvos, then
 * the ability is unavailable until the cooldown elapses.
 */
public final class BossEdgeVolleyPolicy {
    public static final int SALVO_COUNT = 5;
    public static final long SALVO_INTERVAL_TICKS = 16L;
    /** Readable arrival + aim window before the first salvo leaves the corner. */
    public static final long ARRIVAL_AIM_TICKS = 20L;
    /** Spec: 60-90 s between volleys. */
    public static final long COOLDOWN_MILLIS = 75_000L;
    public static final double CORNER_INSET_BLOCKS = 2.0D;
    /** A boss that is still this far from the chosen corner has not repositioned. */
    public static final double REPOSITION_TOLERANCE_BLOCKS = 8.0D;
    /** Spec 3.2 initial tuning: ~4-block local clustering radius. */
    public static final double CLUSTER_RADIUS_BLOCKS = 4.0D;
    /** Spec 3.2 initial tuning: a ~2-3 s dwell, never a one-tick condition. */
    public static final long CLUSTER_DWELL_MILLIS = 2_500L;
    /** A cluster hugging the boss is not "away from the boss" (spec 3.2). */
    public static final double CLUSTER_BOSS_SEPARATION_BLOCKS = 8.0D;

    /** A "cluster" needs at least two players; one player is not a formation. */
    public static final int MIN_CLUSTER_MEMBERS = 2;

    private BossEdgeVolleyPolicy() {
    }

    /** One event-local participant position; only x/z matter for clustering. */
    public record ClusterPoint(UUID id, double x, double z) {
        public ClusterPoint {
            if (id == null || !Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("cluster point requires an id and finite x/z");
            }
        }
    }

    /** The majority neighbourhood: its members plus the centroid they occupy. */
    public record Cluster(List<UUID> members, double centreX, double centreZ) {
        public Cluster {
            members = List.copyOf(members);
        }
    }

    /**
     * Spec 3.2: a sustained majority cluster away from the boss. Returns the
     * largest neighbourhood that holds a strict majority of the roster and sits
     * at least {@link #CLUSTER_BOSS_SEPARATION_BLOCKS} from the boss, else null.
     * A single far outlier never counts as the whole formation.
     */
    public static Cluster majorityCluster(List<ClusterPoint> players,
                                          double bossX, double bossZ) {
        if (players == null || players.isEmpty()
                || !Double.isFinite(bossX) || !Double.isFinite(bossZ)) {
            return null;
        }
        int needed = players.size() / 2 + 1;
        double radiusSquared = CLUSTER_RADIUS_BLOCKS * CLUSTER_RADIUS_BLOCKS;
        Cluster best = null;
        for (ClusterPoint anchor : players) {
            List<UUID> members = new ArrayList<>();
            double sumX = 0.0D;
            double sumZ = 0.0D;
            for (ClusterPoint candidate : players) {
                double dx = candidate.x() - anchor.x();
                double dz = candidate.z() - anchor.z();
                if (dx * dx + dz * dz <= radiusSquared) {
                    members.add(candidate.id());
                    sumX += candidate.x();
                    sumZ += candidate.z();
                }
            }
            if (members.size() < needed || members.size() < MIN_CLUSTER_MEMBERS) {
                continue;
            }
            double centreX = sumX / members.size();
            double centreZ = sumZ / members.size();
            double bossDx = centreX - bossX;
            double bossDz = centreZ - bossZ;
            double separationSquared = CLUSTER_BOSS_SEPARATION_BLOCKS * CLUSTER_BOSS_SEPARATION_BLOCKS;
            if (bossDx * bossDx + bossDz * bossDz < separationSquared) {
                continue;
            }
            if (best == null || members.size() > best.members().size()) {
                best = new Cluster(members, centreX, centreZ);
            }
        }
        return best;
    }

    /**
     * Sticky dwell timer: reports true only once the same cluster condition has
     * held continuously for {@link #CLUSTER_DWELL_MILLIS}. Any break resets it.
     */
    public static final class ClusterDwell {
        private long heldSinceMillis;
        private boolean satisfied;

        public boolean observe(boolean clustered, long nowMillis) {
            if (!clustered) {
                heldSinceMillis = 0L;
                satisfied = false;
                return false;
            }
            if (heldSinceMillis == 0L) {
                heldSinceMillis = nowMillis;
            }
            satisfied = nowMillis - heldSinceMillis >= CLUSTER_DWELL_MILLIS;
            return satisfied;
        }

        public boolean satisfied() {
            return satisfied;
        }

        public void reset() {
            heldSinceMillis = 0L;
            satisfied = false;
        }
    }

    public static boolean allowsPhase(BossPhase phase) {
        return phase == BossPhase.RIFT
                || phase == BossPhase.OVERLOAD
                || phase == BossPhase.RAGE;
    }

    public static boolean onCooldown(long nowMillis, long nextAllowedMillis) {
        return nowMillis < nextAllowedMillis;
    }

    /**
     * A salvo that is already scheduled must re-check the boss phase: a boss that
     * enters LAST_SEAL or dies between salvos cancels the rest of the cast.
     */
    public static boolean salvoAllowed(BossPhase phase, boolean bossActive,
                                       boolean bossAlive, boolean targetLegal) {
        return bossActive && bossAlive && targetLegal && allowsPhase(phase);
    }

    /** What a volley attempt should do before any salvo is scheduled. */
    public enum Decision {
        /** Phase allows it, the cooldown expired and the boss reached a safe corner. */
        START,
        /** Wrong phase or still cooling down; nothing is attempted. */
        GATED,
        /** No safe corner exists: abort without consuming the cooldown. */
        NO_CORNER,
        /** The corner exists but the boss never got there: abort, keep the cooldown. */
        REPOSITION_FAILED
    }

    /** Pure gate: a volley may only start from an allowed phase, off cooldown, at a corner. */
    public static Decision decide(boolean phaseAllows, boolean cornerAvailable, boolean coolingDown) {
        if (!phaseAllows || coolingDown) {
            return Decision.GATED;
        }
        return cornerAvailable ? Decision.START : Decision.NO_CORNER;
    }

    /** Whether the boss actually ended up at the corner it was sent to. */
    public static boolean relocated(double distanceToCorner) {
        return Double.isFinite(distanceToCorner) && distanceToCorner <= REPOSITION_TOLERANCE_BLOCKS;
    }
}