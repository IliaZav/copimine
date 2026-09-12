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
    private volatile String lastFailure = "";

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

    /**
     * Return unresolved records owned by exactly one current event generation.
     * A legacy six-field record or a different owner is never guessed into the
     * current attempt; the caller must put the event into recovery instead.
     */
    public synchronized List<Entry> unresolvedFor(String eventId, long generation) {
        if (eventId == null || eventId.isBlank() || generation <= 0L) {
            throw new JournalCorruptionException("current deposit journal owner is incomplete");
        }
        List<Entry> result = new ArrayList<>();
        for (Entry entry : unresolved()) {
            if (entry.legacy()) {
                throw new JournalCorruptionException(
                        "legacy unscoped deposit record requires administrative recovery: " + entry.id());
            }
            if (!entry.belongsTo(eventId, generation)) {
                throw new JournalCorruptionException(
                        "deposit record belongs to a different event generation: " + entry.id());
            }
            result.add(entry);
        }
        return result;
    }

    public String lastFailure() {
        return lastFailure;
    }

    private boolean append(Entry entry) {
        try {
            Files.createDirectories(path.getParent());
            String line = String.join("\t",
                    entry.id(), entry.eventId(), Long.toString(entry.generation()),
                    entry.playerUuid().toString(), entry.material().name(),
                    Integer.toString(entry.amount()), Integer.toString(entry.afterProgress()),
                    entry.status()) + "\n";
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
            return true;
        } catch (IOException error) {
            lastFailure = "deposit journal write failed: " + error.getMessage();
            throw new JournalCorruptionException(lastFailure, error);
        }
    }

    private Map<String, Entry> latest() {
        Map<String, Entry> result = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return result;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String[] lines = content.split("\\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                // String.split(..., -1) exposes the empty token after the
                // journal's required final newline. It is a delimiter
                // artifact, not a blank record. A second newline still
                // produces an earlier blank token and is rejected below.
                if (index == lines.length - 1 && content.endsWith("\n")) {
                    continue;
                }
                boolean finalUnterminatedLine = index == lines.length - 1
                        && !content.endsWith("\n");
                if (finalUnterminatedLine && line.isBlank()) {
                    continue;
                }
                if (line.isBlank()) {
                    throw malformed("blank complete journal line");
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != 8 && fields.length != 6) {
                    // Only a final line with fewer than the six legacy fields
                    // can be
                    // proven to be torn by an interrupted append. A complete
                    // six-field line, even without a trailing newline, must
                    // still parse strictly and must never disappear as if it
                    // were harmless recovery noise.
                    if (finalUnterminatedLine && fields.length < 6) {
                        lastFailure = "torn final deposit journal line ignored";
                        continue;
                    }
                    throw malformed("expected six tab-separated fields");
                }
                try {
                    Entry entry;
                    if (fields.length == 8) {
                        entry = new Entry(fields[0], fields[1], Long.parseLong(fields[2]),
                                UUID.fromString(fields[3]), Material.valueOf(fields[4]),
                                Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), fields[7]);
                    } else {
                        // Schema-1..3 deposit lines had no owner. They remain
                        // readable for diagnostics, but unresolved legacy
                        // work is rejected by unresolvedFor().
                        entry = new Entry(fields[0], UUID.fromString(fields[1]),
                                Material.valueOf(fields[2]), Integer.parseInt(fields[3]),
                                Integer.parseInt(fields[4]), fields[5]);
                    }
                    result.put(fields[0], entry);
                } catch (IllegalArgumentException error) {
                    throw malformed("invalid record: " + error.getMessage(), error);
                }
            }
        } catch (IOException error) {
            lastFailure = "deposit journal read failed: " + error.getMessage();
            throw new JournalCorruptionException(lastFailure, error);
        }
        return result;
    }

    private JournalCorruptionException malformed(String message) {
        lastFailure = message;
        return new JournalCorruptionException(message);
    }

    private JournalCorruptionException malformed(String message, Throwable cause) {
        lastFailure = message;
        return new JournalCorruptionException(message, cause);
    }

    public record Entry(
            String id,
            String eventId,
            long generation,
            UUID playerUuid,
            Material material,
            int amount,
            int afterProgress,
            String status) {
        public Entry(String id, UUID playerUuid, Material material, int amount,
                     int afterProgress, String status) {
            this(id, "", -1L, playerUuid, material, amount, afterProgress, status);
        }

        public Entry {
            eventId = eventId == null ? "" : eventId.trim();
            status = status == null ? "PREPARED" : status;
            validate(id, eventId, generation, playerUuid, material, amount, afterProgress, status);
        }

        private static void validate(String id, String eventId, long generation,
                                     UUID playerUuid, Material material, int amount,
                                     int afterProgress, String status) {
            if (id == null || id.isBlank() || playerUuid == null || material == null
                    || amount < 1 || afterProgress < 0) {
                throw new IllegalArgumentException("invalid deposit journal entry");
            }
            if (id.indexOf('\t') >= 0 || id.indexOf('\n') >= 0
                    || eventId != null && (eventId.indexOf('\t') >= 0 || eventId.indexOf('\n') >= 0)) {
                throw new IllegalArgumentException("deposit journal identity contains a line separator");
            }
            if (eventId.isBlank() && generation != -1L
                    || !eventId.isBlank() && generation <= 0L) {
                throw new IllegalArgumentException("deposit journal owner must contain event id and positive generation");
            }
            if (!status.equals("PREPARED") && !status.equals("ITEM_REMOVED")
                    && !status.equals("COMMITTED") && !status.equals("REFUNDED")
                    && !status.equals("REFUND_PENDING")) {
                throw new IllegalArgumentException("unknown deposit journal status: " + status);
            }
        }

        public boolean legacy() {
            return eventId.isBlank() && generation == -1L;
        }

        public boolean belongsTo(String expectedEventId, long expectedGeneration) {
            return !legacy() && eventId.equals(expectedEventId) && generation == expectedGeneration;
        }

        Entry withStatus(String next) {
            return new Entry(id, eventId, generation, playerUuid, material, amount, afterProgress, next);
        }
    }

    public static final class JournalCorruptionException extends IllegalStateException {
        public JournalCorruptionException(String message) { super(message); }
        public JournalCorruptionException(String message, Throwable cause) { super(message, cause); }
    }
}
