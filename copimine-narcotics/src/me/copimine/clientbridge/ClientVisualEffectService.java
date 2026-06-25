package me.copimine.clientbridge;

import me.copimine.narcotics.CopiMineNarcotics;
import org.bukkit.entity.Player;

public final class ClientVisualEffectService {
    private final CopiMineNarcotics plugin;
    private final ClientCapabilityService capabilities;

    public ClientVisualEffectService(CopiMineNarcotics plugin, ClientCapabilityService capabilities) {
        this.plugin = plugin;
        this.capabilities = capabilities;
    }

    public boolean canUse(Player player, String effectId) {
        return capabilities.supportsEffect(player, effectId);
    }

    public void sendVisualStart(Player player, String effectId, int seconds, float intensity) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodeVisualStart(effectId, seconds, intensity));
    }

    public void sendVisualStop(Player player, String effectId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodeVisualStop(effectId));
    }

    public void clearVisuals(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendPluginMessage(plugin, ClientBridgePayloads.CHANNEL, ClientBridgePayloads.encodeClearAll());
    }
}
