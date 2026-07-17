CREATE TABLE IF NOT EXISTS president_tax_exemptions (
    id TEXT PRIMARY KEY,
    tax_id TEXT NOT NULL DEFAULT '',
    term_id TEXT NOT NULL DEFAULT '',
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    artifact_instance_id TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'TAX_CLOCK_EXEMPTION',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL DEFAULT 0,
    expires_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tax_exemptions_player
    ON president_tax_exemptions(player_uuid);

CREATE INDEX IF NOT EXISTS idx_tax_exemptions_active
    ON president_tax_exemptions(status, expires_at DESC);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260715_011_copimine_tax_clock_exemptions', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'election-core')
ON CONFLICT (version) DO NOTHING;
