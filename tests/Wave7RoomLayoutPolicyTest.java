import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import me.copimine.endevent.domain.Wave7RoomLayoutPolicy;
import me.copimine.endevent.domain.Wave7RoomLayoutPolicy.Room;
import me.copimine.endevent.domain.Wave7RoomLayoutPolicy.Snapshot;
import me.copimine.endevent.domain.Wave7RoomLayoutPolicy.Trial;

public final class Wave7RoomLayoutPolicyTest {
    public static void main(String[] args) {
        samplesTwoDistinctTrialsForTwoPlayers();
        samplesThreeDistinctTrialsForThreePlayers();
        seededSelectionAndSnapshotRestoreAreStable();
        fourOrMorePlayersRunEveryTrialInParallel();
        fullRoomLayoutsRandomizeTrialAssignments();
        extraPlayersAreDistributedInStableRoundRobinOrder();
        rejectsInvalidRostersAndPersistedSnapshots();
        snapshotAndRoomMembershipAreImmutable();
        System.out.println("Wave7RoomLayoutPolicyTest OK");
    }

    private static void samplesTwoDistinctTrialsForTwoPlayers() {
        List<UUID> players = List.of(id(1), id(2));
        Set<Trial> observed = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            Snapshot snapshot = Wave7RoomLayoutPolicy.select(10 + seed, players, new Random(seed * 1_000_000_007L));
            check(snapshot.rooms().size() == 2, "two players get two parallel rooms");
            check(snapshot.rooms().stream().allMatch(room -> room.players().size() == 1),
                    "each two-player room has one player");
            Set<Trial> selected = trials(snapshot.rooms());
            check(selected.size() == 2, "two-room sample draws without replacement");
            check(Trial.values().length == 4 && Set.of(Trial.values()).containsAll(selected),
                    "two-room sample only uses the shared trial pool");
            observed.addAll(selected);
        }
        check(observed.equals(Set.of(Trial.values())),
                "seeded two-player samples can select every trial in the pool");
    }

    private static void samplesThreeDistinctTrialsForThreePlayers() {
        List<UUID> players = List.of(id(1), id(2), id(3));
        Set<Trial> observed = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            Snapshot snapshot = Wave7RoomLayoutPolicy.select(1000 + seed, players, new Random(seed * 1_000_000_007L));
            check(snapshot.rooms().size() == 3, "three players get three parallel rooms");
            check(snapshot.rooms().stream().allMatch(room -> room.players().size() == 1),
                    "each three-player room has one player");
            Set<Trial> selected = trials(snapshot.rooms());
            check(selected.size() == 3, "three-room sample draws distinct trials without replacement");
            check(Trial.values().length == 4 && Set.of(Trial.values()).containsAll(selected),
                    "three-room sample only uses the shared trial pool");
            observed.addAll(selected);
        }
        check(observed.equals(Set.of(Trial.values())),
                "seeded three-player samples can select every trial in the pool");
    }

    private static void seededSelectionAndSnapshotRestoreAreStable() {
        List<UUID> players = List.of(id(8), id(4), id(5));
        Snapshot first = Wave7RoomLayoutPolicy.select(44, players, new Random(9917));
        Snapshot second = Wave7RoomLayoutPolicy.select(44, players, new Random(9917));
        check(first.equals(second), "same seed and roster produce an identical snapshot");
        Snapshot reorderedRoster = Wave7RoomLayoutPolicy.select(44, reverse(players), new Random(9917));
        check(reorderedRoster.equals(first), "roster iteration order does not change a seeded small-room selection");
        Snapshot restored = Wave7RoomLayoutPolicy.restore(first.generation(), first.rooms());
        check(restored.equals(first), "restore preserves exact room roles and owners without rerolling");
        check(restored.roomByPlayer().equals(first.roomByPlayer()),
                "restore preserves the exact player-to-room mapping");
    }

    private static void fourOrMorePlayersRunEveryTrialInParallel() {
        for (int playerCount : List.of(4, 5, 6, 10, 20)) {
            Snapshot snapshot = Wave7RoomLayoutPolicy.select(200 + playerCount,
                    roster(playerCount), new Random(123));
            check(snapshot.rooms().size() == 4, "four or more players keep four parallel rooms");
            check(trials(snapshot.rooms()).equals(Set.of(Trial.values())),
                    "four or more players run Warden, Echo, Marksman and Archmage together");
            check(snapshot.rooms().stream().allMatch(room -> !room.players().isEmpty()),
                    "no selected trial has an empty room");
        }
    }

    private static void fullRoomLayoutsRandomizeTrialAssignments() {
        Set<List<Trial>> assignments = new HashSet<>();
        for (long seed = 0; seed < 24; seed++) {
            List<Trial> orderedTrials = Wave7RoomLayoutPolicy.select(500 + seed,
                    roster(4), new Random(seed)).rooms().stream().map(Room::trial).toList();
            check(new HashSet<>(orderedTrials).equals(Set.of(Trial.values())),
                    "full layouts retain every unique trial");
            assignments.add(orderedTrials);
        }
        check(assignments.size() > 1,
                "all four parallel rooms randomize which player room receives each trial");
    }

    private static void extraPlayersAreDistributedInStableRoundRobinOrder() {
        List<UUID> players = roster(10);
        Snapshot first = Wave7RoomLayoutPolicy.select(300, players, new Random(5));
        List<UUID> sorted = new ArrayList<>(players);
        sorted.sort(java.util.Comparator.comparing(UUID::toString));
        for (int index = 0; index < sorted.size(); index++) {
            int expectedRoom = index % 4;
            check(first.roomFor(sorted.get(index)).orElseThrow() == expectedRoom,
                    "sorted extra players are assigned in round-robin room order");
        }
        check(first.rooms().stream().map(Room::players).mapToInt(List::size).boxed().toList()
                        .equals(List.of(3, 3, 2, 2)),
                "ten players balance across four rooms with a deterministic remainder");
        Snapshot reversedInput = Wave7RoomLayoutPolicy.select(300, reverse(players), new Random(5));
        check(reversedInput.equals(first), "roster iteration order does not change deterministic distribution");
    }

    private static void rejectsInvalidRostersAndPersistedSnapshots() {
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(1, null, new Random(1)));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(1, List.of(id(1)), new Random(1)));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(1, List.of(id(1), id(1)), new Random(1)));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(1, java.util.Arrays.asList(id(1), null), new Random(1)));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(1, roster(21), new Random(1)));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(0, List.of(id(1), id(2)), new Random(1)));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.select(1, List.of(id(1), id(2)), null));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.restore(0,
                List.of(new Room(0, Trial.WARDEN, List.of(id(1))),
                        new Room(1, Trial.ECHO, List.of(id(2))))));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.restore(2,
                List.of(new Room(0, Trial.WARDEN, List.of(id(1))),
                        new Room(0, Trial.ECHO, List.of(id(2))))));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.restore(3,
                List.of(new Room(0, Trial.WARDEN, List.of(id(1))),
                        new Room(1, Trial.WARDEN, List.of(id(2))))));
        expectIllegalArgument(() -> Wave7RoomLayoutPolicy.restore(4,
                List.of(new Room(0, Trial.WARDEN, List.of(id(1), id(2))),
                        new Room(1, Trial.ECHO, List.of(id(2))))));
    }

    private static void snapshotAndRoomMembershipAreImmutable() {
        Snapshot snapshot = Wave7RoomLayoutPolicy.select(5, roster(4), new Random(2));
        expectUnsupported(() -> snapshot.rooms().clear());
        expectUnsupported(() -> snapshot.rooms().get(0).players().clear());
        expectUnsupported(() -> snapshot.roomByPlayer().clear());
    }

    private static Set<Trial> trials(List<Room> rooms) {
        Set<Trial> result = new HashSet<>();
        for (Room room : rooms) result.add(room.trial());
        return result;
    }

    private static List<UUID> roster(int size) {
        List<UUID> result = new ArrayList<>();
        for (int player = 1; player <= size; player++) result.add(id(player));
        return result;
    }

    private static List<UUID> reverse(List<UUID> values) {
        List<UUID> result = new ArrayList<>(values);
        java.util.Collections.reverse(result);
        return result;
    }

    private static UUID id(int value) { return new UUID(0, value); }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("expected immutable snapshot");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
