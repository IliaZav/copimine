package me.copimine.endevent.domain;

import java.util.Locale;

/**
 * V6 §963 / owner request: the Phase 5 (RAGE, 30–20%) Echo copies must be
 * <b>semi-transparent duplicates of the locked player</b>, drawn by the real
 * vanilla player renderer, not by the Guardian body they used to borrow.
 *
 * <p>This class is the single presentation contract shared by the server (copy
 * epoch band) and the 26.3 client (alpha, tint). It is deliberately dependency-free
 * so the client mod compiles the very same file instead of keeping a drifting
 * copy. Keepalive cadence is owned by
 * {@code me.copimine.endevent.domain.wave7.EchoFrameBroadcastPolicy}: a
 * transition is delivered on its tick and an unchanged frame is refreshed well
 * inside the client's two-second lease.</p>
 *
 * <p>Pure: no Bukkit, no world access, no outcome authority.</p>
 */
public final class Phase5EchoCopyPresentationPolicy {

    /** Owner request: copies read as drained duplicates, never opaque twins. */
    public static final double COPY_ALPHA = 0.55D;

    /**
     * The copy marker lives in the Echo frame's own {@code epoch}, not in a
     * metadata string: {@code EndEventBridgeBodyCodec} refuses any non-empty
     * reserved string on an {@code END_ECHO_*} body ("End Event reserved strings
     * must be empty"), so a reason/source marker could never be signed. The
     * epoch is already part of the 12-field frame the client parses, and Wave 7
     * duel epochs are small runtime counters, so a reserved band above them
     * identifies a Phase 5 copy without widening the wire schema.
     */
    public static final long EPOCH_BASE = 1_000_000L;

    /** The client retires an unrefreshed Echo view after this lease. */
    public static final long CLIENT_LEASE_MILLIS = 2_000L;

    private Phase5EchoCopyPresentationPolicy() {
    }

    /** Epoch for one event generation; strictly above every Wave 7 duel epoch. */
    public static long epochFor(long generation) {
        return EPOCH_BASE + Math.max(0L, generation);
    }

    /**
     * True when an Echo frame epoch belongs to the Phase 5 copy band. Wave 7
     * duels stay below {@link #EPOCH_BASE}, so they keep their opaque body.
     */
    public static boolean isCopyEpoch(long epoch) {
        return epoch >= EPOCH_BASE;
    }

    /** 0..255 alpha byte for a 0..1 alpha; hostile input clamps to invisible. */
    public static int alphaByte(double alpha) {
        double clamped = Double.isFinite(alpha) ? Math.max(0.0D, Math.min(1.0D, alpha)) : 0.0D;
        return (int) Math.round(clamped * 255.0D);
    }

    /** ARGB used by the translucent body pass; RGB is preserved verbatim. */
    public static int translucentTint(int baseRgb, double alpha) {
        return (alphaByte(alpha) << 24) | (baseRgb & 0x00FFFFFF);
    }

    public static String describe(int copyIndex, String family, double alpha, String target) {
        return "PHASE5_PLAYER_ECHO_COPY index=" + copyIndex
                + " family=" + (family == null ? "?" : family)
                + " target=" + (target == null ? "?" : target)
                + " alpha=" + String.format(Locale.ROOT, "%.2f", alpha)
                + " epoch_base=" + EPOCH_BASE;
    }
}
