package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.command.CommandActor;
import me.copimine.dungeon.command.CommandResponse;
import me.copimine.dungeon.domain.DungeonEncounterPlanPolicy;
import me.copimine.dungeon.domain.DungeonRewardLedger;
import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.DungeonRoomLifecycle;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.ParticipationMode;
import me.copimine.dungeon.domain.PhysicalGatePolicy;
import me.copimine.dungeon.domain.PhysicalGateTransaction;
import me.copimine.dungeon.domain.RoomState;
import me.copimine.dungeon.domain.SpawnAnchor;
import me.copimine.dungeon.domain.SecretTrigger;
import me.copimine.dungeon.domain.UnlockMode;
import me.copimine.dungeon.store.DungeonDefinitionStore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonRuntime implements Listener {
    private static final int INTERVAL = 5;
    private static final int EMPTY_ROOM_GRACE = 60;
    private static final int MAX_FIGHT_TICKS = 20 * 60 * 20;

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final Set<String> playtestRooms;
    private final Path data;
    private final NamespacedKey ownerKey;
    private final DungeonRewardMailbox mailbox;
    private final Map<String, RoomRuntime> rooms = new LinkedHashMap<>();
    private final Map<UUID, Owner> owners = new HashMap<>();
    private BukkitTask task;
    private long generation;
    private long tick;

    private record Owner(RoomRuntime room, long generation, boolean enemy, long expiresTick) {}
    private record GateRuntime(GateDefinition gate, PhysicalGateTransaction transaction,
                               BukkitGateWorld world) {}

    public DungeonRuntime(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
        playtestRooms = Set.copyOf(plugin.getConfig().getStringList("dungeons.runtime.playtest_rooms"));
        data = plugin.getDataFolder().toPath().resolve("runtime");
        ownerKey = new NamespacedKey(plugin, "attempt_generation");
        mailbox = new DungeonRewardMailbox(plugin, data.resolve("rewards"));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean start(DungeonDefinitionStore store) {
        if (!enabled || task != null || !store.loadProblems().isEmpty()) {
            return false;
        }
        try {
            Path counter = data.resolve("generation");
            if (!Files.notExists(counter)) {
                generation = Long.parseLong(Files.readString(counter).trim());
                if (generation < 0) {
                    throw new IOException("Negative dungeon generation");
                }
            }
            long maxRewardGeneration = mailbox.load();
            if (generation < maxRewardGeneration) {
                throw new IOException("Dungeon generation is older than its reward checkpoints");
            }
            for (DungeonRoomDefinition room : store.catalog().rooms().values()) {
                if (room.enabled()) {
                    rooms.put(room.id(), buildRoom(store, room));
                }
            }
            var masks = rooms.values().stream().flatMap(room -> room.gateDefinitions.stream()).toList();
            if (!PhysicalGatePolicy.overlapConflicts(masks).isEmpty()) {
                throw new IOException("Published gate and secret masks overlap");
            }
        } catch (IOException | RuntimeException error) {
            plugin.getLogger().severe("Dungeon runtime could not load checkpoints: " + error.getMessage());
            rooms.clear();
            return false;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(mailbox, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::onTick, 1L, INTERVAL);
        return true;
    }

    private RoomRuntime buildRoom(DungeonDefinitionStore store, DungeonRoomDefinition room) throws IOException {
        if (room.bounds() == null || room.trigger() == null || !room.bounds().contains(room.trigger())) {
            throw new IOException("Missing or invalid room/trigger bounds for " + room.id());
        }
        List<SpawnAnchor> anchors = new ArrayList<>();
        for (String id : room.spawnAnchorIds()) {
            if (id.equals(room.reward().anchorId())) continue;
            SpawnAnchor anchor = store.catalog().spawn(id);
            if (anchor != null) {
                anchors.add(anchor);
            }
        }
        List<GateDefinition> gates = new ArrayList<>();
        for (String id : room.gateIds()) {
            GateDefinition gate = store.catalog().gate(id);
            if (gate == null || !gate.roomId().equals(room.id()) || !gate.world().equals(room.world())) {
                throw new IOException("Invalid gate owner/world for " + id);
            }
            PhysicalGatePolicy.doorMask(gate);
            gates.add(gate);
        }
        Map<String, SecretTrigger> secrets = new LinkedHashMap<>();
        for (String id : room.secretIds()) {
            var secret = store.catalog().secret(id);
            if (secret == null || !secret.roomId().equals(room.id()) || !secret.world().equals(room.world())
                    || !secret.style().isSecretFamily()) {
                throw new IOException("Invalid secret passage owner, world or style for " + id);
            }
            GateDefinition gate = secret.physicalGate();
            PhysicalGatePolicy.doorMask(gate);
            gates.add(gate);
            secrets.put(gate.id(), secret.trigger());
        }
        var rewardAnchor = store.catalog().spawn(room.reward().anchorId());
        if (room.reward().mode() != me.copimine.dungeon.domain.RewardMode.PERSONAL
                && (rewardAnchor == null || !room.id().equals(rewardAnchor.roomId())
                    || !room.world().equals(rewardAnchor.world()) || !room.bounds().contains(rewardAnchor.bounds()))) {
            throw new IOException("Invalid physical reward anchor for " + room.id());
        }
        return new RoomRuntime(room, List.copyOf(anchors), List.copyOf(gates), mailbox.pending(room.id()), rewardAnchor, Map.copyOf(secrets));
    }

    public Runnable prepareRevision(DungeonDefinitionStore published, String roomId) throws IOException {
        if (!enabled) {
            return () -> {};
        }
        if (task == null) {
            throw new IOException("Runtime checkpoints are unavailable");
        }
        RoomRuntime current = rooms.get(roomId);
        if (current != null && (current.life != null || !current.pendingRewards.isEmpty())) {
            throw new IOException("Дождитесь завершения активной попытки комнаты.");
        }
        DungeonRoomDefinition definition = published.catalog().room(roomId);
        RoomRuntime replacement = definition == null || !definition.enabled()
                ? null : buildRoom(published, definition);
        if (current != null && replacement != null) {
            for (GateDefinition previous : current.gateDefinitions) {
                boolean retained = replacement != null && replacement.gateDefinitions.stream().anyMatch(gate ->
                        gate.id().equals(previous.id()) && gate.world().equals(previous.world())
                                && gate.bounds().equals(previous.bounds()));
                if (!retained && !Files.notExists(journalPath(previous))) {
                    throw new IOException("У ворот " + previous.id() + " есть журнал блоков. "
                            + "Для смены маски сначала нужен перенос журнала.");
                }
            }
        }
        return () -> {
            if (current != null) current.cleanup();
            if (replacement == null) rooms.remove(roomId);
            else rooms.put(roomId, replacement);
        };
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (RoomRuntime room : rooms.values()) {
            room.cleanup();
            if (room.life != null && room.life.state() == RoomState.FIGHTING) {
                room.life.abort();
            }
        }
        rooms.clear();
        mailbox.stop();
    }

    public boolean officialRoomReady(String roomId) {
        RoomRuntime room = rooms.get(roomId);
        return task != null && room != null && room.baselineReady && !playtestRooms.contains(roomId)
                && room.pendingRewards.isEmpty() && room.life == null && room.playtestStarter == null;
    }

    public int roomCount() {
        return rooms.size();
    }

    public CommandResponse requestPlaytest(CommandActor actor, String id, boolean solo) {
        RoomRuntime room = rooms.get(id);
        if (task == null || room == null || !playtestRooms.contains(id)) {
            return CommandResponse.fail("Нужна опубликованная тестовая комната из dungeons.runtime.playtest_rooms.");
        }
        Player player = actor.id() == null ? null : Bukkit.getPlayer(actor.id());
        if (player == null || !eligible(player) || !inside(player.getLocation(), room.room.world(), room.room.trigger())) {
            return CommandResponse.fail("Встаньте в зону входа тестовой комнаты в режиме выживания.");
        }
        if (room.life != null || !room.baselineReady || !room.pendingRewards.isEmpty()
                || room.playtestStarter != null || tick < room.cooldownUntil) {
            return CommandResponse.fail("Комната ещё восстанавливается, занята или на перезарядке.");
        }
        room.playtestStarter = player.getUniqueId();
        room.playtestSolo = solo;
        room.candidates.add(player.getUniqueId());
        return CommandResponse.ok("Боевой тест поставлен в очередь. Награды и вехи отключены.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Player player = event.getPlayer();
        if (to == null || !eligible(player) || sameBlock(event.getFrom(), to)) {
            return;
        }
        for (RoomRuntime room : rooms.values()) {
            if (inside(to, room.room.world(), room.room.trigger())) {
                room.enter(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGateInteract(PlayerInteractEvent event) {
        var action = event.getAction();
        var block = event.getClickedBlock();
        if (block == null || action != org.bukkit.event.block.Action.PHYSICAL
                && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            activateSecret(event.getPlayer(), block, SecretTrigger.Action.HIT);
            return;
        }
        if (action == org.bukkit.event.block.Action.PHYSICAL) {
            activateSecret(event.getPlayer(), block, SecretTrigger.Action.PLATE);
            return;
        }
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (activateSecret(event.getPlayer(), block, SecretTrigger.Action.BUTTON)
                || activateSecret(event.getPlayer(), block, SecretTrigger.Action.LEVER)
                || activateSecret(event.getPlayer(), block, SecretTrigger.Action.RUNE)) return;
        Location at = block.getLocation();
        for (RoomRuntime room : rooms.values()) {
            if (!room.cleared || !room.activeParticipant(event.getPlayer())) {
                continue;
            }
            for (GateRuntime gate : room.gates) {
                if (!room.secrets.containsKey(gate.gate().id())
                        && gate.gate().unlock() == UnlockMode.MANUAL_AFTER_ROOM_CLEAR && inside(at, gate.gate().world(), gate.gate().bounds())) {
                    room.open(gate, true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSecretProjectile(ProjectileHitEvent event) {
        if (event.getHitBlock() != null && event.getEntity() instanceof org.bukkit.entity.AbstractArrow arrow
                && arrow.getShooter() instanceof Player player) {
            activateSecret(player, event.getHitBlock(), SecretTrigger.Action.TARGET);
        }
    }

    private boolean activateSecret(Player player, org.bukkit.block.Block block, SecretTrigger.Action action) {
        if (!eligible(player) || !BukkitSecretTrigger.compatible(action, block)) return false;
        for (RoomRuntime room : rooms.values()) {
            if (room.boundWorld != block.getWorld() || !inside(player.getLocation(), room.room.world(), room.room.bounds())) continue;
            for (GateRuntime gate : room.gates) {
                SecretTrigger rule = room.secrets.get(gate.gate().id());
                if (rule == null || rule.action() != action) continue;
                boolean authorized = switch (gate.gate().unlock()) {
                    case ON_ROOM_CLEAR -> false;
                    case MANUAL_AFTER_ROOM_CLEAR -> room.cleared && room.activeParticipant(player);
                    case ON_SPECIFIC_MILESTONE -> (room.life == null || room.cleared)
                            && mailbox.cleared(rule.milestoneRoom());
                };
                if (!authorized) continue;
                boolean match = rule.block() != null ? rule.block().at(block.getX(), block.getY(), block.getZ())
                        : inside(block.getLocation(), gate.gate().world(), gate.gate().bounds());
                if (match && room.open(gate, true)) return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Owner owner = owned(event.getEntity());
        if (owner != null && event.getTarget() != null
                && (!(event.getTarget() instanceof Player player) || !owner.room().activeParticipant(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Owner target = owned(event.getEntity());
        Entity source = event.getDamager();
        Owner attacker = owned(source);
        if (source instanceof Projectile shot && shot.getShooter() instanceof Entity shooter) {
            source = shooter;
            if (attacker == null) {
                attacker = owned(shooter);
            }
        }
        if (target != null && target.enemy()
                && (!(source instanceof Player player) || !target.room().activeParticipant(player)
                || !target.room().fighting(target.generation()))) {
            event.setCancelled(true);
        }
        if (attacker != null && (!(event.getEntity() instanceof Player player)
                || !attacker.room().activeParticipant(player)
                || !attacker.room().fighting(attacker.generation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        Projectile shot = event.getEntity();
        if (shot.getShooter() instanceof Entity shooter) {
            Owner owner = owned(shooter);
            if (owner != null && owner.room().fighting(owner.generation())) {
                if (shot instanceof WindCharge || shot.getType() == EntityType.BREEZE_WIND_CHARGE) {
                    event.setCancelled(true);
                    return;
                }
                track(shot, new Owner(owner.room(), owner.generation(), false, tick + 120));
                if (shot instanceof Arrow arrow) {
                    arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWindImpact(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball shot)) {
            return;
        }
        Owner owner = owned(shot);
        if (owner == null) {
            return;
        }
        if (owner.room().fighting(owner.generation()) && event.getHitEntity() instanceof Player player
                && owner.room().activeParticipant(player)) {
            double health = player.getHealth() + player.getAbsorptionAmount();
            player.damage(3.0, shot);
            if (!player.isDead() && player.getHealth() + player.getAbsorptionAmount() < health) {
                player.setVelocity(shot.getVelocity().normalize().multiply(0.75).setY(0.24));
            }
        }
        shot.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, shot.getLocation(),
                10, 0.25, 0.25, 0.25, 0.03);
        owners.remove(shot.getUniqueId());
        shot.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobBlockChange(EntityChangeBlockEvent event) {
        if (owned(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (owned(event.getEntity()) != null) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void suppressEncounterDrops(EntityDeathEvent event) {
        Owner owner = owned(event.getEntity());
        if (owner != null && owner.enemy() && owner.room().fighting(owner.generation())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        var entity = event.getEntity();
        Owner owner = owned(entity);
        if (owner == null || !owner.enemy() || !owner.room().fighting(owner.generation())) return;
        RoomRuntime room = owner.room();
        UUID id = entity.getUniqueId();
        Player killer = entity.getKiller();
        boolean earned = killer != null && room.activeParticipant(killer);
        room.deathsPending.add(id);
        Bukkit.getScheduler().runTask(plugin, () -> {
            room.deathsPending.remove(id);
            if (event.isCancelled() || !entity.isDead() || !room.fighting(owner.generation())
                    || !owners.remove(id, owner) || room.live.remove(id) == null) return;
            if (!earned) room.combatFailure = true;
        });
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (mailbox.tagged(entity)
                    || entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.LONG) && owned(entity) == null) {
                entity.remove();
            }
        }
    }

    private Owner owned(Entity entity) {
        Owner owner = owners.get(entity.getUniqueId());
        Long tag = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.LONG);
        return owner != null && tag != null && tag == owner.generation() ? owner : null;
    }

    private void track(Entity entity, Owner owner) {
        entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.LONG, owner.generation());
        entity.setPersistent(false);
        owners.put(entity.getUniqueId(), owner);
    }

    private long reserveGeneration() throws IOException {
        long next = Math.incrementExact(generation);
        DungeonDefinitionStore.atomicWrite(data.resolve("generation"), Long.toString(next));
        generation = next;
        return next;
    }

    private void onTick() {
        tick += INTERVAL;
        for (RoomRuntime room : rooms.values()) {
            room.advance();
        }
        if (tick % 40 == 0) {
            owners.entrySet().removeIf(entry -> {
                if (entry.getValue().enemy()) return false;
                Entity projectile = Bukkit.getEntity(entry.getKey());
                if (projectile == null) return true;
                if (tick >= entry.getValue().expiresTick()) {
                    projectile.remove();
                    return true;
                }
                return false;
            });
            mailbox.tick(Bukkit.getOnlinePlayers());
        }
    }

    private final class RoomRuntime implements DungeonRoomLifecycle.Ports {
        private final DungeonRoomDefinition room;
        private final List<SpawnAnchor> anchors;
        private final List<GateDefinition> gateDefinitions;
        private final List<Long> pendingRewards;
        private final SpawnAnchor rewardAnchor;
        private final Map<String, SecretTrigger> secrets;
        private final List<GateRuntime> gates = new ArrayList<>();
        private final Set<UUID> roster = new LinkedHashSet<>();
        private final Map<UUID, DungeonMobCombat> live = new LinkedHashMap<>();
        private final Set<UUID> displays = new LinkedHashSet<>();
        private final DungeonRewardLedger ledger = new DungeonRewardLedger();
        private DungeonRoomLifecycle life;
        private final Set<UUID> candidates = new LinkedHashSet<>();
        private final Set<UUID> deathsPending = new LinkedHashSet<>();
        private boolean baselineReady;
        private boolean cleared;
        private boolean combatFailure;
        private long noPlayersSince = -1;
        private long activeSince;
        private UUID playtestStarter;
        private boolean playtestSolo;
        private long retryAt;
        private long cooldownUntil;
        private World boundWorld;

        private RoomRuntime(DungeonRoomDefinition room, List<SpawnAnchor> anchors,
                            List<GateDefinition> gates, List<Long> pendingRewards, SpawnAnchor rewardAnchor,
                            Map<String, SecretTrigger> secrets) {
            this.room = room;
            this.anchors = anchors;
            gateDefinitions = gates;
            this.pendingRewards = pendingRewards;
            this.rewardAnchor = rewardAnchor;
            this.secrets = secrets;
        }

        private void enter(Player player) {
            if (playtestRooms.contains(room.id())) return;
            if (life == null && tick >= cooldownUntil) {
                candidates.add(player.getUniqueId());
            } else if (life != null && life.state() == RoomState.FIGHTING
                    && room.participation().mode() == ParticipationMode.JOIN_SAFE) {
                roster.add(player.getUniqueId());
            }
        }

        private void activate() {
            List<Player> players = boundWorld.getPlayers().stream().filter(DungeonRuntime::eligible)
                    .filter(player -> inside(player.getLocation(), room.world(), room.trigger()))
                    .sorted(Comparator.comparing(Player::getUniqueId)).toList();
            UUID requested = playtestStarter;
            playtestStarter = null;
            Player starter = players.stream().filter(player -> requested == null
                            ? candidates.contains(player.getUniqueId()) : requested.equals(player.getUniqueId()))
                    .findFirst().orElse(null);
            candidates.clear();
            if (starter == null) return;
            if (requested != null && playtestSolo) {
                roster.add(starter.getUniqueId());
            } else if (room.participation().mode() == ParticipationMode.PARTY_SYNCHRONIZED) {
                var team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(starter.getName());
                if (team == null) {
                    starter.sendMessage("Для синхронного входа нужна общая команда scoreboard.");
                    return;
                }
                if (Bukkit.getOnlinePlayers().stream().filter(DungeonRuntime::eligible)
                        .anyMatch(player -> team.hasEntry(player.getName()) && !players.contains(player))) {
                    return;
                }
                roster.addAll(players.stream().filter(player -> team.hasEntry(player.getName()))
                        .map(Player::getUniqueId).toList());
            } else {
                roster.addAll(players.stream().map(Player::getUniqueId).toList());
            }
            DungeonEncounterPlanPolicy.Plan plan = DungeonEncounterPlanPolicy.plan(room, anchors, roster.size());
            boolean rewardSiteReady = room.reward().mode() == me.copimine.dungeon.domain.RewardMode.PERSONAL
                    || rewardAnchor != null && DungeonRewardSite.valid(room.reward().mode(), boundWorld,
                            rewardAnchor.x(), rewardAnchor.y(), rewardAnchor.z());
            if (!plan.viable() || requested == null && (!rewardSiteReady || DungeonRewardMailbox.lootTable(room) == null)) {
                report("Dungeon " + room.id() + " cannot start: "
                        + (plan.viable() ? "unsupported reward mode or missing loot table" : plan.reason()));
                roster.clear();
                cooldownUntil = tick + 100;
                return;
            }
            combatFailure = false;
            cleared = false;
            activeSince = tick;
            noPlayersSince = -1;
            life = new DungeonRoomLifecycle(room, plan, this);
            handle(life.tryActivate(roster.size(), tick));
        }

        private void advance() {
            World world = Bukkit.getWorld(room.world());
            if (world == null) {
                if (life != null && life.state() == RoomState.FIGHTING) {
                    handle(life.abort());
                }
                return;
            }
            if (boundWorld != world) {
                boundWorld = world;
                gates.clear();
                for (GateDefinition gate : gateDefinitions) {
                    BukkitGateWorld adapter = new BukkitGateWorld(world);
                    gates.add(new GateRuntime(gate,
                            new PhysicalGateTransaction(gate, adapter, gateJournal(world, gate)), adapter));
                }
                baselineReady = false;
            }
            if (tick < retryAt) {
                return;
            }
            if (life == null) {
                if (!pendingRewards.isEmpty()) {
                    if (!openExit() || !release(pendingRewards.getFirst())) {
                        retryAt = tick + 20;
                        return;
                    }
                    pendingRewards.removeFirst();
                    baselineReady = false;
                    return;
                }
                if (!baselineReady) {
                    baselineReady = restoreBaseline();
                    if (!baselineReady) {
                        retryAt = tick + 20;
                        return;
                    }
                }
                if (tick % 20 == 0 && !playtestRooms.contains(room.id())) {
                    for (GateRuntime gate : gates) {
                        SecretTrigger rule = secrets.get(gate.gate().id());
                        if (rule != null && rule.action() == SecretTrigger.Action.AUTO
                                && gate.gate().unlock() == UnlockMode.ON_SPECIFIC_MILESTONE
                                && mailbox.cleared(rule.milestoneRoom())) open(gate, true);
                    }
                }
                if (!candidates.isEmpty() && tick >= cooldownUntil) {
                    activate();
                }
                return;
            }
            if (life.state() == RoomState.FIGHTING) {
                List<Player> players = world.getPlayers().stream().filter(this::activeParticipant).toList();
                if (players.isEmpty()) {
                    if (noPlayersSince < 0) noPlayersSince = tick;
                    if (tick - noPlayersSince >= EMPTY_ROOM_GRACE) {
                        handle(life.abort());
                        return;
                    }
                } else {
                    noPlayersSince = -1;
                }
                if (tick - activeSince >= MAX_FIGHT_TICKS) {
                    handle(life.abort());
                    return;
                }
                for (DungeonMobCombat combat : live.values()) {
                    combat.advance(tick, players);
                }
            }
            handle(life.advance(tick));
        }

        private void handle(DungeonRoomLifecycle.Signal signal) {
            if (signal == DungeonRoomLifecycle.Signal.WAITING) {
                retryAt = tick + 20;
            }
            if (life.state() == RoomState.PUBLISHED_IDLE) {
                cleanup();
                life = null;
                roster.clear();
                cleared = false;
                long seconds = playtestRooms.contains(room.id()) ? 5 : room.resetCooldownSeconds();
                cooldownUntil = tick + (signal == DungeonRoomLifecycle.Signal.REJECTED ? 100 : seconds * 20);
            }
        }

        private boolean fighting(long attempt) {
            return life != null && life.generation() == attempt && life.state() == RoomState.FIGHTING;
        }

        private boolean activeParticipant(Player player) {
            return eligible(player) && roster.contains(player.getUniqueId())
                    && inside(player.getLocation(), room.world(), room.bounds());
        }

        @Override
        public long reserveGeneration(long previous) {
            try {
                return DungeonRuntime.this.reserveGeneration();
            } catch (IOException | RuntimeException error) {
                report("Unable to reserve dungeon generation: " + error.getMessage());
                return previous;
            }
        }

        @Override
        public boolean sealEntrance() {
            for (GateRuntime gate : gates) {
                if (entrance(gate) && !applied(gate.transaction().close(List.of()))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean openExit() {
            for (GateRuntime gate : gates) {
                if ((entrance(gate) || gate.gate().unlock() == UnlockMode.ON_ROOM_CLEAR)
                        && !open(gate, true)) {
                    return false;
                }
            }
            cleared = true;
            return true;
        }

        private boolean restoreBaseline() {
            if (Bukkit.getWorld(room.world()) != boundWorld) {
                return false;
            }
            boolean restored = true;
            for (GateRuntime gate : gates) {
                if (gate.gate().unlock() == UnlockMode.ON_SPECIFIC_MILESTONE) {
                    continue;
                }
                boolean result = entrance(gate) ? open(gate, false) : applied(gate.transaction().recover());
                restored &= result;
            }
            return restored;
        }

        private boolean open(GateRuntime gate, boolean animate) {
            PhysicalGateTransaction.Result result = gate.transaction().open();
            if (animate && result == PhysicalGateTransaction.Result.APPLIED) {
                try {
                    gate.world().animateOpening(plugin, gate.gate(), gate.transaction().journalKeys(), displays);
                } catch (RuntimeException error) {
                    report("Dungeon gate animation skipped for " + gate.gate().id() + ": " + error.getMessage());
                }
            }
            return applied(result);
        }

        @Override
        public int spawnWave(int waveIndex, List<DungeonEncounterPlanPolicy.Assignment> assignments) {
            int spawned = 0;
            for (DungeonEncounterPlanPolicy.Assignment assignment : assignments) {
                if (spawn(assignment)) {
                    spawned++;
                }
            }
            return spawned;
        }

        private boolean spawn(DungeonEncounterPlanPolicy.Assignment assignment) {
            EntityType type;
            try {
                type = EntityType.valueOf(assignment.template().baseType().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException error) {
                return false;
            }
            Location at = resolveSpawn(assignment, type);
            if (at == null) {
                return false;
            }
            Entity entity = null;
            try {
                entity = boundWorld.spawnEntity(at, type);
                if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                    if (entity != null) entity.remove();
                    return false;
                }
                mob.setRemoveWhenFarAway(false);
                if (assignment.template() == me.copimine.dungeon.domain.MobTemplate.ARENA_WARDEN) {
                    mob.customName(net.kyori.adventure.text.Component.translatable(
                            "copimine.dungeon.arena_warden", "Страж арены"));
                    mob.setCustomNameVisible(false);
                }
                track(mob, new Owner(this, life.generation(), true, Long.MAX_VALUE));
                live.put(mob.getUniqueId(), new DungeonMobCombat(mob, assignment.template(), tick));
                return true;
            } catch (RuntimeException error) {
                if (entity != null) {
                    owners.remove(entity.getUniqueId());
                    entity.remove();
                }
                report("Dungeon spawn failed at " + assignment.anchorId() + ": " + error.getMessage());
                return false;
            }
        }

        private Location resolveSpawn(DungeonEncounterPlanPolicy.Assignment assignment, EntityType type) {
            Location center = new Location(boundWorld, assignment.x() + 0.5, assignment.y(), assignment.z() + 0.5);
            SpawnAnchor anchor = anchors.stream().filter(value -> value.id().equals(assignment.anchorId()))
                    .findFirst().orElse(null);
            if (anchor != null && anchor.zone() != null) {
                Cuboid zone = anchor.zone();
                var random = java.util.concurrent.ThreadLocalRandom.current();
                for (int sample = 0; sample < 24; sample++) {
                    int x = sample == 0 ? anchor.x() : zone.minX() + random.nextInt(zone.sizeX());
                    int z = sample == 0 ? anchor.z() : zone.minZ() + random.nextInt(zone.sizeZ());
                    for (int layer = 0; layer < zone.sizeY(); layer++) {
                        Location at = new Location(boundWorld, x + 0.5, zone.minY() + layer, z + 0.5);
                        if (safeSpawn(at, type)) return at;
                    }
                }
                return null;
            }
            double radius = anchor == null ? 0 : Math.min(4.0D, anchor.radius());
            for (int ring = 0; ring <= Math.ceil(radius); ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring
                                || dx * dx + dz * dz > radius * radius) continue;
                        Location at = center.clone().add(dx, 0, dz);
                        if (inside(at, room.world(), room.bounds()) && safeSpawn(at, type)) return at;
                    }
                }
            }
            return null;
        }

        private boolean safeSpawn(Location at, EntityType type) {
            int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
            if (y <= boundWorld.getMinHeight() || y + 2 >= boundWorld.getMaxHeight()
                    || !boundWorld.isChunkLoaded(x >> 4, z >> 4)
                    || !boundWorld.getBlockAt(x, y - 1, z).getType().isSolid()
                    || !boundWorld.getBlockAt(x, y, z).getType().isAir()
                    || !boundWorld.getBlockAt(x, y + 1, z).getType().isAir()) {
                return false;
            }
            double halfWidth = type == EntityType.RAVAGER ? 1.0 : 0.48;
            double height = type == EntityType.ENDERMAN || type == EntityType.WARDEN ? 2.9 : 2.0;
            BoundingBox cell = new BoundingBox(at.getX() - halfWidth, y, at.getZ() - halfWidth,
                    at.getX() + halfWidth, y + height, at.getZ() + halfWidth);
            for (int bx = (int) Math.floor(cell.getMinX()); bx <= Math.floor(cell.getMaxX()); bx++) {
                for (int bz = (int) Math.floor(cell.getMinZ()); bz <= Math.floor(cell.getMaxZ()); bz++) {
                    if (!boundWorld.isChunkLoaded(bx >> 4, bz >> 4)) return false;
                    for (int by = y; by < y + height; by++) {
                        if (!boundWorld.getBlockAt(bx, by, bz).getType().isAir()) return false;
                    }
                }
            }
            return boundWorld.getNearbyEntities(cell, entity -> entity instanceof org.bukkit.entity.LivingEntity
                    || entity instanceof org.bukkit.entity.Vehicle).isEmpty();
        }

        @Override
        public int liveEnemies() {
            for (UUID id : live.keySet()) {
                if (deathsPending.contains(id)) continue;
                Entity entity = Bukkit.getEntity(id);
                if (entity == null || entity.isDead() || !entity.isValid()
                        || !inside(entity.getLocation(), room.world(), room.bounds())) {
                    combatFailure = true;
                }
            }
            return live.size();
        }

        @Override
        public boolean hasCombatFailure() {
            return combatFailure;
        }

        @Override
        public boolean commitClear() {
            if (playtestRooms.contains(room.id())) return true;
            try {
                mailbox.prepare(room, life.generation(), boundWorld, roster, rewardAnchor);
                return true;
            } catch (IOException | RuntimeException error) {
                report("Dungeon clear checkpoint pending for " + room.id() + ": " + error.getMessage());
                return false;
            }
        }

        @Override
        public boolean grantReward() {
            if (playtestRooms.contains(room.id())) return true;
            long attempt = life.generation();
            return ledger.claim(room.id(), attempt, "mailbox", room.reward().mode(),
                    () -> release(attempt)) != DungeonRewardLedger.Decision.DELIVERY_PENDING;
        }

        private boolean release(long attempt) {
            try {
                mailbox.release(room.id(), attempt);
                return true;
            } catch (IOException | RuntimeException error) {
                report("Dungeon reward pending for " + room.id() + ": " + error.getMessage());
                return false;
            }
        }

        @Override
        public boolean recoverAttempt() {
            cleanup();
            return restoreBaseline();
        }

        @Override
        public boolean resetAfterClear() {
            return boundWorld.getPlayers().stream()
                    .noneMatch(player -> activeParticipant(player)) && restoreBaseline();
        }

        @Override
        public void report(String message) {
            plugin.getLogger().warning(message);
        }

        private void cleanup() {
            owners.entrySet().removeIf(entry -> {
                if (entry.getValue().room() != this) return false;
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity != null) entity.remove();
                return true;
            });
            live.clear();
            deathsPending.clear();
            for (UUID id : displays) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
            displays.clear();
        }
    }

    private PhysicalGateTransaction.Journal gateJournal(World world, GateDefinition gate) {
        String identity = world.getUID() + "\t" + encoded(gate.roomId()) + "\t" + gate.bounds();
        Path path = journalPath(gate);
        return new PhysicalGateTransaction.Journal() {
            @Override public List<String> load() throws IOException {
                if (Files.notExists(path)) return List.of();
                List<String> lines = Files.readAllLines(path);
                if (lines.isEmpty() || !identity.equals(lines.getFirst())) {
                    throw new IOException("Gate journal does not match published geometry for " + gate.id());
                }
                return lines.subList(1, lines.size());
            }

            @Override public void write(List<String> keys) throws IOException {
                DungeonDefinitionStore.atomicWrite(path, identity + "\n" + String.join("\n", keys) + "\n");
            }
        };
    }

    private Path journalPath(GateDefinition gate) {
        return data.resolve("gates").resolve(encoded(gate.id()) + ".mask");
    }

    private static String encoded(String id) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean entrance(GateRuntime gate) {
        return gate.gate().initialOpen() && !PhysicalGatePolicy.isSecret(gate.gate())
                && gate.gate().unlock() != UnlockMode.ON_SPECIFIC_MILESTONE;
    }

    private static boolean applied(PhysicalGateTransaction.Result result) {
        return result == PhysicalGateTransaction.Result.APPLIED
                || result == PhysicalGateTransaction.Result.ALREADY_CLOSED
                || result == PhysicalGateTransaction.Result.ALREADY_OPEN;
    }

    private static boolean eligible(Player player) {
        return player.isOnline() && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    private static boolean inside(Location at, String world, Cuboid bounds) {
        return at.getWorld() != null && at.getWorld().getName().equals(world)
                && bounds.contains(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld() && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ();
    }

    public Map<String, String> describe() {
        Map<String, String> states = new LinkedHashMap<>();
        for (RoomRuntime room : rooms.values()) {
            states.put(room.room.id(), room.life == null
                    ? (room.baselineReady ? "idle" : "recovery")
                    : room.life.state() + "#" + room.life.generation());
        }
        return states;
    }
}
