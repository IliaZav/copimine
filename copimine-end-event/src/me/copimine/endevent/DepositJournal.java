package me.copimine.endevent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;

/** Small fsynced PREPARED/ITEM_REMOVED/COMMITTED journal for core deposits. */
public final class DepositJournal {
    private final Path path;

    public DepositJournal(Path dataFolder) {
        this.path = dataFolder.resolve("deposit-journal.tsv");
    }

    public synchronized boolean prepare(Entry entry) {
        return append(entry.withStatus("PREPARED"));
    }

    public synchronized boolean markItemRemoved(Entry entry) {
        return append(entry.withStatus("ITEM_REMOVED"));
    }

    public synchronized boolean commit(Entry entry) {
        return append(entry.withStatus("COMMITTED"));
    }

    public synchronized boolean refund(Entry entry) {
        return append(entry.withStatus("REFUNDED"));
    }

    /** Keep the entry durable when an exact refund cannot fit yet. */
    public synchronized boolean refundPending(Entry entry) {
        return append(entry.withStatus("REFUND_PENDING"));
    }

    public synchronized List<Entry> unresolved() {
        Map<String, Entry> latest = latest();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : latest.values()) {
            if ("PREPARED".equals(entry.status()) || "ITEM_REMOVED".equals(entry.status())
                    || "REFUND_PENDING".equals(entry.status())) {
                result.add(entry);
            }
        }
        return result;
    }

    private boolean append(Entry entry) {
        try {
            Files.createDirectories(path.getParent());
            String line = String.join("\t",
                    entry.id(), entry.playerUuid().toString(), entry.material().name(),
                    Integer.toString(entry.amount()), Integer.toString(entry.afterProgress()),
                    entry.status()) + "\n";
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private Map<String, Entry> latest() {
        Map<String, Entry> result = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 6) {
                    continue;
                }
                try {
                    result.put(fields[0], new Entry(
                            fields[0], UUID.fromString(fields[1]), Material.valueOf(fields[2]),
                            Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), fields[5]));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    public record Entry(
            String id,
            UUID playerUuid,
            Material material,
            int amount,
            int afterProgress,
            String status) {
        public Entry {
            if (id == null || id.isBlank() || playerUuid == null || material == null || amount < 1) {
                throw new IllegalArgumentException("invalid deposit journal entry");
            }
            status = status == null ? "PREPARED" : status;
        }

        Entry withStatus(String next) {
            return new Entry(id, playerUuid, material, amount, afterProgress, next);
        }
    }
}
