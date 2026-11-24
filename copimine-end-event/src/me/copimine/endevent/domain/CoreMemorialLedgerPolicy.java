package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * V6 OV4.10 {@code CoreMemorialService}: the durable, deduplicated winner
 * ledger behind the Core memorial.
 *
 * <p>{@link CoreMemorialLayoutPolicy} decides how a roster is drawn. This policy
 * decides what the roster <em>is</em> after a restart: one entry per accepted
 * victory attempt and participant, carrying the account UUID, the nickname as
 * it was at the moment of victory, the attempt id and the victory timestamp. A
 * nickel memorial that only stored display text cannot repair a renamed
 * account, cannot tell two different accounts with the same name apart, and
 * cannot prove that a rewrite did not duplicate a winner.</p>
 *
 * <p>Three properties are deliberate:</p>
 * <ul>
 *   <li><b>Exactly once per attempt and participant.</b> The ledger is keyed by
 *       {@code attemptId:uuid}. A repeated write, a replayed bridge event, a
 *       double death or a restart that re-reads the same roster records
 *       nothing new, and the nickname and timestamp of the first accepted
 *       victory are frozen: a later rename never rewrites history.</li>
 *   <li><b>Fail closed.</b> A malformed row, an unknown format version, a
 *       duplicate key and an entry beyond {@link #MAX_ENTRIES} are never
 *       silently accepted and never silently dropped: the load reports how many
 *       rows could not be honoured, and the ledger refuses a new winner rather
 *       than evicting an older one.</li>
 *   <li><b>Presentation only.</b> The ledger owns no health, damage, reward or
 *       access state. The memorial must never become the authority for whether
 *       the fight was won; {@code EndRiftVictoryCoordinatorPolicy} owns that
 *       commit, and this class only records its outcome.</li>
 * </ul>
 */
public final class CoreMemorialLedgerPolicy {
    /** Format version written by {@link #encode()} and accepted by {@link #decode}. */
    public static final int VERSION = 1;
    /** Hard ceiling on stored winners; a new winner is refused above it. */
    public static final int MAX_ENTRIES = 4096;
    /** Longest attempt id kept; ids are opaque but bounded. */
    public static final int MAX_ATTEMPT_ID_CHARS = 64;
    /** First non-empty line of an encoded ledger. */
    public static final String HEADER = "copimine-memorial-v" + VERSION;
    private static final char FIELD = '\t';

    private CoreMemorialLedgerPolicy() {
    }

    /** Outcome of one {@link Ledger#record(Entry)} call. */
    public enum Outcome {
        /** The entry was added and the returned ledger is a new value. */
        RECORDED,
        /** This attempt and participant is already recorded; nothing changed. */
        DUPLICATE,
        /** The entry was malformed; nothing changed. */
        REJECTED,
        /** The ledger is at {@link #MAX_ENTRIES}; nothing changed and no winner was evicted. */
        FULL
    }

    /** One participant's accepted victory, frozen at the moment it was accepted. */
    public record Entry(String uuid, String nickname, String attemptId, long victoryEpochMillis) {
        public Entry {
            uuid = canonicalUuid(uuid);
            if (uuid == null) {
                throw new IllegalArgumentException("uuid must be a well-formed account UUID");
            }
            String id = attemptId == null ? "" : attemptId.trim();
            if (id.isEmpty() || id.length() > MAX_ATTEMPT_ID_CHARS) {
                throw new IllegalArgumentException("attempt id must be 1.." + MAX_ATTEMPT_ID_CHARS + " chars");
            }
            for (int i = 0; i < id.length(); i++) {
                char c = id.charAt(i);
                boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == ':'
                        || c == '.' || c == '/';
                if (!allowed) {
                    throw new IllegalArgumentException("attempt id has an unsupported character: " + c);
                }
            }
            attemptId = id;
            nickname = CoreMemorialLayoutPolicy.sanitize(nickname);
            if (nickname.isEmpty()) {
                throw new IllegalArgumentException("nickname must contain at least one visible character");
            }
            if (victoryEpochMillis < 0L) {
                throw new IllegalArgumentException("victory time must not be negative");
            }
        }

        /** The identity the ledger is keyed by; never the display name. */
        public String key() {
            return attemptId + ':' + uuid;
        }
    }

    /** Result of recording: the resulting ledger plus what happened. */
    public record Write(Ledger ledger, Outcome outcome, String key) {
        public Write {
            ledger = ledger == null ? Ledger.empty() : ledger;
            outcome = outcome == null ? Outcome.REJECTED : outcome;
            key = key == null ? "" : key;
        }

        public boolean changed() {
            return outcome == Outcome.RECORDED;
        }
    }

    /** What {@link #decode(String)} made of a stored payload. */
    public enum LoadStatus {
        /** Nothing was stored yet. */
        EMPTY,
        /** The format was recognised and the surviving rows were loaded. */
        LOADED,
        /** The header was missing or unknown; nothing was trusted and nothing loaded. */
        REJECTED
    }

    /** Result of a load, including every row that could not be honoured. */
    public record Loaded(LoadStatus status, Ledger ledger, int rejectedRows) {
        public Loaded {
            status = status == null ? LoadStatus.REJECTED : status;
            ledger = ledger == null ? Ledger.empty() : ledger;
            rejectedRows = Math.max(0, rejectedRows);
        }

        /** True when every stored row was honoured. */
        public boolean intact() {
            return rejectedRows == 0;
        }
    }

    /**
     * An immutable set of accepted victories.
     *
     * <p>Insertion order is preserved so the file grows append-only; the
     * canonical order used for writing and display is computed by
     * {@link #sorted()} and {@link #display()}.</p>
     */
    public record Ledger(List<Entry> entries) {
        public Ledger {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public static Ledger empty() {
            return new Ledger(List.of());
        }

        public int size() {
            return entries.size();
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        /** True when this attempt and participant is already recorded. */
        public boolean contains(String attemptId, String uuid) {
            String canon = canonicalUuid(uuid);
            if (canon == null || attemptId == null) {
                return false;
            }
            String key = attemptId.trim() + ':' + canon;
            for (Entry entry : entries) {
                if (entry.key().equals(key)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Record one accepted victory.
         *
         * <p>Idempotent per {@code attemptId:uuid}: a duplicate returns
         * {@link Outcome#DUPLICATE} with the same ledger instance, so a replayed
         * event cannot append a second line or refresh the stored nickname and
         * timestamp. At {@link #MAX_ENTRIES} the ledger returns
         * {@link Outcome#FULL} instead of evicting the oldest winner.</p>
         */
        public Write record(Entry entry) {
            if (entry == null) {
                return new Write(this, Outcome.REJECTED, "");
            }
            if (contains(entry.attemptId(), entry.uuid())) {
                return new Write(this, Outcome.DUPLICATE, entry.key());
            }
            if (entries.size() >= MAX_ENTRIES) {
                return new Write(this, Outcome.FULL, entry.key());
            }
            List<Entry> next = new ArrayList<>(entries.size() + 1);
            next.addAll(entries);
            next.add(entry);
            return new Write(new Ledger(next), Outcome.RECORDED, entry.key());
        }

        /**
         * Canonical order: victory time ascending, then attempt id, then UUID.
         *
         * <p>Every tie-break is total and independent of insertion order, so two
         * servers that accepted the same victories in a different order write
         * byte-identical files.</p>
         */
        public List<Entry> sorted() {
            List<Entry> ordered = new ArrayList<>(entries);
            ordered.sort((a, b) -> {
                int byTime = Long.compare(a.victoryEpochMillis(), b.victoryEpochMillis());
                if (byTime != 0) {
                    return byTime;
                }
                int byAttempt = a.attemptId().compareTo(b.attemptId());
                return byAttempt != 0 ? byAttempt : a.uuid().compareTo(b.uuid());
            });
            return List.copyOf(ordered);
        }

        /** Display names in canonical order, for the bounded viewport. */
        public List<String> nicknames() {
            List<String> names = new ArrayList<>(entries.size());
            for (Entry entry : sorted()) {
                names.add(entry.nickname());
            }
            return List.copyOf(names);
        }

        /** The bounded memorial block the display renders. */
        public CoreMemorialLayoutPolicy.View display() {
            return CoreMemorialLayoutPolicy.layout(nicknames());
        }

        /**
         * Serialise deterministically: the header, then one row per entry in
         * canonical order, tab separated, newline terminated.
         */
        public String encode() {
            StringBuilder out = new StringBuilder(HEADER).append('\n');
            for (Entry entry : sorted()) {
                out.append(entry.uuid()).append(FIELD)
                        .append(entry.attemptId()).append(FIELD)
                        .append(entry.nickname()).append(FIELD)
                        .append(entry.victoryEpochMillis()).append('\n');
            }
            return out.toString();
        }
    }

    /**
     * Parse a stored ledger.
     *
     * <p>A missing payload is {@link LoadStatus#EMPTY}, not an error: the first
     * victory legitimately starts from nothing. A payload whose first non-blank
     * line is not exactly {@link #HEADER} is {@link LoadStatus#REJECTED} and
     * contributes no entries, because a foreign format may mean something else
     * by the same columns. Once the header matches, every malformed row, every
     * duplicate key and every row beyond {@link #MAX_ENTRIES} is counted in
     * {@link Loaded#rejectedRows()} and left out, so the caller can report the
     * exact loss instead of discovering it as a missing name months later.</p>
     */
    public static Loaded decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new Loaded(LoadStatus.EMPTY, Ledger.empty(), 0);
        }
        String[] lines = encoded.split("\n", -1);
        int headerIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0 || !lines[headerIndex].trim().equals(HEADER)) {
            return new Loaded(LoadStatus.REJECTED, Ledger.empty(), 0);
        }
        Ledger ledger = Ledger.empty();
        int rejected = 0;
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            Entry entry = parseRow(line);
            if (entry == null) {
                rejected++;
                continue;
            }
            Write write = ledger.record(entry);
            if (write.changed()) {
                ledger = write.ledger();
            } else {
                rejected++;
            }
        }
        return new Loaded(LoadStatus.LOADED, ledger, rejected);
    }

    /** Strict row parser; returns null rather than throwing on a corrupt file. */
    private static Entry parseRow(String line) {
        List<String> fields = new ArrayList<>(4);
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == FIELD) {
                fields.add(line.substring(start, i));
                start = i + 1;
            }
        }
        fields.add(line.substring(start));
        if (fields.size() != 4) {
            return null;
        }
        long epoch;
        try {
            epoch = Long.parseLong(fields.get(3).trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
        try {
            return new Entry(fields.get(0), fields.get(2), fields.get(1), epoch);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * Canonical dashed lowercase UUID, or null when the input is not one.
     *
     * <p>Strict on purpose: 32 hex digits with optional dashes in the standard
     * positions. A lenient parse would map two different strings onto one key
     * and could merge two accounts in the memorial.</p>
     */
    public static String canonicalUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(32);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '-') {
                continue;
            }
            if (!isHex(c)) {
                return null;
            }
            hex.append(Character.toLowerCase(c));
        }
        if (hex.length() != 32) {
            return null;
        }
        StringBuilder out = new StringBuilder(36);
        for (int i = 0; i < 32; i++) {
            if (i == 8 || i == 12 || i == 16 || i == 20) {
                out.append('-');
            }
            out.append(hex.charAt(i));
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
