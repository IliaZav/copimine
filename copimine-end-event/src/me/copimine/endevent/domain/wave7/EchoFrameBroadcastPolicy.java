package me.copimine.endevent.domain.wave7;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Decides whether one presentation frame is worth a signed packet for one
 * viewer.
 *
 * <p>The server used to sign and send one frame per viewer per tick even when
 * nothing a viewer can see had changed. Every one of those packets is an
 * Ed25519 signature and a plugin-message copy, so an idle copy cost as much CPU
 * as a fighting one. The client already extrapolates the item-use clock from
 * the last frame and interpolates position from the carrier entity it is bound
 * to, so a frame that repeats the previous discrete state carries no new
 * information.</p>
 *
 * <p>This policy therefore drops a frame when its viewer-visible signature is
 * unchanged, but re-sends it at least every {@link #KEEPALIVE_TICKS} so the
 * client's two-second lease never lapses and the copy can never silently
 * disappear. A real transition is never delayed: a changed signature is
 * delivered on the tick it happens.</p>
 *
 * <p>Signatures are keyed by viewer <em>and</em> actor. A viewer watching two
 * copies must not have one copy suppress the other, so the actor is part of the
 * key rather than of the signature alone.</p>
 *
 * <p>No Minecraft or Bukkit type appears here: this is arithmetic over the
 * wire fields, which is what lets the gate compile and drive it directly.</p>
 */
public final class EchoFrameBroadcastPolicy {
    /** A repeated frame is refreshed at least this often so the client lease never lapses. */
    public static final long KEEPALIVE_TICKS = 16L;

    /** Number of pipe-separated fields in {@code EchoPresentationProbeState.Frame.fields()}. */
    private static final int FRAME_FIELDS = 12;
    /** Position of the monotonic sequence counter, which changes every frame by design. */
    private static final int SEQUENCE_INDEX = 2;
    /** Position of the extrapolated item-use elapsed counter, which the client advances itself. */
    private static final int USE_ELAPSED_INDEX = 6;

    /** One viewer's last delivered signature and the tick it was delivered on. */
    public record Sent(String signature, long tick) { }

    private final Map<Key, Sent> sent = new HashMap<>();

    private record Key(UUID viewer, UUID actor) {
        Key {
            Objects.requireNonNull(viewer);
            Objects.requireNonNull(actor);
        }
    }

    /**
     * The viewer-visible part of a frame's field list.
     *
     * <p>Everything that changes what a viewer sees is kept (pose, sprint, hand,
     * use duration, swing, hurt, death, equipment, owner, epoch). The sequence
     * counter is dropped because it advances every frame by definition, and the
     * use-elapsed counter is dropped because the client advances it locally from
     * the frame's arrival time. The owner is kept so a copy re-skinned to another
     * carrier cannot be mistaken for the same presentation.</p>
     *
     * <p>A field list of an unexpected shape is returned unchanged, so an
     * unknown wire change degrades to "always send" rather than to silence.</p>
     */
    public static String signatureOf(String fields) {
        if (fields == null) return "";
        String[] parts = fields.split("\\|", -1);
        if (parts.length != FRAME_FIELDS) return fields;
        StringBuilder signature = new StringBuilder(fields.length());
        for (int index = 0; index < parts.length; index++) {
            if (index == SEQUENCE_INDEX || index == USE_ELAPSED_INDEX) continue;
            if (signature.length() > 0) signature.append('|');
            signature.append(parts[index]);
        }
        return signature.toString();
    }

    /**
     * True when this viewer has not yet seen this exact signature, or has not
     * seen it for {@link #KEEPALIVE_TICKS}. A clock that appears to move
     * backwards is treated as a change so a reset can never starve a viewer.
     */
    public boolean shouldSend(UUID viewer, UUID actor, String signature, long tick) {
        if (viewer == null || actor == null || signature == null) return false;
        Sent last = sent.get(new Key(viewer, actor));
        if (last == null) return true;
        if (!last.signature().equals(signature)) return true;
        long elapsed = tick - last.tick();
        if (elapsed < 0L) return true;
        return elapsed >= KEEPALIVE_TICKS;
    }

    /** Record a signature that was actually accepted by the transport. */
    public void record(UUID viewer, UUID actor, String signature, long tick) {
        if (viewer == null || actor == null || signature == null) return;
        sent.put(new Key(viewer, actor), new Sent(signature, tick));
    }

    /** Drop one viewer's memory, e.g. when a viewer leaves or loses the capability. */
    public void forget(UUID viewer) {
        if (viewer == null) return;
        sent.keySet().removeIf(key -> key.viewer().equals(viewer));
    }

    /** Drop every viewer's memory; called when the whole broadcast scope ends. */
    public void clear() {
        sent.clear();
    }

    /** Number of tracked viewer/actor pairs, for diagnostics and tests. */
    public int tracked() {
        return sent.size();
    }
}