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

public final class CauldronBrewingService {
    private static final long STALE_BREW_STATE_MILLIS = 15L * 60L * 1000L;
    private static final int MINIMUM_RECIPE_CHECK_SIZE = 3;
    private static final int MAX_CACHED_STATES = 10_000;
    private static final double WRONG_MIX_DAMAGE_RADIUS = 6.0D;
    // Bukkit damage uses half-hearts: 14..20 means 7..10 hearts.
    private static final double WRONG_MIX_MIN_DAMAGE = 14.0D;
    private static final double WRONG_MIX_MAX_DAMAGE = 20.0D;

    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private NarcoticsRecipeService recipeService;
    private NarcoticItemFactory itemFactory;
    private final Map<BlockKey, CauldronState> cache = new ConcurrentHashMap<>();
    private final Map<BlockKey, Object> locks = new ConcurrentHashMap<>();
    private final Set<BlockKey> completionInFlight = ConcurrentHashMap.newKeySet();
    private final Set<BlockKey> persistenceRecoveryInFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean cacheReady = false;

    public CauldronBrewingService(CopiMineNarcotics plugin, NarcoticsConfigService configService, NarcoticsDatabase database, NarcoticsRecipeService recipeService, NarcoticItemFactory itemFactory) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.recipeService = recipeService;
        this.itemFactory = itemFactory;
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
                    for (Map.Entry<BlockKey, NarcoticsDatabase.LoadedBrewingState> entry : states.entrySet()) {
                        NarcoticsDatabase.LoadedBrewingState loaded = entry.getValue();
                        long updatedAtMillis = loaded.updatedAtEpochMillis();
                        if (updatedAtMillis > 0L && updatedAtMillis < 10_000_000_000L) {
                            updatedAtMillis *= 1000L;
                        }
                        CauldronState restored = new CauldronState(List.copyOf(loaded.ingredients()), loaded.version(), updatedAtMillis);
                        cache.merge(entry.getKey(), restored, (current, candidate) -> current.version() >= candidate.version() ? current : candidate);
                    }
                    cacheReady = true;
                    plugin.getLogger().info("Restored " + states.size() + " pending cauldron brew state(s).");
                }))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Brewing state restore failed: " + error.getMessage());
                    Bukkit.getScheduler().runTaskLater(plugin, this::loadBrewingCache, 100L);
                    return null;
                });
    }

    public void runIntegritySweep() {
        long nowMillis = System.currentTimeMillis();
        for (Map.Entry<BlockKey, CauldronState> entry : List.copyOf(cache.entrySet())) {
            BlockKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isSupportedCauldron(block)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D));
                continue;
            }
            if (entry.getValue().isStale(nowMillis)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D));
                continue;
            }
            spawnQueuedParticles(block, entry.getValue().ingredients().size(), false);
        }
    }

    public void reconcileLoadedChunk(String worldName, int chunkX, int chunkZ) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        for (Map.Entry<BlockKey, CauldronState> entry : List.copyOf(cache.entrySet())) {
            BlockKey key = entry.getKey();
            if (!worldName.equals(key.world()) || (key.x() >> 4) != chunkX || (key.z() >> 4) != chunkZ) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isSupportedCauldron(block) || entry.getValue().isStale(nowMillis)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D));
                continue;
            }
            spawnQueuedParticles(block, entry.getValue().ingredients().size(), false);
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
        if (completionInFlight.contains(key) || persistenceRecoveryInFlight.contains(key)) {
            player.sendMessage("§eЭта варка уже обрабатывается. Попробуйте снова через несколько секунд.");
            return false;
        }
        IngredientEntry ingredient = recipeService.cauldronIngredientEntry(stack);
        if (ingredient == null) {
            return false;
        }
        synchronized (lockFor(key)) {
            long nowMillis = System.currentTimeMillis();
            CauldronState base = cache.getOrDefault(key, new CauldronState(List.of(), 0L, nowMillis));
            List<IngredientEntry> current = new ArrayList<>(base.ingredients());
            current.add(ingredient);
            long nextVersion = base.version() + 1L;
            itemFactory.consumeOne(player, stack);

            NarcoticDefinition exact = recipeService.matchExact(current);
            if (current.size() >= MINIMUM_RECIPE_CHECK_SIZE && exact != null) {
                finishBrewing(block, key, exact, nextVersion, current.size(), false, player, ingredient);
                return true;
            }
            int maximumRecipeSize = recipeService.maximumRecipeSize();
            if (current.size() < MINIMUM_RECIPE_CHECK_SIZE) {
                return queueIngredients(block, key, current, nextVersion, nowMillis, base, ingredient, player);
            }
            // The cauldron is a bounded input buffer.  Do not turn a partial
            // mixture into Zhuzevo at the minimum recipe size: a longer
            // recipe must be allowed to receive its final ingredient even if
            // one submitted stack used a representation that is not currently
            // recognized by the recipe matcher.
            if (current.size() < maximumRecipeSize) {
                return queueIngredients(block, key, current, nextVersion, nowMillis, base, ingredient, player);
            }
            return finishWrongMix(block, key, nextVersion, current.size(), player);
        }
    }

    public void handleCauldronBroken(Block block, Location dropLocation) {
        BlockKey key = BlockKey.of(block);
        synchronized (lockFor(key)) {
            CauldronState removed = cache.remove(key);
            if (removed == null || removed.ingredients().isEmpty()) {
                return;
            }
            spawnQueuedParticles(block, removed.ingredients().size(), true);
            if (configService.dropIngredientsOnBreakOrWaterLoss()) {
                for (ItemStack drop : recipeService.ingredientDrops(removed.ingredients())) {
                    block.getWorld().dropItemNaturally(dropLocation, drop);
                }
            }
            database.deleteBrewingState(key, removed.version() + 1L).exceptionally(error -> {
                plugin.getLogger().warning("Brewing state delete failed for " + key + ": " + error.getMessage());
                return null;
            });
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
        locks.clear();
    }

    public void shutdown() {
        cacheReady = false;
        cache.clear();
        locks.clear();
    }

    private boolean finishWrongMix(Block block, BlockKey key, long version, int ingredientCount, org.bukkit.entity.Player initiator) {
        NarcoticDefinition zhuzevo = configService.items().get("zhuzevo");
        if (zhuzevo != null) {
            finishBrewing(block, key, zhuzevo, version, ingredientCount, true, initiator);
        } else {
            clearState(block, key, version);
        }
        return true;
    }

    private void finishBrewing(Block block, BlockKey key, NarcoticDefinition definition, long version, int ingredientCount, boolean wrongMix, org.bukkit.entity.Player initiator) {
        finishBrewing(block, key, definition, version, ingredientCount, wrongMix, initiator, null);
    }

    private void finishBrewing(Block block, BlockKey key, NarcoticDefinition definition, long version,
                               int ingredientCount, boolean wrongMix, org.bukkit.entity.Player initiator,
                               IngredientEntry finalIngredient) {
        if (!completionInFlight.add(key)) {
            return;
        }
        long expectedVersion = Math.max(0L, version - 1L);
        java.util.UUID ownerUuid = initiator == null ? null : initiator.getUniqueId();
        String outputId = database.brewingOutputId(key, expectedVersion, ownerUuid, definition.id());
        database.completeBrewingState(key, expectedVersion, ownerUuid, definition.id()).whenComplete((applied, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || !Boolean.TRUE.equals(applied)) {
                        database.brewingCompletionResolved(key, expectedVersion).whenComplete((resolved, resolveError) ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (resolveError != null) {
                                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                                "Brewing database completion status is unavailable for " + key,
                                                resolveError);
                                        completionInFlight.remove(key);
                                        return;
                                    }
                                    if (Boolean.TRUE.equals(resolved)) {
                                        materializeCompletedBrew(block, key, definition, expectedVersion,
                                                ingredientCount, wrongMix, initiator, outputId);
                                        return;
                                    }
                                    plugin.getLogger().warning("Brewing database completion failed for " + key
                                            + ": " + (error == null ? "state version changed" : error.getMessage()));
                                    if (finalIngredient != null && initiator != null) {
                                        database.queuePendingIngredientRefunds(initiator.getUniqueId(), List.of(finalIngredient))
                                                .exceptionally(refundError -> {
                                                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                                            "Unable to queue failed brewing ingredient refund for " + key,
                                                            refundError);
                                                    return null;
                                                });
                                    }
                                    completionInFlight.remove(key);
                                }));
                    } else {
                        materializeCompletedBrew(block, key, definition, expectedVersion,
                                ingredientCount, wrongMix, initiator, outputId);
                    }
                }));
    }

    private void materializeCompletedBrew(Block block, BlockKey key, NarcoticDefinition definition,
                                          long expectedVersion, int ingredientCount, boolean wrongMix,
                                          org.bukkit.entity.Player initiator, String outputId) {
        if (wrongMix) {
            simulateWrongMixExplosion(block, initiator);
        }
        Location dropLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
        var dropped = plugin.dropCompletedBrewingOutput(dropLocation, definition, outputId);
        if (dropped == null) {
            plugin.getLogger().warning("Brewing output could not be materialized for " + key
                    + "; durable output row remains recoverable.");
        } else {
            database.markBrewingOutputWorldDropped(outputId).exceptionally(markError -> {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Unable to mark brewing output as WORLD_DROPPED " + outputId, markError);
                return null;
            });
        }
        particle(dropLocation, Particle.WITCH, "zhuzevo".equals(definition.id()) ? 24 : 12);
        spawnQueuedParticles(block, Math.max(1, ingredientCount), true);
        clearCompletedState(block, key, expectedVersion + 1L);
        completionInFlight.remove(key);
    }

    private void simulateWrongMixExplosion(Block block, org.bukkit.entity.Player initiator) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 1.0D, 0.5D);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.85F);
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        world.spawnParticle(Particle.SMOKE, center, 42, 0.55D, 0.35D, 0.55D, 0.03D);
        double radiusSquared = WRONG_MIX_DAMAGE_RADIUS * WRONG_MIX_DAMAGE_RADIUS;
        for (org.bukkit.entity.Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distanceSquared(center) <= radiusSquared) {
                nearby.damage(ThreadLocalRandom.current().nextDouble(
                        WRONG_MIX_MIN_DAMAGE, Math.nextUp(WRONG_MIX_MAX_DAMAGE)), initiator);
            }
        }
    }

    private boolean queueIngredients(Block block, BlockKey key, List<IngredientEntry> current, long version,
                                     long nowMillis, CauldronState previous,
                                     IngredientEntry addedIngredient, org.bukkit.entity.Player owner) {
        List<IngredientEntry> frozen = List.copyOf(current);
        cache.put(key, new CauldronState(frozen, version, nowMillis));
        database.saveBrewingState(key, version, frozen).exceptionally(error -> {
            if (!persistenceRecoveryInFlight.add(key)) {
                return null;
            }
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Brewing database save failed for " + key + ": " + error.getMessage(), error);
            synchronized (lockFor(key)) {
                CauldronState currentState = cache.get(key);
                if (currentState == null || currentState.version() != version) {
                    persistenceRecoveryInFlight.remove(key);
                    return null;
                }
                if (previous.ingredients().isEmpty()) {
                    cache.remove(key, currentState);
                } else {
                    cache.put(key, previous);
                }
            }
            database.tombstoneBrewingState(key, version).whenComplete((tombstoned, tombstoneError) -> {
                if (tombstoneError != null) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "Brewing database tombstone failed for " + key, tombstoneError);
                } else if (addedIngredient != null && owner != null) {
                    database.queuePendingIngredientRefunds(owner.getUniqueId(), List.of(addedIngredient))
                            .exceptionally(refundError -> {
                                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                        "Unable to queue failed brewing ingredient refund for " + key, refundError);
                                return null;
                            });
                }
                persistenceRecoveryInFlight.remove(key);
            });
            return null;
        });
        spawnQueuedParticles(block, frozen.size(), false);
        return true;
    }

    private void clearCompletedState(Block block, BlockKey key, long completedVersion) {
        synchronized (lockFor(key)) {
            CauldronState current = cache.get(key);
            if (current != null && current.version() <= completedVersion) {
                cache.remove(key, current);
            }
        }
        extinguishRig(block);
        if (configService.clearCauldronOnCompletion()) {
            block.setType(Material.CAULDRON, false);
        }
    }

    private void clearState(Block block, BlockKey key, long version) {
        cache.remove(key);
        extinguishRig(block);
        if (configService.clearCauldronOnCompletion()) {
            block.setType(Material.CAULDRON, false);
        }
        database.deleteBrewingState(key, version).exceptionally(error -> {
            plugin.getLogger().warning("Brewing state tombstone failed for " + key + ": " + error.getMessage());
            return null;
        });
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

    private Object lockFor(BlockKey key) {
        return locks.computeIfAbsent(key, ignored -> new Object());
    }

    private record CauldronState(List<IngredientEntry> ingredients, long version, long updatedAtMillis) {
        private boolean isStale(long nowMillis) {
            return updatedAtMillis > 0L && nowMillis - updatedAtMillis >= STALE_BREW_STATE_MILLIS;
        }
    }
}
