package me.copimine.endevent.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * How two Wave 7 skeletons fight as a pair instead of as two solos that
 * collide.
 *
 * <p>Until now every Marksman ran its own rotation with no idea where its
 * partner stood, so a pair regularly fired through each other, stacked on the
 * same target and both vanished at once — the party fought one mob twice and
 * the "duo" was decorative. This policy gives the pair three rules:</p>
 *
 * <ol>
 *   <li><b>Lanes, not a crowd.</b> Each archer owns a flank relative to the
 *       target. They never converge on the same point, so neither blocks the
 *       other's line.</li>
 *   <li><b>Alternation.</b> Only one of the pair is in an active cast at a
 *       time, and the other is either covering or supporting. The party always
 *       has something to react to, and never eats two volleys at once.</li>
 *   <li><b>A joint attack.</b> Both must be free, both must see the target and
 *       the pair's shared charge must be full. The result is one narrow
 *       crossfire the party can read and dodge — a real reason to fight the
 *       pair as a pair.</li>
 * </ol>
 *
 * <p>Pure: no Bukkit, no clocks, no entity access. Every duration is a tick
 * count and every decision is a function of the arguments, so a restart can
 * reproduce it exactly.</p>
 */
public final class Wave7SkeletonPairPolicy {
    /** Which flank a partner holds relative to the shared target. */
    public enum Flank {
        LEFT,
        RIGHT
    }

    /** What the pair wants this tick, for each member independently. */
    public enum Order {
        /** Neither the joint attack nor a solo cast is available: keep the lane. */
        HOLD_LANE,
        /** This member casts now; the partner covers. */
        CAST,
        /** Another member is casting: hold the lane and keep the flank open. */
        COVER,
        /** The pair's joint attack has fired; both re-aim from a fixed spread. */
        JOINT_CROSSFIRE
    }

    /** Ticks the pair must charge together before the joint attack unlocks. */
    public static final int JOINT_CHARGE_TICKS = 80;

    /** Ticks between two joint attacks, counted from the previous resolution. */
    public static final int JOINT_COOLDOWN_TICKS = 240;

    /** Both members must be this close to the target for a joint attack. */
    public static final double JOINT_MAX_DISTANCE_BLOCKS = 22.0D;

    /**
     * Half-angle between the two crossfire lines, in degrees.
     *
     * <p>Wide enough that the two archers are visibly not firing the same line,
     * narrow enough that a player who steps between the beams is still inside
     * the danger zone and must commit to a side to escape.</p>
     */
    public static final double JOINT_SPREAD_DEGREES = 11.0D;

    /** Maximum lateral distance between partners, so they stay a recognisable pair. */
    public static final double MAX_PARTNER_SEPARATION_BLOCKS = 14.0D;

    private Wave7SkeletonPairPolicy() {
    }

    /** One member's live state, reduced to what a pairing decision needs. */
    public record Member(UUID actorId, Flank flank, double distanceToTargetSquared,
                         boolean hasLineOfSight, boolean busyCasting, boolean hidden,
                         long chargeTicks) {
        public Member {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(flank, "flank");
            if (!Double.isFinite(distanceToTargetSquared) || distanceToTargetSquared < 0.0D
                    || chargeTicks < 0L) {
                throw new IllegalArgumentException("invalid skeleton pair member state");
            }
        }

        /**
         * A member that can start something right now.
         *
         * <p>Hidden counts as busy on purpose: a vanishing Marksman is not a
         * legal firing line, and pairing it with a partner would let the pair
         * fire the joint attack from invisibility.</p>
         */
        public boolean free() {
            return !busyCasting && !hidden && hasLineOfSight;
        }
    }

    /** The pair's joint attack readiness. */
    public record JointReadiness(boolean charged, boolean inRange, boolean separated,
                                 boolean bothFree, long chargeTicks) {
        public boolean ready() {
            return charged && inRange && separated && bothFree;
        }
    }

    /**
     * Flanks are assigned deterministically from the actor id, never randomly.
     *
     * <p>A random flank would flip when a member reloaded and the pair would
     * swap sides mid-fight; deriving it from the UUID keeps each archer on its
     * own wing for the whole encounter and survives a restart.</p>
     */
    public static Flank flankOf(UUID actorId) {
        if (actorId == null) throw new IllegalArgumentException("actorId is required");
        // Least significant bit of the most significant word: stable across JVMs.
        return (actorId.getMostSignificantBits() & 1L) == 0L ? Flank.LEFT : Flank.RIGHT;
    }

    /** Lateral offset of a flank, in blocks, applied perpendicular to the target line. */
    public static double laneOffsetBlocks(Flank flank) {
        // Mirrored, and non-zero: a zero lane is exactly the collision this
        // policy exists to prevent.
        return flank == Flank.LEFT ? -3.5D : 3.5D;
    }

    /**
     * Joint attack readiness for the pair.
     *
     * @param lastJointTick the tick of the previous joint attack, or a negative value if never
     */
    public static JointReadiness jointReadiness(Member first, Member second, long tick,
                                                long lastJointTick) {
        if (first == null || second == null) return new JointReadiness(false, false, false, false, 0L);
        // Only a genuine pair: the same actor twice is a data error, not a duo.
        if (first.actorId().equals(second.actorId())) {
            return new JointReadiness(false, false, false, false, 0L);
        }
        // A pair that has never fired still has to CHARGE. This used to read
        // "no previous joint attack" as "fully charged", so the first crossfire
        // was available on the tick the pair spawned and the party met a
        // two-archer trap before it had seen the archers work. The charge is
        // measured from the pair's first observed tick, exactly like the
        // interval after a fired one, so both limits are the same number.
        boolean fired = lastJointTick >= 0L;
        long since = fired ? tick - lastJointTick : Math.max(0L, tick);
        boolean charged = since >= (fired ? JOINT_COOLDOWN_TICKS : JOINT_CHARGE_TICKS);
        long charge = since;
        double maxDistance = JOINT_MAX_DISTANCE_BLOCKS * JOINT_MAX_DISTANCE_BLOCKS;
        boolean inRange = first.distanceToTargetSquared() <= maxDistance
                && second.distanceToTargetSquared() <= maxDistance;
        // Separation is measured along the target line: two archers on the same
        // spot are not a crossfire even if their ids differ.
        double separation = Math.abs(Math.sqrt(first.distanceToTargetSquared())
                - Math.sqrt(second.distanceToTargetSquared()));
        boolean separated = separation <= MAX_PARTNER_SEPARATION_BLOCKS;
        boolean bothFree = first.free() && second.free();
        return new JointReadiness(charged, inRange, separated, bothFree, charge);
    }

    /**
     * Decides one member's order.
     *
     * <p>Priority is explicit and documented, because "why did nobody shoot"
     * is the failure mode a pair policy creates if it is vague:</p>
     * <ol>
     *   <li>Joint attack when the pair is ready — it is stronger than any solo.</li>
     *   <li>Otherwise this member casts only if its partner is not casting, so
     *       the two never trade volleys simultaneously.</li>
     *   <li>Otherwise cover: hold the lane, stay out of the partner's line.</li>
     * </ol>
     */
    public static Order orderFor(Member self, Member partner, JointReadiness readiness) {
        if (self == null || partner == null) return Order.HOLD_LANE;
        if (readiness != null && readiness.ready()) return Order.JOINT_CROSSFIRE;
        if (self.busyCasting()) return Order.CAST;
        if (self.hidden()) return Order.HOLD_LANE;
        if (!self.hasLineOfSight()) return Order.HOLD_LANE;
        // Alternation: the partner in an active cast owns this instant.
        if (partner.busyCasting()) return Order.COVER;
        return Order.CAST;
    }

    /**
     * Angular offset of one flank's crossfire line from the straight target line.
     *
     * <p>Anchored on the flank so the pair always crosses in front of the
     * target rather than diverging away from it.</p>
     */
    public static double crossfireOffsetDegrees(Flank flank) {
        return flank == Flank.LEFT ? -JOINT_SPREAD_DEGREES : JOINT_SPREAD_DEGREES;
    }

    /**
     * Why a joint attack was refused, for logs and for the command output.
     *
     * <p>Never used to gate gameplay; it exists so an operator testing the pair
     * sees the reason instead of guessing.</p>
     */
    public static Optional<String> refusalReason(JointReadiness readiness) {
        if (readiness == null) return Optional.of("no readiness");
        if (readiness.ready()) return Optional.empty();
        if (!readiness.charged()) return Optional.of("charging");
        if (!readiness.inRange()) return Optional.of("out of range");
        if (!readiness.separated()) return Optional.of("partners too far apart");
        if (!readiness.bothFree()) return Optional.of("a partner is busy");
        return Optional.of("not ready");
    }
}
