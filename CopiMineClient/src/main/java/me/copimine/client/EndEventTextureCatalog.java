package me.copimine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Single source of truth for the client-side End Rift entity textures.
 * Server resource packs cannot replace one entity texture by UUID, so these
 * assets deliberately live in CopiMineClient and are selected only after the
 * server has bound the matching entity UUID.
 */
public final class EndEventTextureCatalog {
    private static final Map<String, Identifier> VISUAL_TEXTURES = createVisualTextures();
    private static final Set<String> LOGGED_LOOKUPS = new HashSet<>();

    private EndEventTextureCatalog() {
    }

    public static Identifier textureForVisual(String visualId) {
        if (visualId == null || visualId.isBlank()) {
            return null;
        }
        return VISUAL_TEXTURES.get(visualId.trim());
    }

    public static boolean isAvailable(Identifier texture) {
        if (texture == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.getResourceManager() != null
                && client.getResourceManager().getResource(texture).isPresent();
    }

    public static void logLookup(String context, Identifier texture) {
        if (texture == null) {
            return;
        }
        String key = String.valueOf(context) + "|" + texture;
        if (LOGGED_LOOKUPS.add(key)) {
            CopiMineClientLogger.info("End Rift texture lookup context=" + context
                    + " texture=" + texture + " resourcePresent=" + isAvailable(texture));
        }
    }

    public static List<String> diagnosticLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Identifier> entry : VISUAL_TEXTURES.entrySet()) {
            lines.add(entry.getKey() + " -> " + entry.getValue()
                    + " / present=" + isAvailable(entry.getValue()));
        }
        return List.copyOf(lines);
    }

    public static Map<String, Identifier> visualTextures() {
        return Map.copyOf(VISUAL_TEXTURES);
    }

    private static Map<String, Identifier> createVisualTextures() {
        Map<String, Identifier> textures = new LinkedHashMap<>();
        textures.put("END_RIFT_ENDERMAN_V1", entityTexture("end_rift_enderman.png"));
        textures.put("END_RIFT_ELITE_V1", entityTexture("end_rift_elite.png"));
        textures.put("END_RIFT_SPIDER_V1", entityTexture("end_rift_spider.png"));
        textures.put("END_RIFT_SHULKER_V1", entityTexture("end_rift_shulker.png"));
        textures.put("END_RIFT_SKELETON_V1", entityTexture("end_rift_skeleton.png"));
        textures.put("END_RIFT_ELITE_SKELETON_V1", entityTexture("end_rift_elite_skeleton.png"));
        textures.put("END_RIFT_OBELISK_FULL_V1", entityTexture("end_event_rift_obelisk_full_hd.png"));
        textures.put("END_RIFT_OBELISK_DAMAGED_V1", entityTexture("end_event_rift_obelisk_damaged_hd.png"));
        textures.put("END_RIFT_OBELISK_CRITICAL_V1", entityTexture("end_event_rift_obelisk_critical_hd.png"));
        textures.put("END_RIFT_FIREBALL_V1", entityTexture("end_event_rift_fireball_hd.png"));
        return Map.copyOf(textures);
    }

    public static Identifier entityTexture(String fileName) {
        return Identifier.of("copimineclient", "textures/entity/" + fileName);
    }
}
