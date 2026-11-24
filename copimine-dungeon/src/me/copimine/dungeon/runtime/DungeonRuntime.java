package me.copimine.dungeon.runtime;

import me.copimine.dungeon.domain.Cuboid;
import me.copimine.dungeon.domain.DungeonEncounterPlanPolicy;
import me.copimine.dungeon.domain.DungeonRewardLedger;
import me.copimine.dungeon.domain.DungeonRoomDefinition;
import me.copimine.dungeon.domain.DungeonRoomLifecycle;
import me.copimine.dungeon.domain.GateDefinition;
import me.copimine.dungeon.domain.PhysicalGateTransaction;
import me.copimine.dungeon.domain.RoomState;
import me.copimine.dungeon.domain.SpawnAnchor;
import me.copimine.dungeon.store.DungeonDefinitionStore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Opt-in Paper adapter for the dungeon runtime controllers (spec OV4.10
 * {@code DungeonRoomController} and {@code PhysicalGateController}).
 *
 * <p>This class is deliberately the only place where the dungeon plugin touches
 * the live world. It is disabled unless {@code dungeons.runtime.enabled} is true,
 * and even then it only starts rooms whose durable definition is enabled, whose
 * world is loaded and whose spawn anchors exist. All game rules live in the pure
 * {@link DungeonRoomLifecycle}, {@link DungeonEncounterPlanPolicy} and
 * {@link PhysicalGateTransaction} cores; this adapter only materialises their
 * decisions (spawn, gate mask, reward receipt) and drives them from the tick
 * loop and {@link PlayerMoveEvent} trigger.</p>
 *
 * <p>Reward delivery is a receipt, not fabricated loot: the ledger records an
 * exactly-once claim keyed by room generation and player, and the configured loot
 * table is reported. Wiring a concrete loot source is an explicit follow-up and
 * never guessed here.</p>
 */
public final class DungeonRuntime implements Listener {

    private final JavaPlugin plugin;
    private final boolean enabled;
    private final DungeonRewardLedger ledger = new DungeonRewardLedger();
    private final Map<String, RoomRuntime> rooms = new LinkedHashMap<>();
    private BukkitTask task;
    private long tick;

    public DungeonRuntime(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean start(DungeonDefinitionStore store) {
        if (!enabled) {
            plugin.getLogger().info("Dungeon runtime disabled (dungeons.runtime.enabled=false).");
            return false;
        }
        for (DungeonRoomDefinition room : store.catalog().rooms().values()) {
            if (!room.enabled() || room.world().isBlank() || room.bounds() == null
                    || room.trigger() == null) {
                continue;
            }
            World world = plugin.getServer().getWorld(room.world());
            if (world == null) {
                plugin.getLogger().warning("Dungeon runtime skipped room '" + room.id()
                        + "': world '" + room.world() + "' is not loaded.");
                continue;
            }
            List<SpawnAnchor> anchors = new ArrayList<>();
            for (String anchorId : room.spawnAnchorIds()) {
                SpawnAnchor anchor = store.catalog().spawn(anchorId);
                if (anchor != null) {
                    anchors.add(anchor);
                }
            }
            List<GateRuntime> gates = new ArrayList<>();
            for (String gateId : room.gateIds()) {
                GateDefinition gate = store.catalog().gate(gateId);
                if (gate == null || !gate.world().equalsIgnoreCase(room.world())) {
                    continue;
                }
                gates.add(new GateRuntime(gate,
                        new PhysicalGateTransaction(gate, new BukkitGateWorld(world))));
            }
            rooms.put(room.id(), new RoomRuntime(room, anchors, gates));
        }
        if (rooms.isEmpty()) {
            plugin.getLogger().info("Dungeon runtime has no startable rooms; staying inert.");
            return false;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::onTick, 20L, 20L);
        plugin.getLogger().info("Dungeon runtime enabled for " + rooms.size() + " room(s).");
        return true;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (RoomRuntime room : rooms.values()) {
            room.shutdown();
        }
        rooms.clear();
    }

    /** Exposed for tests/diagnostics only; never a gameplay shortcut. */
    public int roomCount() {
        return rooms.size();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        for (RoomRuntime room : rooms.values()) {
            if (room.containsTrigger(player)) {
                room.requestActivation();
                return;
            }
        }
    }

    private void onTick() {
        tick++;
        for (RoomRuntime room : rooms.values()) {
            room.advance();
        }
    }

    private final class GateRuntime {
        private final GateDefinition gate;
        private final PhysicalGateTransaction transaction;

        private GateRuntime(GateDefinition gate, PhysicalGateTransaction transaction) {
            this.gate = gate;
            this.transaction = transaction;
        }
    }

    private final class RoomRuntime implements DungeonRoomLifecycle.Ports {
        private final DungeonRoomDefinition room;
        private final List<SpawnAnchor> anchors;
        private final List<GateRuntime> gates;
        private final Set<UUID> live = new HashSet<>();
        private DungeonRoomLifecycle life;

        private RoomRuntime(DungeonRoomDefinition room, List<SpawnAnchor> anchors,
                            List<GateRuntime> gates) {
            this.room = room;
            this.anchors = anchors;
            this.gates = gates;
        }

        private boolean containsTrigger(Player player) {
            if (!player.getWorld().getName().equals(room.world())) {
                return false;
            }
            Location location = player.getLocation();
            return room.trigger().contains(location.getBlockX(), location.getBlockY(),
                    location.getBlockZ());
        }

        private void requestActivation() {
            if (life != null && life.state() != RoomState.PUBLISHED_IDLE) {
                return;
            }
            int eligible = eligibleParticipants();
            if (eligible < 1) {
                return;
            }
            DungeonEncounterPlanPolicy.Plan plan =
                    DungeonEncounterPlanPolicy.plan(room, anchors, eligible);
            if (!plan.viable()) {
                plugin.getLogger().warning("Dungeon room '" + room.id()
                        + "' cannot start: " + plan.reason());
                return;
            }
            life = new DungeonRoomLifecycle(room, plan, this);
            DungeonRoomLifecycle.Signal signal = life.tryActivate(eligible, tick);
            plugin.getLogger().info("Dungeon room '" + room.id() + "' activation -> " + signal
                    + " (generation " + life.generation() + ", roster " + eligible + ").");
        }

        private void advance() {
            if (life == null) {
                return;
            }
            DungeonRoomLifecycle.Signal signal = life.advance(tick);
            if (signal != DungeonRoomLifecycle.Signal.FIGHTING) {
                plugin.getLogger().info("Dungeon room '" + room.id() + "' -> " + signal
                        + " (" + life.state() + ").");
            }
            if (life.state() == RoomState.PUBLISHED_IDLE) {
                life = null;
            }
        }

        private int eligibleParticipants() {
            int count = 0;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR && containsTrigger(player)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public boolean sealEntrance() {
            boolean sealed = true;
            for (GateRuntime gate : gates) {
                PhysicalGateTransaction.Result result =
                        gate.transaction.close(occupiedBlocks(gate.gate));
                if (result != PhysicalGateTransaction.Result.APPLIED
                        && result != PhysicalGateTransaction.Result.ALREADY_CLOSED) {
                    plugin.getLogger().warning("Gate '" + gate.gate.id() + "' did not seal: " + result);
                    sealed = false;
                }
            }
            return sealed;
        }

        @Override
        public boolean openExit() {
            boolean opened = true;
            for (GateRuntime gate : gates) {
                PhysicalGateTransaction.Result result = gate.transaction.open();
                if (result != PhysicalGateTransaction.Result.APPLIED
                        && result != PhysicalGateTransaction.Result.ALREADY_OPEN) {
                    plugin.getLogger().warning("Gate '" + gate.gate.id() + "' did not open: " + result);
                    opened = false;
                }
            }
            return opened;
        }

        @Override
        public int spawnWave(int waveIndex, List<DungeonEncounterPlanPolicy.Assignment> assignments) {
            World world = plugin.getServer().getWorld(room.world());
            if (world == null) {
                return 0;
            }
            int spawned = 0;
            for (DungeonEncounterPlanPolicy.Assignment assignment : assignments) {
                EntityType type = resolveType(assignment.template().baseType());
                if (type == null) {
                    plugin.getLogger().warning("Unknown base entity type '"
                            + assignment.template().baseType() + "' for template "
                            + assignment.template().id());
                    continue;
                }
                Location location = new Location(world,
                        assignment.x() + 0.5D, assignment.y(), assignment.z() + 0.5D);
                try {
                    Entity entity = world.spawnEntity(location, type);
                    entity.setPersistent(false);
                    live.add(entity.getUniqueId());
                    spawned++;
                } catch (RuntimeException error) {
                    plugin.getLogger().warning("Failed to spawn " + type + " for room '"
                            + room.id() + "': " + error.getMessage());
                }
            }
            return spawned;
        }

        @Override
        public int liveEnemies() {
            live.removeIf(uuid -> {
                Entity entity = Bukkit.getEntity(uuid);
                return entity == null || entity.isDead() || !entity.isValid();
            });
            return live.size();
        }

        @Override
        public boolean grantReward() {
            String loot = room.reward().lootTableId();
            int granted = 0;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (!player.getWorld().getName().equals(room.world())) {
                    continue;
                }
                Location location = player.getLocation();
                if (!room.bounds().contains(location.getBlockX(), location.getBlockY(),
                        location.getBlockZ())) {
                    continue;
                }
                DungeonRewardLedger.Decision decision = ledger.claim(room.id(),
                        life == null ? 0L : life.generation(), player.getUniqueId().toString(),
                        room.reward().mode());
                if (decision == DungeonRewardLedger.Decision.GRANTED) {
                    granted++;
                }
            }
            plugin.getLogger().info("Dungeon room '" + room.id() + "' reward receipt: mode="
                    + room.reward().mode() + " loot=" + (loot.isBlank() ? "<none>" : loot)
                    + " grants=" + granted + " (item delivery is a configured follow-up).");
            return true;
        }

        @Override
        public void report(String message) {
            plugin.getLogger().info(message);
        }

        private List<int[]> occupiedBlocks(GateDefinition gate) {
            List<int[]> occupied = new ArrayList<>();
            Cuboid bounds = gate.bounds();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.getWorld().getName().equals(gate.world())) {
                    continue;
                }
                Location location = player.getLocation();
                if (bounds.contains(location.getBlockX(), location.getBlockY(),
                        location.getBlockZ())) {
                    occupied.add(new int[]{location.getBlockX(), location.getBlockY(),
                            location.getBlockZ()});
                }
            }
            return occupied;
        }

        private void shutdown() {
            for (GateRuntime gate : gates) {
                gate.transaction.recover();
            }
            for (UUID uuid : live) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
            live.clear();
            life = null;
        }
    }

    private static EntityType resolveType(String baseType) {
        if (baseType == null || baseType.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(baseType.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    /** Stable diagnostic snapshot of the runtime state. */
    public Map<String, String> describe() {
        Map<String, String> state = new HashMap<>();
        for (Map.Entry<String, RoomRuntime> entry : rooms.entrySet()) {
            RoomRuntime room = entry.getValue();
            state.put(entry.getKey(), room.life == null ? "idle"
                    : room.life.state() + "#" + room.life.generation());
        }
        return state;
    }
}
