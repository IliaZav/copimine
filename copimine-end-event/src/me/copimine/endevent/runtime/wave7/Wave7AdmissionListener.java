package me.copimine.endevent.runtime.wave7;

import java.util.Objects;
import java.util.UUID;
import me.copimine.endevent.runtime.wave7.Wave7AdmissionRuntimeAdapter.Decision;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Applies {@link Wave7AdmissionRuntimeAdapter} to the live Wave 7 events. It
 * cancels only the offending action and never rewrites damage or AI state.
 */
public final class Wave7AdmissionListener implements Listener {
    private final Wave7AdmissionRuntimeAdapter admission;

    public Wave7AdmissionListener(Wave7AdmissionRuntimeAdapter admission) {
        this.admission = Objects.requireNonNull(admission);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.isCancelled() || event.getTarget() == null) return;
        if (admission.target(id(event.getEntity()), id(event.getTarget())) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;
        Projectile projectile = event.getEntity();
        if (admission.launch(id(projectile), origin(projectile)) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        Entity direct = event.getDamageSource() == null ? null : event.getDamageSource().getDirectEntity();
        if (direct == null) direct = event.getDamager();
        UUID projectile = direct instanceof Projectile ? id(direct) : null;
        if (admission.damage(projectile, origin(direct), id(event.getEntity())) == Decision.DENY) {
            event.setCancelled(true);
        }
    }

    public void clear() { admission.clear(); }

    private static UUID id(Object entity) {
        return entity instanceof Entity value ? value.getUniqueId() : null;
    }

    private static UUID origin(Entity entity) {
        return entity instanceof Projectile projectile ? id(projectile.getShooter()) : id(entity);
    }
}