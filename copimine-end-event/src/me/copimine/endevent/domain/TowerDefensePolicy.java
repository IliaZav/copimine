package me.copimine.endevent.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure immutable policy for the tower-defense core and its retry boundary. */
public final class TowerDefensePolicy {
    public static final double BASE_HEALTH = 1_200.0D;
    public static final double HEALTH_PER_EXTRA_PLAYER = 300.0D;
    public static final double MAX_HEALTH = 4_200.0D;
    public static final long DEFENSE_MILLIS = 180_000L;

    private TowerDefensePolicy() { }

    public static CoreState start(int players, long nowMillis) {
        if (players < 0 || nowMillis < 0L || nowMillis > Long.MAX_VALUE - DEFENSE_MILLIS) {
            throw new IllegalArgumentException("invalid tower start");
        }
        double health = Math.min(MAX_HEALTH, BASE_HEALTH + Math.max(0, players - 2) * HEALTH_PER_EXTRA_PLAYER);
        return new CoreState(players, health, health, nowMillis, nowMillis + DEFENSE_MILLIS,
                Set.of(), 1, Outcome.ACTIVE);
    }

    public static CoreState damage(CoreState state, String attackId, double amount) {
        if (state == null || state.outcome() != Outcome.ACTIVE || attackId == null || attackId.trim().isEmpty()
                || !finite(amount) || amount < 0.0D || state.appliedAttackIds().contains(attackId.trim())) return state;
        Set<String> attacks = new LinkedHashSet<>(state.appliedAttackIds());
        attacks.add(attackId.trim());
        return state.withHealth(Math.max(0.0D, state.currentHealth() - amount), attacks);
    }

    public static CoreState finish(CoreState state, long nowMillis) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (nowMillis < 0L) throw new IllegalArgumentException("timestamp must be non-negative");
        if (state.outcome() != Outcome.ACTIVE) return state;
        Outcome outcome = state.currentHealth() <= 0.0D || nowMillis > state.deadlineMillis()
                ? Outcome.FAILURE : Outcome.SUCCESS;
        return state.withOutcome(outcome);
    }

    /**
     * Commit a healthy defense when the scheduler observes its deadline late.
     * Bukkit ticks are discrete, so requiring the observed timestamp to equal
     * the deadline would turn normal tick drift into a false failure.  The
     * policy still evaluates health at the exact deadline and therefore keeps
     * a depleted Core a failure.
     */
    public static CoreState completeAtDeadline(CoreState state, long observedNowMillis) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (observedNowMillis < 0L) throw new IllegalArgumentException("timestamp must be non-negative");
        if (state.outcome() != Outcome.ACTIVE || observedNowMillis < state.deadlineMillis()) {
            return state;
        }
        return finish(state, state.deadlineMillis());
    }

    public static CoreState retry(CoreState state, long nowMillis) {
        if (state == null || nowMillis < 0L || nowMillis > Long.MAX_VALUE - DEFENSE_MILLIS) {
            throw new IllegalArgumentException("invalid retry");
        }
        if (state.outcome() != Outcome.FAILURE) return state;
        return new CoreState(state.players(), state.maxHealth(), state.maxHealth(), nowMillis,
                nowMillis + DEFENSE_MILLIS, Set.of(), state.attempt() + 1, Outcome.ACTIVE);
    }

    public static CoreState cleanRetry(CoreState state, long nowMillis) { return retry(state, nowMillis); }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    public enum Outcome { ACTIVE, SUCCESS, FAILURE }

    public record CoreState(int players, double maxHealth, double currentHealth, long startedAtMillis,
                            long deadlineMillis, Set<String> appliedAttackIds, int attempt, Outcome outcome) {
        public CoreState {
            appliedAttackIds = Set.copyOf(appliedAttackIds == null ? Set.of() : appliedAttackIds);
            if (players < 0 || !finite(maxHealth) || maxHealth <= 0.0D || maxHealth > MAX_HEALTH
                    || !finite(currentHealth) || currentHealth < 0.0D || currentHealth > maxHealth
                    || startedAtMillis < 0L || deadlineMillis < startedAtMillis || attempt < 1 || outcome == null
                    || appliedAttackIds.stream().anyMatch(id -> id == null || id.trim().isEmpty())) {
                throw new IllegalArgumentException("invalid core state");
            }
        }

        private CoreState withHealth(double health, Set<String> attacks) {
            return new CoreState(players, maxHealth, health, startedAtMillis, deadlineMillis, attacks, attempt, outcome);
        }
        private CoreState withOutcome(Outcome next) {
            return new CoreState(players, maxHealth, currentHealth, startedAtMillis, deadlineMillis,
                    appliedAttackIds, attempt, next);
        }
    }
}
