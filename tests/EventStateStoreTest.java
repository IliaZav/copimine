import me.copimine.endevent.EventSnapshot;
import me.copimine.endevent.EventStateStore;
import me.copimine.endevent.domain.EventPhase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class EventStateStoreTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("copimine-end-state-test-");
        EventStateStore store = new EventStateStore(directory, "event-state.yml", "event-state.yml.bak", 1);

        EventSnapshot first = snapshot("first-event", EventPhase.COLLECTING.name(), 3L);
        check(store.save(first), "first durable state save must succeed");
        check(store.load().valid(), "primary state must load after first save");
        check(store.load().source().equals("PRIMARY"), "first load must use primary state");

        EventSnapshot second = snapshot("second-event", EventPhase.READY_FOR_PLAYERS.name(), 4L);
        check(store.save(second), "second durable state save must succeed");
        check(Files.exists(directory.resolve("event-state.yml.bak")), "second save must create a backup");

        Files.writeString(directory.resolve("event-state.yml"), "event: [broken\n");
        EventStateStore.LoadResult fromBackup = store.load();
        check(fromBackup.valid(), "corrupt primary must fall back to backup");
        check(fromBackup.source().equals("BACKUP"), "fallback must identify backup source");
        check(fromBackup.snapshot().eventId().equals(first.eventId()), "backup must contain the previous state");

        Files.writeString(directory.resolve("event-state.yml.bak"), "event: [also-broken\n");
        EventStateStore.LoadResult recovery = store.load();
        check(!recovery.valid(), "corrupt primary and backup must require recovery");
        check(recovery.snapshot().eventPhase() == EventPhase.RECOVERY_REQUIRED,
                "double corruption must produce recovery-required state");
        check(!recovery.reason().isBlank(), "double corruption must retain a diagnostic reason");

        Executor direct = Runnable::run;
        check(store.saveAsync(first, direct).join(), "async persistence must marshal through supplied executor");
        System.out.println("EventStateStoreTest OK");
    }

    private static EventSnapshot snapshot(String eventId, String phase, long generation) {
        UUID player = UUID.nameUUIDFromBytes(eventId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new EventSnapshot(
                1, eventId, generation, phase, "CopiMine", 10, 70, 20, "minecraft:crying_obsidian", 1,
                0, 54, 0, 20, 86, 40,
                Map.of("DIAMOND", 100), Map.of("DIAMOND", 25),
                List.of(new EventSnapshot.PadSnapshot(10, 70, 25, 5.0D, 0.0D, "minecraft:purpur_block")),
                Set.of(player), Set.of(player), Map.of(player, "PENDING"), Map.of(),
                false, false, false, false, false, false, false, false, "PENDING", "NONE", 123L, "");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
