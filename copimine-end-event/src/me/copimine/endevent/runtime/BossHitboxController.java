package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.BossHitboxDedupePolicy;
import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxTransformPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the server-side Interaction proxy rig for one Rift Guardian.
 *
 * <p>The controller deliberately does not add its proxies to the event's
 * health/AI entity map: an Interaction has no health authority of its own and
 * its teleport updates must not enter the normal combat-entity teleport
 * policy.  Durable tags still identify the parent boss, event id, generation,
 * and model part so recovery and cleanup can find every proxy after a reload.</p>
 */
public final class BossHitboxController {
    public static final String KIND = "BOSS_HITBOX";
    public static final int MAX_PROXY_COUNT = BossHitboxProfile.MAX_PROXY_COUNT;

    private final JavaPlugin plugin;
    private final BossHitboxProfile profile;
    private final BossHitboxDedupePolicy dedupe = new BossHitboxDedupePolicy();
    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private final NamespacedKey eventKey;
    private final NamespacedKey generationKey;
    private final NamespacedKey kindKey;
    private final NamespacedKey parentKey;
    private final NamespacedKey partKey;
    private final NamespacedKey segmentKey;
    private UUID bossUuid;
    private String eventId = "";
    private long generation = Long.MIN_VALUE;
    private boolean debug;

    public BossHitboxController(JavaPlugin plugin, BossHitboxProfile profile) {
        if (plugin == null || profile == null) {
            throw new IllegalArgumentException("plugin and hitbox profile are required");
        }
        this.plugin = plugin;
        this.profile = profile;
        this.eventKey = new NamespacedKey(plugin, "boss_hitbox_event");
        this.generationKey = new NamespacedKey(plugin, "boss_hitbox_generation");
        this.kindKey = new NamespacedKey(plugin, "boss_hitbox_kind");
        this.parentKey = new NamespacedKey(plugin, "boss_hitbox_parent");
        this.partKey = new NamespacedKey(plugin, "boss_hitbox_part");
        this.segmentKey = new NamespacedKey(plugin, "boss_hitbox_segment");
    }

    public BossHitboxProfile profile() {
        return profile;
    }

    /** Starts a fresh rig and removes tracked/leftover proxies for this event. */
    public boolean begin(LivingEntity boss, String eventId, long generation) {
        if (boss == null || !boss.isValid() || boss.isDead()
                || boss.getWorld() == null || eventId == null || eventId.isBlank()) {
            return false;
        }
        cleanup();
        cleanupWorlds(plugin.getServer().getWorlds(), eventId);
        this.bossUuid = boss.getUniqueId();
        this.eventId = eventId;
        this.generation = generation;
        for (BossHitboxProfile.Part part : profile.parts()) {
            if (slots.size() >= MAX_PROXY_COUNT) {
                break;
            }
            Interaction proxy = spawnProxy(boss, part);
            if (proxy == null) {
                cleanup();
                return false;
            }
            slots.put(proxy.getUniqueId(), new Slot(proxy.getUniqueId(), part.id(), part.segmentIndex()));
        }
        update(boss, Map.of());
        return slots.size() == profile.proxyCount();
    }

    /**
     * Reindexes proxies left by a server restart.  A missing or malformed
     * proxy is removed and rebuilt, while a valid matching rig is reused.
     */
    public boolean recover(LivingEntity boss, String eventId, long generation,
                           Collection<World> worlds) {
        if (boss == null || !boss.isValid() || boss.isDead()
                || eventId == null || eventId.isBlank()) {
            return false;
        }
        cleanup();
        this.bossUuid = boss.getUniqueId();
        this.eventId = eventId;
        this.generation = generation;
        Map<String, Interaction> recovered = new LinkedHashMap<>();
        for (World world : worlds == null ? List.<World>of() : worlds) {
            if (world == null) {
                continue;
            }
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!(entity instanceof Interaction interaction)
                        || !matchesTag(interaction, eventId, boss.getUniqueId())) {
                    continue;
                }
                long taggedGeneration = interaction.getPersistentDataContainer()
                        .getOrDefault(generationKey, PersistentDataType.LONG, Long.MIN_VALUE);
                if (taggedGeneration != generation) {
                    interaction.remove();
                    continue;
                }
                String key = partKey(interaction) + ":" + segmentKey(interaction);
                if (recovered.put(key, interaction) != null) {
                    interaction.remove();
                }
            }
        }
        for (BossHitboxProfile.Part part : profile.parts()) {
            String key = part.id().name() + ":" + part.segmentIndex();
            Interaction proxy = recovered.remove(key);
            if (proxy == null || !proxy.isValid()) {
                cleanupEntities(recovered.values());
                return begin(boss, eventId, generation);
            }
            slots.put(proxy.getUniqueId(), new Slot(proxy.getUniqueId(), part.id(), part.segmentIndex()));
        }
        cleanupEntities(recovered.values());
        update(boss, Map.of());
        return slots.size() == profile.proxyCount();
    }

    /** Updates/reuses the existing proxies; no entity is spawned in this path. */
    public void update(LivingEntity boss,
                       Map<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> poses) {
        if (boss == null || bossUuid == null || !bossUuid.equals(boss.getUniqueId())
                || !boss.isValid() || boss.isDead() || slots.isEmpty()) {
            return;
        }
        EnumMap<BossHitboxProfile.PartId, BossHitboxTransformPolicy.PoseOffset> safePoses =
                new EnumMap<>(BossHitboxProfile.PartId.class);
        if (poses != null) {
            safePoses.putAll(poses);
        }
        Iterator<Map.Entry<UUID, Slot>> iterator = slots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Slot> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Interaction proxy) || !proxy.isValid()) {
                iterator.remove();
                continue;
            }
            BossHitboxProfile.Part part = profile.parts().stream()
                    .filter(candidate -> candidate.id() == entry.getValue().partId()
                            && candidate.segmentIndex() == entry.getValue().segmentIndex())
                    .findFirst().orElse(null);
            if (part == null) {
                proxy.remove();
                iterator.remove();
                continue;
            }
            BossHitboxTransformPolicy.Box box = BossHitboxTransformPolicy.transform(
                    part,
                    new BossHitboxTransformPolicy.Anchor(
                            boss.getLocation().getX(), boss.getLocation().getY(),
                            boss.getLocation().getZ(), boss.getLocation().getYaw()),
                    safePoses.getOrDefault(part.id(), BossHitboxTransformPolicy.PoseOffset.NONE));
            Location destination = new Location(boss.getWorld(), box.center().x(),
                    box.center().y(), box.center().z(), boss.getYaw(), 0.0F);
            proxy.teleport(destination);
            proxy.setInteractionWidth((float) Math.max(box.width(), box.depth()));
            proxy.setInteractionHeight((float) box.height());
        }
    }

    public boolean owns(Interaction proxy) {
        return proxy != null && slots.containsKey(proxy.getUniqueId())
                && matchesTag(proxy, eventId, bossUuid);
    }

    public boolean isTagged(Interaction proxy) {
        return proxy != null && KIND.equals(proxy.getPersistentDataContainer()
                .getOrDefault(kindKey, PersistentDataType.STRING, ""));
    }

    public LivingEntity parentBoss(Interaction proxy) {
        if (!owns(proxy)) {
            return null;
        }
        Entity parent = Bukkit.getEntity(bossUuid);
        return parent instanceof LivingEntity living && living.isValid() && !living.isDead()
                ? living : null;
    }

    public BossHitboxProfile.PartId partId(Interaction proxy) {
        Slot slot = proxy == null ? null : slots.get(proxy.getUniqueId());
        return slot == null ? null : slot.partId();
    }

    /**
     * Validates the legacy LivingEntity carrier hit against the composite
     * model boxes.  The client normally selects an Interaction proxy, but a
     * vanilla Enderman renderer can still send a click for the carrier when
     * its native AABB overlaps a proxy.  Do not accept that carrier event
     * merely because the entity UUID is the boss: the source ray must cross a
     * current model box.
     */
    public boolean carrierRayIntersects(LivingEntity boss, Entity source,
                                        Map<BossHitboxProfile.PartId,
                                                BossHitboxTransformPolicy.PoseOffset> poses,
                                        double maxDistance) {
        if (boss == null || source == null || !hasBoss(boss.getUniqueId())
                || !boss.isValid() || boss.isDead() || !source.isValid()) {
            return false;
        }
        Location origin;
        Vector direction;
        if (source instanceof LivingEntity living) {
            origin = living.getEyeLocation();
            direction = origin.getDirection();
        } else if (source instanceof org.bukkit.entity.Projectile projectile) {
            origin = projectile.getLocation();
            direction = projectile.getVelocity();
            if (direction.lengthSquared() < 1.0E-8D) {
                return false;
            }
            direction.normalize();
        } else {
            return false;
        }
        if (origin.getWorld() == null || !origin.getWorld().equals(boss.getWorld())
                || direction.lengthSquared() < 1.0E-8D) {
            return false;
        }
        direction.normalize();
        double distanceLimit = Math.max(0.1D, Math.min(32.0D, maxDistance));
        for (BossHitboxProfile.Part part : profile.parts()) {
            BossHitboxTransformPolicy.PoseOffset pose = poses == null
                    ? BossHitboxTransformPolicy.PoseOffset.NONE
                    : poses.getOrDefault(part.id(), BossHitboxTransformPolicy.PoseOffset.NONE);
            BossHitboxTransformPolicy.Box box = BossHitboxTransformPolicy.transform(part,
                    new BossHitboxTransformPolicy.Anchor(boss.getLocation().getX(),
                            boss.getLocation().getY(), boss.getLocation().getZ(), boss.getLocation().getYaw()),
                    pose);
            if (rayIntersects(box, origin, direction, distanceLimit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a model proxy crossed by a live projectile. Some Paper versions
     * do not expose an Interaction as ProjectileHitEvent#getHitEntity for
     * every projectile shape, so the plugin also performs a bounded server
     * sweep. Checking both directions covers the sampled projectile segment,
     * including a projectile that has already stopped at its far endpoint.
     */
    public Interaction projectileHitProxy(LivingEntity boss,
                                         org.bukkit.entity.Projectile projectile,
                                         Map<BossHitboxProfile.PartId,
                                                 BossHitboxTransformPolicy.PoseOffset> poses,
                                         double maxDistance) {
        if (boss == null || projectile == null || !hasBoss(boss.getUniqueId())
                || !boss.isValid() || boss.isDead() || !projectile.isValid()
                || projectile.getWorld() == null || !projectile.getWorld().equals(boss.getWorld())) {
            return null;
        }
        Vector velocity = projectile.getVelocity();
        if (velocity.lengthSquared() < 1.0E-8D) {
            return null;
        }
        double distance = Math.max(0.25D, Math.min(8.0D, maxDistance));
        Interaction hit = projectileHitProxyAlongRay(boss, projectile.getLocation(),
                velocity.clone().normalize(), poses, distance);
        if (hit != null) {
            return hit;
        }
        return projectileHitProxyAlongRay(boss, projectile.getLocation(),
                velocity.clone().normalize().multiply(-1.0D), poses, distance);
    }

    /** Routes one accepted event through the single generation-scoped dedupe. */
    public boolean acceptHit(Interaction proxy, String attackIdentity,
                             long currentGeneration, long nowMillis) {
        return owns(proxy) && currentGeneration == generation
                && dedupe.accept(attackIdentity, generation, nowMillis);
    }

    public String attackIdentity(EntityDamageByEntityEvent event, long serverTick) {
        if (event == null || event.getDamager() == null) {
            return "environment:" + serverTick;
        }
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile) {
            return "projectile:" + damager.getUniqueId();
        }
        return "melee:" + damager.getUniqueId() + ":" + serverTick;
    }

    public int proxyCount() {
        return slots.size();
    }

    public List<UUID> proxyIds() {
        return List.copyOf(slots.keySet());
    }

    public boolean hasBoss(UUID uuid) {
        return uuid != null && uuid.equals(bossUuid);
    }

    public long generation() {
        return generation;
    }

    public String eventId() {
        return eventId;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean debug() {
        return debug;
    }

    /** Removes tracked proxies and forgets every generation-scoped identity. */
    public void cleanup() {
        cleanupEntities(slots.keySet().stream().map(Bukkit::getEntity).toList());
        slots.clear();
        dedupe.clear();
        bossUuid = null;
        eventId = "";
        generation = Long.MIN_VALUE;
    }

    /** Removes tagged proxies from loaded worlds, including unindexed restart leftovers. */
    public int cleanupWorlds(Collection<World> worlds, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return 0;
        }
        int removed = 0;
        for (World world : worlds == null ? List.<World>of() : worlds) {
            if (world == null) {
                continue;
            }
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof Interaction interaction
                        && matchesEventTag(interaction, eventId)) {
                    interaction.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public Map<UUID, BossHitboxTransformPolicy.Box> debugBoxes(LivingEntity boss,
                                                                Map<BossHitboxProfile.PartId,
                                                                        BossHitboxTransformPolicy.PoseOffset> poses) {
        if (!debug || boss == null || !hasBoss(boss.getUniqueId())) {
            return Map.of();
        }
        Map<UUID, BossHitboxTransformPolicy.Box> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, Slot> entry : slots.entrySet()) {
            BossHitboxProfile.Part part = profile.parts().stream()
                    .filter(candidate -> candidate.id() == entry.getValue().partId()
                            && candidate.segmentIndex() == entry.getValue().segmentIndex())
                    .findFirst().orElse(null);
            if (part == null) {
                continue;
            }
            result.put(entry.getKey(), BossHitboxTransformPolicy.transform(part,
                    new BossHitboxTransformPolicy.Anchor(boss.getLocation().getX(),
                            boss.getLocation().getY(), boss.getLocation().getZ(), boss.getYaw()),
                    poses == null ? BossHitboxTransformPolicy.PoseOffset.NONE
                            : poses.getOrDefault(part.id(), BossHitboxTransformPolicy.PoseOffset.NONE)));
        }
        return Map.copyOf(result);
    }

    private Interaction spawnProxy(LivingEntity boss, BossHitboxProfile.Part part) {
        BossHitboxTransformPolicy.Box initial = BossHitboxTransformPolicy.transform(part,
                new BossHitboxTransformPolicy.Anchor(boss.getLocation().getX(),
                        boss.getLocation().getY(), boss.getLocation().getZ(), boss.getYaw()),
                BossHitboxTransformPolicy.PoseOffset.NONE);
        Location location = new Location(boss.getWorld(), initial.center().x(),
                initial.center().y(), initial.center().z(), boss.getYaw(), 0.0F);
        return boss.getWorld().spawn(location, Interaction.class, proxy -> {
            proxy.setInteractionWidth((float) Math.max(initial.width(), initial.depth()));
            proxy.setInteractionHeight((float) initial.height());
            proxy.setResponsive(true);
            // PDC recovery is part of the runtime contract: a server restart
            // must be able to reindex the exact composite part instead of
            // silently falling back to the vanilla carrier hitbox.
            proxy.setPersistent(true);
            proxy.setGravity(false);
            proxy.setInvulnerable(false);
            proxy.setSilent(true);
            proxy.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, KIND);
            proxy.getPersistentDataContainer().set(eventKey, PersistentDataType.STRING, eventId);
            proxy.getPersistentDataContainer().set(generationKey, PersistentDataType.LONG, generation);
            proxy.getPersistentDataContainer().set(parentKey, PersistentDataType.STRING,
                    boss.getUniqueId().toString());
            proxy.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, part.id().name());
            proxy.getPersistentDataContainer().set(segmentKey, PersistentDataType.INTEGER, part.segmentIndex());
        });
    }

    private boolean matchesTag(Interaction proxy, String expectedEvent, UUID expectedBoss) {
        return matchesEventTag(proxy, expectedEvent)
                && expectedBoss != null && expectedBoss.toString().equals(proxy.getPersistentDataContainer()
                .getOrDefault(parentKey, PersistentDataType.STRING, ""));
    }

    private boolean matchesEventTag(Interaction proxy, String expectedEvent) {
        return isTagged(proxy) && expectedEvent.equals(proxy.getPersistentDataContainer()
                .getOrDefault(eventKey, PersistentDataType.STRING, ""));
    }

    private String partKey(Interaction proxy) {
        return proxy.getPersistentDataContainer().getOrDefault(partKey,
                PersistentDataType.STRING, "");
    }

    private int segmentKey(Interaction proxy) {
        return proxy.getPersistentDataContainer().getOrDefault(segmentKey,
                PersistentDataType.INTEGER, -1);
    }

    private void cleanupEntities(Collection<? extends Entity> entities) {
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private boolean rayIntersects(BossHitboxTransformPolicy.Box box, Location origin,
                                  Vector direction, double maxDistance) {
        double tMin = 0.0D;
        double tMax = maxDistance;
        double[] originValues = {origin.getX(), origin.getY(), origin.getZ()};
        double[] directionValues = {direction.getX(), direction.getY(), direction.getZ()};
        double[] minimums = {box.minX(), box.minY(), box.minZ()};
        double[] maximums = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            double component = directionValues[axis];
            if (Math.abs(component) < 1.0E-8D) {
                if (originValues[axis] < minimums[axis]
                        || originValues[axis] > maximums[axis]) {
                    return false;
                }
                continue;
            }
            double near = (minimums[axis] - originValues[axis]) / component;
            double far = (maximums[axis] - originValues[axis]) / component;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            tMin = Math.max(tMin, near);
            tMax = Math.min(tMax, far);
            if (tMin > tMax) {
                return false;
            }
        }
        return tMax >= 0.0D && tMin <= maxDistance;
    }

    private Interaction projectileHitProxyAlongRay(LivingEntity boss, Location origin,
                                                   Vector direction,
                                                   Map<BossHitboxProfile.PartId,
                                                           BossHitboxTransformPolicy.PoseOffset> poses,
                                                   double maxDistance) {
        if (origin == null || direction == null || direction.lengthSquared() < 1.0E-8D) {
            return null;
        }
        direction.normalize();
        for (Map.Entry<UUID, Slot> entry : slots.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Interaction proxy) || !proxy.isValid()) {
                continue;
            }
            BossHitboxProfile.Part part = profile.parts().stream()
                    .filter(candidate -> candidate.id() == entry.getValue().partId()
                            && candidate.segmentIndex() == entry.getValue().segmentIndex())
                    .findFirst().orElse(null);
            if (part == null) {
                continue;
            }
            BossHitboxTransformPolicy.PoseOffset pose = poses == null
                    ? BossHitboxTransformPolicy.PoseOffset.NONE
                    : poses.getOrDefault(part.id(), BossHitboxTransformPolicy.PoseOffset.NONE);
            BossHitboxTransformPolicy.Box box = BossHitboxTransformPolicy.transform(part,
                    new BossHitboxTransformPolicy.Anchor(boss.getLocation().getX(),
                            boss.getLocation().getY(), boss.getLocation().getZ(), boss.getLocation().getYaw()),
                    pose);
            if (rayIntersects(box, origin, direction, maxDistance)) {
                return proxy;
            }
        }
        return null;
    }

    private record Slot(UUID uuid, BossHitboxProfile.PartId partId, int segmentIndex) {
    }
}
