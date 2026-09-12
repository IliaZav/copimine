import me.copimine.endevent.EventSnapshot;
import me.copimine.endevent.EventStateStore;
import me.copimine.endevent.domain.BossPhase;
import me.copimine.endevent.domain.EventPhase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EventStateStoreTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("copimine-end-state-test-");
        EventStateStore store = new EventStateStore(directory,
                "event-state.yml", "event-state.yml.bak", 4);
        expectRejected(() -> new EventStateStore(directory, "../outside.yml", "backup.yml", 4),
                "state path traversal must be rejected");
        expectRejected(() -> new EventStateStore(directory, "state.yml", "nested/backup.yml", 4),
                "backup path traversal must be rejected");
        expectRejected(() -> new EventStateStore(directory, "state.yml", "backup.yml", 3),
                "the current writer must require schema four");

        UUID player = UUID.randomUUID();
        EventSnapshot first = snapshot("first-event", EventPhase.COLLECTING.name(), 3L, player);
        check(store.save(first), "schema-4 state save must succeed");
        EventStateStore.LoadResult loaded = store.load();
        check(loaded.valid() && "PRIMARY".equals(loaded.source()),
                "schema-4 primary must load directly");
        check(loaded.snapshot().participants().contains(player),
                "participants must survive a round trip");
        check(loaded.snapshot().currentBossPhase() == BossPhase.AWAKENING,
                "current boss phase must survive a round trip");
        String serialized = Files.readString(directory.resolve("event-state.yml"));
        for (String forbidden : List.of("half-health", "final-drain", "boss-stage",
                "virtual-health", "absorption", "judgment")) {
            check(!serialized.toLowerCase(java.util.Locale.ROOT).contains(forbidden),
                    "schema-4 writer must not emit legacy key " + forbidden);
        }

        EventSnapshot second = snapshot("second-event", EventPhase.READY_FOR_PLAYERS.name(), 4L, player);
        check(store.save(second), "second schema-4 save must succeed");
        check(Files.exists(directory.resolve("event-state.yml.bak")),
                "second save must create a backup");
        Files.writeString(directory.resolve("event-state.yml"), "event: [broken\n");
        EventStateStore.LoadResult backup = store.load();
        check(backup.valid() && "BACKUP".equals(backup.source()),
                "corrupt primary must fall back to the backup");
        check("first-event".equals(backup.snapshot().eventId()),
                "backup must contain the previous current snapshot");
        Files.writeString(directory.resolve("event-state.yml.bak"), "event: [also-broken\n");
        EventStateStore.LoadResult recovery = store.load();
        check(!recovery.valid()
                        && recovery.snapshot().eventPhase() == EventPhase.RECOVERY_REQUIRED,
                "double corruption must fail closed to recovery");
        check(!recovery.reason().isBlank(), "recovery must retain a diagnostic reason");
        System.out.println("EventStateStoreTest OK");
    }

    private static EventSnapshot snapshot(String eventId, String phase,
                                          long generation, UUID player) {
        return new EventSnapshot(
                4, eventId, generation, phase, "CopiMine", 10, 70, 20,
                "minecraft:crying_obsidian", 2,
                0, 60, 0, 20, 80, 40,
                Map.of("DIAMOND", 100), Map.of("DIAMOND", 25),
                List.of(new EventSnapshot.PadSnapshot(10, 70, 25, 5.0D, 0.0D,
                        "minecraft:purpur_block")),
                Set.of(player), Set.of(player), Map.of(player, "PENDING"), Map.of(), Map.of(),
                false, false, false, false, "PENDING", null, "PENDING", "NONE",
                123L, 456L, "", Set.of(player), Set.of(1),
                BossPhase.AWAKENING.name(), "NONE", 0L, "NONE", Map.of(), Map.of());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectRejected(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
