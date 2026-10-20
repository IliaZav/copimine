package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Immutable Wave 7 room selection snapshot. Two or three players receive the
 * same number of rooms with distinct randomly sampled trials; four or more
 * players always run all four trial types in parallel.
 */
public final class Wave7RoomLayoutPolicy {
    public static final int MAX_PLAYERS = 20;

    public enum Trial { WARDEN, ECHO, MARKSMAN, ARCHMAGE }

    /** A persisted room assignment. Players are copied so a saved snapshot cannot drift. */
    public record Room(int room, Trial trial, List<UUID> players) {
        public Room {
            if (room < 0 || room >= Trial.values().length || trial == null
                    || players == null || players.isEmpty()
                    || players.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(players).size() != players.size()) {
                throw new IllegalArgumentException("invalid Wave 7 room assignment");
            }
            players = List.copyOf(players);
        }
    }

    /**
     * Immutable selected roles and roster assignment. Persist this object (or
     * its room records) and use {@link #restore(long, List)} after reconnect or
     * restart; restore never draws again from the random source.
     */
    public record Snapshot(long generation, List<Room> rooms) {
        public Snapshot {
            if (generation <= 0L || rooms == null || rooms.isEmpty()
                    || rooms.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("invalid Wave 7 layout snapshot");
            }
            ArrayList<Room> ordered = new ArrayList<>(rooms);
            ordered.sort(Comparator.comparingInt(Room::room));
            rooms = List.copyOf(ordered);
            validate(rooms);
        }

        public int playerCount() {
            return rooms.stream().mapToInt(room -> room.players().size()).sum();
        }

        public Map<UUID, Integer> roomByPlayer() {
            Map<UUID, Integer> result = new LinkedHashMap<>();
            for (Room room : rooms) {
                for (UUID player : room.players()) result.put(player, room.room());
            }
            return Map.copyOf(result);
        }

        public Optional<Integer> roomFor(UUID player) {
            return player == null ? Optional.empty() : Optional.ofNullable(roomByPlayer().get(player));
        }

        private static void validate(List<Room> rooms) {
            int playerCount = rooms.stream().mapToInt(room -> room.players().size()).sum();
            if (playerCount < 2 || playerCount > MAX_PLAYERS) {
                throw new IllegalArgumentException("invalid Wave 7 roster size");
            }
            int expectedRooms = playerCount <= 3 ? playerCount : Trial.values().length;
            if (rooms.size() != expectedRooms) {
                throw new IllegalArgumentException("room count does not match Wave 7 roster size");
            }

            Set<Trial> selectedTrials = new LinkedHashSet<>();
            Set<UUID> assignedPlayers = new HashSet<>();
            int smallestRoom = Integer.MAX_VALUE;
            int largestRoom = 0;
            for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
                Room room = rooms.get(roomIndex);
                if (room.room() != roomIndex || !selectedTrials.add(room.trial())) {
                    throw new IllegalArgumentException("Wave 7 rooms must be contiguous with distinct trials");
                }
                for (UUID player : room.players()) {
                    if (!assignedPlayers.add(player)) {
                        throw new IllegalArgumentException("Wave 7 player is assigned to multiple rooms");
                    }
                }
                smallestRoom = Math.min(smallestRoom, room.players().size());
                largestRoom = Math.max(largestRoom, room.players().size());
            }
            if (assignedPlayers.size() != playerCount || largestRoom - smallestRoom > 1) {
                throw new IllegalArgumentException("Wave 7 roster is not balanced across rooms");
            }
            if (playerCount <= 3 && rooms.stream().anyMatch(room -> room.players().size() != 1)) {
                throw new IllegalArgumentException("small Wave 7 rosters require one player per room");
            }
            if (playerCount >= 4 && !selectedTrials.equals(Set.of(Trial.values()))) {
                throw new IllegalArgumentException("four-player Wave 7 must run every trial in parallel");
            }
        }
    }

    private Wave7RoomLayoutPolicy() { }

    /** Select roles once and return the immutable value to persist for this attempt. */
    public static Snapshot select(long generation, List<UUID> participants, RandomGenerator random) {
        if (generation <= 0L || participants == null || random == null
                || participants.size() < 2 || participants.size() > MAX_PLAYERS
                || participants.stream().anyMatch(Objects::isNull)
                || new HashSet<>(participants).size() != participants.size()) {
            throw new IllegalArgumentException("invalid Wave 7 roster selection input");
        }

        List<UUID> orderedPlayers = new ArrayList<>(participants);
        orderedPlayers.sort(Comparator.comparing(UUID::toString));
        int roomCount = Math.min(orderedPlayers.size(), Trial.values().length);
        List<Trial> selectedTrials = selectTrials(roomCount, random);
        List<List<UUID>> playersByRoom = new ArrayList<>();
        for (int room = 0; room < roomCount; room++) playersByRoom.add(new ArrayList<>());
        for (int player = 0; player < orderedPlayers.size(); player++) {
            playersByRoom.get(player % roomCount).add(orderedPlayers.get(player));
        }

        List<Room> rooms = new ArrayList<>();
        for (int room = 0; room < roomCount; room++) {
            rooms.add(new Room(room, selectedTrials.get(room), playersByRoom.get(room)));
        }
        return new Snapshot(generation, rooms);
    }

    /** Reconstitute the already selected immutable assignments without rerolling. */
    public static Snapshot restore(long generation, List<Room> persistedRooms) {
        return new Snapshot(generation, persistedRooms);
    }

    private static List<Trial> selectTrials(int roomCount, RandomGenerator random) {
        List<Trial> pool = new ArrayList<>(List.of(Trial.values()));
        for (int index = pool.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            Trial swap = pool.get(index);
            pool.set(index, pool.get(other));
            pool.set(other, swap);
        }
        return List.copyOf(pool.subList(0, roomCount));
    }
}
