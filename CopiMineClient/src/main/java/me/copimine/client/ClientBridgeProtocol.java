package me.copimine.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ClientBridgeProtocol {
    public static final String MOD_CHANNEL = "copimine:client_bridge";
    public static final int PROTOCOL_VERSION = 2;
    public static final String TYPE_HELLO = "hello";
    public static final String TYPE_HEARTBEAT = "heartbeat";
    public static final String TYPE_VISUAL_ACK = "visual_ack";
    public static final String TYPE_VISUAL_FINISHED = "visual_finished";
    public static final String TYPE_VISUAL_ERROR = "visual_error";
    public static final String TYPE_VISUAL_START = "visual_start";
    public static final String TYPE_VISUAL_STOP = "visual_stop";
    public static final String TYPE_VISUAL_CLEAR_ALL = "visual_clear_all";
    public static final String TYPE_PING = "ping";
    public static final Set<String> SUPPORTED_EFFECTS = Set.of(
            "DESATURATE",
            "COLOR_CONVOLVE",
            "SCAN_PINCUSHION",
            "GREEN_NOISE",
            "INVERT",
            "WOBBLE",
            "BLOBS",
            "PENCIL",
            "CHAOS"
    );

    private static final int MAX_HELLO_ATTEMPTS = 10;
    private static final long HELLO_RETRY_INTERVAL_MS = 1_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private static int helloAttempts;
    private static boolean helloSent;
    private static boolean helloConfirmed;
    private static boolean connected;
    private static long lastHelloAttemptAt;
    private static long lastHeartbeatSentAt;
    private static long lastServerPingAt;
    private static long lastAckSeq;
    private static String lastError = "";
    private static String sessionId = "";
    private static String clientVersion = "0.1.0";

    private ClientBridgeProtocol() {
    }

    public static void registerNetworking(ClientVisualManager manager) {
        PayloadTypeRegistry.playC2S().register(BridgePayload.ID, BridgePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayload.ID, BridgePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BridgePayload.ID, (payload, context) -> {
            if (payload.protocol() != PROTOCOL_VERSION) {
                lastError = "protocol-mismatch:" + payload.protocol();
                return;
            }
            switch (payload.type()) {
                case TYPE_PING -> {
                    helloConfirmed = true;
                    lastServerPingAt = System.currentTimeMillis();
                }
                case TYPE_VISUAL_START -> context.client().execute(() -> {
                    if (!managerStatusAllowsServerVisuals(manager)) {
                        sendVisualError(payload.seq(), payload.effectId(), "server-visuals-disabled");
                        return;
                    }
                    String effectId = payload.effectId() == null ? "" : payload.effectId().trim().toUpperCase(Locale.ROOT);
                    if (!SUPPORTED_EFFECTS.contains(effectId)) {
                        sendVisualError(payload.seq(), effectId, "unsupported-effect");
                        return;
                    }
                    manager.start(payload.effectId(), payload.seq(), payload.durationSeconds(), payload.intensity(), payload.clearPolicy());
                    sendVisualAck(payload.seq(), payload.effectId(), "STARTED");
                });
                case TYPE_VISUAL_STOP -> context.client().execute(() -> {
                    manager.stop(payload.effectId());
                    sendVisualAck(payload.seq(), payload.effectId(), "STOPPED");
                });
                case TYPE_VISUAL_CLEAR_ALL -> context.client().execute(() -> {
                    manager.clearAll("server_clear");
                    sendVisualAck(payload.seq(), "", "CLEARED");
                });
                default -> {
                }
            }
        });
    }

    public static boolean sendHello() {
        if (!ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return false;
        }
        Set<String> supported = new LinkedHashSet<>();
        for (String effectId : SUPPORTED_EFFECTS) {
            supported.add(effectId.toUpperCase(Locale.ROOT));
        }
        ClientPlayNetworking.send(BridgePayload.hello(sessionId, clientVersion, IrisCompat.shaderPackActive(), supported));
        helloSent = true;
        return helloSent;
    }

    public static void sendHeartbeat() {
        if (!connected || !helloSent || !ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(BridgePayload.heartbeat(sessionId));
        lastHeartbeatSentAt = System.currentTimeMillis();
    }

    public static void sendVisualAck(long seq, String effectId, String status) {
        if (!ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(BridgePayload.visualAck(sessionId, seq, effectId, status));
        lastAckSeq = seq;
    }

    public static void sendVisualFinished(long seq, String effectId, String reason) {
        if (!ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        ClientPlayNetworking.send(BridgePayload.visualFinished(sessionId, seq, effectId, reason));
    }

    public static void sendVisualError(long seq, String effectId, String reason) {
        if (!ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        lastError = reason == null ? "" : reason;
        ClientPlayNetworking.send(BridgePayload.visualError(sessionId, seq, effectId, reason));
    }

    public static void onJoin() {
        connected = true;
        sessionId = UUID.randomUUID().toString();
        helloAttempts = 0;
        helloSent = false;
        helloConfirmed = false;
        lastHelloAttemptAt = 0L;
        lastHeartbeatSentAt = 0L;
        lastServerPingAt = 0L;
        lastAckSeq = 0L;
        lastError = "";
    }

    public static void onDisconnect() {
        connected = false;
        helloAttempts = 0;
        helloSent = false;
        helloConfirmed = false;
        lastHelloAttemptAt = 0L;
        lastHeartbeatSentAt = 0L;
        lastServerPingAt = 0L;
        lastAckSeq = 0L;
        lastError = "";
        sessionId = "";
    }

    public static void tickNetwork(MinecraftClient client) {
        tickHelloRetry(client);
        if (!connected || client.getNetworkHandler() == null || !helloConfirmed) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastHeartbeatSentAt == 0L || now - lastHeartbeatSentAt >= HEARTBEAT_INTERVAL_MS) {
            sendHeartbeat();
        }
    }

    public static void tickHelloRetry(MinecraftClient client) {
        if (!connected || helloConfirmed || client.getNetworkHandler() == null || helloAttempts >= MAX_HELLO_ATTEMPTS) {
            return;
        }
        if (!ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastHelloAttemptAt != 0L && now - lastHelloAttemptAt < HELLO_RETRY_INTERVAL_MS) {
            return;
        }
        lastHelloAttemptAt = now;
        try {
            helloAttempts++;
            sendHello();
        } catch (RuntimeException ignored) {
        }
    }

    public static String handshakeStatusLine() {
        return "protocol=" + PROTOCOL_VERSION
                + ", session=" + (sessionId.isBlank() ? "-" : sessionId)
                + ", helloSent=" + (helloSent ? "yes" : "no")
                + ", helloConfirmed=" + (helloConfirmed ? "yes" : "no")
                + ", attempts=" + helloAttempts + "/" + MAX_HELLO_ATTEMPTS
                + ", lastHeartbeat=" + ageSeconds(lastHeartbeatSentAt)
                + ", lastPing=" + ageSeconds(lastServerPingAt)
                + ", lastAckSeq=" + lastAckSeq
                + ", " + IrisCompat.statusLine()
                + ", lastError=" + (lastError.isBlank() ? "-" : lastError);
    }

    private static String ageSeconds(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return "-";
        }
        return Math.max(0L, (System.currentTimeMillis() - timestampMillis) / 1000L) + "s ago";
    }

    private static boolean managerStatusAllowsServerVisuals(ClientVisualManager manager) {
        return manager != null && manager.serverVisualsAllowed();
    }
}
