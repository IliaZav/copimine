package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Formats the Core resource checklist without exposing Bukkit material keys. */
public final class ResourceProgressFormatter {
    private ResourceProgressFormatter() {
    }

    public static String format(Map<String, Integer> requirements, Map<String, Integer> deposited) {
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            int current = deposited.getOrDefault(entry.getKey(), 0);
            values.add(coloredLabel(entry.getKey()) + " §f" + current + "/" + entry.getValue());
        }
        return String.join(", ", values);
    }

    private static String coloredLabel(String key) {
        return switch (key.toUpperCase()) {
            case "DIAMOND" -> "§bАлмазы";
            case "ENDER_EYE" -> "§aОко Эндера";
            case "AMETHYST_SHARD" -> "§dОсколки аметиста";
            case "BLAZE_ROD" -> "§6Огненные стержни";
            default -> "§f" + key;
        };
    }
}
