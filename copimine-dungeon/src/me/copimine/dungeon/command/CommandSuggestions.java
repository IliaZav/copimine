package me.copimine.dungeon.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nearest-match suggestion helper for {@code /cmdungeon} (spec OV4.3: "Unknown
 * subcommands return exact usage and nearest suggestions").
 *
 * <p>Pure Java, deterministic and cheap: bounded Levenshtein distance over a
 * small candidate set, so a typo produces a useful hint instead of silence.</p>
 */
public final class CommandSuggestions {

    /** Maximum distance at which a candidate is still offered. */
    public static final int MAX_DISTANCE = 3;

    private CommandSuggestions() {
    }

    /** Best candidate within {@link #MAX_DISTANCE}, or {@code null}. */
    public static String nearest(String input, List<String> candidates) {
        if (input == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String needle = input.trim().toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String haystack = candidate.trim().toLowerCase(Locale.ROOT);
            int distance = distance(needle, haystack);
            if (distance < bestDistance || (distance == bestDistance && best != null
                    && haystack.compareTo(best) < 0)) {
                bestDistance = distance;
                best = haystack;
            }
        }
        return bestDistance <= MAX_DISTANCE ? best : null;
    }

    /** Candidates that start with the prefix, preserving order. */
    public static List<String> prefixMatches(String prefix, List<String> candidates) {
        List<String> matches = new ArrayList<>();
        if (candidates == null) {
            return matches;
        }
        String needle = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /** Tab-completion: prefix matches first, then a nearest single fallback. */
    public static List<String> complete(String prefix, List<String> candidates) {
        List<String> matches = prefixMatches(prefix, candidates);
        if (!matches.isEmpty()) {
            return matches;
        }
        String fallback = nearest(prefix, candidates);
        return fallback == null ? List.of() : List.of(fallback);
    }

    public static int distance(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            char left = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                char right = b.charAt(j - 1);
                int cost = left == right ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
