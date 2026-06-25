CREATE TABLE IF NOT EXISTS artifact_items_catalog (
    item_id TEXT PRIMARY KEY,
    category TEXT NOT NULL DEFAULT '',
    material TEXT NOT NULL DEFAULT '',
    display_name TEXT NOT NULL DEFAULT '',
    rarity TEXT NOT NULL DEFAULT '',
    price_ar BIGINT NOT NULL DEFAULT 0,
    supply_limit INTEGER NOT NULL DEFAULT 0,
    per_player_limit INTEGER NOT NULL DEFAULT 0,
    cooldown_seconds INTEGER NOT NULL DEFAULT 0,
    effect_name TEXT NOT NULL DEFAULT 'NONE',
    custom_model_data INTEGER NOT NULL DEFAULT 0,
    effect_chance_percent INTEGER NOT NULL DEFAULT 100,
    visual_effect_id TEXT NOT NULL DEFAULT '',
    lore_json TEXT NOT NULL DEFAULT '[]',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS custom_model_data INTEGER NOT NULL DEFAULT 0;
ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS effect_chance_percent INTEGER NOT NULL DEFAULT 100;
ALTER TABLE artifact_items_catalog ADD COLUMN IF NOT EXISTS visual_effect_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_artifact_items_catalog_category ON artifact_items_catalog(category);
CREATE INDEX IF NOT EXISTS idx_artifact_items_catalog_enabled ON artifact_items_catalog(enabled);
