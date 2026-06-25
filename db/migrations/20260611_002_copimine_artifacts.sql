CREATE TABLE IF NOT EXISTS artifact_items_catalog(
    item_id TEXT PRIMARY KEY,
    category TEXT NOT NULL,
    material TEXT NOT NULL,
    display_name TEXT NOT NULL,
    rarity TEXT NOT NULL,
    price_ar BIGINT NOT NULL,
    cooldown_seconds INTEGER NOT NULL DEFAULT 0,
    effect_name TEXT NOT NULL DEFAULT 'NONE',
    lore_json TEXT NOT NULL DEFAULT '[]',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_item_instances(
    unique_item_id TEXT PRIMARY KEY,
    item_id TEXT NOT NULL,
    owner_uuid TEXT NOT NULL,
    purchase_id TEXT NOT NULL,
    status TEXT NOT NULL,
    repaired_count INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_shops(
    shop_id TEXT PRIMARY KEY,
    world_name TEXT NOT NULL,
    block_x INTEGER NOT NULL,
    block_y INTEGER NOT NULL,
    block_z INTEGER NOT NULL,
    title TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_purchases(
    purchase_id TEXT PRIMARY KEY,
    unique_item_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    item_id TEXT NOT NULL,
    shop_id TEXT NOT NULL,
    price_ar BIGINT NOT NULL,
    bank_tx_id TEXT NOT NULL DEFAULT '',
    idempotency_key TEXT NOT NULL,
    status TEXT NOT NULL,
    delivery_mode TEXT NOT NULL DEFAULT 'DIRECT',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_repairs(
    repair_id TEXT PRIMARY KEY,
    unique_item_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    item_id TEXT NOT NULL,
    repair_cost_ar BIGINT NOT NULL,
    bank_tx_id TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_suspicious_events(
    event_id TEXT PRIMARY KEY,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    event_type TEXT NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_audit_log(
    audit_id TEXT PRIMARY KEY,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    target_id TEXT NOT NULL,
    details TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS artifact_pending_deliveries(
    delivery_id TEXT PRIMARY KEY,
    purchase_id TEXT NOT NULL,
    unique_item_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    item_id TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_artifact_shops_block ON artifact_shops(world_name,block_x,block_y,block_z);
CREATE INDEX IF NOT EXISTS idx_artifact_purchases_player_time ON artifact_purchases(player_uuid,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_instances_item ON artifact_item_instances(item_id,status);
CREATE INDEX IF NOT EXISTS idx_artifact_pending_player ON artifact_pending_deliveries(player_uuid,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_repairs_player ON artifact_repairs(player_uuid,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_suspicious_time ON artifact_suspicious_events(created_at DESC,event_type);

INSERT INTO schema_migrations(version, applied_at, component)
VALUES('20260611_002_copimine_artifacts', EXTRACT(EPOCH FROM NOW())::BIGINT, 'copimine-artifacts')
ON CONFLICT(version) DO NOTHING;
