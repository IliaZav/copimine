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

        Files.writeString(directory.resolve("deposit-journal.tsv"), "malformed\nnot-a-uuid\tbad\n", java.nio.file.StandardOpenOption.APPEND);
        check(journal.unresolved().isEmpty(), "malformed journal lines must be ignored without losing valid state");

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
