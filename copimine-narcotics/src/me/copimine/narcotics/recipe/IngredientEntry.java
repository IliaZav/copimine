package me.copimine.narcotics.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public record IngredientEntry(
        String recipeKey,
        String originalMaterial,
        String potionEffectId,
        String potionBaseType,
        int amount
) {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String serialize() {
        return encode(recipeKey)
                + "~" + encode(originalMaterial)
                + "~" + encode(potionEffectId)
                + "~" + encode(potionBaseType)
                + "~" + Math.max(1, amount);
    }

    public static IngredientEntry deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!raw.contains("~")) {
            return fromLegacyKey(raw);
        }
        String[] parts = raw.split("~", 5);
        if (parts.length != 5) {
            return fromLegacyKey(raw);
        }
        return new IngredientEntry(
                decode(parts[0]),
                decode(parts[1]),
                decode(parts[2]),
                decode(parts[3]),
                parseInt(parts[4], 1)
        );
    }

    public static IngredientEntry fromLegacyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("MATERIAL:")) {
            String material = key.substring("MATERIAL:".length()).toUpperCase(Locale.ROOT);
            return new IngredientEntry(key, material, "", "", 1);
        }
        if (key.startsWith("POTION:")) {
            String effect = key.substring("POTION:".length()).toUpperCase(Locale.ROOT);
            return new IngredientEntry(key, Material.POTION.name(), effect, effect, 1);
        }
        return null;
    }

    public ItemStack toItemStack() {
        Material material = Material.matchMaterial(originalMaterial == null || originalMaterial.isBlank() ? "AIR" : originalMaterial);
        if (material == null || material == Material.AIR) {
            material = Material.PAPER;
        }
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        if (!recipeKey.startsWith("POTION:")) {
            return stack;
        }
        if (!(stack.getItemMeta() instanceof PotionMeta meta)) {
            return stack;
        }
        PotionType basePotion = parsePotionType(potionBaseType, potionEffectId);
        if (basePotion != null) {
            meta.setBasePotionType(basePotion);
        } else {
            PotionEffectType effectType = potionEffectId == null || potionEffectId.isBlank() ? null : PotionEffectType.getByName(potionEffectId.toUpperCase(Locale.ROOT));
            if (effectType != null) {
                meta.addCustomEffect(new PotionEffect(effectType, 20 * 45, 0), true);
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static PotionType parsePotionType(String rawBaseType, String rawEffectId) {
        if (rawBaseType != null && !rawBaseType.isBlank()) {
            try {
                return PotionType.valueOf(rawBaseType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException parseError) {
            }
        }
        if (rawEffectId == null || rawEffectId.isBlank()) {
            return null;
        }
        PotionEffectType target = PotionEffectType.getByName(rawEffectId.toUpperCase(Locale.ROOT));
        if (target == null) {
            return null;
        }
        for (PotionType type : PotionType.values()) {
            if (!type.getPotionEffects().isEmpty()) {
                PotionEffectType typeEffect = type.getPotionEffects().get(0).getType();
                if (typeEffect != null && typeEffect.equals(target)) {
                    return type;
                }
            }
        }
        return null;
    }

    private static String encode(String value) {
        return ENCODER.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException parseError) {
            return fallback;
        }
    }
}
