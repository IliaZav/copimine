package me.copimine.endevent.diagnostics;

/** Diagnostic verbosity.  TRACE is opt-in and is never required for correctness. */
public enum EndRiftDiagnosticMode {
    OFF,
    ESSENTIAL,
    VERBOSE,
    TRACE;

    public static EndRiftDiagnosticMode parse(String value, EndRiftDiagnosticMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? ESSENTIAL : fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? ESSENTIAL : fallback;
        }
    }
}
