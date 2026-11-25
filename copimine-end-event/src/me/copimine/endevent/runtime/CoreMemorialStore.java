package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.CoreMemorialLedgerPolicy;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Entry;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Ledger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;

/** Persists an entire winning roster before the victory saga advances. */
public final class CoreMemorialStore {
    private final Path file;

    public CoreMemorialStore(Path file) {
        this.file = file;
    }

    public Ledger load() throws IOException {
        if (Files.notExists(file)) return Ledger.empty();
        var loaded = CoreMemorialLedgerPolicy.decode(Files.readString(file, StandardCharsets.UTF_8));
        if (!loaded.intact()) throw new IOException("Unreadable Core memorial ledger");
        return loaded.ledger();
    }

    public List<Entry> record(String attempt, Collection<Entry> winners) throws IOException {
        Ledger before = load();
        Ledger ledger = before;
        for (Entry winner : winners) {
            if (!attempt.equals(winner.attemptId())) throw new IOException("Mismatched memorial attempt");
            var write = ledger.record(winner);
            switch (write.outcome()) {
                case RECORDED, DUPLICATE -> ledger = write.ledger();
                case REJECTED, FULL -> throw new IOException("Cannot retain the complete Core memorial roster");
            }
        }
        if (ledger != before) {
            write(ledger.encode());
        }
        return ledger.entries().stream().filter(entry -> attempt.equals(entry.attemptId())).toList();
    }

    private void write(String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
