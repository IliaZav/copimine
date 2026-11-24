import me.copimine.endevent.domain.CoreMemorialLayoutPolicy;
import me.copimine.endevent.domain.CoreMemorialLayoutPolicy.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * V6 Phase 4 / {@code CoreMemorialService}: the large-roster memorial viewport.
 *
 * <p>These checks pin the behaviour a 20+ player raid needs without a server:
 * order does not depend on join order, duplicates collapse case-insensitively,
 * formatting codes and control characters cannot reach the display, the block
 * never exceeds the line/character budget, and an over-capacity roster states
 * how many winners are not shown.</p>
 */
public final class CoreMemorialLayoutPolicyTest {

    public static void main(String[] args) {
        sanitisesFormattingAndControlCharacters();
        dedupesCaseInsensitivelyAndSortsDeterministically();
        keepsASmallRosterComplete();
        boundsALargeRosterAndCountsOverflow();
        respectsThePerLineCharacterBudget();
        handlesEmptyRosters();
        isIdempotent();
        System.out.println("CoreMemorialLayoutPolicyTest OK");
    }

    private static void sanitisesFormattingAndControlCharacters() {
        check(CoreMemorialLayoutPolicy.sanitize("§cИлья§r").equals("Илья"),
                "a colour code is stripped from the displayed name");
        check(CoreMemorialLayoutPolicy.sanitize("  Два   Пробела  ").equals("Два Пробела"),
                "whitespace is collapsed and trimmed");
        check(CoreMemorialLayoutPolicy.sanitize("Bad\u0000Name").equals("BadName"),
                "a control character is dropped");
        check(CoreMemorialLayoutPolicy.sanitize("").isEmpty(), "an empty name stays empty");
        check(CoreMemorialLayoutPolicy.sanitize(null).isEmpty(), "a null name is empty, never NPE");
        String longName = repeat('a', CoreMemorialLayoutPolicy.MAX_NAME_CHARS + 12);
        check(CoreMemorialLayoutPolicy.sanitize(longName).length()
                <= CoreMemorialLayoutPolicy.MAX_NAME_CHARS, "an over-long name is capped");
    }

    private static void dedupesCaseInsensitivelyAndSortsDeterministically() {
        List<String> once = CoreMemorialLayoutPolicy.canonicalNames(
                Arrays.asList("zed", "Ann", "bob", "bob"));
        check(once.equals(List.of("Ann", "bob", "zed")),
                "exact duplicates collapse and the roster sorts stably, got " + once);

        List<String> reversed = CoreMemorialLayoutPolicy.canonicalNames(
                Arrays.asList("bob", "bob", "Ann", "zed"));
        check(once.equals(reversed),
                "the canonical order does not depend on input order, got " + reversed);

        // Two different accounts whose names differ only by case must BOTH stay:
        // collapsing them would silently drop a winner from the memorial.
        List<String> distinctAccounts = CoreMemorialLayoutPolicy.canonicalNames(
                Arrays.asList("Ann", "ann", "ANN"));
        check(distinctAccounts.size() == 3,
                "case-different names are distinct accounts, got " + distinctAccounts);
        check(distinctAccounts.equals(List.of("ANN", "Ann", "ann")),
                "case variants sort by a deterministic case-insensitive key, got " + distinctAccounts);
    }

    private static void keepsASmallRosterComplete() {
        View view = CoreMemorialLayoutPolicy.layout(Arrays.asList("Ann", "bob", "zed"));
        check(view.complete(), "a three-player roster is complete");
        check(view.shown() == 3 && view.total() == 3, "every winner is shown once");
        check(view.overflow() == 0, "no overflow is reported");
        check(view.lines().equals(List.of("Ann, bob, zed")),
                "three names pack onto one line, got " + view.lines());
    }

    private static void boundsALargeRosterAndCountsOverflow() {
        List<String> raid = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            raid.add("player" + i);
        }
        View view = CoreMemorialLayoutPolicy.layout(raid);
        int capacity = CoreMemorialLayoutPolicy.NAMES_PER_LINE * CoreMemorialLayoutPolicy.MAX_LINES;
        check(view.total() == 40, "the total count reflects the whole roster");
        check(view.truncated(), "a 40-player roster is truncated");
        check(view.lines().size() <= CoreMemorialLayoutPolicy.MAX_LINES,
                "the block never exceeds " + CoreMemorialLayoutPolicy.MAX_LINES + " lines, got "
                        + view.lines().size());
        check(view.shown() + view.overflow() == 40,
                "shown plus overflow accounts for every winner");
        check(renderedNameCount(view) == view.shown(),
                "shown must equal the number of names actually rendered, got "
                        + renderedNameCount(view) + " vs " + view.shown());
        check(view.overflow() >= 40 - capacity - CoreMemorialLayoutPolicy.NAMES_PER_LINE,
                "the overflow summary is honest about what is hidden, got " + view.overflow());
        check(view.lines().get(view.lines().size() - 1).contains(String.valueOf(view.overflow())),
                "the last line states the number of hidden winners, got " + view.lines());
        for (String line : view.lines()) {
            check(line.length() <= CoreMemorialLayoutPolicy.MAX_LINE_CHARS,
                    "no rendered line exceeds the character budget: " + line);
        }
    }

    private static void respectsThePerLineCharacterBudget() {
        List<String> wide = Collections.nCopies(
                CoreMemorialLayoutPolicy.NAMES_PER_LINE,
                repeat('W', CoreMemorialLayoutPolicy.MAX_NAME_CHARS));
        View view = CoreMemorialLayoutPolicy.layout(wide);
        check(view.complete(), "identical duplicates collapse, so the roster is complete");
        check(view.shown() == 1, "identical duplicates collapse to one winner");
        List<String> distinct = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            distinct.add(repeat((char) ('A' + i), CoreMemorialLayoutPolicy.MAX_NAME_CHARS));
        }
        View packed = CoreMemorialLayoutPolicy.layout(distinct);
        for (String line : packed.lines()) {
            check(line.length() <= CoreMemorialLayoutPolicy.MAX_LINE_CHARS,
                    "a wide name set still respects the line budget: " + line);
        }
    }

    private static void handlesEmptyRosters() {
        View view = CoreMemorialLayoutPolicy.layout(List.of());
        check(view.total() == 0 && view.shown() == 0 && view.overflow() == 0,
                "an empty roster reports zero winners");
        check(view.lines().equals(List.of(CoreMemorialLayoutPolicy.NO_WINNERS)),
                "an empty roster renders the placeholder, got " + view.lines());
        check(CoreMemorialLayoutPolicy.layout(null).lines()
                        .equals(List.of(CoreMemorialLayoutPolicy.NO_WINNERS)),
                "a null roster renders the placeholder, never NPE");
        check(CoreMemorialLayoutPolicy.layout(Arrays.asList("", "   ", "§x")).lines()
                        .equals(List.of(CoreMemorialLayoutPolicy.NO_WINNERS)),
                "names that sanitise to nothing fall back to the placeholder");
    }

    private static void isIdempotent() {
        List<String> raid = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            raid.add("p" + i);
        }
        View first = CoreMemorialLayoutPolicy.layout(raid);
        View canonical = CoreMemorialLayoutPolicy.layout(
                CoreMemorialLayoutPolicy.canonicalNames(raid));
        check(first.lines().equals(canonical.lines()),
                "laying out a canonicalised roster is identical, got "
                        + first.lines() + " vs " + canonical.lines());
        check(first.shown() == canonical.shown() && first.overflow() == canonical.overflow(),
                "canonicalisation does not change the shown/overflow counts");
    }

    private static int renderedNameCount(View view) {
        int count = 0;
        for (String line : view.lines()) {
            if (line.startsWith("…и ещё ")) {
                continue;
            }
            for (String part : line.split(",")) {
                if (!part.isBlank()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
