import me.copimine.endevent.domain.CoreMemorialLayoutPolicy;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Entry;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Ledger;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.LoadStatus;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Loaded;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Outcome;
import me.copimine.endevent.domain.CoreMemorialLedgerPolicy.Write;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * V6 OV4.10 / {@code CoreMemorialService}: the durable winner ledger.
 *
 * <p>These checks pin the behaviour that decides whether the memorial survives a
 * restart honestly: one line per accepted victory attempt and participant, a
 * frozen nickname and timestamp, a strict canonical UUID, a deterministic byte
 * format, and a load that reports every row it could not honour instead of
 * quietly losing a winner.</p>
 */
public final class CoreMemorialLedgerPolicyTest {
    private static final String ANN = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";

    public static void main(String[] args) {
        canonicalisesUuidsStrictly();
        validatesEntries();
        recordsExactlyOncePerAttemptAndParticipant();
        freezesTheFirstNickname();
        refusesToEvictAWinnerAtTheCap();
        ordersIndependentlyOfInsertionOrder();
        encodesDeterministically();
        roundTripsThroughDecode();
        rejectsAForeignFormat();
        countsEveryRowItCannotHonour();
        boundsTheDisplayLikeTheViewport();
        System.out.println("CoreMemorialLedgerPolicyTest OK");
    }

    private static void canonicalisesUuidsStrictly() {
        check(ANN.equals(CoreMemorialLedgerPolicy.canonicalUuid(ANN)), "a canonical uuid round-trips");
        check(ANN.equals(CoreMemorialLedgerPolicy.canonicalUuid(ANN.toUpperCase(java.util.Locale.ROOT))),
                "an uppercase uuid is lowercased");
        check(ANN.equals(CoreMemorialLedgerPolicy.canonicalUuid("  11111111111111111111111111111111  ")),
                "an undashed uuid gains the standard dashes");
        check(CoreMemorialLedgerPolicy.canonicalUuid(null) == null, "a null uuid is rejected");
        check(CoreMemorialLedgerPolicy.canonicalUuid("") == null, "an empty uuid is rejected");
        check(CoreMemorialLedgerPolicy.canonicalUuid("11111111-1111-1111-1111-11111111111") == null,
                "a short uuid is rejected");
        check(CoreMemorialLedgerPolicy.canonicalUuid("zzzzzzzz-1111-1111-1111-111111111111") == null,
                "a non-hex uuid is rejected");
        check(CoreMemorialLedgerPolicy.canonicalUuid("11111111-1111-1111-1111-11111111111g") == null,
                "a trailing non-hex character is rejected");
    }

    private static void validatesEntries() {
        Entry ok = new Entry(ANN, "Ann", "attempt-1", 1_000L);
        check(ANN.equals(ok.uuid()), "the stored uuid is canonical");
        check(ok.key().equals("attempt-1:" + ANN), "the identity key is attempt plus uuid");

        expectIllegal(() -> new Entry("not-a-uuid", "Ann", "a", 1L), "a malformed uuid is refused");
        expectIllegal(() -> new Entry(ANN, "Ann", "", 1L), "an empty attempt id is refused");
        expectIllegal(() -> new Entry(ANN, "Ann", "has space", 1L), "an attempt id with a space is refused");
        expectIllegal(() -> new Entry(ANN, "Ann", repeat('a', CoreMemorialLedgerPolicy.MAX_ATTEMPT_ID_CHARS + 1), 1L),
                "an over-long attempt id is refused");
        expectIllegal(() -> new Entry(ANN, "   ", "a", 1L), "a blank nickname is refused");
        expectIllegal(() -> new Entry(ANN, "\u00a7c\u00a7r", "a", 1L),
                "a nickname made only of formatting codes is refused");
        expectIllegal(() -> new Entry(ANN, "Ann", "a", -1L), "a negative victory time is refused");

        Entry cleaned = new Entry(ANN, "  \u00a7cAnn\u00a7r   the  Brave ", "a", 1L);
        check(cleaned.nickname().equals("Ann the Brave"), "the nickname is sanitised, got " + cleaned.nickname());
        Entry longName = new Entry(ANN, repeat('n', 40), "a", 1L);
        check(longName.nickname().length() == CoreMemorialLayoutPolicy.MAX_NAME_CHARS,
                "an over-long nickname is capped, not dropped");
        Entry zeroTime = new Entry(ANN, "Ann", "a", 0L);
        check(zeroTime.victoryEpochMillis() == 0L, "epoch zero is a legal victory time");
    }

    private static void recordsExactlyOncePerAttemptAndParticipant() {
        Ledger ledger = Ledger.empty();
        check(ledger.isEmpty(), "a new ledger is empty");

        Write first = ledger.record(new Entry(ANN, "Ann", "w1", 10L));
        check(first.outcome() == Outcome.RECORDED, "the first victory is recorded");
        check(first.changed(), "recording reports a change");
        check(first.ledger().size() == 1, "the ledger holds one winner");

        Write replay = first.ledger().record(new Entry(ANN, "Ann", "w1", 11L));
        check(replay.outcome() == Outcome.DUPLICATE, "a replayed victory is a duplicate");
        check(!replay.changed(), "a duplicate is not a change");
        check(replay.ledger() == first.ledger(), "a duplicate returns the same ledger instance");
        check(replay.ledger().size() == 1, "a duplicate does not append a line");

        Write nextAttempt = replay.ledger().record(new Entry(ANN, "Ann", "w2", 20L));
        check(nextAttempt.outcome() == Outcome.RECORDED, "a second attempt by the same account is a new victory");

        Write secondAccount = nextAttempt.ledger().record(new Entry(BOB, "Bob", "w1", 12L));
        check(secondAccount.outcome() == Outcome.RECORDED, "the same attempt records a different account separately");
        check(secondAccount.ledger().size() == 3, "three accepted victories, three entries");

        Write nullEntry = secondAccount.ledger().record(null);
        check(nullEntry.outcome() == Outcome.REJECTED, "a null entry is rejected");
        check(nullEntry.ledger().size() == 3, "a rejected entry changes nothing");

        check(secondAccount.ledger().contains("w1", ANN.toUpperCase(java.util.Locale.ROOT)),
                "membership is keyed by the canonical uuid");
        check(!secondAccount.ledger().contains("w3", ANN), "an unrecorded attempt is not a member");
        check(!secondAccount.ledger().contains(null, ANN), "a null attempt is not a member");
        check(!secondAccount.ledger().contains("w1", "bad"), "a malformed uuid is not a member");
    }

    private static void freezesTheFirstNickname() {
        Ledger ledger = Ledger.empty().record(new Entry(ANN, "Ann", "w1", 10L)).ledger();
        Ledger afterRename = ledger.record(new Entry(ANN, "AnnRenamed", "w1", 99L)).ledger();
        check(afterRename == ledger, "a duplicate write does not rewrite the stored nickname");
        check(afterRename.nicknames().equals(List.of("Ann")),
                "the memorial keeps the victory-time nickname, got " + afterRename.nicknames());

        String encoded = afterRename.encode();
        check(encoded.contains("\tAnn\t10\n"), "the frozen nickname and time are what reach the file");
        check(!encoded.contains("AnnRenamed"), "the later name never reaches the file");
    }

    private static void refusesToEvictAWinnerAtTheCap() {
        List<Entry> full = new ArrayList<>(CoreMemorialLedgerPolicy.MAX_ENTRIES);
        for (int i = 0; i < CoreMemorialLedgerPolicy.MAX_ENTRIES; i++) {
            full.add(new Entry(hexUuid(i), "W" + i, "a" + i, i));
        }
        Ledger ledger = new Ledger(full);
        check(ledger.size() == CoreMemorialLedgerPolicy.MAX_ENTRIES, "the ledger can hold the documented maximum");

        Write overflow = ledger.record(new Entry(hexUuid(999_999), "Late", "late", 1L));
        check(overflow.outcome() == Outcome.FULL, "a full ledger refuses a new winner");
        check(overflow.ledger().size() == CoreMemorialLedgerPolicy.MAX_ENTRIES, "a full ledger keeps its size");
        check(overflow.ledger().entries().get(0).nickname().equals("W0"),
                "the oldest winner is never evicted to make room");
        check(!overflow.changed(), "refusing is not a change");
    }

    private static void ordersIndependentlyOfInsertionOrder() {
        Entry early = new Entry(BOB, "Bob", "a", 5L);
        Entry late = new Entry(ANN, "Ann", "b", 50L);
        Entry sameTimeB = new Entry(BOB, "Bob", "b", 50L);
        Entry sameTimeA = new Entry(ANN, "Ann", "a2", 50L);

        Ledger forward = Ledger.empty()
                .record(early).ledger()
                .record(late).ledger()
                .record(sameTimeB).ledger()
                .record(sameTimeA).ledger();
        Ledger backward = Ledger.empty()
                .record(sameTimeA).ledger()
                .record(sameTimeB).ledger()
                .record(late).ledger()
                .record(early).ledger();

        check(forward.sorted().equals(backward.sorted()), "canonical order does not depend on insertion order");
        check(forward.encode().equals(backward.encode()), "the encoded bytes do not depend on insertion order");
        check(forward.sorted().get(0).victoryEpochMillis() == 5L, "the oldest victory sorts first");
        check(forward.sorted().get(1).attemptId().equals("a2"), "a time tie is broken by attempt id first");
        check(forward.sorted().get(2).attemptId().equals("b")
                        && forward.sorted().get(2).uuid().equals(ANN)
                        && forward.sorted().get(3).uuid().equals(BOB),
                "an attempt id tie is broken by uuid");
    }

    private static void encodesDeterministically() {
        Ledger ledger = Ledger.empty()
                .record(new Entry(BOB, "Bob", "w1", 20L)).ledger()
                .record(new Entry(ANN, "Ann", "w1", 10L)).ledger();
        String encoded = ledger.encode();
        check(encoded.startsWith(CoreMemorialLedgerPolicy.HEADER + "\n"), "the file starts with its version header");
        check(encoded.endsWith("\n"), "the file ends with a newline");
        check(encoded.equals(ledger.encode()), "encoding is stable across calls");
        String[] rows = encoded.split("\n");
        check(rows.length == 3, "a header and one row per winner, got " + rows.length);
        check(rows[1].startsWith(ANN + "\t" + "w1" + "\tAnn\t10"), "rows are canonical order, uuid, attempt, name, time");
        check(rows[1].indexOf('\t') > 0 && rows[1].split("\t", -1).length == 4, "a row has exactly four fields");
    }

    private static void roundTripsThroughDecode() {
        Ledger ledger = Ledger.empty()
                .record(new Entry(ANN, "Ann", "w1", 10L)).ledger()
                .record(new Entry(BOB, "Bob", "w1", 20L)).ledger()
                .record(new Entry(ANN, "Ann", "w2", 30L)).ledger();
        Loaded loaded = CoreMemorialLedgerPolicy.decode(ledger.encode());
        check(loaded.status() == LoadStatus.LOADED, "a written ledger loads");
        check(loaded.intact(), "a written ledger loads with nothing rejected");
        check(loaded.ledger().encode().equals(ledger.encode()), "a round-trip is byte-identical");
        check(loaded.ledger().display().shown() == ledger.display().shown(), "the display survives a round-trip");

        Loaded empty = CoreMemorialLedgerPolicy.decode(null);
        check(empty.status() == LoadStatus.EMPTY && empty.ledger().isEmpty(), "a missing payload is empty, not corrupt");
        check(CoreMemorialLedgerPolicy.decode("   \n\n ").status() == LoadStatus.EMPTY,
                "a whitespace-only payload is empty");
        check(CoreMemorialLedgerPolicy.decode("").rejectedRows() == 0, "an empty payload rejects nothing");
    }

    private static void rejectsAForeignFormat() {
        Loaded foreign = CoreMemorialLedgerPolicy.decode("winner,winner,chicken,dinner\n");
        check(foreign.status() == LoadStatus.REJECTED, "an unknown header is rejected");
        check(foreign.ledger().isEmpty(), "an unknown format contributes no winners");

        Loaded future = CoreMemorialLedgerPolicy.decode(
                CoreMemorialLedgerPolicy.HEADER.replace("v1", "v2") + "\n" + ANN + "\ta\tAnn\t1\n");
        check(future.status() == LoadStatus.REJECTED, "a future format version is rejected, not guessed");
        check(future.ledger().isEmpty(), "a future version loses nothing to a partial parse");

        Loaded leadingBlank = CoreMemorialLedgerPolicy.decode(
                "\n  \n" + CoreMemorialLedgerPolicy.HEADER + "\n" + ANN + "\ta\tAnn\t1\n");
        check(leadingBlank.status() == LoadStatus.LOADED, "leading blank lines do not break the header");
        check(leadingBlank.ledger().size() == 1, "the row after the header still loads");
    }

    private static void countsEveryRowItCannotHonour() {
        String header = CoreMemorialLedgerPolicy.HEADER + "\n";
        Loaded mixed = CoreMemorialLedgerPolicy.decode(header
                + ANN + "\ta\tAnn\t10\n"
                + "short-row\n"
                + "not-a-uuid\ta\tAnn\t10\n"
                + BOB + "\tb\tBob\tnot-a-number\n"
                + BOB + "\tb\tBob\t-5\n"
                + ANN + "\ta\tAnn\t10\n"
                + "\n"
                + ANN + "\tc\tAnn\textra\tfield\n");
        check(mixed.status() == LoadStatus.LOADED, "a recognisable file still loads");
        check(mixed.ledger().size() == 1, "only the one valid row loads, got " + mixed.ledger().size());
        check(mixed.rejectedRows() == 6,
                "every malformed and duplicate row is counted, got " + mixed.rejectedRows());
        check(!mixed.intact(), "a file with rejected rows is not reported as intact");

        List<String> rows = new ArrayList<>();
        rows.add(header.trim());
        int overflow = 6;
        for (int i = 0; i < CoreMemorialLedgerPolicy.MAX_ENTRIES + overflow; i++) {
            rows.add(hexUuid(i) + "\ta" + i + "\tW" + i + "\t" + i);
        }
        Loaded capped = CoreMemorialLedgerPolicy.decode(String.join("\n", rows) + "\n");
        check(capped.status() == LoadStatus.LOADED, "an over-capacity file still loads its bounded prefix");
        check(capped.ledger().size() == CoreMemorialLedgerPolicy.MAX_ENTRIES, "the ledger is capped at the documented size");
        check(capped.rejectedRows() == overflow,
                "the rows beyond the cap are reported, got " + capped.rejectedRows());

        Loaded duplicates = CoreMemorialLedgerPolicy.decode(header
                + ANN + "\ta\tAnn\t10\n" + ANN + "\ta\tAnn\t11\n");
        check(duplicates.ledger().size() == 1, "a duplicated row in the file does not double a winner");
        check(duplicates.rejectedRows() == 1, "the duplicated row is reported");
        check(duplicates.ledger().entries().get(0).victoryEpochMillis() == 10L,
                "the first stored victory time wins the duplicate");
    }

    private static void boundsTheDisplayLikeTheViewport() {
        Ledger empty = Ledger.empty();
        check(empty.display().lines().equals(List.of(CoreMemorialLayoutPolicy.NO_WINNERS)),
                "an empty ledger renders the viewport placeholder");
        check(empty.nicknames().isEmpty(), "an empty ledger has no names");

        List<Entry> raid = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            raid.add(new Entry(hexUuid(i), "Player" + i, "raid", i));
        }
        Ledger ledger = new Ledger(raid);
        CoreMemorialLayoutPolicy.View view = ledger.display();
        check(view.total() == 40, "every winner is counted, got " + view.total());
        check(view.shown() + view.overflow() == view.total(), "shown plus overflow equals the roster");
        check(view.lines().size() <= CoreMemorialLayoutPolicy.MAX_LINES + 1,
                "the block stays inside the viewport budget, got " + view.lines().size());
        for (String line : view.lines()) {
            check(line.length() <= CoreMemorialLayoutPolicy.MAX_LINE_CHARS,
                    "a line stays inside the character budget: " + line);
        }
    }

    private static String hexUuid(int value) {
        return String.format("%08x-0000-0000-0000-%012x", value, value);
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[Math.max(0, count)];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static void expectIllegal(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
