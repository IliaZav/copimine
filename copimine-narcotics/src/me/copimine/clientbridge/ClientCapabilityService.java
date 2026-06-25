package me.copimine.clientbridge;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientCapabilityService {
    private final Map<UUID, ClientCapabilityState> states = new ConcurrentHashMap<>();
    private volatile long ttlMillis = 30_000L;

    public void setTtlMillis(long ttlMillis) {
        this.ttlMillis = Math.max(5_000L, ttlMillis);
    }

    public void update(Player player, ClientCapabilityState state) {
        states.put(player.getUniqueId(), state);
    }

    public void clear(UUID playerUuid) {
        states.remove(playerUuid);
    }

    public ClientCapabilityState state(Player player) {
        if (player == null) {
            return null;
        }
        ClientCapabilityState state = states.get(player.getUniqueId());
        if (state != null && state.expired(ttlMillis)) {
            states.remove(player.getUniqueId());
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
        ClientCapabilityState state = state(player);
        if (state == null) {
            return "CopiMineClient не обнаружен";
        }
        return "protocol=" + state.protocolVersion()
                + ", clientVersion=" + state.clientVersion()
                + ", clientVisuals=" + state.clientModVisuals()
                + ", clientOverlay=" + state.clientOverlay()
                + ", clientShaderLike=" + state.clientShaderLike()
                + ", irisRequired=" + state.trueIrisShader()
                + ", effects=" + state.supportedEffects().stream().sorted(String::compareToIgnoreCase).toList();
    }

    public String routeHint(Player player, String effectId) {
        ClientCapabilityState state = state(player);
        if (state == null) {
            return "missing-client";
        }
        String normalized = effectId == null ? "CHAOS" : effectId.toUpperCase(Locale.ROOT);
        if (!state.clientModVisuals()) {
            return "client-no-visuals";
        }
        return state.supportedEffects().contains(normalized) ? "client-ready" : "unsupported-effect";
    }
}
