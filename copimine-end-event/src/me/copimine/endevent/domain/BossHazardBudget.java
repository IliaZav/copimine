package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded admission ledger for boss hazards and hard control.
 *
 * <p>The ledger is deliberately independent of Bukkit. A server adapter may
 * reserve a mechanic before its telegraph is committed and release it when
 * the mechanic reaches recovery or cleanup. At most one hard-control
 * reservation may affect a participant at a time.</p>
 */
public final class BossHazardBudget {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    public static final int MAX_HARD_CONTROL_PER_PLAYER = 1;

    public enum MechanicKind {
        MAJOR_GRAB(2, true),
        FINAL_STRIKE(2, true),
        VOID_BLAST(1, false),
        RIFT_PROJECTILE(1, false),
        VOID_MARK(1, true),
        RIFT_ARROWS(1, false),
        ARENA_INFERNO(2, false),
        SUMMON_SERVANTS(1, false),
        REPOSITION(0, false),
        RECOVER(0, false);

        private final int weight;
        private final boolean hardControl;

        MechanicKind(int weight, boolean hardControl) {
            this.weight = weight;
            this.hardControl = hardControl;
        }

        public int weight() {
            return weight;
        }

        public boolean hardControl() {
            return hardControl;
        }
    }

    public static Profile profileForPlayers(int livingPlayers) {
        int players = Math.max(0, Math.min(MAX_PLAYERS, livingPlayers));
        if (players < MIN_PLAYERS) {
            return Profile.EMPTY;
        }
        int global = players <= 3 ? 3 : players <= 5 ? 4 : players <= 10 ? 5
                : players <= 15 ? 6 : 7;
        return new Profile(players, global, 2, MAX_HARD_CONTROL_PER_PLAYER);
    }

    private final Profile profile;
    private final Map<UUID, Reservation> reservations = new LinkedHashMap<>();

    public BossHazardBudget() {
        this(Profile.EMPTY);
    }

    public BossHazardBudget(Profile profile) {
        this.profile = profile == null ? Profile.EMPTY : profile;
    }

    public Decision reserve(UUID target, MechanicKind mechanic, long generation) {
        MechanicKind safeMechanic = mechanic == null ? MechanicKind.RECOVER : mechanic;
        if (profile.players() < MIN_PLAYERS) {
            return rejected("INSUFFICIENT_PLAYERS");
        }
        if (generation <= 0L) {
            return rejected("INVALID_GENERATION");
        }
        if (safeMechanic.hardControl() && target == null) {
            return rejected("TARGET_REQUIRED_FOR_HARD_CONTROL");
        }
        if (safeMechanic.hardControl() && target != null
                && activeHardControls(target) >= profile.hardControlPerPlayer()) {
            return rejected("PLAYER_HARD_CONTROL_FULL");
        }
        if (activeWeight() + safeMechanic.weight() > profile.globalWeight()) {
            return rejected("GLOBAL_BUDGET_FULL");
        }
        if (target != null && playerWeight(target) + safeMechanic.weight() > profile.perPlayerWeight()) {
            return rejected("PLAYER_BUDGET_FULL");
        }
        Reservation reservation = new Reservation(UUID.randomUUID(), target, safeMechanic,
                safeMechanic.weight(), generation);
        reservations.put(reservation.id(), reservation);
        return new Decision(true, "ACCEPTED", reservation);
    }

    public Decision tryReserve(UUID target, MechanicKind mechanic, long generation) {
        return reserve(target, mechanic, generation);
    }

    public boolean canReserve(UUID target, MechanicKind mechanic, long generation) {
        int before = reservations.size();
        Decision decision = reserve(target, mechanic, generation);
        if (decision.accepted()) {
            release(decision.reservation().id());
        }
        return decision.accepted() && reservations.size() == before;
    }

    public boolean release(UUID reservationId) {
        return reservationId != null && reservations.remove(reservationId) != null;
    }

    public int releaseGeneration(long generation) {
        if (generation <= 0L) {
            return 0;
        }
        int before = reservations.size();
        reservations.values().removeIf(value -> value.generation() == generation);
        return before - reservations.size();
    }

    public void clear() {
        reservations.clear();
    }

    public int activeWeight() {
        return reservations.values().stream().mapToInt(Reservation::weight).sum();
    }

    public int activeHardControls(UUID target) {
        if (target == null) {
            return 0;
        }
        return (int) reservations.values().stream()
                .filter(value -> target.equals(value.target()) && value.mechanic().hardControl())
                .count();
    }

    public int playerWeight(UUID target) {
        if (target == null) {
            return 0;
        }
        return reservations.values().stream().filter(value -> target.equals(value.target()))
                .mapToInt(Reservation::weight).sum();
    }

    public List<Reservation> activeReservations() {
        return List.copyOf(new ArrayList<>(reservations.values()));
    }

    public Profile profile() {
        return profile;
    }

    private Decision rejected(String reason) {
        return new Decision(false, reason, null);
    }

    public record Profile(int players, int globalWeight, int perPlayerWeight,
                          int hardControlPerPlayer) {
        public static final Profile EMPTY = new Profile(0, 0, 0, 0);

        public Profile {
            players = Math.max(0, Math.min(MAX_PLAYERS, players));
            globalWeight = Math.max(0, globalWeight);
            perPlayerWeight = Math.max(0, perPlayerWeight);
            hardControlPerPlayer = Math.max(0, Math.min(MAX_HARD_CONTROL_PER_PLAYER,
                    hardControlPerPlayer));
        }
    }

    public record Reservation(UUID id, UUID target, MechanicKind mechanic,
                              int weight, long generation) {
        public Reservation {
            if (id == null || mechanic == null || generation <= 0L) {
                throw new IllegalArgumentException("invalid boss hazard reservation");
            }
            weight = Math.max(0, weight);
        }
    }

    public record Decision(boolean accepted, String reason, Reservation reservation) {
        public Decision {
            reason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
            if (accepted != (reservation != null)) {
                throw new IllegalArgumentException("accepted decision must carry reservation");
            }
        }
    }
}
