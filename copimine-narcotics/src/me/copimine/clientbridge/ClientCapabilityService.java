package me.copimine.clientbridge;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientCapabilityService {
    private final Map<UUID, ClientCapabilityState> states = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastProblems = new ConcurrentHashMap<>();
    private volatile long ttlMillis = 30_000L;

    public void setTtlMillis(long ttlMillis) {
        this.ttlMillis = Math.max(5_000L, ttlMillis);
    }

    public void update(Player player, ClientCapabilityState state) {
        if (player == null || state == null) {
            return;
        }
        states.put(player.getUniqueId(), state);
        lastProblems.remove(player.getUniqueId());
    }

    public boolean touch(Player player, String sessionId) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        ClientCapabilityState state = states.get(uuid);
        if (state == null) {
            return false;
        }
        if (sessionId != null && !sessionId.isBlank() && !sessionId.equals(state.sessionId())) {
            states.remove(uuid);
            lastProblems.put(uuid, "session-mismatch");
            return false;
        }
        states.put(uuid, state.touch(System.currentTimeMillis()));
        return true;
    }

    public void reportProblem(Player player, String reason) {
        if (player == null) {
            return;
        }
        states.remove(player.getUniqueId());
        if (reason != null && !reason.isBlank()) {
            lastProblems.put(player.getUniqueId(), reason);
        }
    }

    public void clear(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        states.remove(playerUuid);
        lastProblems.remove(playerUuid);
    }

    public ClientCapabilityState state(Player player) {
        if (player == null) {
            return null;
        }
        ClientCapabilityState state = states.get(player.getUniqueId());
        if (state != null && state.expired(ttlMillis)) {
            states.remove(player.getUniqueId());
            lastProblems.put(player.getUniqueId(), "heartbeat-timeout");
            return null;
        }
        return state;
    }

    public boolean hasCopiMineClient(Player player) {
        return state(player) != null;
    }

    public boolean supportsClientVisuals(Player player) {
        ClientCapabilityState state = state(player);
        return state != null && state.clientModVisuals();
    }

    public boolean supportsEffect(Player player, String effectId) {
        ClientCapabilityState state = state(player);
        return state != null && state.clientModVisuals() && state.supportsEffect(effectId);
    }

    public String describe(Player player) {
        if (player == null) {
            return "player-missing";
        }
        ClientCapabilityState state = state(player);
        if (state == null) {
            String problem = lastProblems.get(player.getUniqueId());
            return problem == null || problem.isBlank()
                    ? "CopiMineClient не обнаружен"
                    : "CopiMineClient недоступен: " + problem;
        }
        long secondsSinceHeartbeat = Math.max(0L, (System.currentTimeMillis() - state.lastSeenMillis()) / 1000L);
        return "protocol=" + state.protocolVersion()
                + ", session=" + state.sessionId()
                + ", clientVersion=" + state.clientVersion()
                + ", clientVisuals=" + state.clientModVisuals()
                + ", clientOverlay=" + state.clientOverlay()
                + ", clientShaderLike=" + state.clientShaderLike()
                + ", irisShaderPackActive=" + state.trueIrisShader()
                + ", fallbackOnly=" + state.trueIrisShader()
                + ", heartbeatAgo=" + secondsSinceHeartbeat + "s"
                + ", effects=" + state.supportedEffects().stream().sorted(String::compareToIgnoreCase).toList();
    }

    public String routeHint(Player player, String effectId) {
        ClientCapabilityState state = state(player);
        if (state == null) {
            String problem = player == null ? "" : lastProblems.get(player.getUniqueId());
            return problem == null || problem.isBlank() ? "missing-client" : problem;
        }
        String normalized = effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
        if (state.trueIrisShader()) {
            return "iris-shaderpack-active";
        }
        if (!state.clientModVisuals()) {
            return "client-no-visuals";
        }
        return state.supportedEffects().contains(normalized)
                ? "client-ready+post-process"
                : "unsupported-effect+post-process";
    }
}
