package me.copimine.endevent.runtime.wave7;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import me.copimine.endevent.domain.wave7.EchoCombatAdmission;
import me.copimine.endevent.domain.wave7.EchoCombatAdmission.Context;
import me.copimine.endevent.domain.wave7.EchoCombatAdmission.Scope;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/** Bounded source receipts, driven and cleared by the existing encounter loop. */
public final class EchoCombatAdmissionListener implements Listener {
    private static final int MAX_PROJECTILES = 64, MAX_CLOUDS = 16;
    private final Supplier<Context> current;
    private final Map<Projectile, Scope> projectiles = new IdentityHashMap<>();
    private final Map<AreaEffectCloud, Scope> clouds = new IdentityHashMap<>();

    public EchoCombatAdmissionListener(Supplier<Context> current) {
        this.current = Objects.requireNonNull(current);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        Context context = current.get();
        Entity direct = event.getDamageSource().getDirectEntity();
        Entity causing = event.getDamageSource().getCausingEntity();
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (direct == null) direct = damager;
            else if (!Objects.equals(id(direct), id(damager))
                    && (member(context, origin(direct)) || member(context, origin(damager))
                    || member(context, id(event.getEntity())))) {
                event.setCancelled(true); return;
            }
        }
        // Minecraft retains fall/environment physics; missing entity provenance
        // cannot be interpreted as a foreign attacker or a proved owned effect.
        if (direct == null && causing == null) return;
        boolean potionEffect = direct instanceof ThrownPotion || direct instanceof AreaEffectCloud;
        if (!allowed(context, direct, causing, event.getEntity(), potionEffect)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;
        Projectile projectile = event.getEntity();
        Context context = current.get();
        UUID origin = origin(projectile);
        Scope previous = projectiles.get(projectile);
        if (previous != null) {
            if (!previous.current(context, origin)) event.setCancelled(true);
            return; // A duplicate callback cannot renew or rebind this launch.
        }
        if (!member(context, origin)) return;
        if (!context.active() || !sameWorld(context, projectile)
                || context.tick() > Long.MAX_VALUE - EchoCombatAdmission.MAX_SOURCE_TICKS
                || projectiles.size() >= MAX_PROJECTILES) {
            event.setCancelled(true); return;
        }
        projectiles.put(projectile, new Scope(context.pair(), origin, context.tick(),
                context.tick() + EchoCombatAdmission.MAX_SOURCE_TICKS));
        projectile.setPersistent(false);
        if (context.pair().actor().equals(origin) && projectile instanceof AbstractArrow arrow)
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (event.isCancelled() || event.getHitEntity() == null) return;
        if (!allowed(current.get(), event.getEntity(), null, event.getHitEntity(), false))
            event.setCancelled(true); // Let an admitted shot pass an unrelated body.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (event.isCancelled()) return;
        Context context = current.get();
        for (var target : event.getAffectedEntities()) {
            if (!allowed(context, event.getEntity(), null, target, true)) event.setIntensity(target, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLingering(LingeringPotionSplashEvent event) {
        if (event.isCancelled()) return;
        Context context = current.get();
        Projectile projectile = event.getEntity();
        Scope scope = projectiles.get(projectile);
        if (scope == null && !member(context, origin(projectile))) return;
        AreaEffectCloud cloud = event.getAreaEffectCloud();
        if (scope == null || !scope.current(context, origin(projectile))
                || !scope.origin().equals(origin(cloud)) || !sameWorld(context, cloud)
                || !sameWorld(context, projectile) || !clouds.containsKey(cloud) && clouds.size() >= MAX_CLOUDS) {
            event.setCancelled(true); cloud.remove(); return;
        }
        clouds.putIfAbsent(cloud, scope); // Inherit the original launch lifetime.
        cloud.setPersistent(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        if (event.isCancelled()) return;
        Context context = current.get();
        event.getAffectedEntities().removeIf(target -> !allowed(context, event.getEntity(), null, target, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!event.isCancelled() && !allowed(current.get(), event.getCombuster(), null, event.getEntity(), false))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!event.isCancelled() && event.getTarget() != null
                && !allowed(current.get(), event.getEntity(), null, event.getTarget(), false))
            event.setCancelled(true);
    }

    private boolean allowed(Context context, Entity direct, Entity causing, Entity target, boolean selfEffect) {
        UUID source = origin(direct), victim = id(target);
        Scope scope = direct instanceof Projectile projectile ? projectiles.get(projectile)
                : direct instanceof AreaEffectCloud cloud ? clouds.get(cloud) : null;
        if (scope == null && !member(context, source) && !member(context, id(causing))
                && !member(context, victim)) return true;
        if (!sameWorld(context, direct) || !sameWorld(context, target)
                || causing != null && !Objects.equals(id(causing), source)) return false;
        if (direct instanceof Projectile || direct instanceof AreaEffectCloud) {
            if (scope == null || !scope.current(context, source)) return false;
        }
        // Pets, summoned helpers and contradictory DamageSource chains never
        // become the owner just because a causing UUID points at that player.
        return EchoCombatAdmission.mayApply(context, source, victim, selfEffect);
    }

    private static boolean member(Context context, UUID entity) {
        return context != null && context.pair().contains(entity);
    }
    private static boolean sameWorld(Context context, Entity entity) {
        return context != null && entity != null && context.pair().world().equals(entity.getWorld().getUID());
    }
    private static UUID id(Object entity) { return entity instanceof Entity value ? value.getUniqueId() : null; }
    private static UUID origin(Entity entity) {
        return entity instanceof Projectile projectile ? id(projectile.getShooter())
                : entity instanceof AreaEffectCloud cloud ? id(cloud.getSource()) : id(entity);
    }

    /** Five-tick cleanup visits only these bounded handles, never world entities. */
    public void tick() {
        Context context = current.get();
        expire(projectiles, context); expire(clouds, context);
    }
    private static <T extends Entity> void expire(Map<T, Scope> tracked, Context context) {
        for (var iterator = tracked.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getValue().current(context, origin(entry.getKey()))
                    && sameWorld(context, entry.getKey()) && entry.getKey().isValid()) continue;
            if (entry.getKey().isValid()) entry.getKey().remove();
            iterator.remove(); // Retain the handle if native removal throws.
        }
    }
    public void clear() { expire(projectiles, null); expire(clouds, null); }
    public int trackedProjectiles() { return projectiles.size(); }
    public int trackedClouds() { return clouds.size(); }
}
