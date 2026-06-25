package me.copimine.narcotics.cauldron;

import me.copimine.narcotics.CopiMineNarcotics;
import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.db.NarcoticsDatabase;
import me.copimine.narcotics.item.NarcoticItemFactory;
import me.copimine.narcotics.model.NarcoticDefinition;
import me.copimine.narcotics.recipe.IngredientEntry;
import me.copimine.narcotics.recipe.NarcoticsRecipeService;
import me.copimine.narcotics.util.BlockKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CauldronBrewingService {
    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private NarcoticsRecipeService recipeService;
    private NarcoticItemFactory itemFactory;
    private final Map<BlockKey, CauldronState> cache = new ConcurrentHashMap<>();
    private final Map<BlockKey, Object> locks = new ConcurrentHashMap<>();

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
            return;
        }
        database.loadBrewingStates().thenAccept(states -> {
                    cache.clear();
                    for (Map.Entry<BlockKey, NarcoticsDatabase.LoadedBrewingState> entry : states.entrySet()) {
                        cache.put(entry.getKey(), new CauldronState(entry.getValue().ingredients(), entry.getValue().version()));
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().warning("Brewing cache preload failed: " + error.getMessage());
                    return null;
                });
    }

    public boolean isSupportedCauldron(Block block) {
        if (block == null || block.getType() != Material.WATER_CAULDRON) {
            return false;
        }
        if (!configService.requireFullWater()) {
            return true;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Levelled levelled)) {
            return false;
        }
        return levelled.getLevel() >= levelled.getMaximumLevel();
    }

    public boolean tryAddIngredient(org.bukkit.entity.Player player, Block block, ItemStack stack) {
        if (itemFactory.isOfficialFinishedItem(stack)) {
            return false;
        }
        BlockKey key = BlockKey.of(block);
        IngredientEntry ingredient = recipeService.ingredientEntry(stack);
        if (ingredient == null) {
            return false;
        }
        synchronized (lockFor(key)) {
            CauldronState base = cache.getOrDefault(key, new CauldronState(List.of(), 0L));
            List<IngredientEntry> current = new ArrayList<>(base.ingredients());
            current.add(ingredient);
            long nextVersion = base.version() + 1L;
            itemFactory.consumeOne(player, stack);

            NarcoticDefinition exact = recipeService.matchExact(current);
            if (exact != null) {
                finishBrewing(block, key, exact, nextVersion);
                return true;
            }
            if (!recipeService.canStillBecomeRecipe(current)) {
                NarcoticDefinition zhuzevo = configService.items().get("zhuzevo");
                if (zhuzevo != null) {
                    finishBrewing(block, key, zhuzevo, nextVersion);
                } else {
                    clearState(block, key, nextVersion);
                }
                return true;
            }
            List<IngredientEntry> frozen = List.copyOf(current);
            cache.put(key, new CauldronState(frozen, nextVersion));
            database.saveBrewingState(key, nextVersion, frozen).exceptionally(error -> {
                plugin.getLogger().warning("Brewing state save failed for " + key + ": " + error.getMessage());
                return null;
            });
            particle(block.getLocation().add(0.5D, 0.9D, 0.5D), Particle.SMOKE, 8);
            return true;
        }
    }

    public void handleCauldronBroken(Block block, Location dropLocation) {
        BlockKey key = BlockKey.of(block);
        synchronized (lockFor(key)) {
            CauldronState removed = cache.remove(key);
            if (removed == null || removed.ingredients().isEmpty()) {
                return;
            }
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

    public void handleCauldronLevelChange(Block block, BlockData newState) {
        BlockKey key = BlockKey.of(block);
        if (!cache.containsKey(key)) {
            return;
        }
        boolean stillFull = newState instanceof Levelled levelled && levelled.getLevel() >= levelled.getMaximumLevel() && block.getType() == Material.WATER_CAULDRON;
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
        cache.clear();
        locks.clear();
    }

    private void finishBrewing(Block block, BlockKey key, NarcoticDefinition definition, long version) {
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), itemFactory.createOfficialItem(definition, 1));
        particle(block.getLocation().add(0.5D, 1.0D, 0.5D), Particle.WITCH, "zhuzevo".equals(definition.id()) ? 24 : 12);
        clearState(block, key, version);
    }

    private void clearState(Block block, BlockKey key, long version) {
        cache.remove(key);
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

    private Object lockFor(BlockKey key) {
        return locks.computeIfAbsent(key, ignored -> new Object());
    }

    private record CauldronState(List<IngredientEntry> ingredients, long version) {
    }
}
