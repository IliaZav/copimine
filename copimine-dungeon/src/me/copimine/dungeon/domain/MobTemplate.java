package me.copimine.dungeon.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enemy template registry (spec OV4.0 #3 / OV4.4).
 *
 * <p>The only <em>new</em> dungeon species are the three vanilla-derived Trial
 * Chambers mobs (Rift Silverfish / Breeze / Bogged). Existing already-authorized
 * elite and End Rift templates may be reused as encounter templates but must
 * not invent additional species.</p>
 */
public enum MobTemplate {
    // D-MOB-01..03: exactly three vanilla-derived dungeon mobs.
    RIFT_SILVERFISH("rift_silverfish", "SILVERFISH", Category.DUNGEON_EXTENSION),
    RIFT_BREEZE("rift_breeze", "BREEZE", Category.DUNGEON_EXTENSION),
    RIFT_BOGGED("rift_bogged", "BOGGED", Category.DUNGEON_EXTENSION),

    // Reusable, already-authorized elite / End Rift actor templates.
    RIFT_GIANT("rift_giant", "ZOMBIE", Category.REUSABLE_EVENT),
    RIFT_RAM("rift_ram", "RAVAGER", Category.REUSABLE_EVENT),
    ARENA_WARDEN("arena_warden", "WARDEN", Category.REUSABLE_EVENT),
    ELITE_ZOMBIE("elite_zombie", "ZOMBIE", Category.REUSABLE_EVENT),
    ELITE_SKELETON("elite_skeleton", "SKELETON", Category.REUSABLE_EVENT),
    ELITE_ENDERMAN("elite_enderman", "ENDERMAN", Category.REUSABLE_EVENT),
    ELITE_SPIDER("elite_spider", "SPIDER", Category.REUSABLE_EVENT),
    ORDINARY_ZOMBIE("zombie", "ZOMBIE", Category.REUSABLE_EVENT),
    ORDINARY_SKELETON("skeleton", "SKELETON", Category.REUSABLE_EVENT),
    ORDINARY_SPIDER("spider", "SPIDER", Category.REUSABLE_EVENT),
    ORDINARY_ENDERMAN("enderman", "ENDERMAN", Category.REUSABLE_EVENT);

    public enum Category {
        DUNGEON_EXTENSION,
        REUSABLE_EVENT
    }

    private final String id;
    private final String baseType;
    private final Category category;

    MobTemplate(String id, String baseType, Category category) {
        this.id = id;
        this.baseType = baseType;
        this.category = category;
    }

    public String id() {
        return id;
    }

    /** Bukkit EntityType base name; the runtime later maps this to a real spawn. */
    public String baseType() {
        return baseType;
    }

    public Category category() {
        return category;
    }

    public boolean isDungeonExtension() {
        return category == Category.DUNGEON_EXTENSION;
    }

    public boolean isReusableEventTemplate() {
        return category == Category.REUSABLE_EVENT;
    }

    public static MobTemplate fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (MobTemplate template : values()) {
            if (template.id.equals(normalized) || template.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return template;
            }
        }
        return null;
    }

    public static List<String> allIds() {
        List<String> ids = new ArrayList<>(values().length);
        for (MobTemplate template : values()) {
            ids.add(template.id);
        }
        return ids;
    }

    public static List<String> dungeonExtensionIds() {
        List<String> ids = new ArrayList<>(3);
        for (MobTemplate template : values()) {
            if (template.isDungeonExtension()) {
                ids.add(template.id);
            }
        }
        return ids;
    }
}
