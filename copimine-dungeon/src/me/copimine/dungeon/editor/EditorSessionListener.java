package me.copimine.dungeon.editor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Session-lifecycle guard (spec OV4.3): "dropping, unequipping, changing world,
 * revoke, death and logout immediately remove editor overlays and
 * subscriptions." Tearing the session down removes the overlay and any pending
 * selection so nothing leaks into the ordinary fight.
 */
public final class EditorSessionListener implements Listener {

    private final EditorSessionManager sessions;

    public EditorSessionListener(EditorSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        sessions.close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        sessions.close(player.getUniqueId());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        // Dropping the wand ends the editing overlay; the definition store is untouched.
        sessions.close(event.getPlayer().getUniqueId());
    }
}
