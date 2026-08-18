package me.copimine.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public final class ClientReadyProtocol {
    public static final int PROTOCOL_VERSION = 3;
    public static final String READY_CHANNEL = "copimine:client_ready";
    public static final String ACK_CHANNEL = "copimine:client_ready_ack";

    private static final ClientReadyRetryState RETRY_STATE = new ClientReadyRetryState();
    private static boolean registered;
    private static boolean connected;
    private static boolean timeoutLogged;
    private static String lastError = "";
    private static String lastClientVersion = "unknown";

    private ClientReadyProtocol() {
    }

    public static void registerNetworking() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(ClientReadyPayload.ID, ClientReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClientReadyAckPayload.ID, ClientReadyAckPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ClientReadyAckPayload.ID, (payload, context) -> {
            RETRY_STATE.acknowledge(payload.accepted(), payload.reason());
            if (payload.accepted()) {
                CopiMineClientLogger.info("[CopiMineClient/Gate] Server accepted protocol=" + payload.requiredProtocol());
            } else {
                lastError = payload.reason().isBlank() ? "INTERNAL_GATE_ERROR" : payload.reason();
                CopiMineClientLogger.warn("[CopiMineClient/Gate] Server rejected client reason="
                        + lastError + " clientProtocol=" + payload.receivedProtocol()
                        + " requiredProtocol=" + payload.requiredProtocol());
            }
        });
    }

    public static void onJoin() {
        connected = true;
        timeoutLogged = false;
        lastError = "";
        lastClientVersion = clientVersion();
        RETRY_STATE.start(System.currentTimeMillis());
    }

    public static void onDisconnect() {
        connected = false;
        RETRY_STATE.reset();
        timeoutLogged = false;
        lastError = "";
        lastClientVersion = "unknown";
    }

    public static void tick(MinecraftClient client) {
        if (!connected || client.getNetworkHandler() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (RETRY_STATE.shouldSend(now) && ClientPlayNetworking.canSend(ClientReadyPayload.ID)) {
            try {
                lastClientVersion = clientVersion();
                int attempt = RETRY_STATE.attempts() + 1;
                ClientPlayNetworking.send(new ClientReadyPayload(PROTOCOL_VERSION, lastClientVersion));
                RETRY_STATE.markSent(now);
                CopiMineClientLogger.info("[CopiMineClient/Gate] Sending CLIENT_READY attempt="
                        + attempt + " protocol=" + PROTOCOL_VERSION + " version=" + lastClientVersion);
            } catch (RuntimeException error) {
                lastError = "CLIENT_CHANNEL_ERROR";
                CopiMineClientLogger.error("[CopiMineClient/Gate] CLIENT_READY send failed", error);
            }
        }
        if (RETRY_STATE.expired(now) && !timeoutLogged) {
            timeoutLogged = true;
            lastError = "CLIENT_READY_TIMEOUT";
            CopiMineClientLogger.warn("[CopiMineClient/Gate] CLIENT_READY deadline elapsed attempts=" + RETRY_STATE.attempts());
        }
    }

    public static String clientVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("copimineclient")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (RuntimeException error) {
            return "unknown";
        }
    }

    public static String statusLine() {
        return "protocol=" + PROTOCOL_VERSION
                + ", version=" + lastClientVersion
                + ", attempts=" + RETRY_STATE.attempts() + "/" + ClientReadyRetryState.MAX_ATTEMPTS
                + ", accepted=" + (RETRY_STATE.accepted() ? "yes" : "no")
                + ", active=" + (RETRY_STATE.active() ? "yes" : "no")
                + ", lastError=" + (lastError.isBlank() ? "-" : lastError);
    }
}
