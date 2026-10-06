package me.copimine.client;

import java.util.UUID;

/** Echo semantics carried inside the unchanged shared bridge v2 envelope. */
public record EchoPresentationPacket(String operation, EchoPresentationState.Frame frame) {
    public static EchoPresentationPacket decode(BridgePayload payload) {
        if (payload == null || payload.protocol() != 2 || !"END_RIFT_ECHO_V1".equals(payload.shaderpack())
                || payload.clearPolicy().length() > 256 || payload.status().length() > 64)
            throw new IllegalArgumentException("Invalid Echo bridge envelope");
        String operation = switch (payload.type()) {
            case "END_EVENT:END_ECHO_BIND" -> "BIND";
            case "END_EVENT:END_ECHO_STATE" -> "STATE";
            case "END_EVENT:END_ECHO_REMOVE" -> "REMOVE";
            default -> throw new IllegalArgumentException("Unknown Echo operation");
        };
        String[] p = payload.clearPolicy().split("\\|", -1);
        if (p.length != 12 || !p[4].equals("0") && !p[4].equals("1"))
            throw new IllegalArgumentException("Invalid Echo semantic field count");
        return new EchoPresentationPacket(operation, new EchoPresentationState.Frame(
                UUID.fromString(payload.sessionId()), payload.seq(), Long.parseLong(p[1]),
                UUID.fromString(payload.clientVersion()), UUID.fromString(payload.mode()), UUID.fromString(p[0]),
                payload.status(), Long.parseLong(p[2]), EchoPresentationState.Pose.valueOf(p[3]),
                p[4].equals("1"), EchoPresentationState.UseHand.valueOf(p[5]),
                Integer.parseInt(p[6]), Integer.parseInt(p[7]), Long.parseLong(p[8]),
                Long.parseLong(p[9]), Integer.parseInt(p[10]), Integer.parseInt(p[11])));
    }
}
