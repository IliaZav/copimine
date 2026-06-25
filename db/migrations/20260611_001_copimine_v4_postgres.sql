CREATE SCHEMA IF NOT EXISTS copimine;
SET search_path TO copimine;

CREATE TABLE IF NOT EXISTS cmv4_schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at BIGINT NOT NULL,
    component TEXT NOT NULL DEFAULT 'migration'
);

CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at BIGINT NOT NULL,
    component TEXT NOT NULL DEFAULT 'migration'
);

CREATE TABLE IF NOT EXISTS cmv4_audit_events (
    id BIGSERIAL PRIMARY KEY,
    time BIGINT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT '',
    admin_only INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT 'system'
);

CREATE TABLE IF NOT EXISTS cmv4_players (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL DEFAULT '',
    display_name TEXT NOT NULL DEFAULT '',
    first_seen BIGINT NOT NULL DEFAULT 0,
    last_seen BIGINT NOT NULL DEFAULT 0,
    last_quit BIGINT NOT NULL DEFAULT 0,
    online INTEGER NOT NULL DEFAULT 0,
    last_world TEXT NOT NULL DEFAULT '',
    last_x INTEGER DEFAULT 0,
    last_y INTEGER DEFAULT 0,
    last_z INTEGER DEFAULT 0,
    client_brand TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cm_admin_users (
    username TEXT PRIMARY KEY,
    username_norm TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'admin',
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT '',
    updated_at INTEGER NOT NULL DEFAULT 0,
    updated_by TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'db'
);

CREATE TABLE IF NOT EXISTS cm_admin_sessions (
    jti TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'admin',
    token_hash TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    ip TEXT NOT NULL DEFAULT '',
    user_agent TEXT NOT NULL DEFAULT '',
    revoked_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS applications (
    id TEXT PRIMARY KEY,
    election_id TEXT,
    applicant_uuid TEXT,
    applicant_name TEXT,
    statement TEXT,
    submitted_at INTEGER,
    status TEXT DEFAULT 'PENDING',
    reviewed_by TEXT DEFAULT '',
    reviewed_at INTEGER DEFAULT 0,
    verdict_reason TEXT DEFAULT '',
    visible_in_game INTEGER DEFAULT 1,
    deleted_by TEXT DEFAULT '',
    deleted_at INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS admin_requests (
    id TEXT PRIMARY KEY,
    player_uuid TEXT,
    player_name TEXT,
    message TEXT,
    status TEXT DEFAULT 'OPEN',
    created_at INTEGER DEFAULT 0,
    updated_at INTEGER DEFAULT 0,
    assigned_to TEXT DEFAULT '',
    closed_by TEXT DEFAULT '',
    close_reason TEXT DEFAULT '',
    snapshot TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cmv4_account_links (
    id BIGSERIAL PRIMARY KEY,
    minecraft_uuid TEXT NOT NULL,
    discord_id TEXT NOT NULL DEFAULT '',
    site_user_id TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    linked_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cmv4_bank_accounts (
    account_id TEXT PRIMARY KEY,
    owner_uuid TEXT NOT NULL,
    owner_name TEXT NOT NULL DEFAULT '',
    account_type TEXT NOT NULL DEFAULT 'PLAYER',
    currency TEXT NOT NULL DEFAULT 'AR',
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cmv4_bank_ledger (
    tx_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    counterparty_account_id TEXT NOT NULL DEFAULT '',
    player_uuid TEXT NOT NULL DEFAULT '',
    tx_type TEXT NOT NULL,
    amount BIGINT NOT NULL CHECK (amount >= 0),
    balance_after BIGINT NOT NULL DEFAULT 0,
    idempotency_key TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'COMMITTED',
    created_at BIGINT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cmv4_bank_transfers (
    tx_id TEXT PRIMARY KEY,
    from_account_id TEXT NOT NULL,
    to_account_id TEXT NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL DEFAULT 'AR',
    status TEXT NOT NULL DEFAULT 'COMMITTED',
    idempotency_key TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS site_accounts (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    username_norm TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'player',
    enabled INTEGER NOT NULL DEFAULT 1,
    minecraft_uuid TEXT NOT NULL DEFAULT '',
    minecraft_name TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0,
    last_login_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_web_accounts (
    id TEXT PRIMARY KEY,
    site_account_id TEXT NOT NULL,
    minecraft_uuid TEXT NOT NULL DEFAULT '',
    minecraft_name TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS minecraft_account_links (
    minecraft_uuid TEXT PRIMARY KEY,
    minecraft_name TEXT NOT NULL DEFAULT '',
    site_account_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    linked_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS whitelist_account_links (
    minecraft_uuid TEXT PRIMARY KEY,
    minecraft_name TEXT NOT NULL DEFAULT '',
    site_account_id TEXT NOT NULL,
    whitelisted INTEGER NOT NULL DEFAULT 1,
    synced_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS one_time_link_codes (
    id TEXT PRIMARY KEY,
    site_account_id TEXT NOT NULL,
    minecraft_name TEXT NOT NULL,
    minecraft_uuid TEXT NOT NULL DEFAULT '',
    code_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    used_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS login_sessions (
    jti TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'player',
    token_hash TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    revoked_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS password_hashes (
    account_id TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_profile_cache (
    minecraft_uuid TEXT PRIMARY KEY,
    minecraft_name TEXT NOT NULL DEFAULT '',
    display_name TEXT NOT NULL DEFAULT '',
    online INTEGER NOT NULL DEFAULT 0,
    last_seen BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0,
    profile_json TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS player_last_seen (
    minecraft_uuid TEXT PRIMARY KEY,
    minecraft_name TEXT NOT NULL DEFAULT '',
    last_seen BIGINT NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS player_settings (
    minecraft_uuid TEXT PRIMARY KEY,
    site_account_id TEXT NOT NULL DEFAULT '',
    settings_json TEXT NOT NULL DEFAULT '{}',
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bank_pin_hashes (
    minecraft_uuid TEXT PRIMARY KEY,
    site_account_id TEXT NOT NULL DEFAULT '',
    pin_hash TEXT NOT NULL,
    must_change INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS temporary_pin_resets (
    id TEXT PRIMARY KEY,
    minecraft_uuid TEXT NOT NULL,
    site_account_id TEXT NOT NULL DEFAULT '',
    pin_hash TEXT NOT NULL,
    delivery_blob TEXT NOT NULL DEFAULT '',
    expires_at BIGINT NOT NULL,
    used_at BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pin_reset_audit (
    id BIGSERIAL PRIMARY KEY,
    minecraft_uuid TEXT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS failed_pin_attempts (
    id BIGSERIAL PRIMARY KEY,
    minecraft_uuid TEXT NOT NULL,
    site_account_id TEXT NOT NULL DEFAULT '',
    attempted_at BIGINT NOT NULL,
    source TEXT NOT NULL DEFAULT 'site'
);

CREATE TABLE IF NOT EXISTS account_lockouts (
    account_id TEXT PRIMARY KEY,
    locked_until BIGINT NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE temporary_pin_resets ADD COLUMN IF NOT EXISTS delivery_blob TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_temporary_pin_resets_uuid_time ON temporary_pin_resets(minecraft_uuid, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_pin_reset_audit_uuid_time ON pin_reset_audit(minecraft_uuid, created_at DESC);

CREATE TABLE IF NOT EXISTS security_events (
    id BIGSERIAL PRIMARY KEY,
    time BIGINT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'site'
);

CREATE TABLE IF NOT EXISTS ar_accounts (
    account_id TEXT PRIMARY KEY,
    owner_uuid TEXT NOT NULL,
    owner_name TEXT NOT NULL DEFAULT '',
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ar_batches (
    batch_id TEXT PRIMARY KEY,
    material TEXT NOT NULL DEFAULT 'DIAMOND_ORE',
    amount BIGINT NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_ledger (
    id BIGSERIAL PRIMARY KEY,
    tx_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    tx_type TEXT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_operations (
    id BIGSERIAL PRIMARY KEY,
    operation_id TEXT NOT NULL UNIQUE,
    operation_type TEXT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'COMMITTED',
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_transfers (
    tx_id TEXT PRIMARY KEY,
    from_account_id TEXT NOT NULL,
    to_account_id TEXT NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    created_at BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMMITTED'
);

CREATE TABLE IF NOT EXISTS ar_deposits (
    tx_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    created_at BIGINT NOT NULL,
    source TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_withdrawals (
    tx_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    created_at BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'COMMITTED'
);

CREATE TABLE IF NOT EXISTS ar_admin_issues (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    owner_uuid TEXT NOT NULL DEFAULT '',
    amount BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_suspicious_items (
    id BIGSERIAL PRIMARY KEY,
    owner_uuid TEXT NOT NULL DEFAULT '',
    owner_name TEXT NOT NULL DEFAULT '',
    material TEXT NOT NULL DEFAULT '',
    amount BIGINT NOT NULL DEFAULT 0,
    detected_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_ground_expiration_log (
    id BIGSERIAL PRIMARY KEY,
    world TEXT NOT NULL DEFAULT '',
    x INTEGER DEFAULT 0,
    y INTEGER DEFAULT 0,
    z INTEGER DEFAULT 0,
    amount BIGINT NOT NULL DEFAULT 0,
    expired_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS ar_money_supply_snapshots (
    id BIGSERIAL PRIMARY KEY,
    created_at BIGINT NOT NULL,
    total_accounts BIGINT NOT NULL DEFAULT 0,
    total_physical BIGINT NOT NULL DEFAULT 0,
    total_supply BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL DEFAULT 0,
    updated_by TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ar_atms (
    id TEXT PRIMARY KEY,
    world TEXT NOT NULL DEFAULT '',
    x INTEGER NOT NULL DEFAULT 0,
    y INTEGER NOT NULL DEFAULT 0,
    z INTEGER NOT NULL DEFAULT 0,
    name TEXT NOT NULL DEFAULT 'CopiMine Bank',
    active INTEGER NOT NULL DEFAULT 1,
    created_by TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS atm_events (
    id BIGSERIAL PRIMARY KEY,
    atm_id TEXT NOT NULL DEFAULT '',
    player_uuid TEXT NOT NULL DEFAULT '',
    event_type TEXT NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS atm_sessions (
    id TEXT PRIMARY KEY,
    atm_id TEXT NOT NULL DEFAULT '',
    player_uuid TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'OPEN',
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS atm_audit (
    id BIGSERIAL PRIMARY KEY,
    atm_id TEXT NOT NULL DEFAULT '',
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS elections (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    started_at BIGINT NOT NULL DEFAULT 0,
    ended_at BIGINT NOT NULL DEFAULT 0,
    scheduled_end_at BIGINT NOT NULL DEFAULT 0,
    started_by TEXT NOT NULL DEFAULT '',
    ended_by TEXT NOT NULL DEFAULT '',
    winner_uuid TEXT NOT NULL DEFAULT '',
    winner_name TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS election_phases (
    id BIGSERIAL PRIMARY KEY,
    election_id TEXT NOT NULL,
    phase TEXT NOT NULL,
    opened_at BIGINT NOT NULL DEFAULT 0,
    closed_at BIGINT NOT NULL DEFAULT 0,
    actor TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS election_candidates (
    id BIGSERIAL PRIMARY KEY,
    election_id TEXT NOT NULL,
    minecraft_uuid TEXT NOT NULL,
    minecraft_name TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS election_applications (
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL DEFAULT '',
    applicant_uuid TEXT NOT NULL DEFAULT '',
    applicant_name TEXT NOT NULL DEFAULT '',
    statement TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING',
    submitted_at BIGINT NOT NULL DEFAULT 0,
    reviewed_by TEXT NOT NULL DEFAULT '',
    reviewed_at BIGINT NOT NULL DEFAULT 0,
    verdict_reason TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS election_application_reviews (
    id BIGSERIAL PRIMARY KEY,
    application_id TEXT NOT NULL,
    reviewer TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT '',
    reviewed_at BIGINT NOT NULL DEFAULT 0,
    reason TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS election_ballots (
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    voter_uuid TEXT NOT NULL,
    voter_name TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ISSUED',
    issued_at BIGINT NOT NULL DEFAULT 0,
    used_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS election_votes (
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    voter_uuid TEXT NOT NULL,
    candidate_uuid TEXT NOT NULL,
    ballot_id TEXT NOT NULL DEFAULT '',
    station_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS election_stations (
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    world TEXT NOT NULL DEFAULT '',
    x INTEGER NOT NULL DEFAULT 0,
    y INTEGER NOT NULL DEFAULT 0,
    z INTEGER NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS election_results (
    election_id TEXT PRIMARY KEY,
    winner_uuid TEXT NOT NULL DEFAULT '',
    winner_name TEXT NOT NULL DEFAULT '',
    total_votes BIGINT NOT NULL DEFAULT 0,
    computed_at BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS election_presidents (
    id BIGSERIAL PRIMARY KEY,
    election_id TEXT NOT NULL DEFAULT '',
    minecraft_uuid TEXT NOT NULL DEFAULT '',
    minecraft_name TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    assigned_at BIGINT NOT NULL DEFAULT 0,
    removed_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS election_decrees (
    id TEXT PRIMARY KEY,
    president_uuid TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PUBLISHED'
);

CREATE TABLE IF NOT EXISTS election_petitions (
    id TEXT PRIMARY KEY,
    creator_uuid TEXT NOT NULL DEFAULT '',
    title TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'OPEN',
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS election_audit (
    id BIGSERIAL PRIMARY KEY,
    election_id TEXT NOT NULL DEFAULT '',
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS admin_actions (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    target TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS plugin_events (
    id BIGSERIAL PRIMARY KEY,
    source TEXT NOT NULL DEFAULT '',
    event_type TEXT NOT NULL,
    actor TEXT NOT NULL DEFAULT '',
    target TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS site_audit (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    target TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS prank_audit (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    target TEXT NOT NULL DEFAULT '',
    prank TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS system_checks (
    key TEXT PRIMARY KEY,
    ok INTEGER NOT NULL DEFAULT 0,
    checked_at BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS repair_actions (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS smoke_results (
    id BIGSERIAL PRIMARY KEY,
    check_name TEXT NOT NULL,
    ok INTEGER NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS discord_status_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS discord_notifications_log (
    id BIGSERIAL PRIMARY KEY,
    channel_id TEXT NOT NULL DEFAULT '',
    object_type TEXT NOT NULL DEFAULT '',
    object_id TEXT NOT NULL DEFAULT '',
    sent_at BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS bridge_events (
    id BIGSERIAL PRIMARY KEY,
    source TEXT NOT NULL DEFAULT '',
    event_type TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS status_channel_snapshots (
    id BIGSERIAL PRIMARY KEY,
    channel_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    payload TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS auth_migration_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS auth_users_imported (
    minecraft_uuid TEXT PRIMARY KEY,
    minecraft_name TEXT NOT NULL DEFAULT '',
    imported_at BIGINT NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS auth_whitelist_sync (
    minecraft_uuid TEXT PRIMARY KEY,
    minecraft_name TEXT NOT NULL DEFAULT '',
    synced_at BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS auth_login_checks (
    id BIGSERIAL PRIMARY KEY,
    minecraft_uuid TEXT NOT NULL DEFAULT '',
    minecraft_name TEXT NOT NULL DEFAULT '',
    checked_at BIGINT NOT NULL,
    ok INTEGER NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS auth_effects_disable_audit (
    id BIGSERIAL PRIMARY KEY,
    actor TEXT NOT NULL DEFAULT '',
    target_uuid TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_cmv4_audit_time ON cmv4_audit_events (time DESC, action);
CREATE INDEX IF NOT EXISTS idx_cmv4_players_online ON cmv4_players (online, last_seen DESC);
CREATE INDEX IF NOT EXISTS idx_cm_admin_sessions_user_exp ON cm_admin_sessions (username, expires_at, revoked_at);
CREATE INDEX IF NOT EXISTS idx_cm_admin_sessions_exp ON cm_admin_sessions (expires_at, revoked_at);
CREATE INDEX IF NOT EXISTS idx_cmv7_applications_election_status ON applications (election_id, status, deleted_at);
CREATE INDEX IF NOT EXISTS idx_cmv7_admin_requests_status_created ON admin_requests (status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_account_links_discord_active
    ON cmv4_account_links (discord_id)
    WHERE discord_id <> '' AND status = 'ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_account_links_site_active
    ON cmv4_account_links (site_user_id)
    WHERE site_user_id <> '' AND status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_cmv4_account_links_minecraft ON cmv4_account_links (minecraft_uuid, status);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_owner_type_active
    ON cmv4_bank_accounts (owner_uuid, account_type, currency)
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_cmv4_bank_accounts_owner ON cmv4_bank_accounts (owner_uuid, status);
CREATE INDEX IF NOT EXISTS idx_cmv4_bank_ledger_account_time ON cmv4_bank_ledger (account_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_ledger_idempotency
    ON cmv4_bank_ledger (idempotency_key)
    WHERE idempotency_key <> '';
CREATE INDEX IF NOT EXISTS idx_cmv4_bank_transfers_from_time ON cmv4_bank_transfers (from_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cmv4_bank_transfers_to_time ON cmv4_bank_transfers (to_account_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_transfers_idempotency
    ON cmv4_bank_transfers (idempotency_key)
    WHERE idempotency_key <> '';
CREATE INDEX IF NOT EXISTS idx_site_accounts_minecraft ON site_accounts (minecraft_uuid);
CREATE INDEX IF NOT EXISTS idx_link_codes_account_status ON one_time_link_codes (site_account_id, status, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_failed_pin_attempts_uuid_time ON failed_pin_attempts (minecraft_uuid, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_security_events_time ON security_events (time DESC, action);
CREATE INDEX IF NOT EXISTS idx_ar_accounts_owner ON ar_accounts (owner_uuid, status);
CREATE INDEX IF NOT EXISTS idx_ar_ledger_account_time ON ar_ledger (account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ar_operations_time ON ar_operations (created_at DESC, operation_type);
CREATE INDEX IF NOT EXISTS idx_ar_transfers_from_time ON ar_transfers (from_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ar_transfers_to_time ON ar_transfers (to_account_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_atms_location_active ON ar_atms (world, x, y, z) WHERE active = 1;
CREATE INDEX IF NOT EXISTS idx_atm_events_atm_time ON atm_events (atm_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_election_candidates_election ON election_candidates (election_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS ux_election_votes_once ON election_votes (election_id, voter_uuid);
CREATE INDEX IF NOT EXISTS idx_election_votes_candidate ON election_votes (election_id, candidate_uuid);
CREATE INDEX IF NOT EXISTS idx_election_audit_time ON election_audit (created_at DESC, action);
CREATE INDEX IF NOT EXISTS idx_admin_actions_time ON admin_actions (created_at DESC, action);
CREATE INDEX IF NOT EXISTS idx_plugin_events_time ON plugin_events (created_at DESC, event_type);
CREATE INDEX IF NOT EXISTS idx_site_audit_time ON site_audit (created_at DESC, action);
CREATE INDEX IF NOT EXISTS idx_bridge_events_created ON bridge_events (created_at DESC, event_type);
CREATE INDEX IF NOT EXISTS idx_status_channel_snapshots_created ON status_channel_snapshots (created_at DESC, channel_id);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260611_001_copimine_v4_postgres', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 'migration')
ON CONFLICT (version) DO NOTHING;

INSERT INTO schema_migrations(version, applied_at, component)
VALUES ('20260611_001_copimine_v4_postgres', (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 'migration')
ON CONFLICT (version) DO NOTHING;
