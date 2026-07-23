-- Additive migration for the simplified role-play election workflow.
-- Existing elections, accounts, ballots, presidents and economy rows remain.

ALTER TABLE elections ADD COLUMN IF NOT EXISTS voting_started_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS voting_deadline_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS voting_ended_at BIGINT NOT NULL DEFAULT 0;

ALTER TABLE president_terms ADD COLUMN IF NOT EXISTS resignation_reason TEXT NOT NULL DEFAULT '';
ALTER TABLE president_terms ADD COLUMN IF NOT EXISTS removed_by TEXT NOT NULL DEFAULT '';
ALTER TABLE president_terms ADD COLUMN IF NOT EXISTS ended_at BIGINT NOT NULL DEFAULT 0;

ALTER TABLE elections ADD COLUMN IF NOT EXISTS winner_uuid TEXT NOT NULL DEFAULT '';
ALTER TABLE elections ADD COLUMN IF NOT EXISTS winner_name TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS election_voting_blocks (
    id TEXT PRIMARY KEY,
    election_id TEXT NOT NULL,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT '',
    updated_at BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_election_voting_blocks_coords
    ON election_voting_blocks(election_id, world, x, y, z);

CREATE INDEX IF NOT EXISTS idx_election_voting_blocks_active
    ON election_voting_blocks(election_id, active);

CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_ballot_id
    ON votes(ballot_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_voter_round
    ON votes(election_id, round_no, voter_uuid);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260723_013_rp_election_workflow', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'admin-web')
ON CONFLICT (version) DO NOTHING;
