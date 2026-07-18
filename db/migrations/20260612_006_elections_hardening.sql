-- CopiMine elections hardening.
--
-- Older pre-v7 snapshots used cmv731/cmv7 tables, while the current release
-- uses the rebuilt election tables.  This migration must therefore be a
-- no-op for missing legacy tables and only add an index when the required
-- columns are actually present.

DO $$
BEGIN
    IF to_regclass('copimine.cmv731_votes') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv731_votes'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv731_votes'::regclass AND attname = 'voter_uuid' AND NOT attisdropped) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv731_votes_once ON copimine.cmv731_votes(election_id, voter_uuid)';
    END IF;

    IF to_regclass('copimine.cmv731_votes') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv731_votes'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv731_votes'::regclass AND attname = 'ballot_id' AND NOT attisdropped) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv731_votes_ballot_once ON copimine.cmv731_votes(election_id, ballot_id)';
    END IF;

    IF to_regclass('copimine.candidates') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.candidates'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.candidates'::regclass AND attname = 'player_uuid' AND NOT attisdropped) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_candidates_once ON copimine.candidates(election_id, player_uuid)';
    ELSIF to_regclass('copimine.candidates') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.candidates'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.candidates'::regclass AND attname = 'uuid' AND NOT attisdropped) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_candidates_once ON copimine.candidates(election_id, uuid)';
    END IF;

    IF to_regclass('copimine.applications') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.applications'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.applications'::regclass AND attname = 'applicant_uuid' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.applications'::regclass AND attname = 'deleted_at' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.applications'::regclass AND attname = 'status' AND NOT attisdropped) THEN
        EXECUTE $sql$
            CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_applications_active_once
            ON copimine.applications(election_id, applicant_uuid)
            WHERE COALESCE(deleted_at, 0) = 0
              AND UPPER(COALESCE(status, '')) IN ('PENDING', 'APPROVED')
        $sql$;
    END IF;

    IF to_regclass('copimine.cmv7_ballot_issues') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_ballot_issues'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_ballot_issues'::regclass AND attname = 'voter_uuid' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_ballot_issues'::regclass AND attname = 'used' AND NOT attisdropped) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_ballot_issues_active_once ON copimine.cmv7_ballot_issues(election_id, voter_uuid) WHERE COALESCE(used, 0) = 0';
    END IF;

    IF to_regclass('copimine.cmv7_polling_stations') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_polling_stations'::regclass AND attname = 'world' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_polling_stations'::regclass AND attname = 'x' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_polling_stations'::regclass AND attname = 'y' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_polling_stations'::regclass AND attname = 'z' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_polling_stations'::regclass AND attname = 'active' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_polling_stations'::regclass AND attname = 'archived_at' AND NOT attisdropped) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_polling_stations_location_active ON copimine.cmv7_polling_stations(world, x, y, z) WHERE COALESCE(active, 0) = 1 AND COALESCE(archived_at, 0) = 0';
    END IF;

    IF to_regclass('copimine.cmv7_election_settings') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_election_settings'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_election_settings'::regclass AND attname = 'stage' AND NOT attisdropped) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_cmv7_election_settings_stage ON copimine.cmv7_election_settings(election_id, stage)';
    END IF;

    IF to_regclass('copimine.cmv7_election_curators') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_election_curators'::regclass AND attname = 'election_id' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_election_curators'::regclass AND attname = 'uuid' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_election_curators'::regclass AND attname = 'active' AND NOT attisdropped)
       AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'copimine.cmv7_election_curators'::regclass AND attname = 'role' AND NOT attisdropped) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_cmv7_election_curators_active ON copimine.cmv7_election_curators(election_id, uuid, active, role)';
    END IF;
END $$;
