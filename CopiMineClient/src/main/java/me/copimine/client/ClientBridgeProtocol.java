package me.copimine.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
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
    public static final String TYPE_CAPABILITIES_UPDATE = "capabilities_update";
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
            "CHAOS",
            "OVERDOSE",
            "ZHUZEVO_TRIP"
    );

    private static final int MAX_HELLO_ATTEMPTS = 10;
    private static final long HELLO_RETRY_INTERVAL_MS = 1_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;

    private static int helloAttempts;
    private static boolean helloSent;
    private static boolean helloAcknowledged;
    private static boolean connected;
    private static long lastHelloAttemptAt;
    private static long lastHeartbeatSentAt;
    private static long lastServerPingAt;
    private static long lastAckSeq;
    private static String lastError = "";
    private static String sessionId = "";
    private static String clientVersion = CopiMineClient.CLIENT_VERSION;
    private static boolean irisShaderPackActive;
    private static boolean lastReportedIrisShaderPackActive;
    private static boolean irisDetectionFailureLogged;
    private static ClientVisualManager registeredVisualManager;

    private ClientBridgeProtocol() {
    }

    public static void registerNetworking(ClientVisualManager manager) {
        registeredVisualManager = manager;
        PayloadTypeRegistry.playC2S().register(BridgePayload.ID, BridgePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BridgePayload.ID, BridgePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(BridgePayload.ID, (payload, context) -> {
            if (payload.protocol() != PROTOCOL_VERSION) {
                lastError = "protocol-mismatch:" + payload.protocol();
                CopiMineClientLogger.warn("Rejected bridge payload because of protocol mismatch: " + payload.protocol());
                return;
            }
            CopiMineClientLogger.info("Received bridge payload: type=" + payload.type() + ", seq=" + payload.seq() + ", effect=" + payload.effectId() + ", shaderpack=" + payload.shaderpack());
            switch (payload.type()) {
                case TYPE_PING -> {
                    lastServerPingAt = System.currentTimeMillis();
                    helloAcknowledged = true;
                    CopiMineClientLogger.info("Bridge ping acknowledged");
                }
                case TYPE_VISUAL_START -> context.client().execute(() -> {
                    if (!managerStatusAllowsServerVisuals(manager)) {
                        CopiMineClientLogger.warn("Rejected visual start because server visuals are disabled");
                        sendVisualError(payload.seq(), payload.effectId(), "server-visuals-disabled");
                        return;
                    }
                    if (manager.start(
                            payload.effectId(),
                            payload.shaderpack(),
                            payload.seq(),
                            payload.durationMillis(),
                            payload.intensity(),
                            payload.clearPolicy(),
                            payload.fadeInMillis(),
                            payload.fadeOutMillis(),
                            payload.source()
                    )) {
                        String route = manager.activeRuntimeRouteName();
                        CopiMineClientLogger.info("Visual started: seq=" + payload.seq() + ", effect=" + payload.effectId() + ", route=" + route);
                        sendVisualAck(payload.seq(), payload.effectId(), "STARTED_" + route);
                    } else {
                        CopiMineClientLogger.warn("Visual start failed: seq=" + payload.seq() + ", effect=" + payload.effectId() + ", reason=" + manager.lastFailureReason());
                        sendVisualError(payload.seq(), payload.effectId(), manager.lastFailureReason());
                    }
                });
                case TYPE_VISUAL_STOP -> context.client().execute(() -> {
                    manager.stop(payload.effectId());
                    CopiMineClientLogger.info("Visual stop received: seq=" + payload.seq() + ", effect=" + payload.effectId());
                    sendVisualAck(payload.seq(), payload.effectId(), "STOPPED");
                });
                case TYPE_VISUAL_CLEAR_ALL -> context.client().execute(() -> {
                    manager.clearAll("server_clear");
                    CopiMineClientLogger.info("Visual clear-all received");
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
        irisShaderPackActive = detectIrisShaderPackInUse();
        boolean visualsAvailable = detectServerVisualsAllowed();
        boolean shaderpackRuntimeAvailable = visualsAvailable && detectShaderpackRuntimeAvailable();
        ClientPlayNetworking.send(BridgePayload.hello(
                sessionId,
                clientVersion,
                supportedEffects(),
                visualsAvailable,
                shaderpackRuntimeAvailable,
                irisShaderPackActive
        ));
        helloSent = true;
        lastReportedIrisShaderPackActive = irisShaderPackActive;
        CopiMineClientLogger.info("Bridge hello sent: session=" + sessionId + ", shaderpackRuntimeAvailable=" + shaderpackRuntimeAvailable + ", irisActive=" + irisShaderPackActive);
        return true;
    }

    public static void sendCapabilitiesUpdate() {
        if (!connected || !helloSent || !helloAcknowledged || !ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        irisShaderPackActive = detectIrisShaderPackInUse();
        boolean visualsAvailable = detectServerVisualsAllowed();
        boolean shaderpackRuntimeAvailable = visualsAvailable && detectShaderpackRuntimeAvailable();
        ClientPlayNetworking.send(BridgePayload.capabilitiesUpdate(
                sessionId,
                clientVersion,
                supportedEffects(),
                visualsAvailable,
                shaderpackRuntimeAvailable,
                irisShaderPackActive
        ));
        lastReportedIrisShaderPackActive = irisShaderPackActive;
        CopiMineClientLogger.info("Bridge capabilities update sent: irisActive=" + irisShaderPackActive + ", shaderpackRuntimeAvailable=" + shaderpackRuntimeAvailable);
    }

    public static void sendHeartbeat() {
        if (!connected || !helloSent || !helloAcknowledged || !ClientPlayNetworking.canSend(BridgePayload.ID)) {
            return;
        }
        boolean visualsAvailable = detectServerVisualsAllowed();
        boolean shaderpackRuntimeAvailable = visualsAvailable && detectShaderpackRuntimeAvailable();
        ClientPlayNetworking.send(BridgePayload.heartbeat(sessionId, visualsAvailable, shaderpackRuntimeAvailable, irisShaderPackActive));
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
        CopiMineClientLogger.warn("Visual error reported to server: seq=" + seq + ", effect=" + effectId + ", reason=" + reason);
    }

    public static void onJoin() {
        connected = true;
        sessionId = UUID.randomUUID().toString();
        helloAttempts = 0;
        helloSent = false;
        helloAcknowledged = false;
        lastHelloAttemptAt = 0L;
        lastHeartbeatSentAt = 0L;
        lastServerPingAt = 0L;
        lastAckSeq = 0L;
        lastError = "";
        irisShaderPackActive = false;
        lastReportedIrisShaderPackActive = false;
        irisDetectionFailureLogged = false;
        CopiMineClientLogger.info("Bridge session opened: " + sessionId);
    }

    public static void onDisconnect() {
        connected = false;
        helloAttempts = 0;
        helloSent = false;
        helloAcknowledged = false;
        lastHelloAttemptAt = 0L;
        lastHeartbeatSentAt = 0L;
        lastServerPingAt = 0L;
        lastAckSeq = 0L;
        lastError = "";
        sessionId = "";
        irisShaderPackActive = false;
        lastReportedIrisShaderPackActive = false;
        irisDetectionFailureLogged = false;
        CopiMineClientLogger.info("Bridge session closed");
    }

    public static void tickNetwork(MinecraftClient client) {
        tickHelloRetry(client);
        if (!connected || client.getNetworkHandler() == null || !helloAcknowledged) {
            return;
        }
        long now = System.currentTimeMillis();
        irisShaderPackActive = detectIrisShaderPackInUse();
        if (irisShaderPackActive != lastReportedIrisShaderPackActive) {
            sendCapabilitiesUpdate();
        }
        if (lastHeartbeatSentAt == 0L || now - lastHeartbeatSentAt >= HEARTBEAT_INTERVAL_MS) {
            sendHeartbeat();
        }
    }

    public static void tickHelloRetry(MinecraftClient client) {
        if (!connected || helloAcknowledged || client.getNetworkHandler() == null || helloAttempts >= MAX_HELLO_ATTEMPTS) {
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
        } catch (RuntimeException error) {
            CopiMineClientLogger.error("Bridge hello retry failed", error);
        }
    }

    public static String handshakeStatusLine() {
        return "protocol=" + PROTOCOL_VERSION
                + ", session=" + (sessionId.isBlank() ? "-" : sessionId)
                + ", helloSent=" + (helloSent ? "yes" : "no")
                + ", helloAck=" + (helloAcknowledged ? "yes" : "no")
                + ", attempts=" + helloAttempts + "/" + MAX_HELLO_ATTEMPTS
                + ", lastHeartbeat=" + ageSeconds(lastHeartbeatSentAt)
                + ", lastPing=" + ageSeconds(lastServerPingAt)
                + ", lastAckSeq=" + lastAckSeq
                + ", irisShaderPackActive=" + (irisShaderPackActive ? "yes" : "no")
                + ", shaderpackRuntimeAvailable=" + (detectShaderpackRuntimeAvailable() ? "yes" : "no")
                + ", renderer=shaderpack-first+post-process-fallback"
                + ", lastError=" + (lastError.isBlank() ? "-" : lastError);
    }

    public static boolean isIrisShaderPackActive() {
        return irisShaderPackActive;
    }

    private static Set<String> supportedEffects() {
        Set<String> supported = new LinkedHashSet<>();
        for (String effectId : SUPPORTED_EFFECTS) {
            supported.add(effectId.toUpperCase(Locale.ROOT));
        }
        return supported;
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

    private static boolean detectServerVisualsAllowed() {
        return managerStatusAllowsServerVisuals(registeredVisualManager);
    }

    private static boolean detectShaderpackRuntimeAvailable() {
        return registeredVisualManager != null && registeredVisualManager.shaderpackRuntimeAvailable();
    }

    private static boolean detectIrisShaderPackInUse() {
        try {
            if (!FabricLoader.getInstance().isModLoaded("iris")) {
                return false;
            }
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
            Object value = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
            return value instanceof Boolean enabled && enabled;
        } catch (Throwable error) {
            if (!irisDetectionFailureLogged) {
                irisDetectionFailureLogged = true;
                lastError = "iris-detect-failed:" + error.getClass().getSimpleName();
                CopiMineClientLogger.warn("Iris shader-pack detection failed; runtime diagnostics may be degraded", error);
            }
            return false;
        }
    }
}
