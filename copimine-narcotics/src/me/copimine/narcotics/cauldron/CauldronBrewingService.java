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
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CauldronBrewingService {
    private static final String DISPLAY_TAG = "copimine_narcotics_cauldron_display";
    private static final long STALE_BREW_STATE_MILLIS = 15L * 60L * 1000L;

    private final CopiMineNarcotics plugin;
    private NarcoticsConfigService configService;
    private final NarcoticsDatabase database;
    private NarcoticsRecipeService recipeService;
    private NarcoticItemFactory itemFactory;
    private final Map<BlockKey, CauldronState> cache = new ConcurrentHashMap<>();
    private final Map<BlockKey, Object> locks = new ConcurrentHashMap<>();
    private final int animationTaskId;

    public CauldronBrewingService(CopiMineNarcotics plugin, NarcoticsConfigService configService, NarcoticsDatabase database, NarcoticsRecipeService recipeService, NarcoticItemFactory itemFactory) {
        this.plugin = plugin;
        this.configService = configService;
        this.database = database;
        this.recipeService = recipeService;
        this.itemFactory = itemFactory;
        this.animationTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFloatingVisuals, 4L, 4L).getTaskId();
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
        database.clearBrewingStates().thenRun(() -> plugin.getLogger().info("Temporary cauldron brew states cleared on enable."))
                .exceptionally(error -> {
                    plugin.getLogger().warning("Brewing cache preload failed: " + error.getMessage());
                    return null;
                });
    }

    public void runIntegritySweep() {
        long nowMillis = System.currentTimeMillis();
        for (Map.Entry<BlockKey, CauldronState> entry : List.copyOf(cache.entrySet())) {
            BlockKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world == null) {
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
            if (!hasFloatingVisuals(block, key)) {
                updateFloatingVisuals(block, key, entry.getValue().ingredients());
            }
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
            if (!base.ingredients().isEmpty() && !hasFloatingVisuals(block, key)) {
                handleCauldronBroken(block, block.getLocation().add(0.5D, 0.7D, 0.5D));
                base = cache.getOrDefault(key, new CauldronState(List.of(), 0L, nowMillis));
            }
            if (!base.ingredients().isEmpty() && !recipeService.canStillBecomeRecipe(base.ingredients())) {
                clearState(block, key, base.version() + 1L);
                base = new CauldronState(List.of(), 0L, nowMillis);
            }
            List<IngredientEntry> current = new ArrayList<>(base.ingredients());
            current.add(ingredient);
            long nextVersion = base.version() + 1L;
            itemFactory.consumeOne(player, stack);

            NarcoticDefinition exact = recipeService.matchExact(current);
            if (exact != null) {
                finishBrewing(block, key, exact, nextVersion);
                return true;
            }
            int minimumRecipeSize = recipeService.minimumRecipeSize();
            int maximumRecipeSize = recipeService.maximumRecipeSize();
            if (current.size() < Math.max(2, minimumRecipeSize)) {
                return queueIngredients(block, key, current, nextVersion, nowMillis);
            }
            boolean canStillBecomeRecipe = recipeService.canStillBecomeRecipe(current);
            if (canStillBecomeRecipe && current.size() < maximumRecipeSize) {
                return queueIngredients(block, key, current, nextVersion, nowMillis);
            }
            if (current.size() < maximumRecipeSize) {
                return queueIngredients(block, key, current, nextVersion, nowMillis);
            }
            if (current.size() >= Math.max(2, minimumRecipeSize)) {
                NarcoticDefinition zhuzevo = configService.items().get("zhuzevo");
                if (zhuzevo != null) {
                    finishBrewing(block, key, zhuzevo, nextVersion);
                } else {
                    clearState(block, key, nextVersion);
                }
                return true;
            }
            return queueIngredients(block, key, current, nextVersion, nowMillis);
        }
    }

    public void handleCauldronBroken(Block block, Location dropLocation) {
        BlockKey key = BlockKey.of(block);
        synchronized (lockFor(key)) {
            CauldronState removed = cache.remove(key);
            if (removed == null || removed.ingredients().isEmpty()) {
                return;
            }
            clearFloatingVisuals(block, key);
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
        cache.keySet().forEach(this::clearFloatingVisuals);
        cache.clear();
        locks.clear();
    }

    public void shutdown() {
        plugin.getServer().getScheduler().cancelTask(animationTaskId);
        cache.keySet().forEach(this::clearFloatingVisuals);
        cache.clear();
        locks.clear();
    }

    private void finishBrewing(Block block, BlockKey key, NarcoticDefinition definition, long version) {
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), itemFactory.createOfficialItem(definition, 1));
        particle(block.getLocation().add(0.5D, 1.0D, 0.5D), Particle.WITCH, "zhuzevo".equals(definition.id()) ? 24 : 12);
        clearState(block, key, version);
    }

    private boolean queueIngredients(Block block, BlockKey key, List<IngredientEntry> current, long version, long nowMillis) {
        List<IngredientEntry> frozen = List.copyOf(current);
        cache.put(key, new CauldronState(frozen, version, nowMillis));
        updateFloatingVisuals(block, key, frozen);
        database.saveBrewingState(key, version, frozen).exceptionally(error -> {
            plugin.getLogger().warning("Brewing state save failed for " + key + ": " + error.getMessage());
            return null;
        });
        particle(block.getLocation().add(0.5D, 0.9D, 0.5D), Particle.SMOKE, 8);
        return true;
    }

    private void clearState(Block block, BlockKey key, long version) {
        cache.remove(key);
        clearFloatingVisuals(block, key);
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

    private void updateFloatingVisuals(Block cauldron, BlockKey key, List<IngredientEntry> ingredients) {
        clearFloatingVisuals(cauldron, key);
        if (ingredients.isEmpty()) {
            return;
        }
        Location center = cauldron.getLocation().add(0.5D, 1.2D, 0.5D);
        int total = ingredients.size();
        for (int index = 0; index < total; index++) {
            ItemStack icon = ingredients.get(index).toItemStack();
            double angle = (Math.PI * 2.0D * index) / Math.max(1, total);
            double radius = total == 1 ? 0.0D : 0.22D;
            Location displayLocation = center.clone().add(Math.cos(angle) * radius, 0.06D * (index % 2), Math.sin(angle) * radius);
            ItemDisplay display = cauldron.getWorld().spawn(displayLocation, ItemDisplay.class, spawned -> {
                spawned.setItemStack(icon);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.addScoreboardTag(DISPLAY_TAG);
                spawned.addScoreboardTag(displayTagFor(key));
            });
            display.setRotation((float) Math.toDegrees(angle), 0.0F);
        }
    }

    private void tickFloatingVisuals() {
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockKey, CauldronState> entry : List.copyOf(cache.entrySet())) {
            BlockKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world == null) {
                continue;
            }
            Block cauldron = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isSupportedCauldron(cauldron)) {
                continue;
            }
            List<ItemDisplay> displays = world.getNearbyEntities(
                            cauldron.getLocation().add(0.5D, 1.0D, 0.5D),
                            1.2D,
                            1.4D,
                            1.2D,
                            entity -> entity instanceof ItemDisplay
                                    && entity.getScoreboardTags().contains(DISPLAY_TAG)
                                    && entity.getScoreboardTags().contains(displayTagFor(key)))
                    .stream()
                    .filter(ItemDisplay.class::isInstance)
                    .map(ItemDisplay.class::cast)
                    .sorted(Comparator.comparing(entity -> entity.getUniqueId().toString()))
                    .toList();
            if (displays.isEmpty()) {
                continue;
            }
            Location center = cauldron.getLocation().add(0.5D, 1.2D, 0.5D);
            int total = Math.max(1, displays.size());
            for (int index = 0; index < displays.size(); index++) {
                ItemDisplay display = displays.get(index);
                double orbit = (now / 700.0D) + ((Math.PI * 2.0D * index) / total);
                double radius = total == 1 ? 0.0D : 0.22D;
                double bob = 0.04D * Math.sin((now / 220.0D) + index);
                Location target = center.clone().add(Math.cos(orbit) * radius, bob + 0.05D * (index % 2), Math.sin(orbit) * radius);
                display.teleport(target);
                display.setRotation((float) Math.toDegrees(orbit) + 90.0F, 0.0F);
            }
        }
    }

    private void clearFloatingVisuals(Block block, BlockKey key) {
        block.getWorld().getNearbyEntities(block.getLocation().add(0.5D, 1.0D, 0.5D), 1.2D, 1.4D, 1.2D, entity ->
                entity instanceof ItemDisplay
                        && entity.getScoreboardTags().contains(DISPLAY_TAG)
                        && entity.getScoreboardTags().contains(displayTagFor(key)))
                .forEach(entity -> entity.remove());
    }

    private void clearFloatingVisuals(BlockKey key) {
        org.bukkit.World world = plugin.getServer().getWorld(key.world());
        if (world == null) {
            return;
        }
        Location center = new Location(world, key.x() + 0.5D, key.y() + 1.0D, key.z() + 0.5D);
        world.getNearbyEntities(center, 1.2D, 1.4D, 1.2D, entity ->
                entity instanceof ItemDisplay
                        && entity.getScoreboardTags().contains(DISPLAY_TAG)
                        && entity.getScoreboardTags().contains(displayTagFor(key)))
                .forEach(entity -> entity.remove());
    }

    private boolean hasFloatingVisuals(Block block, BlockKey key) {
        return !block.getWorld().getNearbyEntities(block.getLocation().add(0.5D, 1.0D, 0.5D), 1.2D, 1.4D, 1.2D, entity ->
                entity instanceof ItemDisplay
                        && entity.getScoreboardTags().contains(DISPLAY_TAG)
                        && entity.getScoreboardTags().contains(displayTagFor(key))).isEmpty();
    }

    private String displayTagFor(BlockKey key) {
        return "copimine_cauldron_" + key.world() + "_" + key.x() + "_" + key.y() + "_" + key.z();
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
