package me.copimine.narcotics.cauldron;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.db.NarcoticsDatabase;
import me.copimine.narcotics.item.NarcoticItemFactory;
import me.copimine.narcotics.model.NarcoticDefinition;
import me.copimine.narcotics.recipe.IngredientEntry;
import me.copimine.narcotics.recipe.NarcoticsRecipeService;
import me.copimine.narcotics.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.Objects;

public final class CauldronBrewingService {
    private static final long STALE_BREW_STATE_MILLIS = 15L * 60L * 1000L;
    private static final int MINIMUM_RECIPE_CHECK_SIZE = 3;
    private static final int MAX_CACHED_STATES = 10_000;

    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private NarcoticsRecipeService recipeService;
    private NarcoticItemFactory itemFactory;
    private final Map<BlockKey, CauldronState> cache = new ConcurrentHashMap<>();
    // A per-block lock map grows forever when players break arbitrary blocks.
    // Fixed stripes preserve the required serialization without retaining every
    // historical world coordinate in memory.
    private final Object[] lockStripes = new Object[256];
    private final Object cacheAdmissionLock = new Object();
    private final Map<ChunkKey, Set<BlockKey>> cacheByChunk = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<BlockKey> integrityQueue = new ConcurrentLinkedQueue<>();
    private final Set<BlockKey> integrityQueued = ConcurrentHashMap.newKeySet();
    private final Set<BlockKey> completionInFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean cacheReady = false;

    public CauldronBrewingService(CopiMineNarcotics plugin, NarcoticsConfigService configService, NarcoticsDatabase database, NarcoticsRecipeService recipeService, NarcoticItemFactory itemFactory) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.recipeService = recipeService;
        this.itemFactory = itemFactory;
        for (int index = 0; index < lockStripes.length; index++) {
            lockStripes[index] = new Object();
        }
    }

    public void reload(NarcoticsConfigService configService, NarcoticsRecipeService recipeService, NarcoticItemFactory itemFactory) {
        this.configService = configService;
        this.recipeService = recipeService;
        this.recipeService.setItemFactory(itemFactory);
        this.itemFactory = itemFactory;
    }

    public void preloadCacheIfEnabled() {
        if (!configService.preloadBrewingCacheOnEnable()) {
            cacheReady = true;
            return;
        }
        cacheReady = false;
        loadBrewingCache();
    }

    private void loadBrewingCache() {
        database.loadBrewingStates(MAX_CACHED_STATES).thenAccept(states -> Bukkit.getScheduler().runTask(plugin, () -> {
                    int restoredCount = 0;
                    long nowMillis = System.currentTimeMillis();
                    for (Map.Entry<BlockKey, NarcoticsDatabase.LoadedBrewingState> entry : states.entrySet()) {
                        if (restoredCount >= MAX_CACHED_STATES) {
                            plugin.getLogger().warning("Brewing state cache reached its safety limit; remaining rows were ignored.");
                            break;
                        }
                        NarcoticsDatabase.LoadedBrewingState loaded = entry.getValue();
                        long updatedAtMillis = loaded.updatedAtEpochMillis();
                        if (updatedAtMillis > 0L && updatedAtMillis < 10_000_000_000L) {
                            updatedAtMillis *= 1000L;
                        }
                        if (updatedAtMillis > 0L && nowMillis - updatedAtMillis >= STALE_BREW_STATE_MILLIS) {
                            // A state that survived a restart longer than the
                            // safety window is no longer trusted as a live
                            // cauldron transaction. Tombstone it first, then
                            // queue exactly one ingredient refund.
                            UUID staleOwner = parseOwner(loaded.ownerUuid());
                            database.tombstoneBrewingState(entry.getKey(), loaded.version()).whenComplete((applied, error) -> {
                                if (error != null) {
                                    plugin.getLogger().warning("Unable to tombstone stale brewing state " + entry.getKey() + ": " + error.getMessage());
                                    return;
                                }
                                if (!Boolean.TRUE.equals(applied)) {
                                    // A newer state won the race.  Its
                                    // ingredients remain owned by that state;
                                    // never issue a refund for the old row.
                                    return;
                                }
                                if (staleOwner != null && !loaded.ingredients().isEmpty()) {
                                    for (IngredientEntry ingredient : loaded.ingredients()) {
                                        database.queuePendingRefund(staleOwner, "INGREDIENT:" + ingredient.serialize(), 1);
                                    }
                                }
                            });
                            continue;
                        }
                        CauldronState restored = new CauldronState(List.copyOf(loaded.ingredients()), loaded.version(), updatedAtMillis, parseOwner(loaded.ownerUuid()));
                        // Merge by version so a late preload response can
                        // never overwrite a newer in-memory ingredient state.
                        CauldronState selected = cache.merge(entry.getKey(), restored,
                                (current, incoming) -> current.version() >= incoming.version() ? current : incoming);
                        cacheState(entry.getKey(), selected);
                        restoredCount++;
                    }
                    cacheReady = true;
                    plugin.getLogger().info("Restored " + restoredCount + " pending cauldron brew state(s).");
                }))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Brewing state restore failed: " + error.getMessage());
                    Bukkit.getScheduler().runTaskLater(plugin, this::loadBrewingCache, 100L);
                    return null;
                });
    }

    public void runIntegritySweep() {
        long nowMillis = System.currentTimeMillis();
        for (int processed = 0; processed < 100; processed++) {
            BlockKey key = integrityQueue.poll();
            if (key == null) {
                return;
            }
            integrityQueued.remove(key);
            CauldronState state = cache.get(key);
            if (state == null) {
                continue;
            }
            // The final ingredient is handled by a durable one-shot
            // transaction. Do not keep repainting the pending-brew particle
            // loop while that transaction is in flight.
            if (completionInFlight.contains(key)) {
                continue;
            }
            World world = plugin.getServer().getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                enqueueIntegrityCheck(key);
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isSupportedCauldron(block)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D), state.isStale(nowMillis));
                continue;
            }
            Map.Entry<BlockKey, CauldronState> entry = Map.entry(key, state);
            if (entry.getValue().isStale(nowMillis)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D), true);
                continue;
            }
            spawnQueuedParticles(block, state.ingredients().size(), false);
            enqueueIntegrityCheck(key);
        }
    }

    public void reconcileLoadedChunk(String worldName, int chunkX, int chunkZ) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        Set<BlockKey> indexed = cacheByChunk.get(new ChunkKey(worldName, chunkX, chunkZ));
        if (indexed == null || indexed.isEmpty()) {
            return;
        }
        for (BlockKey key : Set.copyOf(indexed)) {
            CauldronState state = cache.get(key);
            if (state == null) {
                continue;
            }
            if (completionInFlight.contains(key)) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isSupportedCauldron(block) || state.isStale(nowMillis)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D), state.isStale(nowMillis));
                continue;
            }
            spawnQueuedParticles(block, state.ingredients().size(), false);
            enqueueIntegrityCheck(key);
        }
    }

    public boolean isSupportedCauldron(Block block) {
        if (block == null || block.getType() != Material.WATER_CAULDRON) {
            return false;
        }
        if (!hasBrewingRig(block)) {
            return false;
        }
        if (!configService.requireFullWater()) {
            return true;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Levelled levelled)) {
            return false;
        }
        return isFullWaterLevel(levelled);
    }

    public boolean tryAddIngredient(org.bukkit.entity.Player player, Block block, ItemStack stack) {
        if (player == null || block == null || stack == null) {
            return false;
        }
        if (!cacheReady) {
            player.sendMessage("§eВарки ещё загружаются. Попробуйте снова через несколько секунд.");
            return false;
        }
        if (!database.hasAsyncCapacity()) {
            player.sendMessage("§eВарка временно недоступна: база данных занята. Попробуйте через несколько секунд.");
            return false;
        }
        if (!isSupportedCauldron(block)) {
            return false;
        }
        if (itemFactory.isOfficialFinishedItem(stack)) {
            return false;
        }
        BlockKey key = BlockKey.of(block);
        IngredientEntry ingredient = recipeService.cauldronIngredientEntry(stack);
        if (ingredient == null) {
            return false;
        }
        synchronized (lockFor(key)) {
            long nowMillis = System.currentTimeMillis();
            CauldronState base = cache.getOrDefault(key, new CauldronState(List.of(), 0L, nowMillis, player.getUniqueId()));
            UUID playerUuid = player == null ? null : player.getUniqueId();
            if (completionInFlight.contains(key)) {
                if (player != null) {
                    player.sendMessage("§eЗавершение варки уже обрабатывается.");
                }
                return false;
            }
            UUID ownerUuid = base.ownerUuid() == null ? playerUuid : base.ownerUuid();
            List<IngredientEntry> current = new ArrayList<>(base.ingredients());
            current.add(ingredient);
            long nextVersion = base.version() + 1L;

            NarcoticDefinition exact = recipeService.matchExact(current);
            if (current.size() >= MINIMUM_RECIPE_CHECK_SIZE && exact != null) {
                return prepareFinalIngredient(block, key, exact, nextVersion, current.size(), false,
                        ownerUuid, player, stack, ingredient, current);
            }
            int maximumRecipeSize = recipeService.maximumRecipeSize();
            boolean canStillBecomeRecipe = recipeService.canStillBecomeRecipe(current);
            if (current.size() < MINIMUM_RECIPE_CHECK_SIZE) {
                return queueIngredients(block, key, current, nextVersion, nowMillis, player, stack);
            }
            if (recipeService.containsUnrecognizedIngredient(current)) {
                return prepareFinalIngredient(block, key, configService.items().get("zhuzevo"), nextVersion,
                        current.size(), true, ownerUuid, player, stack, ingredient, current);
            }
            if (canStillBecomeRecipe && current.size() < maximumRecipeSize) {
                return queueIngredients(block, key, current, nextVersion, nowMillis, player, stack);
            }
            return prepareFinalIngredient(block, key, configService.items().get("zhuzevo"), nextVersion,
                    current.size(), true, ownerUuid, player, stack, ingredient, current);
        }
    }

    public void handleCauldronBroken(Block block, Location dropLocation) {
        handleCauldronBroken(block, dropLocation, false);
    }

    private void handleCauldronBroken(Block block, Location dropLocation, boolean stale) {
        BlockKey key = BlockKey.of(block);
        // BlockBreakEvent is emitted for every block in the world. Do not even
        // acquire a lock when there is no pending brew at this coordinate.
        if (!cache.containsKey(key)) {
            return;
        }
        synchronized (lockFor(key)) {
            CauldronState pending = cache.get(key);
            if (pending == null || pending.ingredients().isEmpty()) {
                return;
            }
            database.tombstoneBrewingState(key, pending.version()).whenComplete((applied, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                synchronized (lockFor(key)) {
                    CauldronState current = cache.get(key);
                    if (error != null || !Boolean.TRUE.equals(applied) || current == null || current.version() != pending.version()) {
                        if (error != null) {
                            plugin.getLogger().warning("Brewing state delete failed for " + key + ": " + error.getMessage());
                        }
                        return;
                    }
                    removeCachedState(key, current);
                    spawnQueuedParticles(block, current.ingredients().size(), true);
                    if (current.ownerUuid() != null) {
                        // A world drop is not a durable delivery: a restart,
                        // hopper or another player can consume it before the
                        // owner receives the refund. Always use the mailbox,
                        // including when the old config requested drops.
                        queueIngredientRefunds(current.ownerUuid(), current.ingredients(), key + (stale ? ":stale" : ":break"));
                    } else {
                        plugin.getLogger().severe("Brewing state " + key + " has no owner; ingredient refund requires manual review.");
                    }
                }
            }));
        }
    }

    public void handleRigSupportBroken(Block block) {
        if (block == null) {
            return;
        }
        Block directAbove = block.getRelative(BlockFace.UP);
        if (cache.containsKey(BlockKey.of(directAbove))) {
            handleCauldronBroken(directAbove, directAbove.getLocation().add(0.5D, 0.7D, 0.5D));
        }
        Block twoAbove = block.getRelative(BlockFace.UP, 2);
        if (cache.containsKey(BlockKey.of(twoAbove))) {
            handleCauldronBroken(twoAbove, twoAbove.getLocation().add(0.5D, 0.7D, 0.5D));
        }
    }

    public void handleCauldronLevelChange(Block block, BlockData newState) {
        BlockKey key = BlockKey.of(block);
        if (!cache.containsKey(key)) {
            return;
        }
        boolean stillFull = block.getType() == Material.WATER_CAULDRON
                && newState instanceof Levelled levelled
                && (!configService.requireFullWater() || isFullWaterLevel(levelled))
                && hasBrewingRig(block);
        if (!stillFull) {
            handleCauldronBroken(block, block.getLocation().add(0.5D, 0.5D, 0.5D));
        }
    }

    public int cachedStateCount() {
        return cache.size();
    }

    public void clearCache() {
        cache.clear();
        cacheByChunk.clear();
        integrityQueue.clear();
        integrityQueued.clear();
        completionInFlight.clear();
    }

    public void shutdown() {
        cacheReady = false;
        clearCache();
    }

    private boolean prepareFinalIngredient(Block block, BlockKey key, NarcoticDefinition definition,
                                           long version, int ingredientCount, boolean wrongMix, UUID ownerUuid,
                                           org.bukkit.entity.Player player, ItemStack stack, IngredientEntry ingredient,
                                           List<IngredientEntry> completedIngredients) {
        if (definition == null) {
            if (player != null) {
                player.sendMessage("§cВарка не может завершиться: в конфигурации отсутствует результат ошибочной смеси.");
            }
            return false;
        }
        if (!completionInFlight.add(key)) {
            return false;
        }
        long expectedStoredVersion = Math.max(0L, version - 1L);
        database.prepareBrewingCompletionIntent(key, expectedStoredVersion, ownerUuid, definition.id(),
                        ingredient == null ? "" : ingredient.serialize())
                .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    synchronized (lockFor(key)) {
                        CauldronState current = cache.get(key);
                        if (error != null || current == null || current.version() != expectedStoredVersion
                                || !Objects.equals(current.ownerUuid(), ownerUuid)) {
                            completionInFlight.remove(key);
                            if (error != null) {
                                plugin.getLogger().warning("Brewing completion intent failed for " + key + ": " + error.getMessage());
                            }
                            if (player != null) {
                                player.sendMessage("§cВарка не сохранена. Ингредиент не списан.");
                            }
                            return;
                        }
                        // The completion intent is durable before this physical mutation.
                        cacheState(key, new CauldronState(List.copyOf(completedIngredients), version,
                                System.currentTimeMillis(), ownerUuid));
                        itemFactory.consumeOne(player, stack);
                        finishBrewing(block, key, definition, version, ingredientCount, wrongMix, ownerUuid);
                    }
                }));
        return true;
    }

    private void finishBrewing(Block block, BlockKey key, NarcoticDefinition definition, long version, int ingredientCount, boolean wrongMix, UUID ownerUuid) {
        // `version` is the in-memory version after the final ingredient.  The
        // final ingredient is intentionally not persisted as a live state: the
        // atomic completion CAS advances the durable previous version by one,
        // so stale workers cannot tombstone a newer brew at this block.
        long expectedStoredVersion = Math.max(0L, version - 1L);
        database.completeBrewingState(key, expectedStoredVersion, ownerUuid, definition.id())
                .whenComplete((applied, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        synchronized (lockFor(key)) {
                            CauldronState current = cache.get(key);
                            if (error != null || !Boolean.TRUE.equals(applied)
                                || current == null || current.version() != version) {
                            completionInFlight.remove(key);
                            if (error != null) {
                                plugin.getLogger().warning("Brewing completion tombstone failed for " + key + ": " + error.getMessage());
                            }
                            return;
                        }
                        removeCachedState(key, current);
                        if (wrongMix) {
                            simulateWrongMixExplosion(block);
                        }
                        if (ownerUuid != null) {
                            // The output row was committed with the tombstone.
                            // Claim it through the durable mailbox rather than
                            // dropping an untracked item in a crash window.
                            plugin.requestPendingBrewingOutputDelivery(ownerUuid);
                        } else {
                            plugin.getLogger().severe("Completed brewing output " + definition.id()
                                    + " has no owner; durable delivery requires manual review.");
                        }
                        if (block.getWorld() != null) {
                            particle(block.getLocation().add(0.5D, 1.0D, 0.5D), Particle.WITCH,
                                    "zhuzevo".equals(definition.id()) ? 24 : 12);
                            spawnQueuedParticles(block, Math.max(1, ingredientCount), true);
                        }
                        extinguishRig(block);
                        if (configService.clearCauldronOnCompletion()) {
                            block.setType(Material.CAULDRON, false);
                        }
                        completionInFlight.remove(key);
                    }
                }));
    }

    private void simulateWrongMixExplosion(Block block) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 1.0D, 0.5D);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.85F);
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        world.spawnParticle(Particle.SMOKE, center, 42, 0.55D, 0.35D, 0.55D, 0.03D);
        // Wrong mixtures are cosmetic.  Applying direct damage here bypasses
        // region/PvP policy and cannot be represented in the durable brew
        // transaction, so no player damage is generated by the effect.
    }

    private boolean queueIngredients(Block block, BlockKey key, List<IngredientEntry> current, long version, long nowMillis, org.bukkit.entity.Player player, ItemStack consumed) {
        List<IngredientEntry> frozen = List.copyOf(current);
        UUID ownerUuid = player == null ? null : player.getUniqueId();
        synchronized (cacheAdmissionLock) {
            if (!cache.containsKey(key) && cache.size() >= MAX_CACHED_STATES) {
                if (player != null) {
                    player.sendMessage("§eВарка временно недоступна: достигнут лимит активных котлов. Попробуйте позже.");
                }
                plugin.getLogger().warning("Brewing state cache reached its safety limit; rejecting a new cauldron at " + key + ".");
                return false;
            }
            // Admission and consumption are one main-thread operation. This
            // prevents a full-cache rejection from consuming an ingredient.
            if (player != null && consumed != null) {
                itemFactory.consumeOne(player, consumed);
            }
            cacheState(key, new CauldronState(frozen, version, nowMillis, ownerUuid));
        }
        CompletableFuture<Void> persisted = ownerUuid == null
                ? database.saveBrewingState(key, version, frozen)
                : database.saveBrewingState(key, version, frozen, ownerUuid);
        persisted.exceptionally(error -> {
            plugin.getLogger().warning("Brewing state database save failed for " + key + ": " + error.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                synchronized (lockFor(key)) {
                    CauldronState currentState = cache.get(key);
                    if (currentState != null && currentState.version() == version && currentState.ingredients().equals(frozen)) {
                        // Do not refund until the compensating tombstone is
                        // committed.  If the original save actually reached
                        // PostgreSQL but its acknowledgement was lost, an
                        // early refund would duplicate the ingredient on the
                        // next restart.
                        database.tombstoneBrewingState(key, version).whenComplete((deleted, deleteError) ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    synchronized (lockFor(key)) {
                                        CauldronState stillCurrent = cache.get(key);
                                        if (deleteError != null || !Boolean.TRUE.equals(deleted) || stillCurrent == null
                                                || stillCurrent.version() != version
                                                || !stillCurrent.ingredients().equals(frozen)) {
                                            if (deleteError != null) {
                                                plugin.getLogger().warning("Brewing state compensation tombstone failed for " + key + ": " + deleteError.getMessage());
                                            }
                                            return;
                                        }
                                        removeCachedState(key, stillCurrent);
                                        refundFailedIngredient(block, key, frozen, ownerUuid, consumed);
                                    }
                                }));
                    }
                }
            });
            return null;
        });
        spawnQueuedParticles(block, frozen.size(), false);
        return true;
    }

    private void refundFailedIngredient(Block block, BlockKey key, List<IngredientEntry> frozen,
                                        UUID ownerUuid, ItemStack consumed) {
        if (ownerUuid == null) {
            plugin.getLogger().severe("Failed brewing persistence at " + key
                    + " has no owner; consumed ingredient requires manual review.");
            return;
        }
        database.queuePendingIngredientRefunds(ownerUuid, frozen)
                .whenComplete((queued, queueError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (queueError != null) {
                        plugin.getLogger().warning("Ingredient refund queue failed for " + key + ": " + queueError.getMessage());
                    }
                    org.bukkit.entity.Player online = Bukkit.getPlayer(ownerUuid);
                    if (online != null && online.isOnline()) {
                        online.sendMessage("§cВарка не сохранена. Ингредиент поставлен в безопасную очередь возврата.");
                    }
                }));
    }

    private void clearState(Block block, BlockKey key, long version) {
        database.tombstoneBrewingState(key, version).whenComplete((applied, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            synchronized (lockFor(key)) {
                CauldronState current = cache.get(key);
                if (error != null || !Boolean.TRUE.equals(applied) || current == null || current.version() != version) {
                    if (error != null) {
                        plugin.getLogger().warning("Brewing state tombstone failed for " + key + ": " + error.getMessage());
                    }
                    return;
                }
                removeCachedState(key, current);
                extinguishRig(block);
                if (configService.clearCauldronOnCompletion()) {
                    block.setType(Material.CAULDRON, false);
                }
            }
        }));
    }

    private void particle(Location location, Particle particle, int count) {
        location.getWorld().spawnParticle(particle, location, count, 0.25D, 0.25D, 0.25D, 0.01D);
    }

    private boolean hasBrewingRig(Block cauldron) {
        Block fire = cauldron.getRelative(BlockFace.DOWN);
        Block fuel = fire.getRelative(BlockFace.DOWN);
        return (fire.getType() == Material.FIRE || fire.getType() == Material.SOUL_FIRE)
                && fuel.getType() == Material.NETHERRACK;
    }

    private boolean isFullWaterLevel(Levelled levelled) {
        int level = levelled.getLevel();
        int max = levelled.getMaximumLevel();
        return level == 0 || level >= max;
    }

    private void extinguishRig(Block cauldron) {
        Block fire = cauldron.getRelative(BlockFace.DOWN);
        if (fire.getType() == Material.FIRE || fire.getType() == Material.SOUL_FIRE) {
            fire.setType(Material.AIR, false);
        }
    }

    private void spawnQueuedParticles(Block cauldron, int ingredientCount, boolean completionBurst) {
        World world = cauldron.getWorld();
        if (world == null) {
            return;
        }
        Location center = cauldron.getLocation().add(0.5D, 0.92D, 0.5D);
        int safeCount = Math.max(1, ingredientCount);
        int loops = completionBurst ? Math.min(10, 3 + safeCount) : Math.min(6, 2 + safeCount);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particle[] palette = completionBurst
                ? new Particle[]{Particle.WITCH, Particle.SMOKE, Particle.SMALL_FLAME, Particle.ENCHANT}
                : new Particle[]{Particle.SMOKE, Particle.SMALL_FLAME, Particle.ENCHANT, Particle.HAPPY_VILLAGER, Particle.PORTAL};
        for (int index = 0; index < loops; index++) {
            Particle particle = palette[random.nextInt(palette.length)];
            double offsetX = random.nextDouble(-0.22D, 0.22D);
            double offsetY = random.nextDouble(0.02D, 0.22D);
            double offsetZ = random.nextDouble(-0.22D, 0.22D);
            int count = completionBurst ? random.nextInt(5, 10) : random.nextInt(2, 5);
            double extra = completionBurst ? 0.02D : 0.005D;
            world.spawnParticle(particle, center.clone().add(offsetX, offsetY, offsetZ), count, 0.04D, 0.05D, 0.04D, extra);
        }
    }

    private void cacheState(BlockKey key, CauldronState state) {
        CauldronState previous = cache.put(key, state);
        if (previous != null && previous != state) {
            removeFromChunkIndex(key);
        }
        cacheByChunk.computeIfAbsent(new ChunkKey(key.world(), key.x() >> 4, key.z() >> 4), ignored -> ConcurrentHashMap.newKeySet())
                .add(key);
        enqueueIntegrityCheck(key);
    }

    private void removeCachedState(BlockKey key, CauldronState expected) {
        if (cache.remove(key, expected)) {
            removeFromChunkIndex(key);
            integrityQueued.remove(key);
        }
    }

    private void removeFromChunkIndex(BlockKey key) {
        ChunkKey chunk = new ChunkKey(key.world(), key.x() >> 4, key.z() >> 4);
        Set<BlockKey> indexed = cacheByChunk.get(chunk);
        if (indexed != null) {
            indexed.remove(key);
            if (indexed.isEmpty()) {
                cacheByChunk.remove(chunk, indexed);
            }
        }
    }

    private void enqueueIntegrityCheck(BlockKey key) {
        if (key != null && integrityQueued.add(key)) {
            integrityQueue.offer(key);
        }
    }

    private Object lockFor(BlockKey key) {
        return lockStripes[Math.floorMod(key.hashCode(), lockStripes.length)];
    }

    private void queueIngredientRefunds(UUID ownerUuid, List<IngredientEntry> ingredients, String reason) {
        if (ownerUuid == null || ingredients == null || ingredients.isEmpty()) {
            return;
        }
        for (IngredientEntry ingredient : ingredients) {
            database.queuePendingRefund(ownerUuid, "INGREDIENT:" + ingredient.serialize(), 1)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            plugin.getLogger().warning("Ingredient mailbox refund failed for " + reason + ": " + error.getMessage());
                        }
                    });
        }
    }

    private UUID parseOwner(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record CauldronState(List<IngredientEntry> ingredients, long version, long updatedAtMillis, UUID ownerUuid) {
        private boolean isStale(long nowMillis) {
            return updatedAtMillis > 0L && nowMillis - updatedAtMillis >= STALE_BREW_STATE_MILLIS;
        }
    }

    private record ChunkKey(String world, int x, int z) {
    }
}
