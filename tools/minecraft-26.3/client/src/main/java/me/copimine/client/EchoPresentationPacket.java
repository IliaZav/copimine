package me.copimine.client;

import java.util.UUID;
import me.copimine.endevent.domain.Phase5EchoCopyPresentationPolicy;

/** Echo semantics carried inside the shared bridge v3 envelope. */
public record EchoPresentationPacket(String operation, EchoPresentationState.Frame frame,
                                     float copyAlpha) {
    /** An ordinary Wave 7 echo is drawn as itself, not as a translucent copy. */
    private static final float OPAQUE = 1.0F;

    public static EchoPresentationPacket decode(BridgePayload payload) {
        if (payload == null || payload.protocol() != ClientBridgeProtocol.PROTOCOL_VERSION
                || !"END_RIFT_ECHO_V1".equals(payload.shaderpack())
                || payload.clearPolicy().length() > 256 || payload.status().length() > 64)
            throw new IllegalArgumentException("Invalid Echo bridge envelope");
        String operation = switch (payload.messageType()) {
            case "END_EVENT:END_ECHO_BIND" -> "BIND";
            case "END_EVENT:END_ECHO_STATE" -> "STATE";
            case "END_EVENT:END_ECHO_REMOVE" -> "REMOVE";
            default -> throw new IllegalArgumentException("Unknown Echo operation");
        };
        String[] p = payload.clearPolicy().split("\\|", -1);
        if (p.length != 12 || !p[4].equals("0") && !p[4].equals("1"))
            throw new IllegalArgumentException("Invalid Echo semantic field count");
        // V6 Phase 5: a RAGE player copy is identified by its reserved epoch band.
        // The signed body forbids non-empty reserved strings, so the marker
        // cannot travel in `reason`; the frame epoch already does. A Wave 7 duel
        // epoch stays below EPOCH_BASE and keeps its opaque drained body.
        long epoch = Long.parseLong(p[1]);
        float alpha = Phase5EchoCopyPresentationPolicy.isCopyEpoch(epoch)
                ? (float) Phase5EchoCopyPresentationPolicy.COPY_ALPHA : OPAQUE;
        return new EchoPresentationPacket(operation, new EchoPresentationState.Frame(
                UUID.fromString(payload.sessionId()), payload.seq(), epoch,
                UUID.fromString(payload.clientVersion()), UUID.fromString(payload.mode()), UUID.fromString(p[0]),
                payload.status(), Long.parseLong(p[2]), EchoPresentationState.Pose.valueOf(p[3]),
                p[4].equals("1"), EchoPresentationState.UseHand.valueOf(p[5]),
                Integer.parseInt(p[6]), Integer.parseInt(p[7]), Long.parseLong(p[8]),
                Long.parseLong(p[9]), Integer.parseInt(p[10]), Integer.parseInt(p[11])), alpha);
    }
}
