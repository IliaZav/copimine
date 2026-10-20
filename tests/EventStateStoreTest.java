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
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

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
        check("0".equals(loaded.snapshot().objectiveProgress()
                        .get("reality-split.player." + player)),
                "dotted objective keys must survive a round trip without being flattened");
        check("1160".equals(loaded.snapshot().objectiveProgress()
                        .get("reality-split.generation")),
                "generation objective key must survive a round trip");
        Path legacyDirectory = Files.createTempDirectory("copimine-end-state-legacy-");
        String legacyYaml = """
                schema-version: 4
                event:
                  event-id: legacy-event
                  generation: 1160
                  phase: READY_FOR_PLAYERS
                  world: CopiMine
                  core:
                    x: 10
                    y: 70
                    z: 20
                    block-data: minecraft:crying_obsidian
                  required-players: 2
                  arena:
                    min-x: 0
                    min-y: 60
                    min-z: 0
                    max-x: 20
                    max-y: 80
                    max-z: 40
                objective:
                  progress:
                    reality-split.player.%s: '0'
                    reality-split.generation: '1160'
                    test-wave: '7'
                """.formatted(player);
        Files.writeString(legacyDirectory.resolve("event-state.yml"), legacyYaml);
        EventStateStore.LoadResult legacyLoaded = new EventStateStore(legacyDirectory,
                "event-state.yml", "event-state.yml.bak", 4).load();
        check(legacyLoaded.valid(), "pre-entry-list state must remain readable");
        check("0".equals(legacyLoaded.snapshot().objectiveProgress()
                        .get("reality-split.player." + player)),
                "legacy dotted objective keys must be recovered before migration");
        Path orderedDirectory = Files.createTempDirectory("copimine-end-state-ordered-");
        EventStateStore orderedStore = new EventStateStore(orderedDirectory,
                "event-state.yml", "event-state.yml.bak", 4);
        ArrayList<Runnable> queued = new ArrayList<>();
        CompletableFuture<Boolean> pending = orderedStore.saveAsync(
                snapshot("stale-async", EventPhase.COLLECTING.name(), 3L, player), queued::add);
        check(queued.size() == 1 && !pending.isDone(),
                "saveAsync must enqueue without running the snapshot immediately");
        check(orderedStore.save(snapshot("newer-sync", EventPhase.READY_FOR_PLAYERS.name(), 4L, player)),
                "newer synchronous checkpoint must succeed");
        queued.get(0).run();
        check(pending.join(), "an obsolete queued save is a successful no-op after supersession");
        check("newer-sync".equals(orderedStore.load().snapshot().eventId()),
                "an older async snapshot must never roll state back");
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
                BossPhase.AWAKENING.name(), "NONE", 0L, "NONE",
                Map.of("reality-split.player." + player, "0",
                        "reality-split.generation", "1160", "test-wave", "7"),
                Map.of());
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
