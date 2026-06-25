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

ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_stage TEXT NOT NULL DEFAULT 'NONE';
ALTER TABLE elections ADD COLUMN IF NOT EXISTS current_round INTEGER NOT NULL DEFAULT 1;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS candidate_limit INTEGER NOT NULL DEFAULT 4;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_term_days INTEGER NOT NULL DEFAULT 7;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS manual_winner_uuid TEXT NOT NULL DEFAULT '';
ALTER TABLE elections ADD COLUMN IF NOT EXISTS manual_winner_name TEXT NOT NULL DEFAULT '';
ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_uuid TEXT NOT NULL DEFAULT '';
ALTER TABLE elections ADD COLUMN IF NOT EXISTS president_name TEXT NOT NULL DEFAULT '';
ALTER TABLE elections ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 0;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS second_round_needed INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS election_stages(
    id BIGSERIAL PRIMARY KEY,
    election_id TEXT NOT NULL,
    stage TEXT NOT NULL,
    round_no INTEGER NOT NULL DEFAULT 1,
    actor TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS polling_stations(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL DEFAULT '',
    world TEXT NOT NULL DEFAULT '',
    x INTEGER NOT NULL DEFAULT 0,
    y INTEGER NOT NULL DEFAULT 0,
    z INTEGER NOT NULL DEFAULT 0,
    chair_uuid TEXT NOT NULL DEFAULT '',
    chair_name TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0,
    text_display_uuid TEXT NOT NULL DEFAULT '',
    applications_issued INTEGER NOT NULL DEFAULT 0,
    ballots_issued INTEGER NOT NULL DEFAULT 0,
    ballots_submitted INTEGER NOT NULL DEFAULT 0,
    ballots_annulled INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cik_chairs(
    id BIGSERIAL PRIMARY KEY,
    station_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    assigned_at BIGINT NOT NULL DEFAULT 0,
    assigned_by TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS cik_seals(
    id TEXT PRIMARY KEY,
    station_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    election_id TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    issued_at BIGINT NOT NULL DEFAULT 0,
    issued_by TEXT NOT NULL DEFAULT '',
    revoked_at BIGINT NOT NULL DEFAULT 0,
    revoked_by TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS candidate_applications(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    station_id TEXT NOT NULL DEFAULT '',
    answers TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ISSUED',
    chair_recommendation TEXT NOT NULL DEFAULT '',
    chair_note TEXT NOT NULL DEFAULT '',
    admin_status TEXT NOT NULL DEFAULT 'PENDING',
    admin_note TEXT NOT NULL DEFAULT '',
    book_signed_at BIGINT NOT NULL DEFAULT 0,
    submitted_at BIGINT NOT NULL DEFAULT 0,
    reviewed_at BIGINT NOT NULL DEFAULT 0,
    reviewed_by TEXT NOT NULL DEFAULT '',
    issued_at BIGINT NOT NULL DEFAULT 0,
    issued_by TEXT NOT NULL DEFAULT '',
    book_token TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS candidates(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    application_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1,
    last_result INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ballots(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    round_no INTEGER NOT NULL DEFAULT 1,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    station_id TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ISSUED',
    issued_at BIGINT NOT NULL DEFAULT 0,
    issued_by TEXT NOT NULL DEFAULT '',
    confirmed_candidate_uuid TEXT NOT NULL DEFAULT '',
    confirmed_candidate_name TEXT NOT NULL DEFAULT '',
    confirmed_at BIGINT NOT NULL DEFAULT 0,
    submitted_at BIGINT NOT NULL DEFAULT 0,
    submitted_station_id TEXT NOT NULL DEFAULT '',
    annulled_at BIGINT NOT NULL DEFAULT 0,
    annulled_by TEXT NOT NULL DEFAULT '',
    annul_reason TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS votes(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    round_no INTEGER NOT NULL DEFAULT 1,
    ballot_id TEXT NOT NULL,
    voter_uuid TEXT NOT NULL,
    voter_name TEXT NOT NULL DEFAULT '',
    candidate_uuid TEXT NOT NULL,
    candidate_name TEXT NOT NULL DEFAULT '',
    station_id TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rounds(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    round_no INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    started_at BIGINT NOT NULL DEFAULT 0,
    ended_at BIGINT NOT NULL DEFAULT 0,
    winner_uuid TEXT NOT NULL DEFAULT '',
    winner_name TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS president_terms(
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    president_uuid TEXT NOT NULL,
    president_name TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    started_at BIGINT NOT NULL DEFAULT 0,
    ends_at BIGINT NOT NULL DEFAULT 0,
    removed_at BIGINT NOT NULL DEFAULT 0,
    removed_by TEXT NOT NULL DEFAULT '',
    last_broadcast_at BIGINT NOT NULL DEFAULT 0,
    last_law_replace_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS president_laws(
    id TEXT PRIMARY KEY,
    term_id TEXT NOT NULL,
    president_uuid TEXT NOT NULL,
    text TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at BIGINT NOT NULL DEFAULT 0,
    published_at BIGINT NOT NULL DEFAULT 0,
    replaced_law_id TEXT NOT NULL DEFAULT '',
    slot_no INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS president_law_reviews(
    id BIGSERIAL PRIMARY KEY,
    law_id TEXT NOT NULL,
    reviewer TEXT NOT NULL DEFAULT '',
    decision TEXT NOT NULL DEFAULT '',
    note TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS president_broadcasts(
    id TEXT PRIMARY KEY,
    term_id TEXT NOT NULL,
    president_uuid TEXT NOT NULL,
    format TEXT NOT NULL DEFAULT 'CHAT',
    text TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS president_taxes(
    id TEXT PRIMARY KEY,
    term_id TEXT NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS president_tax_payments(
    id TEXT PRIMARY KEY,
    tax_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL DEFAULT '',
    amount BIGINT NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    details TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS protected_blocks(
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    linked_id TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS text_display_links(
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    linked_id TEXT NOT NULL,
    world TEXT NOT NULL DEFAULT '',
    entity_uuid TEXT NOT NULL DEFAULT '',
    text TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_polling_stations_active ON polling_stations(active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_candidate_applications_status ON candidate_applications(election_id, admin_status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_ballots_player ON ballots(election_id, round_no, player_uuid, status);
CREATE INDEX IF NOT EXISTS idx_votes_round_candidate ON votes(election_id, round_no, candidate_uuid);
CREATE INDEX IF NOT EXISTS idx_president_laws_status ON president_laws(status, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_tax_payments_tax_player ON president_tax_payments(tax_id, player_uuid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_protected_blocks_coords ON protected_blocks(world, x, y, z, active);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260621_007_copimine_election_core_rebuild', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'election-core')
ON CONFLICT (version) DO NOTHING;
