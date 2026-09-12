package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Immutable, bounded input to the boss brain. Bukkit/world scanning belongs
 * to the adapter; the brain only receives this event-local snapshot.
 */
public record BossPerceptionSnapshot(
        BossPhase phase,
        EndRiftObjective.Objective objective,
        List<PlayerObservation> players,
        Position bossPosition,
        HazardSnapshot hazards,
        String chamberId,
        long generation,
        boolean movementStuck) {

    public BossPerceptionSnapshot {
        phase = phase == null ? BossPhase.AWAKENING : phase;
        players = boundedPlayers(players);
        bossPosition = bossPosition == null ? new Position(0.0D, 0.0D, 0.0D) : bossPosition;
        hazards = hazards == null ? HazardSnapshot.EMPTY : hazards;
        chamberId = chamberId == null ? "" : chamberId.trim();
        generation = Math.max(0L, generation);
    }

    public List<PlayerObservation> livingPlayers() {
        return players.stream().filter(PlayerObservation::alive).toList();
    }

    public List<PlayerObservation> isolatedPlayers() {
        return livingPlayers().stream().filter(PlayerObservation::isolated).toList();
    }

    public Position partyCentroid() {
        List<PlayerObservation> living = livingPlayers();
        if (living.isEmpty()) {
            return bossPosition;
        }
        double x = living.stream().mapToDouble(PlayerObservation::x).average().orElse(bossPosition.x());
        double y = living.stream().mapToDouble(PlayerObservation::y).average().orElse(bossPosition.y());
        double z = living.stream().mapToDouble(PlayerObservation::z).average().orElse(bossPosition.z());
        return new Position(x, y, z);
    }

    public double partySpreadRadius() {
        Position center = partyCentroid();
        return livingPlayers().stream().mapToDouble(player -> distance(center, player.x(), player.y(), player.z()))
                .max().orElse(0.0D);
    }

    public boolean partyStacked() {
        return livingPlayers().size() >= 2 && partySpreadRadius() <= 4.0D;
    }

    public boolean partySpread() {
        return partySpreadRadius() >= 8.0D || !isolatedPlayers().isEmpty();
    }

    public boolean bossSurrounded() {
        long close = livingPlayers().stream().filter(player -> player.distanceToBoss() <= 3.5D).count();
        return close >= 2;
    }

    private static double distance(Position center, double x, double y, double z) {
        double dx = center.x() - x;
        double dy = center.y() - y;
        double dz = center.z() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<PlayerObservation> boundedPlayers(List<PlayerObservation> values) {
        List<PlayerObservation> copy = new ArrayList<>();
        if (values != null) {
            values.stream().filter(value -> value != null && value.playerId() != null)
                    .sorted(Comparator.comparing(value -> value.playerId().toString()))
                    .limit(20).forEach(copy::add);
        }
        return List.copyOf(copy);
    }

    public record Position(double x, double y, double z) {
        public Position {
            x = finite(x) ? x : 0.0D;
            y = finite(y) ? y : 0.0D;
            z = finite(z) ? z : 0.0D;
        }
    }

    public record PlayerObservation(UUID playerId, boolean alive, double healthFraction,
                                    double distanceToBoss, boolean lineOfSight,
                                    boolean isolated, double recentDamageThreat,
                                    boolean recentlyTargeted, boolean recentlyHardControlled,
                                    boolean objectiveThreat, double x, double y, double z,
                                    double velocityX, double velocityY, double velocityZ,
                                    String chamberId) {
        public PlayerObservation {
            healthFraction = clamp(healthFraction, 0.0D, 1.0D);
            distanceToBoss = nonNegative(distanceToBoss);
            recentDamageThreat = nonNegative(recentDamageThreat);
            x = finite(x) ? x : 0.0D;
            y = finite(y) ? y : 0.0D;
            z = finite(z) ? z : 0.0D;
            velocityX = finite(velocityX) ? velocityX : 0.0D;
            velocityY = finite(velocityY) ? velocityY : 0.0D;
            velocityZ = finite(velocityZ) ? velocityZ : 0.0D;
            chamberId = chamberId == null ? "" : chamberId.trim();
        }

        public static PlayerObservation at(UUID id, double x, double y, double z,
                                           double healthFraction, double distance,
                                           boolean lineOfSight) {
            return new PlayerObservation(id, true, healthFraction, distance, lineOfSight,
                    false, 0.0D, false, false, false, x, y, z, 0.0D, 0.0D, 0.0D, "");
        }
    }

    public record HazardSnapshot(int majorHazards, int activeHardControls,
                                 int activeSummons, boolean arenaLocked,
                                 boolean safeLaneAvailable) {
        public static final HazardSnapshot EMPTY = new HazardSnapshot(0, 0, 0, false, true);

        public HazardSnapshot {
            majorHazards = Math.max(0, majorHazards);
            activeHardControls = Math.max(0, activeHardControls);
            activeSummons = Math.max(0, activeSummons);
        }
    }

    private static double nonNegative(double value) {
        return finite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, finite(value) ? value : min));
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
