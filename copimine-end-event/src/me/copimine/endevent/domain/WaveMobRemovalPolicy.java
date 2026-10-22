package me.copimine.endevent.domain;

import java.util.Locale;

/** Distinguishes an actual wave-mob death from external or administrative removal. */
public final class WaveMobRemovalPolicy {
    private WaveMobRemovalPolicy() { }

    public static Disposition classify(boolean currentSessionOwner, boolean cleanupAuthorized,
                                        String cause) {
        if (!currentSessionOwner) return Disposition.IGNORE;
        if (cause == null || cause.isBlank()) return Disposition.UNEXPECTED;
        return switch (cause.trim().toUpperCase(Locale.ROOT)) {
            case "UNLOAD" -> Disposition.UNLOAD;
            case "DEATH" -> Disposition.CONFIRMED_DEATH;
            default -> cleanupAuthorized
                    ? Disposition.AUTHORIZED_CLEANUP : Disposition.UNEXPECTED;
        };
    }

    public enum Disposition {
        IGNORE,
        UNLOAD,
        CONFIRMED_DEATH,
        AUTHORIZED_CLEANUP,
        UNEXPECTED
    }
}
