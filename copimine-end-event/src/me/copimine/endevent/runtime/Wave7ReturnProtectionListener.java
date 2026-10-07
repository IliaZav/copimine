package me.copimine.endevent.runtime;

import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/** Only the short, server-owned staging state grants protection; offense revokes it first. */
public final class Wave7ReturnProtectionListener implements Listener {
    private final Predicate<Player> protectedOwner;
    private final Consumer<Player> cancelReturn;

    public Wave7ReturnProtectionListener(Predicate<Player> protectedOwner, Consumer<Player> cancelReturn) {
        this.protectedOwner = java.util.Objects.requireNonNull(protectedOwner);
        this.cancelReturn = java.util.Objects.requireNonNull(cancelReturn);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncomingDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && protectedOwner.test(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOutgoingDamage(EntityDamageByEntityEvent event) {
        Player player = event.getDamager() instanceof Player direct ? direct
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                ? shooter : null;
        if (player != null && protectedOwner.test(player)) {
            cancelReturn.accept(player);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBowRelease(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && protectedOwner.test(player)) {
            cancelReturn.accept(player);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player && protectedOwner.test(player)) {
            cancelReturn.accept(player);
            event.setCancelled(true);
        }
    }
}
