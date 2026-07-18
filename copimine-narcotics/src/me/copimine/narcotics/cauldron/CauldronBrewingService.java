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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

public final class CauldronBrewingService {
    private static final long STALE_BREW_STATE_MILLIS = 15L * 60L * 1000L;
    private static final int MINIMUM_RECIPE_CHECK_SIZE = 3;

    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private NarcoticsRecipeService recipeService;
    private NarcoticItemFactory itemFactory;
    private final Map<BlockKey, CauldronState> cache = new ConcurrentHashMap<>();
    private final Map<BlockKey, Object> locks = new ConcurrentHashMap<>();
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
        database.loadBrewingStates().thenAccept(states -> Bukkit.getScheduler().runTask(plugin, () -> {
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
                finishBrewing(block, key, exact, nextVersion, current.size(), false, player);
                return true;
            }
            int maximumRecipeSize = recipeService.maximumRecipeSize();
            boolean canStillBecomeRecipe = recipeService.canStillBecomeRecipe(current);
            if (current.size() < MINIMUM_RECIPE_CHECK_SIZE) {
                return queueIngredients(block, key, current, nextVersion, nowMillis);
            }
            if (recipeService.containsUnrecognizedIngredient(current)) {
                return finishWrongMix(block, key, nextVersion, current.size(), player);
            }
            if (canStillBecomeRecipe && current.size() < maximumRecipeSize) {
                return queueIngredients(block, key, current, nextVersion, nowMillis);
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
        if (wrongMix) {
            simulateWrongMixExplosion(block, initiator);
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), itemFactory.createOfficialItem(definition, 1));
        particle(block.getLocation().add(0.5D, 1.0D, 0.5D), Particle.WITCH, "zhuzevo".equals(definition.id()) ? 24 : 12);
        spawnQueuedParticles(block, Math.max(1, ingredientCount), true);
        clearState(block, key, version);
    }

    private void simulateWrongMixExplosion(Block block, org.bukkit.entity.Player initiator) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 1.0D, 0.5D);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.85F);
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        world.spawnParticle(Particle.SMOKE, center, 42, 0.55D, 0.35D, 0.55D, 0.03D);
        for (org.bukkit.entity.Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distanceSquared(center) <= 25.0D) {
                nearby.damage(ThreadLocalRandom.current().nextDouble(6.0D, 15.0001D), initiator);
            }
        }
    }

    private boolean queueIngredients(Block block, BlockKey key, List<IngredientEntry> current, long version, long nowMillis) {
        List<IngredientEntry> frozen = List.copyOf(current);
        cache.put(key, new CauldronState(frozen, version, nowMillis));
        database.saveBrewingState(key, version, frozen).exceptionally(error -> {
            plugin.getLogger().warning("Brewing state save failed for " + key + ": " + error.getMessage());
            return null;
        });
        spawnQueuedParticles(block, frozen.size(), false);
        return true;
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
