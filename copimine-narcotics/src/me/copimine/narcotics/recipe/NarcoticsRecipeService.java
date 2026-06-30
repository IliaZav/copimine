package me.copimine.narcotics.recipe;

import me.copimine.narcotics.config.NarcoticsConfigService;
import me.copimine.narcotics.item.NarcoticItemFactory;
import me.copimine.narcotics.model.NarcoticDefinition;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NarcoticsRecipeService {
    private final Set<String> recognizedIngredients = new HashSet<>();
    private NarcoticItemFactory itemFactory;
    private Map<String, NarcoticDefinition> items;

    public NarcoticsRecipeService(NarcoticsConfigService configService, NarcoticItemFactory itemFactory) {
        this.itemFactory = itemFactory;
        reload(configService);
    }

    public void reload(NarcoticsConfigService configService) {
        items = configService.items();
        recognizedIngredients.clear();
        for (NarcoticDefinition definition : items.values()) {
            recognizedIngredients.addAll(definition.recipeIngredients());
        }
    }

    public void setItemFactory(NarcoticItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    public boolean isRecognizedIngredient(ItemStack stack) {
        return ingredientEntry(stack) != null;
    }

    public boolean canEnterCauldron(ItemStack stack) {
        return cauldronIngredientEntry(stack) != null;
    }

    public String ingredientKey(ItemStack stack) {
        IngredientEntry entry = ingredientEntry(stack);
        return entry == null ? null : entry.recipeKey();
    }

    public IngredientEntry ingredientEntry(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        if (itemFactory != null && itemFactory.isOfficialFinishedItem(stack)) {
            return null;
        }
        if (isPotion(stack)) {
            String key = potionIngredientKey(stack);
            if (key != null && recognizedIngredients.contains(key)) {
                return createPotionEntry(stack, key);
            }
        }
        String materialKey = "MATERIAL:" + stack.getType().name();
        return recognizedIngredients.contains(materialKey)
                ? new IngredientEntry(materialKey, stack.getType().name(), "", "", 1)
                : null;
    }

    public IngredientEntry cauldronIngredientEntry(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        if (itemFactory != null && itemFactory.isOfficialFinishedItem(stack)) {
            return null;
        }
        IngredientEntry recognized = ingredientEntry(stack);
        if (recognized != null) {
            return recognized;
        }
        if (isPotion(stack)) {
            return createPotionEntry(stack, genericPotionKey(stack));
        }
        return new IngredientEntry("MATERIAL:" + stack.getType().name(), stack.getType().name(), "", "", 1);
    }

    public NarcoticDefinition matchExact(List<IngredientEntry> ingredientEntries) {
        for (NarcoticDefinition definition : items.values()) {
            if (definition.recipeIngredients().isEmpty()) {
                continue;
            }
            if (definition.recipeIngredients().size() != ingredientEntries.size()) {
                continue;
            }
            if (matchesCounts(definition.recipeCounts(), counts(ingredientEntries))) {
                return definition;
            }
        }
        return null;
    }

    public boolean canStillBecomeRecipe(List<IngredientEntry> ingredientEntries) {
        Map<String, Integer> current = counts(ingredientEntries);
        for (NarcoticDefinition definition : items.values()) {
            if (definition.recipeIngredients().isEmpty()) {
                continue;
            }
            if (ingredientEntries.size() > definition.recipeIngredients().size()) {
                continue;
            }
            boolean valid = true;
            for (Map.Entry<String, Integer> entry : current.entrySet()) {
                if (definition.recipeCounts().getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    public List<ItemStack> ingredientDrops(List<IngredientEntry> ingredientEntries) {
        List<ItemStack> drops = new ArrayList<>();
        for (IngredientEntry entry : ingredientEntries) {
            if (entry == null) {
                continue;
            }
            drops.add(entry.toItemStack());
        }
        return drops;
    }

    private boolean isPotion(ItemStack stack) {
        return stack.getType() == Material.POTION || stack.getType() == Material.SPLASH_POTION || stack.getType() == Material.LINGERING_POTION;
    }

    private String potionIngredientKey(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
            return null;
        }
        if (meta.hasCustomEffects()) {
            for (var effect : meta.getCustomEffects()) {
                String key = "POTION:" + effect.getType().getName().toUpperCase(Locale.ROOT);
                if (recognizedIngredients.contains(key)) {
                    return key;
                }
            }
        }
        PotionType base = meta.getBasePotionType();
        if (base == null) {
            return null;
        }
        if (base.getPotionEffects().isEmpty()) {
            return null;
        }
        PotionEffectType type = base.getPotionEffects().get(0).getType();
        if (type == null || type.getName() == null) {
            return null;
        }
        String key = "POTION:" + type.getName().toUpperCase(Locale.ROOT);
        return recognizedIngredients.contains(key) ? key : null;
    }

    private String genericPotionKey(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
            return "MATERIAL:" + stack.getType().name();
        }
        if (meta.hasCustomEffects()) {
            for (var effect : meta.getCustomEffects()) {
                if (effect.getType().getName() != null) {
                    return "POTION:" + effect.getType().getName().toUpperCase(Locale.ROOT);
                }
            }
        }
        PotionType base = meta.getBasePotionType();
        if (base != null && !base.getPotionEffects().isEmpty()) {
            PotionEffectType type = base.getPotionEffects().get(0).getType();
            if (type != null && type.getName() != null) {
                return "POTION:" + type.getName().toUpperCase(Locale.ROOT);
            }
        }
        return "MATERIAL:" + stack.getType().name();
    }

    private IngredientEntry createPotionEntry(ItemStack stack, String recipeKey) {
        String originalMaterial = stack.getType().name();
        String potionBaseType = "";
        String effectId = "";
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            if (meta.getBasePotionType() != null) {
                potionBaseType = meta.getBasePotionType().name();
                if (!meta.getBasePotionType().getPotionEffects().isEmpty()) {
                    PotionEffectType effect = meta.getBasePotionType().getPotionEffects().get(0).getType();
                    if (effect != null && effect.getName() != null) {
                        effectId = effect.getName().toUpperCase(Locale.ROOT);
                    }
                }
            }
            if (effectId.isBlank() && meta.hasCustomEffects()) {
                for (var effect : meta.getCustomEffects()) {
                    if (effect.getType().getName() != null) {
                        effectId = effect.getType().getName().toUpperCase(Locale.ROOT);
                        break;
                    }
                }
            }
        }
        if (effectId.isBlank() && recipeKey.startsWith("POTION:")) {
            effectId = recipeKey.substring("POTION:".length());
        }
        return new IngredientEntry(recipeKey, originalMaterial, effectId, potionBaseType, 1);
    }

    private Map<String, Integer> counts(List<IngredientEntry> ingredientEntries) {
        java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
        for (IngredientEntry entry : ingredientEntries) {
            if (entry != null) {
                map.merge(entry.recipeKey(), Math.max(1, entry.amount()), Integer::sum);
            }
        }
        return map;
    }

    private boolean matchesCounts(Map<String, Integer> left, Map<String, Integer> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            if (!right.getOrDefault(entry.getKey(), 0).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
