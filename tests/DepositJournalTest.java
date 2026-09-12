import me.copimine.endevent.DepositJournal;
import org.bukkit.Material;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class DepositJournalTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("copimine-end-deposit-test-");
        DepositJournal journal = new DepositJournal(directory);
        UUID player = UUID.randomUUID();
        DepositJournal.Entry entry = new DepositJournal.Entry("deposit-1", player, Material.DIAMOND, 12, 12, "PREPARED");

        check(journal.prepare(entry), "PREPARED record must be fsynced");
        check(statuses(journal.unresolved()).equals(List.of("PREPARED")),
                "prepared entry must be recoverable");
        check(journal.markItemRemoved(entry), "ITEM_REMOVED record must be fsynced");
        check(statuses(journal.unresolved()).equals(List.of("ITEM_REMOVED")),
                "item-removed entry must remain unresolved until commit/refund");
        check(journal.commit(entry), "COMMITTED record must be fsynced");
        check(journal.unresolved().isEmpty(), "committed entry must leave no unresolved work");

        DepositJournal.Entry refund = new DepositJournal.Entry("deposit-2", player, Material.ENDER_EYE, 4, 4, "PREPARED");
        check(journal.prepare(refund), "second prepare must succeed");
        check(journal.refund(refund), "REFUNDED record must be fsynced");
        check(journal.unresolved().isEmpty(), "refunded entry must leave no unresolved work");

        DepositJournal.Entry pending = new DepositJournal.Entry("deposit-3", player, Material.DIAMOND, 2, 2, "PREPARED");
        check(journal.prepare(pending), "pending refund prepare must succeed");
        check(journal.refundPending(pending), "REFUND_PENDING record must be fsynced");
        check(statuses(journal.unresolved()).equals(List.of("REFUND_PENDING")),
                "full inventory refund must remain explicitly recoverable");
        check(journal.refund(pending), "a later free slot must close the pending refund");
        check(journal.unresolved().isEmpty(), "closed pending refund must leave no unresolved work");

        Files.writeString(directory.resolve("deposit-journal.tsv"), "partial\tentry",
                java.nio.file.StandardOpenOption.APPEND);
        check(journal.unresolved().isEmpty(),
                "only a provably torn final journal line may be ignored");

        Path scopedDirectory = Files.createTempDirectory("copimine-end-deposit-scoped-");
        try {
            DepositJournal scopedJournal = new DepositJournal(scopedDirectory);
            DepositJournal.Entry scoped = new DepositJournal.Entry(
                    "event-1:deposit-1", "event-1", 7L, player,
                    Material.DIAMOND, 3, 3, "PREPARED");
            check(scopedJournal.prepare(scoped), "scoped prepare must be durable");
            check(scopedJournal.unresolvedFor("event-1", 7L).size() == 1,
                    "current event generation must see its own unresolved deposit");
            boolean ownerRejected = false;
            try {
                scopedJournal.unresolvedFor("event-2", 8L);
            } catch (DepositJournal.JournalCorruptionException expected) {
                ownerRejected = true;
            }
            check(ownerRejected,
                    "a deposit from another event generation must fail closed");
        } finally {
            try (var paths = Files.walk(scopedDirectory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Best-effort cleanup for this isolated temporary test directory.
                    }
                });
            }
        }

        Path legacyDirectory = Files.createTempDirectory("copimine-end-deposit-legacy-");
        try {
            Files.writeString(legacyDirectory.resolve("deposit-journal.tsv"),
                    "legacy-1\t" + player + "\tDIAMOND\t1\t1\tPREPARED\n");
            boolean legacyRejected = false;
            try {
                new DepositJournal(legacyDirectory).unresolvedFor("event-1", 7L);
            } catch (DepositJournal.JournalCorruptionException expected) {
                legacyRejected = true;
            }
            check(legacyRejected,
                    "an unresolved legacy unscoped deposit must require explicit recovery");
        } finally {
            try (var paths = Files.walk(legacyDirectory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Best-effort cleanup for this isolated temporary test directory.
                    }
                });
            }
        }

        Path corruptDirectory = Files.createTempDirectory("copimine-end-deposit-corrupt-");
        Files.writeString(corruptDirectory.resolve("deposit-journal.tsv"),
                "not-a-uuid\tbad\tDIAMOND\t1\t1\tPREPARED");
        boolean corrupt = false;
        try {
            new DepositJournal(corruptDirectory).unresolved();
        } catch (DepositJournal.JournalCorruptionException expected) {
            corrupt = true;
        }
        check(corrupt, "a complete malformed record must fail closed");
        try (var paths = Files.walk(corruptDirectory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Best-effort cleanup for this isolated temporary test directory.
                }
            });
        }

        boolean rejected = false;
        try {
            new DepositJournal.Entry("", player, Material.DIAMOND, 1, 1, "PREPARED");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "invalid journal identity must be rejected");
        System.out.println("DepositJournalTest OK");
    }

    private static List<String> statuses(List<DepositJournal.Entry> entries) {
        return entries.stream().map(DepositJournal.Entry::status).toList();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
