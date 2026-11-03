package me.copimine.endevent.domain;

import java.util.List;
import java.util.UUID;

/**
 * Makes the boss fight a group, not a queue.
 *
 * <p>The shipped controller rotated the boss target on a fixed timer and never
 * looked at who was actually hitting it. A party that spreads out therefore saw
 * a boss that ignored the player carving into its back for up to eleven seconds
 * while still chasing someone across the arena. That reads as a boss that does
 * not fight the group.</p>
 *
 * <p>This policy adds the two reactions a group fight needs, while keeping the
 * authored target lock (spec 3.1, 10-12 s) as the damping rule:</p>
 * <ol>
 *   <li><b>Retaliation.</b> Take a swing at the last attacker, so damage has an
 *       answer. Retaliation is rate limited, because a party landing twenty hits
 *       a second must not make the boss thrash between targets.</li>
 *   <li><b>Spread.</b> A target that ignores the boss too long hands the focus
 *       to someone who has waited longest, so no single player is ever glued to
 *       the boss for a whole phase.</li>
 * </ol>
 *
 * <p>Pure decision-making: no Bukkit types, no state. The caller owns timers.</p>
 */
public final class BossGroupAggroPolicy {
    /** Minimum gap between two retaliation switches. */
    public static final long RETALIATION_COOLDOWN_MILLIS = 2_500L;
    /** A retaliation may cut at most this much of the authored lock. */
    public static final double MAX_LOCK_CUT = 0.5D;
    /** Below this many eligible players the boss simply duels: no juggling. */
    public static final int MIN_GROUP_SIZE_FOR_SPREAD = 3;

    private BossGroupAggroPolicy() { }

    /** What the caller should do with this tick's sample. */
    public enum Decision {
        /** Keep the current focus; nothing changed. */
        HOLD,
        /** Switch focus to the supplied attacker (retaliation). */
        RETALIATE,
        /** Switch focus to the supplied player who has waited longest. */
        SPREAD,
        /** No eligible target at all. */
        CLEAR
    }

    /** One sampled candidate with the facts the policy needs. */
    public record Candidate(UUID id, boolean alive, boolean eligible,
                            long lastDamagedBossMillis) {
        public Candidate {
            if (id == null) {
                throw new IllegalArgumentException("candidate id is required");
            }
        }
    }

    /**
     * Choose this tick's focus.
     *
     * @param candidates         every eligible party member, any order
     * @param current            the boss's current focus, may be null
     * @param nowMillis          authoritative clock
     * @param lastRetaliationMillis when the boss last switched by retaliation
     * @param lockUntilMillis    the authored target lock deadline
     * @param focusSinceMillis   when the current focus was taken
     */
    public static Outcome evaluate(List<Candidate> candidates,
                                   UUID current,
                                   long nowMillis,
                                   long lastRetaliationMillis,
                                   long lockUntilMillis,
                                   long focusSinceMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        List<Candidate> eligible = candidates == null ? List.of()
                : candidates.stream().filter(c -> c != null && c.alive() && c.eligible()).toList();
        if (eligible.isEmpty()) {
            return new Outcome(Decision.CLEAR, null, lockUntilMillis);
        }
        boolean currentStillEligible = current != null
                && eligible.stream().anyMatch(c -> c.id().equals(current));
        if (!currentStillEligible) {
            // Someone left or died: the caller must pick again immediately, and
            // the longest-waiting player is the fairest choice. A switch always
            // installs a full authored lock; reporting "now" here made every new
            // focus start life already expired.
            Candidate next = longestWaiting(eligible, null);
            return new Outcome(Decision.SPREAD, next.id(), nowMillis + lockLengthMillis(lockUntilMillis, focusSinceMillis));
        }
        boolean locked = nowMillis < lockUntilMillis;

        // 1. Retaliation: an attacker the boss has not answered yet.
        Candidate attacker = lastAttacker(eligible, current, nowMillis);
        boolean retaliationReady = nowMillis - lastRetaliationMillis >= RETALIATION_COOLDOWN_MILLIS;
        if (attacker != null && retaliationReady && locked) {
            long remaining = lockUntilMillis - nowMillis;
            long authored = Math.max(1L, lockUntilMillis - focusSinceMillis);
            long cutAllowance = (long) Math.ceil(authored * MAX_LOCK_CUT);
            if (remaining <= cutAllowance) {
                // The retaliation cut the back half of the lock we inherited.
                // Reporting "now" as the new deadline handed the fresh focus a
                // lock that was already expired on the very next tick, so the
                // answer was immediately re-spread and the boss never actually
                // turned on the attacker. The new focus gets the same authored
                // length as any other focus, measured from now.
                return new Outcome(Decision.RETALIATE, attacker.id(),
                        nowMillis + lockLengthMillis(lockUntilMillis, focusSinceMillis));
            }
        }
        if (!locked) {
            // The authored lock expired: prefer the attacker, otherwise spread.
            long fresh = nowMillis + lockLengthMillis(lockUntilMillis, focusSinceMillis);
            if (attacker != null) {
                return new Outcome(Decision.RETALIATE, attacker.id(), fresh);
            }
            Candidate next = longestWaiting(eligible, current);
            return new Outcome(Decision.SPREAD, next.id(), fresh);
        }
        return new Outcome(Decision.HOLD, current, lockUntilMillis);
    }

    /**
     * Length of the authored lock, recovered from the observed window.
     *
     * <p>The caller passes the deadline and the moment the focus was taken, so
     * the policy never invents its own duration; only when both are missing or
     * inconsistent does it fall back to the spec default.</p>
     */
    private static long lockLengthMillis(long lockUntilMillis, long focusSinceMillis) {
        long observed = lockUntilMillis - focusSinceMillis;
        return observed > 0L ? observed : BossTargetPolicy.DEFAULT_LOCK_MILLIS;
    }

    /** Most recent attacker who is not already the focus and is recent enough. */
    private static Candidate lastAttacker(List<Candidate> eligible, UUID current, long nowMillis) {
        Candidate best = null;
        for (Candidate candidate : eligible) {
            if (candidate.id().equals(current)) continue;
            if (candidate.lastDamagedBossMillis() <= 0L) continue;
            if (nowMillis - candidate.lastDamagedBossMillis() > RETALIATION_COOLDOWN_MILLIS * 2L) continue;
            if (best == null || candidate.lastDamagedBossMillis() > best.lastDamagedBossMillis()) {
                best = candidate;
            }
        }
        return best;
    }

    private static Candidate longestWaiting(List<Candidate> eligible, UUID exclude) {
        Candidate best = null;
        for (Candidate candidate : eligible) {
            if (candidate.id().equals(exclude)) continue;
            if (best == null || candidate.lastDamagedBossMillis() < best.lastDamagedBossMillis()) {
                best = candidate;
            }
        }
        return best == null ? eligible.get(0) : best;
    }

    /** Result of one evaluation. */
    public record Outcome(Decision decision, UUID target, long newLockUntilMillis) {
        public Outcome {
            if (decision == null) {
                throw new IllegalArgumentException("decision is required");
            }
            if (newLockUntilMillis < 0L) {
                throw new IllegalArgumentException("lock deadline must be non-negative");
            }
        }
    }
}
