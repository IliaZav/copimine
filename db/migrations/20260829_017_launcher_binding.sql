-- Launcher-only schema. Apply only after the production database backup has
-- been verified. This migration creates no player, gameplay, AuthMe, or
-- economy data and does not alter existing tables.
BEGIN;

SET LOCAL search_path TO copimine;

CREATE TABLE IF NOT EXISTS launcher_link_challenges (
    challenge_id TEXT PRIMARY KEY,
    device_id_hash TEXT NOT NULL,
    minecraft_name TEXT NOT NULL DEFAULT '',
    launcher_version TEXT NOT NULL DEFAULT '',
    code_hash TEXT NOT NULL,
    poll_token_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    site_account_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    expires_at BIGINT NOT NULL DEFAULT 0,
    authorized_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS launcher_account_links (
    device_id_hash TEXT PRIMARY KEY,
    site_account_id TEXT NOT NULL,
    minecraft_name TEXT NOT NULL DEFAULT '',
    launcher_version TEXT NOT NULL DEFAULT '',
    linked_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_launcher_link_challenges_expiry
    ON launcher_link_challenges (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_launcher_link_challenges_device
    ON launcher_link_challenges (device_id_hash, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_launcher_account_links_site
    ON launcher_account_links (site_account_id, updated_at DESC);

COMMIT;
