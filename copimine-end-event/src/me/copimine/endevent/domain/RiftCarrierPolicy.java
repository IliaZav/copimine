package me.copimine.endevent.domain;

import java.util.UUID;

/**
 * Server-authoritative state machine for the Wave 1 Rift Carrier objective.
 * A carrier death creates a charge; only a living participant can pick it up
 * and deliver it at the Core.  The generation fence makes stale entities
 * unable to complete a newer attempt.
 */
public final class RiftCarrierPolicy {
    public static final int REQUIRED_DELIVERIES = 3;
    public static final long CHARGE_TIMEOUT_TICKS = 200L;

    private RiftCarrierPolicy() {
    }

    public static State initial(long generation) {
        return new State(generation, 0, null, null, null, 0L, Phase.WAITING_FOR_CARRIER);
    }

    public static State selectCarrier(State state, long generation, UUID carrier) {
        if (!valid(state, generation) || carrier == null || isComplete(state)) {
            return state;
        }
        if (state.phase() != Phase.WAITING_FOR_CARRIER
                && state.phase() != Phase.READY_FOR_NEXT_CARRIER) {
            return state;
        }
        return state.withCarrier(carrier, Phase.CARRIER_ACTIVE);
    }

    public static State carrierDied(State state, long generation, UUID carrier,
                                    UUID charge, long nowTick) {
        if (!valid(state, generation) || carrier == null || charge == null
                || !carrier.equals(state.carrier()) || state.phase() != Phase.CARRIER_ACTIVE
                || isComplete(state)) {
            return state;
        }
        return new State(state.generation(), state.delivered(), null, charge, null,
                safeNow(nowTick) + CHARGE_TIMEOUT_TICKS, Phase.CHARGE_DROPPED);
    }

    public static State pickUp(State state, long generation, UUID player,
                               UUID charge, long nowTick) {
        if (!valid(state, generation) || player == null || !charge.equals(state.charge())
                || state.phase() != Phase.CHARGE_DROPPED || expired(state, nowTick)) {
            return state;
        }
        return new State(state.generation(), state.delivered(), null, charge, player,
                safeNow(nowTick) + CHARGE_TIMEOUT_TICKS, Phase.CHARGE_CARRIED);
    }

    public static State deliver(State state, long generation, UUID player,
                                boolean atCore, long nowTick) {
        if (!valid(state, generation) || player == null || !player.equals(state.holder())
                || !atCore || state.phase() != Phase.CHARGE_CARRIED
                || expired(state, nowTick)) {
            return state;
        }
        int delivered = Math.min(REQUIRED_DELIVERIES, state.delivered() + 1);
        Phase next = delivered >= REQUIRED_DELIVERIES
                ? Phase.COMPLETE : Phase.READY_FOR_NEXT_CARRIER;
        return new State(state.generation(), delivered, null, null, null, 0L, next);
    }

    /** Transfer an unclaimed or carried charge after its ten-second window. */
    public static State timeoutTransfer(State state, long generation, UUID nextCarrier,
                                         long nowTick) {
        if (!valid(state, generation) || isComplete(state)
                || (state.phase() != Phase.CHARGE_DROPPED
                && state.phase() != Phase.CHARGE_CARRIED)
                || !expired(state, nowTick) || nextCarrier == null) {
            return state;
        }
        return new State(state.generation(), state.delivered(), nextCarrier,
                null, null, 0L, Phase.CARRIER_ACTIVE);
    }

    /**
     * Returns whether the Bukkit adapter should create one bounded replacement
     * carrier after all scheduled groups have been exhausted.  This is kept in
     * the policy so a temporary loss of all eligible mobs cannot leave Wave 1
     * permanently waiting for a carrier, while the adapter still owns the
     * replacement cap and spawn mechanics.
     */
    public static boolean shouldSpawnReplacement(State state, long generation,
                                                 boolean allGroupsSpawned,
                                                 int liveCandidates) {
        if (!valid(state, generation) || isComplete(state) || !allGroupsSpawned
                || liveCandidates > 0) {
            return false;
        }
        return state.phase() == Phase.WAITING_FOR_CARRIER
                || state.phase() == Phase.READY_FOR_NEXT_CARRIER;
    }

    /**
     * Clears an expired dropped/carried charge before the adapter creates a
     * fresh candidate.  The delivered count is preserved and the generation
     * fence remains intact.
     */
    public static State expireForReplacement(State state, long generation, long nowTick) {
        if (!valid(state, generation) || isComplete(state)
                || (state.phase() != Phase.CHARGE_DROPPED
                && state.phase() != Phase.CHARGE_CARRIED)
                || !expired(state, nowTick)) {
            return state;
        }
        return new State(state.generation(), state.delivered(), null, null, null,
                0L, Phase.READY_FOR_NEXT_CARRIER);
    }

    public static boolean isComplete(State state) {
        return state != null && state.delivered() >= REQUIRED_DELIVERIES
                && state.phase() == Phase.COMPLETE;
    }

    public static boolean expired(State state, long nowTick) {
        return state != null && state.deadlineTick() > 0L
                && safeNow(nowTick) >= state.deadlineTick();
    }

    private static boolean valid(State state, long generation) {
        return state != null && state.generation() == generation
                && generation > 0L;
    }

    private static long safeNow(long tick) {
        return Math.max(0L, tick);
    }

    public enum Phase {
        WAITING_FOR_CARRIER,
        CARRIER_ACTIVE,
        CHARGE_DROPPED,
        CHARGE_CARRIED,
        READY_FOR_NEXT_CARRIER,
        COMPLETE
    }

    public record State(long generation, int delivered, UUID carrier, UUID charge,
                        UUID holder, long deadlineTick, Phase phase) {
        public State {
            if (generation <= 0L) {
                throw new IllegalArgumentException("generation must be positive");
            }
            if (delivered < 0 || delivered > REQUIRED_DELIVERIES) {
                throw new IllegalArgumentException("delivered charges out of bounds");
            }
            phase = phase == null ? Phase.WAITING_FOR_CARRIER : phase;
            if (phase == Phase.CARRIER_ACTIVE && carrier == null) {
                throw new IllegalArgumentException("active carrier is required");
            }
            if ((phase == Phase.CHARGE_DROPPED || phase == Phase.CHARGE_CARRIED)
                    && charge == null) {
                throw new IllegalArgumentException("charge is required");
            }
            if (phase == Phase.CHARGE_CARRIED && holder == null) {
                throw new IllegalArgumentException("charge holder is required");
            }
            if (deadlineTick < 0L) {
                throw new IllegalArgumentException("deadline cannot be negative");
            }
        }

        private State withCarrier(UUID nextCarrier, Phase nextPhase) {
            return new State(generation, delivered, nextCarrier, null, null, 0L, nextPhase);
        }
    }
}
