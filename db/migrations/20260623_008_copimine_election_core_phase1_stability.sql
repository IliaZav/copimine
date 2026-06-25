ALTER TABLE elections ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE elections ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS round_candidates (
    election_id TEXT NOT NULL,
    round_no INTEGER NOT NULL DEFAULT 1,
    candidate_uuid TEXT NOT NULL,
    candidate_name TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (election_id, round_no, candidate_uuid)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_candidate_applications_election_player
    ON candidate_applications(election_id, player_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_election_player
    ON candidates(election_id, player_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_ballot_id
    ON votes(ballot_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_votes_voter_round
    ON votes(election_id, round_no, voter_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ballots_active_player_round
    ON ballots(election_id, round_no, player_uuid)
    WHERE status IN ('ISSUED', 'CONFIRMED', 'DEPOSITED');

CREATE UNIQUE INDEX IF NOT EXISTS uq_round_candidates_round_player
    ON round_candidates(election_id, round_no, candidate_uuid);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260623_008_copimine_election_core_phase1_stability', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'election-core')
ON CONFLICT (version) DO NOTHING;
