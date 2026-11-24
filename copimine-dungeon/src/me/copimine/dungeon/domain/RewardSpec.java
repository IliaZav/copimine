package me.copimine.dungeon.domain;

import java.util.Objects;

/**
 * Configured reward definition (spec OV4.4). Loot is committed exactly once
 * after the gate opens; an unsafe anchor fails validation rather than silently
 * dropping loot into a wall.
 */
public record RewardSpec(RewardMode mode, String anchorId, String lootTableId) {

    public RewardSpec {
        Objects.requireNonNull(mode, "mode");
        anchorId = anchorId == null ? "" : anchorId.trim();
        lootTableId = lootTableId == null ? "" : lootTableId.trim();
        if (mode == RewardMode.CHEST && anchorId.isEmpty()) {
            throw new IllegalArgumentException("Режим CHEST требует безопасный якорь награды.");
        }
    }

    public static RewardSpec chest(String anchorId, String lootTableId) {
        return new RewardSpec(RewardMode.CHEST, anchorId, lootTableId);
    }

    public static RewardSpec personal(String lootTableId) {
        return new RewardSpec(RewardMode.PERSONAL, "", lootTableId);
    }
}
