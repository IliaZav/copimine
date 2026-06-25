CREATE TABLE IF NOT EXISTS narcotics_schema_version (
  version INTEGER PRIMARY KEY,
  applied_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS narcotics_brewing_states (
  world_name TEXT NOT NULL,
  x INTEGER NOT NULL,
  y INTEGER NOT NULL,
  z INTEGER NOT NULL,
  ingredients_csv TEXT NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (world_name, x, y, z)
);

CREATE TABLE IF NOT EXISTS narcotics_player_overdose (
  player_uuid TEXT PRIMARY KEY,
  current_scale INTEGER NOT NULL DEFAULT 0,
  last_consumed_at BIGINT NOT NULL DEFAULT 0,
  overdose_until BIGINT NOT NULL DEFAULT 0,
  inverted_movement_until BIGINT NOT NULL DEFAULT 0,
  last_item_id TEXT NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS narcotics_player_usage_window (
  player_uuid TEXT PRIMARY KEY,
  window_started_at BIGINT NOT NULL DEFAULT 0,
  last_item_id TEXT NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS narcotics_config_values (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS narcotics_admin_audit (
  id TEXT PRIMARY KEY,
  actor TEXT NOT NULL DEFAULT '',
  action TEXT NOT NULL DEFAULT '',
  details TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS narcotics_item_texture_migrations (
  id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL DEFAULT '',
  texture_mode TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_narcotics_brewing_updated
ON narcotics_brewing_states(updated_at);

CREATE INDEX IF NOT EXISTS idx_narcotics_admin_audit_created
ON narcotics_admin_audit(created_at);
