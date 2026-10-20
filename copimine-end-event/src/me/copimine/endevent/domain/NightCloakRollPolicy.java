package me.copimine.endevent.domain;

import java.util.Locale;
import java.util.SplittableRandom;

/** Durable decision policy for the independent End Rift Night Cloak roll. */
public final class NightCloakRollPolicy {
    public static final String WON = "WON";
    public static final String NOT_WON = "NOT_WON";
    public static final String DELIVERED = "DELIVERED";
    public static final String ALREADY_ISSUED = "ALREADY_ISSUED";
    public static final String PENDING_DELIVERY = "PENDING_DELIVERY";

    private NightCloakRollPolicy() {
    }

    /**
     * Resolves a missing result once.  A known result is returned verbatim so
     * retries and restarts cannot reroll a participant.
     */
    public static String resolve(String persisted, double chance, long seed) {
        String existing = normalize(persisted);
        if (isWon(existing) || NOT_WON.equals(existing)) {
            return existing;
        }
        if (!Double.isFinite(chance) || chance <= 0.0D) {
            return NOT_WON;
        }
        if (chance >= 1.0D) {
            return WON;
        }
        return new SplittableRandom(seed).nextDouble() < chance ? WON : NOT_WON;
    }

    public static boolean isWon(String result) {
        String normalized = normalize(result);
        return WON.equals(normalized) || DELIVERED.equals(normalized)
                || ALREADY_ISSUED.equals(normalized) || PENDING_DELIVERY.equals(normalized);
    }

    public static boolean isFinal(String result) {
        String normalized = normalize(result);
        return NOT_WON.equals(normalized) || DELIVERED.equals(normalized)
                || ALREADY_ISSUED.equals(normalized) || PENDING_DELIVERY.equals(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
