-- CopiMine elections hardening.
-- Idempotent guards for one vote, one active candidate/application/ballot and one active station per block.

CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv731_votes_once
    ON cmv731_votes(election_id, voter_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv731_votes_ballot_once
    ON cmv731_votes(election_id, ballot_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_candidates_once
    ON candidates(election_id, uuid);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_applications_active_once
    ON applications(election_id, applicant_uuid)
    WHERE COALESCE(deleted_at, 0) = 0
      AND UPPER(COALESCE(status, '')) IN ('PENDING', 'APPROVED');

CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_ballot_issues_active_once
    ON cmv7_ballot_issues(election_id, voter_uuid)
    WHERE COALESCE(used, 0) = 0;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_polling_stations_location_active
    ON cmv7_polling_stations(world, x, y, z)
    WHERE COALESCE(active, 0) = 1
      AND COALESCE(archived_at, 0) = 0;

CREATE INDEX IF NOT EXISTS idx_cmv7_election_settings_stage
    ON cmv7_election_settings(election_id, stage);

CREATE INDEX IF NOT EXISTS idx_cmv7_election_curators_active
    ON cmv7_election_curators(election_id, uuid, active, role);
