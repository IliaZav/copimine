package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.BossHitboxDedupePolicy;
import me.copimine.endevent.domain.BossHitboxPose;
import me.copimine.endevent.domain.BossHitboxProfile;
import me.copimine.endevent.domain.BossHitboxProxyReconciliationPolicy;
import me.copimine.endevent.domain.BossHitboxTransformPolicy;
import me.copimine.endevent.domain.BossOrientedHitboxPolicy;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<UUID, BossOrientedHitboxPolicy.Vec3> previousProjectilePositions =
            new LinkedHashMap<>();
    private final Set<UUID> consumedProjectileIds = new HashSet<>();
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
        updateProxies(boss, Map.of());
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
        updateProxies(boss, Map.of());
        return slots.size() == profile.proxyCount();
    }

    /** Updates/reuses the existing proxies; no entity is spawned in this path. */
    public void update(LivingEntity boss,
                       Map<BossHitboxProfile.PartKey, BossHitboxPose> poses) {
        if (boss == null || bossUuid == null || !bossUuid.equals(boss.getUniqueId())
                || !boss.isValid() || boss.isDead() || slots.isEmpty()) {
            return;
        }
        if (!ensureHealthy(boss)) {
            return;
        }
        updateProxies(boss, poses);
    }

    private void updateProxies(LivingEntity boss,
                                Map<BossHitboxProfile.PartKey, BossHitboxPose> poses) {
        Map<BossHitboxProfile.PartKey, BossHitboxPose> safePoses =
                poses == null ? Map.of() : Map.copyOf(poses);
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
            BossHitboxPose pose = safePoses.getOrDefault(partKey(part), BossHitboxPose.NONE);
            BossHitboxTransformPolicy.Box box = BossHitboxTransformPolicy.transformWithPose(
                    part,
                    new BossHitboxTransformPolicy.Anchor(
                            boss.getLocation().getX(), boss.getLocation().getY(),
                            boss.getLocation().getZ(), boss.getLocation().getYaw()),
                    pose);
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
     * Validates a hit reported against one square Interaction proxy against
     * the exact model-space envelope for that segment.  Paper exposes only a
     * square horizontal Interaction size, so the proxy itself is deliberately
     * treated as a broad selector; damage is accepted only when the attacker's
     * ray also crosses the selected rotated model box.
     */
    public boolean proxyRayIntersects(LivingEntity boss, Interaction proxy,
                                      Entity source,
                                      Map<BossHitboxProfile.PartKey,
                                              BossHitboxPose> poses,
                                      double maxDistance) {
        if (boss == null || source == null
                || !hasBoss(boss.getUniqueId()) || !boss.isValid() || boss.isDead()) {
            return false;
        }
        if (!ensureHealthy(boss) || !owns(proxy)) {
            return false;
        }
        BossHitboxProfile.Part part = partForProxy(proxy);
        SourceRay ray = sourceRay(source);
        if (part == null || ray == null || ray.origin().getWorld() == null
                || !ray.origin().getWorld().equals(boss.getWorld())) {
            return false;
        }
        return rayIntersects(transformedObb(boss, part, poses), ray.origin(),
                ray.direction(), boundedDistance(maxDistance));
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
                                        Map<BossHitboxProfile.PartKey,
                                                BossHitboxPose> poses,
                                        double maxDistance) {
        if (boss == null || source == null || !hasBoss(boss.getUniqueId())
                || !boss.isValid() || boss.isDead()) {
            return false;
        }
        if (!ensureHealthy(boss)) {
            return false;
        }
        SourceRay ray = sourceRay(source);
        if (ray == null || ray.origin().getWorld() == null
                || !ray.origin().getWorld().equals(boss.getWorld())) {
            return false;
        }
        double distanceLimit = boundedDistance(maxDistance);
        for (BossHitboxProfile.Part part : profile.parts()) {
            if (rayIntersects(transformedObb(boss, part, poses), ray.origin(),
                    ray.direction(), distanceLimit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a model proxy crossed by a projectile's finite previous-to-current
     * sample.  The first observation is a zero-length sample at the current
     * point; every later observation represents exactly one server tick.
     */
    public Interaction projectileHitProxy(LivingEntity boss,
                                         org.bukkit.entity.Projectile projectile,
                                         Map<BossHitboxProfile.PartKey,
                                                 BossHitboxPose> poses) {
        if (boss == null || projectile == null || !hasBoss(boss.getUniqueId())
                || !boss.isValid() || boss.isDead() || !projectile.isValid()
                || projectile.getWorld() == null || !projectile.getWorld().equals(boss.getWorld())) {
            return null;
        }
        BossOrientedHitboxPolicy.Vec3 current = point(projectile.getLocation());
        if (current == null) {
            return null;
        }
        BossOrientedHitboxPolicy.Vec3 previous = previousProjectilePositions.put(
                projectile.getUniqueId(), current);
        if (previous == null) {
            previous = current;
        }
        return projectileHitProxySegment(boss, previous, current, poses);
    }

    /**
     * Validates the proxy reported by ProjectileHitEvent against the same
     * finite sample tracked for the projectile.  Updating the sample here
     * keeps the event path and the scheduled fallback on one trajectory.
     */
    public boolean proxySegmentIntersects(LivingEntity boss, Interaction proxy,
                                          org.bukkit.entity.Projectile projectile,
                                          Map<BossHitboxProfile.PartKey,
                                                  BossHitboxPose> poses) {
        if (boss == null || projectile == null || !owns(proxy)
                || !hasBoss(boss.getUniqueId()) || !boss.isValid() || boss.isDead()
                || !projectile.isValid() || projectile.getLocation() == null
                || projectile.getWorld() == null || !projectile.getWorld().equals(boss.getWorld())) {
            return false;
        }
        BossOrientedHitboxPolicy.Vec3 current = point(projectile.getLocation());
        if (current == null) {
            return false;
        }
        BossOrientedHitboxPolicy.Vec3 previous = previousProjectilePositions.put(
                projectile.getUniqueId(), current);
        if (previous == null) {
            previous = current;
        }
        if (!ensureHealthy(boss) || !owns(proxy)) {
            return false;
        }
        BossHitboxProfile.Part part = partForProxy(proxy);
        return part != null && BossOrientedHitboxPolicy.segmentIntersects(
                transformedObb(boss, part, poses), previous, current);
    }

    /** Finds the first canonical proxy crossed by one finite projectile sample. */
    private Interaction projectileHitProxySegment(
            LivingEntity boss,
            BossOrientedHitboxPolicy.Vec3 previous,
            BossOrientedHitboxPolicy.Vec3 current,
            Map<BossHitboxProfile.PartKey, BossHitboxPose> poses) {
        if (!ensureHealthy(boss)) {
            return null;
        }
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
            if (BossOrientedHitboxPolicy.segmentIntersects(
                    transformedObb(boss, part, poses), previous, current)) {
                return proxy;
            }
        }
        return null;
    }

    /** Routes one accepted event through the single generation-scoped dedupe. */
    public boolean acceptHit(Interaction proxy, String attackIdentity,
                             long currentGeneration, long nowMillis) {
        if (!owns(proxy) || currentGeneration != generation) {
            return false;
        }
        LivingEntity boss = parentBoss(proxy);
        if (boss == null || !ensureHealthy(boss)) {
            return false;
        }
        return owns(proxy) && currentGeneration == generation
                && dedupe.accept(attackIdentity, generation, nowMillis);
    }

    /**
     * Accepts one projectile UUID for the whole encounter generation.  The
     * short melee/projectile dedupe remains useful for duplicate Bukkit
     * callbacks, while this durable UUID set prevents a still-live projectile
     * from being accepted again after that TTL expires.
     */
    public boolean acceptProjectileHit(Interaction proxy,
                                       org.bukkit.entity.Projectile projectile,
                                       long currentGeneration, long nowMillis) {
        if (projectile == null) {
            return false;
        }
        UUID projectileId = projectile.getUniqueId();
        if (consumedProjectileIds.contains(projectileId)) {
            return false;
        }
        if (!acceptHit(proxy, "projectile:" + projectileId,
                currentGeneration, nowMillis)) {
            return false;
        }
        consumedProjectileIds.add(projectileId);
        return true;
    }

    /** Stops retaining a projectile's trajectory after it has been removed. */
    public void forgetProjectile(UUID projectileId) {
        if (projectileId != null) {
            previousProjectilePositions.remove(projectileId);
        }
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

    /**
     * Reconciles the live tagged selector set before any damage route runs.
     * A repair rebuilds the complete generation-owned rig, so a partially
     * missing limb can never leave the surviving proxies as a false authority.
     */
    private boolean ensureHealthy(LivingEntity boss) {
        if (boss == null || bossUuid == null || !bossUuid.equals(boss.getUniqueId())
                || eventId.isBlank() || slots.isEmpty()) {
            return false;
        }
        BossHitboxProxyReconciliationPolicy.Result reconciliation =
                liveReconciliation();
        if (!reconciliation.requiresRebuild()) {
            return true;
        }
        String repairEventId = eventId;
        long repairGeneration = generation;
        UUID repairBossId = boss.getUniqueId();
        boolean rebuilt = begin(boss, repairEventId, repairGeneration);
        if (!rebuilt) {
            plugin.getLogger().warning("BOSS_HITBOX_PROXY_REPAIR_FAILED event=" + repairEventId
                    + " boss=" + repairBossId + " generation=" + repairGeneration
                    + " missing=" + reconciliation.missing()
                    + " stale=" + reconciliation.stale()
                    + " duplicates=" + reconciliation.duplicates()
                    + " malformed=" + reconciliation.malformed());
            return false;
        }
        for (BossHitboxProxyReconciliationPolicy.Key key
                : BossHitboxProxyReconciliationPolicy.expectedKeys(profile)) {
            plugin.getLogger().info("BOSS_HITBOX_PROXY_RECREATED event=" + repairEventId
                    + " boss=" + repairBossId + " part=" + key.partId()
                    + " segment=" + key.segmentIndex() + " generation=" + repairGeneration);
        }
        return true;
    }

    private BossHitboxProxyReconciliationPolicy.Result liveReconciliation() {
        List<BossHitboxProxyReconciliationPolicy.Key> live = new ArrayList<>();
        int malformed = slots.size() == profile.proxyCount() ? 0 : 1;
        for (Slot slot : slots.values()) {
            Entity entity = Bukkit.getEntity(slot.uuid());
            if (!(entity instanceof Interaction proxy) || !proxy.isValid()
                    || !slotMetadataMatches(proxy, slot)) {
                malformed++;
                continue;
            }
            live.add(new BossHitboxProxyReconciliationPolicy.Key(
                    slot.partId(), slot.segmentIndex()));
        }
        Set<UUID> indexed = Set.copyOf(slots.keySet());
        for (World world : plugin.getServer().getWorlds()) {
            if (world == null) {
                continue;
            }
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!(entity instanceof Interaction proxy) || !isTagged(proxy)
                        || !matchesTag(proxy, eventId, bossUuid)) {
                    continue;
                }
                long taggedGeneration = proxy.getPersistentDataContainer().getOrDefault(
                        generationKey, PersistentDataType.LONG, Long.MIN_VALUE);
                if (taggedGeneration != generation) {
                    malformed++;
                    continue;
                }
                if (indexed.contains(proxy.getUniqueId())) {
                    continue;
                }
                BossHitboxProfile.PartId taggedPart = taggedPart(proxy);
                int taggedSegment = segmentKey(proxy);
                if (taggedPart == null || taggedSegment < 0) {
                    malformed++;
                    continue;
                }
                live.add(new BossHitboxProxyReconciliationPolicy.Key(
                        taggedPart, taggedSegment));
            }
        }
        BossHitboxProxyReconciliationPolicy.Result result =
                BossHitboxProxyReconciliationPolicy.reconcile(
                BossHitboxProxyReconciliationPolicy.expectedKeys(profile), live);
        return result.withMalformed(malformed);
    }

    private boolean slotMetadataMatches(Interaction proxy, Slot slot) {
        if (proxy == null || slot == null || !matchesTag(proxy, eventId, bossUuid)) {
            return false;
        }
        long taggedGeneration = proxy.getPersistentDataContainer().getOrDefault(
                generationKey, PersistentDataType.LONG, Long.MIN_VALUE);
        return taggedGeneration == generation
                && slot.partId().name().equals(partKey(proxy))
                && slot.segmentIndex() == segmentKey(proxy);
    }

    private BossHitboxProfile.PartId taggedPart(Interaction proxy) {
        try {
            return BossHitboxProfile.PartId.valueOf(partKey(proxy));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    /** Removes tracked proxies and forgets every generation-scoped identity. */
    public void cleanup() {
        cleanupEntities(slots.keySet().stream().map(Bukkit::getEntity).toList());
        slots.clear();
        dedupe.clear();
        previousProjectilePositions.clear();
        consumedProjectileIds.clear();
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
                                                                Map<BossHitboxProfile.PartKey,
                                                                        BossHitboxPose> poses) {
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
            result.put(entry.getKey(), BossHitboxTransformPolicy.transformWithPose(part,
                    new BossHitboxTransformPolicy.Anchor(boss.getLocation().getX(),
                            boss.getLocation().getY(), boss.getLocation().getZ(), boss.getYaw()),
                    poses == null ? BossHitboxPose.NONE
                            : poses.getOrDefault(partKey(part), BossHitboxPose.NONE)));
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

    private BossHitboxProfile.Part partForProxy(Interaction proxy) {
        Slot slot = proxy == null ? null : slots.get(proxy.getUniqueId());
        if (slot == null) {
            return null;
        }
        return profile.parts().stream()
                .filter(candidate -> candidate.id() == slot.partId()
                        && candidate.segmentIndex() == slot.segmentIndex())
                .findFirst()
                .orElse(null);
    }

    private BossOrientedHitboxPolicy.OrientedBox transformedObb(
            LivingEntity boss,
            BossHitboxProfile.Part part,
            Map<BossHitboxProfile.PartKey,
                    BossHitboxPose> poses) {
        BossHitboxPose pose = poses == null
                ? BossHitboxPose.NONE
                : poses.getOrDefault(partKey(part), BossHitboxPose.NONE);
        Location location = boss.getLocation();
        return BossOrientedHitboxPolicy.fromPartWithPose(part,
                new BossHitboxTransformPolicy.Anchor(location.getX(), location.getY(),
                        location.getZ(), location.getYaw()), pose);
    }

    private SourceRay sourceRay(Entity source) {
        if (source == null || !source.isValid()) {
            return null;
        }
        Location origin;
        Vector direction;
        if (source instanceof LivingEntity living) {
            origin = living.getEyeLocation();
            direction = origin.getDirection();
        } else if (source instanceof org.bukkit.entity.Projectile projectile) {
            origin = projectile.getLocation();
            direction = projectile.getVelocity();
        } else {
            return null;
        }
        if (origin == null || origin.getWorld() == null || direction == null
                || direction.lengthSquared() < 1.0E-8D) {
            return null;
        }
        return new SourceRay(origin, direction.clone().normalize());
    }

    private BossOrientedHitboxPolicy.Vec3 point(Location location) {
        if (location == null || location.getWorld() == null
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())) {
            return null;
        }
        return new BossOrientedHitboxPolicy.Vec3(
                location.getX(), location.getY(), location.getZ());
    }

    private double boundedDistance(double maxDistance) {
        return Math.max(0.1D, Math.min(32.0D, maxDistance));
    }

    private boolean rayIntersects(BossOrientedHitboxPolicy.OrientedBox box, Location origin,
                                  Vector direction, double maxDistance) {
        if (box == null || origin == null || direction == null
                || origin.getWorld() == null || direction.lengthSquared() < 1.0E-8D) {
            return false;
        }
        return BossOrientedHitboxPolicy.nearestHitDistance(
                new BossOrientedHitboxPolicy.Ray(
                        new BossOrientedHitboxPolicy.Vec3(origin.getX(), origin.getY(), origin.getZ()),
                        new BossOrientedHitboxPolicy.Vec3(direction.getX(), direction.getY(), direction.getZ())),
                box, maxDistance).isPresent();
    }

    private BossHitboxProfile.PartKey partKey(BossHitboxProfile.Part part) {
        return new BossHitboxProfile.PartKey(part.id(), part.segmentIndex());
    }

    private record SourceRay(Location origin, Vector direction) {
    }

    private record Slot(UUID uuid, BossHitboxProfile.PartId partId, int segmentIndex) {
    }
}
