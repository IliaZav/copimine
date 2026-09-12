import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.copimine.endevent.HazardMutationJournal;

public final class HazardMutationJournalTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("end-rift-hazard-journal-test-");
        try {
            HazardMutationJournal journal = new HazardMutationJournal(directory);
            String worldId = java.util.UUID.randomUUID().toString();
            HazardMutationJournal.Entry entry = new HazardMutationJournal.Entry(
                    10, 67, -4, "minecraft:stone", "minecraft:air", "OBELISK",
                    worldId, "event-1", 7L);

            check(journal.prepare("event-1", 7L, "CopiMine", List.of(entry)),
                    "prepare must be durable");
            check(journal.load().status() == HazardMutationJournal.Status.PREPARED,
                    "prepared state must be readable");
            HazardMutationJournal.Entry loadedEntry = journal.load().entries().get(0);
            check(loadedEntry.isObeliskMutation(), "obelisk mutation must be explicit");
            check(worldId.equals(loadedEntry.worldId()), "entry world UUID must round-trip");
            check("event-1".equals(loadedEntry.eventId()), "entry event UUID must round-trip");
            check(loadedEntry.generation() == 7L, "entry generation must round-trip");
            check(journal.markApplied(), "applied state must be durable");
            check(journal.load().status() == HazardMutationJournal.Status.APPLIED,
                    "applied state must be readable");
            check(journal.markRestored(), "restored state must be durable");
            check(journal.load().status() == HazardMutationJournal.Status.RESTORED,
                    "restored state must be readable");

            Files.writeString(journal.path(), "not: valid: yaml: [", StandardCharsets.UTF_8);
            HazardMutationJournal.Snapshot fallback = journal.load();
            check(fallback.valid(), "a corrupt primary must fall back to the backup");
            check(fallback.status() == HazardMutationJournal.Status.APPLIED,
                    "backup must preserve the last known applied mutation state");
            check(fallback.entries().size() == 1, "backup must preserve mutation entries");

            HazardMutationJournal.Entry fire = new HazardMutationJournal.Entry(
                    11, 68, -4, "minecraft:air", "", "FIRE");
            check(fire.isFireMutation(), "fire mutation must be explicitly tagged");
            HazardMutationJournal.Entry safe = new HazardMutationJournal.Entry(
                    12, 68, -4, "minecraft:stone", "minecraft:air", "EMERALD_BARRIER");
            check(safe.isEmeraldBarrierMutation(), "emerald safe-zone mutation must be explicit");
            HazardMutationJournal.Entry barrier = new HazardMutationJournal.Entry(
                    13, 68, -4, "minecraft:stone", "minecraft:air", "BARRIER");
            check(barrier.isBarrierMutation(), "barrier perimeter mutation must be explicit");
            HazardMutationJournal.Entry ice = new HazardMutationJournal.Entry(
                    14, 68, -4, "minecraft:stone", "", "ICE");
            check(ice.isIceMutation(), "ice prisoner mutation must be explicit");
            Path invalidDirectory = Files.createTempDirectory("end-rift-hazard-journal-invalid-");
            try {
                HazardMutationJournal invalidJournal = new HazardMutationJournal(invalidDirectory);
                HazardMutationJournal.Entry duplicate = new HazardMutationJournal.Entry(
                        20, 67, -4, "minecraft:stone", "", "FIRE",
                        worldId, "event-2", 8L);
                HazardMutationJournal.Entry duplicateCell = new HazardMutationJournal.Entry(
                        20, 67, -4, "minecraft:stone", "", "ICE",
                        worldId, "event-2", 8L);
                check(!invalidJournal.prepare("event-2", 8L, "CopiMine",
                                List.of(duplicate, duplicateCell)),
                        "duplicate hazard cells must be rejected before mutation");

                HazardMutationJournal.Entry legacy = new HazardMutationJournal.Entry(
                        21, 67, -4, "minecraft:stone", "", "FIRE");
                check(!invalidJournal.prepare("event-2", 8L, "CopiMine", List.of(legacy)),
                        "legacy unscoped hazard entries must not enter a new generation");
            } finally {
                try (var paths = Files.walk(invalidDirectory)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best-effort cleanup for this isolated temporary test directory.
                        }
                    });
                }
            }
            System.out.println("HazardMutationJournalTest OK");
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Best-effort cleanup for this isolated temporary test directory.
                    }
                });
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
