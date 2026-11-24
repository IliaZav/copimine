package me.copimine.dungeon.store;

import java.util.List;

/**
 * Outcome of a {@code room import} operation. Problems are human-readable
 * (Russian) and a dry-run never publishes or mutates the live catalogue.
 */
public record ImportResult(List<String> problems, boolean applied, int importedDefinitions) {

    public ImportResult {
        problems = List.copyOf(problems);
    }

    public static ImportResult rejected(List<String> problems) {
        return new ImportResult(problems, false, 0);
    }

    public static ImportResult dryRun(List<String> problems, int definitions) {
        return new ImportResult(problems, false, definitions);
    }

    public static ImportResult applied(List<String> problems, int definitions) {
        return new ImportResult(problems, true, definitions);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public boolean isClean() {
        return problems.isEmpty();
    }
}
