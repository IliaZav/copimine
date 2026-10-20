package me.copimine.endevent;

import java.util.UUID;
import me.copimine.endevent.bridge.EndEventBridgeV4Access;
import me.copimine.endevent.bridge.EndEventBridgeV4Sender;
import me.copimine.protocol.BridgeSigner;
import me.copimine.protocol.ClientModSessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Loaded reflectively so End Event can still run when its soft dependency is absent. */
public final class EndEventBridgeV4Bootstrap {
    private EndEventBridgeV4Bootstrap() {
    }

    public static EndEventBridgeV4Access create(CopiMineEndEvent eventPlugin) {
        Plugin narcotics = Bukkit.getPluginManager().getPlugin("CopiMineNarcotics");
        if (narcotics == null || !narcotics.isEnabled()) {
            return null;
        }
        ClientModSessionRegistry registry = Bukkit.getServicesManager().load(ClientModSessionRegistry.class);
        BridgeSigner signer = Bukkit.getServicesManager().load(BridgeSigner.class);
        if (registry == null || signer == null) {
            return null;
        }
        EndEventBridgeV4Sender sender = new EndEventBridgeV4Sender(registry, signer,
                System::currentTimeMillis,
                (recipient, payload) -> {
                    Player player = Bukkit.getPlayer(recipient);
                    if (player != null && player.isOnline()) {
                        player.sendPluginMessage(eventPlugin, EndEventBridgeV4Sender.CHANNEL, payload);
                    }
                }, eventPlugin::currentEndEventBridgeContext,
                eventPlugin::handleEndEventBridgeAbility,
                eventPlugin::onEndEventBridgeReady);
        return sender;
    }
}
