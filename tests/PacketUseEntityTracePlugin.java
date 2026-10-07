package local.endrift.trace;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Local-only packet trace used to separate network decoding from Bukkit damage dispatch. */
public final class PacketUseEntityTracePlugin extends JavaPlugin implements Listener {
    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.MONITOR,
                PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                getLogger().info("USE_ENTITY_TRACE player=" + event.getPlayer().getName()
                        + " packet=" + event.getPacket());
            }
        });
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        getLogger().info("BUKKIT_INTERACT_ENTITY player=" + event.getPlayer().getName()
                + " entity=" + event.getRightClicked().getType() + ":" + event.getRightClicked().getUniqueId());
    }

    @EventHandler
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        getLogger().info("BUKKIT_ANIMATION player=" + event.getPlayer().getName()
                + " type=" + event.getAnimationType());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        getLogger().info("BUKKIT_DAMAGE damager=" + event.getDamager().getType() + ":"
                + event.getDamager().getUniqueId() + " entity=" + event.getEntity().getType() + ":"
                + event.getEntity().getUniqueId() + " cause=" + event.getCause()
                + " damage=" + event.getDamage() + " cancelled=" + event.isCancelled());
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
    }
}
