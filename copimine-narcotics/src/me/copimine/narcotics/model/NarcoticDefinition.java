package me.copimine.narcotics.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record NarcoticDefinition(
        String id,
        String displayName,
        Material material,
        Material fallbackMaterial,
        String textureKey,
        int customModelData,
        int overdoseWeight,
        String visualEffectId,
        List<String> recipeIngredients,
        Map<String, Integer> recipeCounts,
        List<ConfiguredEffect> normalEffects,
        List<ConfiguredEffect> overdoseEffects
) {
    public static NarcoticDefinition of(
            String id,
            String displayName,
            Material material,
            Material fallbackMaterial,
            String textureKey,
            int customModelData,
            int overdoseWeight,
            String visualEffectId,
            List<String> recipeIngredients,
            List<ConfiguredEffect> normalEffects,
            List<ConfiguredEffect> overdoseEffects
    ) {
        Map<String, Integer> counts = recipeIngredients.stream()
                .collect(Collectors.toMap(Function.identity(), ingredient -> 1, Integer::sum));
        return new NarcoticDefinition(
                id.toLowerCase(Locale.ROOT),
                displayName,
                material,
                fallbackMaterial,
                textureKey,
                customModelData,
                overdoseWeight,
                visualEffectId.toUpperCase(Locale.ROOT),
                List.copyOf(recipeIngredients),
                Map.copyOf(counts),
                List.copyOf(normalEffects),
                List.copyOf(overdoseEffects)
        );
    }

    public String plainDisplayName() {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName));
    }

    public int maxEffectDurationSeconds(boolean overdose) {
        List<ConfiguredEffect> source = overdose ? overdoseEffects : normalEffects;
        int max = 0;
        for (ConfiguredEffect effect : source) {
            max = Math.max(max, effect.durationSeconds());
        }
        return max;
    }
}
