package me.copimine.endevent.domain;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Who a Wave 7 miniboss swings at.
 *
 * <p>Every trial actor used to pick the closest eligible player on every attack
 * and nothing else. In a party that means the same person is hit by every cycle
 * until they break off, which reads as a miniboss that does not fight the group,
 * and it lets the rest of the party free-hit from behind for the whole trial.</p>
 *
 * <p>The rules here are deliberately boring:</p>
 * <ol>
 *   <li>Someone who is not eligible (outside the chamber, dead, left) is never
 *       chosen.</li>
 *   <li>A player the actor has not hit yet is preferred over one it has, so the
 *       first pass over a party is a sweep rather than a focus.</li>
 *   <li>Within the preferred set the closest wins, because a miniboss that
 *       walks past a nearby threat to reach a distant one looks broken.</li>
 *   <li>Once everyone has been hit, the least recently hit player wins, so the
 *       sweep repeats instead of collapsing onto one victim.</li>
 *   <li>The actor never picks the same player twice in a row while there is an
 *       alternative, which is what makes a 2-3 person party feel answered.</li>
 * </ol>
 *
 * <p>Pure: no Bukkit types, no clock, no randomness. Callers pass the facts.</p>
 */
public final class Wave7TrialTargetPolicy {
    private Wave7TrialTargetPolicy() { }

    /**
     * One eligible candidate.
     *
     * @param id               stable player identity
     * @param distanceSquared  squared horizontal distance to the actor
     * @param lastHitOrdinal   attack counter of the last time this actor hit
     *                         them, or {@link #NEVER} if it never has
     */
    public record Candidate(UUID id, double distanceSquared, long lastHitOrdinal) { }

    /** Sentinel for "this actor never hit this player". */
    public static final long NEVER = Long.MIN_VALUE;

    /**
     * Pick this attack's target.
     *
     * @param candidates     eligible players, any order, may be empty
     * @param previousTarget the target of the actor's previous attack, may be null
     * @param attackOrdinal  monotonically increasing counter of this actor's attacks
     * @return the chosen id, or null when nobody is eligible
     */
    public static UUID choose(List<Candidate> candidates, UUID previousTarget, long attackOrdinal) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<Candidate> usable = candidates.stream()
                .filter(candidate -> candidate != null && candidate.id() != null
                        && Double.isFinite(candidate.distanceSquared())
                        && candidate.distanceSquared() >= 0.0D)
                .toList();
        if (usable.isEmpty()) {
            return null;
        }
        // Prefer someone alive and in reach; fall back to anyone rather than
        // skipping the attack entirely.
        List<Candidate> pool = usable.size() > 1 && previousTarget != null
                ? usable.stream().filter(c -> !c.id().equals(previousTarget)).toList()
                : usable;
        if (pool.isEmpty()) {
            pool = usable;
        }
        return pool.stream()
                .min(Comparator
                        .comparingLong((Candidate c) -> c.lastHitOrdinal() == NEVER ? 0L : 1L)
                        .thenComparingLong(c -> c.lastHitOrdinal() == NEVER ? 0L : c.lastHitOrdinal())
                        .thenComparingDouble(Candidate::distanceSquared))
                .orElseThrow()
                .id();
    }
}
