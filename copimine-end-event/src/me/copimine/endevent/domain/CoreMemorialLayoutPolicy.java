package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * V6 Phase 4: {@code CoreMemorialService} text viewport for a large winner
 * roster.
 *
 * <p>The current memorial joins every winner into one {@code "a, b, c, ..."}
 * line. A full 11-player raid already overflows the 240 px display, and a
 * 20+ player roster turns the memorial into an unreadable wall. This policy
 * fixes only the parts that can be decided without a server: how names are
 * sanitised, how duplicates are collapsed, how the roster is ordered, and how
 * it is packed into a bounded, deterministic set of lines with an explicit
 * overflow count.</p>
 *
 * <p>It is deliberately state-free and owns no health, rewards or gameplay
 * values: the memorial is a memorial. The caller persists
 * {@link #canonicalNames(List)} (stable, deduped, sorted) and renders
 * {@link #layout(List)}.</p>
 */
public final class CoreMemorialLayoutPolicy {
    /** Longest single name kept; longer input is truncated, never dropped. */
    public static final int MAX_NAME_CHARS = 16;
    /** Names packed onto one rendered line. */
    public static final int NAMES_PER_LINE = 3;
    /** Hard ceiling on rendered body lines (title/chrome are added by the caller). */
    public static final int MAX_LINES = 5;
    /** Hard ceiling on one rendered line, in characters. */
    public static final int MAX_LINE_CHARS = 48;
    /** Placeholder when no winner name survives sanitising. */
    public static final String NO_WINNERS = "участники";

    private CoreMemorialLayoutPolicy() {
    }

    /** The bounded, deterministic view the display renders. */
    public record View(List<String> lines, int shown, int total, int overflow) {
        public View {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        /** True when the roster did not fit and a "+N" summary line is present. */
        public boolean truncated() {
            return overflow > 0;
        }

        /** True when the whole roster is visible. */
        public boolean complete() {
            return overflow == 0;
        }
    }

    /**
     * Sanitise, collapse exact duplicates and sort the roster into the stable
     * order that is safe to persist and re-render.
     *
     * <p>Deduplication is exact, never case-insensitive: {@code "Ann"} and
     * {@code "ann"} are two different accounts, and a memorial that printed one
     * winner twice or swallowed one of them would both lose a name and lie about
     * the roster size. The sort is case-insensitive with a natural-order
     * tie-break, so the same roster always renders identically regardless of
     * join order.</p>
     */
    public static List<String> canonicalNames(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return List.of();
        }
        java.util.Set<String> unique = new java.util.LinkedHashSet<>();
        for (String raw : rawNames) {
            String clean = sanitize(raw);
            if (!clean.isEmpty()) {
                unique.add(clean);
            }
        }
        List<String> names = new ArrayList<>(unique);
        names.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(name -> name));
        return List.copyOf(names);
    }

    /**
     * One name as it may be displayed: no formatting codes, no control
     * characters, collapsed whitespace, trimmed, and length-capped so a hostile
     * or corrupt profile cannot push the whole viewport off screen.
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') { // drop the following formatting code too
                i++;
                continue;
            }
            if (Character.isISOControl(c)) {
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    c = ' ';
                } else {
                    continue;
                }
            }
            if (Character.isWhitespace(c)) {
                c = ' ';
            }
            if (c == ' ') {
                if (lastWasSpace) {
                    continue;
                }
                lastWasSpace = true;
            } else {
                lastWasSpace = false;
            }
            cleaned.append(c);
        }
        String trimmed = cleaned.toString().trim();
        if (trimmed.length() > MAX_NAME_CHARS) {
            return trimmed.substring(0, MAX_NAME_CHARS).trim();
        }
        return trimmed;
    }

    /**
     * Render the bounded viewport for the given roster.
     *
     * <p>Names are packed {@value #NAMES_PER_LINE} per line, and a line is
     * closed early if adding the next name would exceed {@value #MAX_LINE_CHARS}.
     * When the roster does not fit in {@value #MAX_LINES} body lines, the last
     * available line is given to a summary such as {@code "…и ещё 25"} and the
     * names it displaces are reported as {@link View#overflow()}, so
     * {@code shown + overflow == total} always holds and the block never grows
     * past the display budget.</p>
     */
    public static View layout(List<String> rawNames) {
        List<String> names = canonicalNames(rawNames);
        if (names.isEmpty()) {
            return new View(List.of(NO_WINNERS), 0, 0, 0);
        }

        List<String> lines = new ArrayList<>();
        int index = 0;
        while (index < names.size() && lines.size() < MAX_LINES) {
            boolean finalSlot = lines.size() == MAX_LINES - 1;
            int lineStart = index;
            StringBuilder line = new StringBuilder();
            int onLine = 0;
            while (index < names.size() && onLine < NAMES_PER_LINE) {
                String name = names.get(index);
                int extra = (onLine == 0 ? 0 : 2) + name.length();
                if (onLine > 0 && line.length() + extra > MAX_LINE_CHARS) {
                    break;
                }
                if (onLine > 0) {
                    line.append(", ");
                }
                line.append(name);
                index++;
                onLine++;
            }
            if (finalSlot && index < names.size()) {
                // The budget is spent and names remain. Rendering this line would
                // silently drop the rest, so give the slot to the summary instead
                // and report exactly those names as overflow.
                index = lineStart;
                break;
            }
            lines.add(line.toString());
        }

        int shown = index;
        int overflow = names.size() - shown;
        if (overflow > 0) {
            lines.add("…и ещё " + overflow);
        }
        return new View(lines, shown, names.size(), overflow);
    }
}
