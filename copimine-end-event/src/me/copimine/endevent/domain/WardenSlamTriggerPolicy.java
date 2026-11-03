package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

/**
 * When the Warden should stop swinging in a line and slam the ground instead.
 *
 * <p>The authored Warden cycle is three directional attacks: a narrow strike, a
 * wide strike and a sonic lunge. All three are line-shaped and all three can be
 * stepped out of. Against a party that fights properly -- surround the boss,
 * spread around it, close in together -- that cycle is trivially beaten: nobody
 * is ever in the narrow line, and the wide strike reaches two of five.</p>
 *
 * <p>This policy answers the two positions a group actually takes:</p>
 * <ul>
 *   <li><b>Cluster.</b> Several players standing on top of each other. A
 *       directional swing hits one of them; a ground slam hits the pile.</li>
 *   <li><b>Encircled.</b> Players spread around the Warden on most of the
 *       compass, so whichever way it turns it leaves somebody behind. A slam is
 *       the only attack that does not need to pick a facing.</li>
 * </ul>
 *
 * <p>Both answers are rate limited by the caller's cooldown, and both require a
 * minimum party size, because against one or two players the slam would be
 * strictly worse than the authored swing. Pure: distances and identities in,
 * one decision out.</p>
 */
public final class WardenSlamTriggerPolicy {
    /** Two players this close to each other count as one pile. */
    public static final double CLUSTER_RADIUS_BLOCKS = 3.0D;
    /** How many players must be piled up for a slam to be worth it. */
    public static final int CLUSTER_MIN_PLAYERS = 3;
    /** The Warden is surrounded when this share of the compass is occupied. */
    public static final double ENCIRCLE_MIN_COVERAGE = 0.6D;
    /** ... by at least this many players. */
    public static final int ENCIRCLE_MIN_PLAYERS = 3;
    /** Rings are measured around the Warden, not around the whole arena. */
    public static final double ENCIRCLE_RADIUS_BLOCKS = 6.0D;
    /** The compass is cut into this many sectors when measuring coverage. */
    public static final int COMPASS_SECTORS = 4;
    /**
     * Nobody standing closer than this is part of an encirclement.
     *
     * <p>A party hugging the Warden is a pile, and a pile is already handled by
     * the cluster rule. Measuring coverage from under his feet would let three
     * players standing shoulder to shoulder "surround" him, which is exactly the
     * case the cluster test is for.</p>
     */
    public static final double ENCIRCLE_MIN_DISTANCE_BLOCKS = 1.5D;

    private WardenSlamTriggerPolicy() {
    }

    /** Why a slam was chosen, so the runtime can log and present it. */
    public enum Reason {
        /** Nobody close enough to justify a slam. */
        NONE,
        /** A pile of players is standing together. */
        CLUSTER,
        /** The party has the Warden surrounded. */
        ENCIRCLED
    }

    /**
     * One eligible player relative to the Warden.
     *
     * @param id   stable identity
     * @param dx   horizontal offset from the Warden, blocks (east positive)
     * @param dz   horizontal offset from the Warden, blocks (south positive)
     */
    public record Around(UUID id, double dx, double dz) {
        public Around {
            if (id == null) {
                throw new IllegalArgumentException("player id is required");
            }
            if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
                throw new IllegalArgumentException("offsets must be finite");
            }
        }

        public double distance() {
            return Math.hypot(dx, dz);
        }
    }

    /** The decision plus the evidence behind it. */
    public record Decision(Reason reason, int pileSize, double coverage) {
        public Decision {
            if (reason == null) {
                throw new IllegalArgumentException("reason is required");
            }
        }

        public boolean slam() {
            return reason != Reason.NONE;
        }
    }

    /**
     * Decide whether this tick deserves a slam.
     *
     * @param around  every eligible player positioned relative to the Warden
     * @param ready   whether the slam cooldown has elapsed
     */
    public static Decision evaluate(List<Around> around, boolean ready) {
        List<Around> targets = around == null ? List.of() : around.stream()
                .filter(java.util.Objects::nonNull).toList();
        if (!ready || targets.size() < CLUSTER_MIN_PLAYERS) {
            return new Decision(Reason.NONE, 0, 0.0D);
        }
        int pile = largestPile(targets);
        if (pile >= CLUSTER_MIN_PLAYERS) {
            return new Decision(Reason.CLUSTER, pile, compassCoverage(targets));
        }
        double coverage = compassCoverage(targets);
        if (targets.size() >= ENCIRCLE_MIN_PLAYERS && coverage >= ENCIRCLE_MIN_COVERAGE) {
            return new Decision(Reason.ENCIRCLED, pile, coverage);
        }
        return new Decision(Reason.NONE, pile, coverage);
    }

    /**
     * Size of the largest group of players standing within the cluster radius of
     * each other.
     *
     * <p>Greedy over the sorted list rather than a full graph search: with a
     * party capped at twenty the difference is invisible, and the greedy answer
     * is stable across ticks, which matters more than being optimal.</p>
     */
    static int largestPile(List<Around> targets) {
        int best = 0;
        for (Around anchor : targets) {
            int size = 0;
            for (Around other : targets) {
                if (Math.hypot(other.dx() - anchor.dx(), other.dz() - anchor.dz())
                        <= CLUSTER_RADIUS_BLOCKS) {
                    size++;
                }
            }
            best = Math.max(best, size);
        }
        return best;
    }

    /**
     * Share of the compass around the Warden that has a player on it.
     *
     * <p>Only players inside {@link #ENCIRCLE_RADIUS_BLOCKS} count; someone
     * kiting from across the arena is not part of an encirclement.</p>
     */
    static double compassCoverage(List<Around> targets) {
        boolean[] sectors = new boolean[COMPASS_SECTORS];
        int considered = 0;
        for (Around candidate : targets) {
            double distance = candidate.distance();
            if (distance > ENCIRCLE_RADIUS_BLOCKS) continue;
            if (distance < ENCIRCLE_MIN_DISTANCE_BLOCKS) continue;
            considered++;
            double angle = Math.atan2(candidate.dz(), candidate.dx());
            int sector = (int) Math.floor((angle + Math.PI) / (Math.PI * 2.0D) * COMPASS_SECTORS);
            sectors[Math.floorMod(sector, COMPASS_SECTORS)] = true;
        }
        if (considered == 0) return 0.0D;
        int occupied = 0;
        for (boolean held : sectors) if (held) occupied++;
        return occupied / (double) COMPASS_SECTORS;
    }
}
