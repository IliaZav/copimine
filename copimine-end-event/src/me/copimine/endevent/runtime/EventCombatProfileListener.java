package me.copimine.endevent.runtime;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import me.copimine.endevent.runtime.EventCombatProfileService.Context;
import me.copimine.endevent.runtime.EventCombatProfileService.Movement;
import me.copimine.endevent.runtime.EventCombatProfileService.Weapon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Observes existing gameplay transactions; never changes damage, movement or inventory. */
public final class EventCombatProfileListener implements Listener {
    private record Anchor(double x, double z, UUID target, long tick) { }
    private record Release(long tick, Weapon weapon, Object hand) { }
    private final EventCombatProfileService profiles;
    private final Function<Player, Context> contextProvider;
    private final Predicate<Entity> ownedCombatTarget;
    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private final Map<UUID, Long> forcedUntil = new HashMap<>();
    private final Map<UUID, Release> releases = new HashMap<>();
    private final Map<EntityDamageByEntityEvent, Boolean> receipts = new IdentityHashMap<>();
    private final ArrayDeque<EntityDamageByEntityEvent> receiptOrder = new ArrayDeque<>();
    private long generation, nextReceipt;

    public EventCombatProfileListener(EventCombatProfileService profiles,
            Function<Player, Context> contextProvider, Predicate<Entity> ownedCombatTarget) {
        this.profiles = profiles; this.contextProvider = contextProvider;
        this.ownedCombatTarget = ownedCombatTarget;
    }
    private Context context(Player player) {
        Context context = contextProvider.apply(player);
        if (!profiles.accepts(context)) return null;
        useRuntime(context.runtimeGeneration());
        return context;
    }
    private void useRuntime(long nextGeneration) {
        if (generation != nextGeneration) { clear(); generation = nextGeneration; nextReceipt = 0; }
    }
    public void clear() {
        anchors.clear(); forcedUntil.clear(); releases.clear(); receipts.clear(); receiptOrder.clear();
        // Service receipts outlive anchor cleanup within the same attempt.
        // Reusing their identifiers here would silently discard the next real hit.
    }
    public void forget(UUID player) {
        anchors.remove(player); forcedUntil.remove(player); releases.remove(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getFinalDamage() <= 0 || !ownedCombatTarget.test(event.getEntity())) return;
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player p ? p : null;
        if (attacker != null) recordAcceptedHit(event, attacker, context(attacker));
    }
    /** Called only AFTER the existing authoritative health mutation succeeds. */
    public void recordAcceptedHit(EntityDamageByEntityEvent event, Player attacker, Context context) {
        if (!profiles.accepts(context) || event.getFinalDamage() <= 0) return;
        useRuntime(context.runtimeGeneration());
        if (receipts.containsKey(event)) return;
        Weapon weapon = event.getDamager() instanceof Trident ? Weapon.OTHER_RANGED
                : event.getDamager() instanceof AbstractArrow arrow
                ? arrow.isShotFromCrossbow() ? Weapon.CROSSBOW : Weapon.BOW
                : event.getDamager() instanceof Projectile ? Weapon.OTHER_RANGED
                : weapon(attacker.getInventory().getItemInMainHand());
        if (event.getDamager() == attacker && (weapon == Weapon.BOW || weapon == Weapon.CROSSBOW))
            weapon = Weapon.OTHER_MELEE;
        boolean descendingMelee = event.getDamager() == attacker && !attacker.isOnGround()
                && attacker.getFallDistance() > 0 && attacker.getVelocity().getY() < 0;
        if (!profiles.acceptedHit(context, "hit:" + generation + ":" + (++nextReceipt),
                weapon, event.isCritical(), descendingMelee)) return;
        receipts.put(event, Boolean.TRUE); receiptOrder.addLast(event);
        if (receiptOrder.size() > 256) receipts.remove(receiptOrder.removeFirst());
    }
    private static Weapon weapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return Weapon.NONE;
        String type = item.getType().name();
        if (type.endsWith("_SWORD")) return Weapon.SWORD;
        if (type.endsWith("_AXE")) return Weapon.AXE;
        if (item.getType() == Material.BOW) return Weapon.BOW;
        if (item.getType() == Material.CROSSBOW) return Weapon.CROSSBOW;
        return Weapon.OTHER_MELEE;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) return;
        Context context = context(player);
        Weapon weapon = weapon(event.getBow());
        if (context == null || weapon != Weapon.BOW && weapon != Weapon.CROSSBOW) return;
        Release release = new Release(context.tick(), weapon, event.getHand());
        if (release.equals(releases.put(player.getUniqueId(), release))) return;
        profiles.bowRelease(context, weapon, Math.max(0, player.getActiveItemUsedTime()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrossbowLoad(EntityLoadCrossbowEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player player)
            profiles.crossbowLoad(context(player), Math.max(0, player.getActiveItemUsedTime()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (!event.isCancelled() && event.getAnimationType() == PlayerAnimationType.ARM_SWING)
            profiles.swing(context(event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwitch(PlayerItemHeldEvent event) {
        if (!event.isCancelled()) profiles.switchItem(context(event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;
        ItemStack item = event.getItem();
        boolean healing = item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE;
        if (item.getType() == Material.POTION && item.getItemMeta() instanceof PotionMeta potion) {
            PotionType type = potion.getBasePotionType();
            healing |= type == PotionType.HEALING || type == PotionType.STRONG_HEALING
                    || type == PotionType.REGENERATION || type == PotionType.LONG_REGENERATION
                    || type == PotionType.STRONG_REGENERATION;
        }
        Player player = event.getPlayer();
        if (healing) profiles.healUse(context(player), player.getHealth() / player.getMaxHealth());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled()) suppressForcedMovement(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (!event.isCancelled()) suppressForcedMovement(event.getPlayer());
    }
    private void suppressForcedMovement(Player player) {
        Context context = context(player);
        if (context == null) return;
        anchors.remove(player.getUniqueId());
        forcedUntil.put(player.getUniqueId(), context.tick() + 20L);
    }
    /** Main chooses an existing tracked target once per five ticks; no world scan or path request. */
    public void sample(Player player, LivingEntity target) {
        Context context = context(player);
        UUID owner = player.getUniqueId();
        if (context == null || target == null || context.forcedMotion()
                || forcedUntil.getOrDefault(owner, 0L) > context.tick()
                || !player.getWorld().equals(target.getWorld())) {
            anchors.remove(owner); return;
        }
        Location position = player.getLocation(), enemy = target.getLocation();
        double dx = enemy.getX() - position.getX(), dz = enemy.getZ() - position.getZ();
        double dy = enemy.getY() - position.getY();
        Anchor previous = anchors.get(owner);
        double strafe = 0, approach = 0;
        if (previous != null && previous.target().equals(target.getUniqueId())
                && context.tick() > previous.tick() && context.tick() - previous.tick() <= 10L) {
            double stepX = position.getX() - previous.x(), stepZ = position.getZ() - previous.z();
            double yaw = Math.toRadians(position.getYaw());
            strafe = stepX * Math.cos(yaw) + stepZ * Math.sin(yaw);
            double distance = Math.hypot(dx, dz);
            if (distance > .01) approach = (stepX * dx + stepZ * dz) / distance;
        }
        if (profiles.sample(context, new Movement(Math.sqrt(dx * dx + dy * dy + dz * dz),
                player.isSprinting(), strafe, player.isBlocking(), approach,
                player.getHealth() / player.getMaxHealth()))) {
            anchors.put(owner, new Anchor(position.getX(), position.getZ(), target.getUniqueId(), context.tick()));
        }
    }
}
