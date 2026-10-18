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
    private OwnerLethalReceipt ownerLethalReceipt;
    private OwnerActionIntent ownerActionIntent;
    private OwnerActionReceipt ownerActionReceipt;

    private record OwnerLethalReceipt(EchoCombatAdmission.Pair pair, long tick,
                                     EntityDamageEvent damageEvent) { }
    private record OwnerActionIntent(EchoCombatAdmission.Pair pair, long tick, UUID target) { }
    private record OwnerActionReceipt(EchoCombatAdmission.Pair pair, long tick, UUID target,
                                     EntityDamageByEntityEvent damageEvent) { }

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

    /** Observe the final uncancelled hit; only its synchronous death callback can consume it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLethalOwnerDamage(EntityDamageEvent event) {
        Context context = current.get();
        if (context == null || event == null || event.getEntity() == null) return;
        UUID target = id(event.getEntity());
        if (ownerLethalReceipt != null && ownerLethalReceipt.pair().actor().equals(target)) {
            ownerLethalReceipt = null;
        }
        if (event.isCancelled() || !context.active() || !context.pair().actor().equals(target)
                || !(event.getEntity() instanceof org.bukkit.entity.LivingEntity living)
                || event.getFinalDamage() <= 0.0D || living.getHealth() > event.getFinalDamage()) return;

        Entity direct = event.getDamageSource() == null ? null : event.getDamageSource().getDirectEntity();
        Entity causing = event.getDamageSource() == null ? null : event.getDamageSource().getCausingEntity();
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (direct == null) direct = damager;
            else if (!Objects.equals(id(direct), id(damager))) return;
        }
        if (!context.pair().owner().equals(origin(direct))
                || !allowed(context, direct, causing, event.getEntity(), false)) return;
        ownerLethalReceipt = new OwnerLethalReceipt(context.pair(), context.tick(), event);
    }

    /** Arm one exact server-requested actor-to-owner attack before the synchronous Bukkit call. */
    public boolean beginOwnerCombatAction(EchoCombatAdmission.Pair expected, UUID target) {
        Context context = current.get();
        if (expected == null || target == null || context == null || !context.active()
                || !expected.equals(context.pair()) || !expected.owner().equals(target)
                || ownerActionIntent != null || ownerActionReceipt != null) return false;
        ownerActionIntent = new OwnerActionIntent(expected, context.tick(), target);
        return true;
    }

    /** Record only a real, final, uncancelled positive native hit for the armed duel action. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAcceptedOwnerCombatAction(EntityDamageByEntityEvent event) {
        OwnerActionIntent intent = ownerActionIntent;
        if (intent == null || event == null || event.isCancelled() || event.getFinalDamage() <= 0.0D
                || !intent.target().equals(id(event.getEntity()))) return;
        Context context = current.get();
        Entity damager = event.getDamager();
        Entity direct = event.getDamageSource() == null ? null : event.getDamageSource().getDirectEntity();
        Entity causing = event.getDamageSource() == null ? null : event.getDamageSource().getCausingEntity();
        if (context == null || !context.active() || !intent.pair().equals(context.pair())
                || intent.tick() != context.tick() || !intent.pair().actor().equals(id(damager))
                || direct != null && !intent.pair().actor().equals(id(direct))
                || causing != null && !intent.pair().actor().equals(id(causing))
                || !sameWorld(context, damager) || !sameWorld(context, event.getEntity())
                || !EchoCombatAdmission.mayApply(context, intent.pair().actor(), intent.target(), false)) return;
        ownerActionReceipt = new OwnerActionReceipt(intent.pair(), intent.tick(), intent.target(), event);
    }

    /** Consume the exact one-use positive damage outcome; cancelled/zero hits cannot spend supplies. */
    public boolean consumeAcceptedOwnerCombatAction(EchoCombatAdmission.Pair expected, UUID target,
                                                    EntityDamageEvent lastDamageCause) {
        OwnerActionIntent intent = ownerActionIntent;
        OwnerActionReceipt receipt = ownerActionReceipt;
        ownerActionIntent = null;
        ownerActionReceipt = null;
        Context context = current.get();
        return intent != null && receipt != null && expected != null && target != null
                && expected.equals(intent.pair()) && expected.equals(receipt.pair())
                && target.equals(intent.target()) && target.equals(receipt.target())
                && intent.tick() == receipt.tick()
                && (context != null && context.active() && expected.equals(context.pair())
                && context.tick() == receipt.tick() || lastDamageCause == receipt.damageEvent())
                && lastDamageCause == receipt.damageEvent()
                && receipt.damageEvent().getFinalDamage() > 0.0D && !receipt.damageEvent().isCancelled();
    }

    /** Clear an armed action after an exception or rejected native event. */
    public void cancelOwnerCombatAction(EchoCombatAdmission.Pair expected) {
        if (ownerActionIntent != null && (expected == null || expected.equals(ownerActionIntent.pair()))) {
            ownerActionIntent = null;
            ownerActionReceipt = null;
        }
    }

    /** Register a server-fired Echo arrow after its owner was assigned and before velocity is applied. */
    public boolean registerServerLaunchedEchoArrow(Projectile projectile, UUID expectedActor) {
        Context context = current.get();
        if (projectile == null || expectedActor == null || context == null || !context.active()
                || !context.pair().actor().equals(expectedActor)
                || !expectedActor.equals(origin(projectile)) || !projectile.isValid()
                || !sameWorld(context, projectile) || projectiles.containsKey(projectile)
                || projectiles.size() >= MAX_PROJECTILES
                || context.tick() > Long.MAX_VALUE - EchoCombatAdmission.MAX_SOURCE_TICKS) {
            return false;
        }
        projectiles.put(projectile, new Scope(context.pair(), expectedActor, context.tick(),
                context.tick() + EchoCombatAdmission.MAX_SOURCE_TICKS));
        projectile.setPersistent(false);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        return true;
    }

    /** Remove only the exact projectile receipt just registered by this actor. */
    public void discardServerLaunchedEchoArrow(Projectile projectile, UUID expectedActor) {
        if (projectile == null || expectedActor == null) return;
        Context context = current.get();
        Scope scope = projectiles.get(projectile);
        if (scope != null && context != null && context.pair().actor().equals(expectedActor)
                && scope.pair().equals(context.pair()) && scope.origin().equals(expectedActor)) {
            projectiles.remove(projectile);
        }
        if (projectile.isValid()) projectile.remove();
    }

    /** Consume a one-use owner lethal-hit receipt for the exact duel and native death cause. */
    public boolean consumeOwnerLethalDeath(EchoCombatAdmission.Pair expected,
                                            EntityDamageEvent lastDamageCause) {
        OwnerLethalReceipt receipt = ownerLethalReceipt;
        if (receipt == null) return false;
        ownerLethalReceipt = null;
        Context context = current.get();
        return expected != null && context != null && expected.equals(receipt.pair())
                && expected.equals(context.pair()) && context.tick() == receipt.tick()
                && lastDamageCause == receipt.damageEvent();
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
        if (ownerLethalReceipt != null && (context == null || !context.active()
                || !ownerLethalReceipt.pair().equals(context.pair())
                || ownerLethalReceipt.tick() != context.tick())) {
            ownerLethalReceipt = null;
        }
        if (ownerActionIntent != null && (context == null || !context.active()
                || !ownerActionIntent.pair().equals(context.pair())
                || ownerActionIntent.tick() != context.tick())) {
            ownerActionIntent = null;
            ownerActionReceipt = null;
        }
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
    public void clear() {
        ownerLethalReceipt = null;
        // A lethal accepted hit may synchronously unregister its actor before
        // owner.damage(...) returns. Keep its one-use outcome for that caller.
        if (ownerActionReceipt == null) ownerActionIntent = null;
        expire(projectiles, null); expire(clouds, null);
    }
    public int trackedProjectiles() { return projectiles.size(); }
    public int trackedClouds() { return clouds.size(); }
}
