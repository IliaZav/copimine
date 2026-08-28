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
            HazardMutationJournal.Entry entry = new HazardMutationJournal.Entry(
                    10, 67, -4, "minecraft:stone", "minecraft:air");

            check(journal.prepare("event-1", 7L, "CopiMine", List.of(entry)),
                    "prepare must be durable");
            check(journal.load().status() == HazardMutationJournal.Status.PREPARED,
                    "prepared state must be readable");
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
