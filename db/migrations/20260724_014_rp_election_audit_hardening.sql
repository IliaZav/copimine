-- Additive hardening for the two-stage RP election workflow.
-- Historical rows are kept.  Only duplicate *active* rows from an old
-- release are closed before the database invariant is installed.

WITH ranked_rp AS (
    SELECT id,
           ROW_NUMBER() OVER (
               ORDER BY COALESCE(updated_at, 0) DESC,
                        COALESCE(started_at, 0) DESC,
                        id DESC
           ) AS row_no
      FROM elections
     WHERE COALESCE(active, 0) = 1
       AND lower(COALESCE(notes, '')) = 'rp-two-stage'
)
UPDATE elections AS e
   SET active = 0,
       status = CASE
           WHEN upper(COALESCE(e.current_stage, '')) = 'PRESIDENT_TERM' THEN 'FINISHED'
           ELSE 'MIGRATED'
       END,
       ended_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000,
       ended_by = 'SYSTEM_AUDIT',
       updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
  FROM ranked_rp AS r
 WHERE e.id = r.id
   AND r.row_no > 1;

WITH ranked_protected AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY world, x, y, z
               ORDER BY COALESCE(updated_at, 0) DESC,
                        COALESCE(created_at, 0) DESC,
                        id DESC
           ) AS row_no
      FROM protected_blocks
     WHERE COALESCE(active, 0) = 1
)
UPDATE protected_blocks AS p
   SET active = 0,
       updated_at = EXTRACT(EPOCH FROM NOW())::BIGINT * 1000
  FROM ranked_protected AS r
 WHERE p.id = r.id
   AND r.row_no > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_elections_single_active_rp
    ON elections ((lower(notes)))
    WHERE COALESCE(active, 0) = 1
      AND lower(COALESCE(notes, '')) = 'rp-two-stage';

CREATE UNIQUE INDEX IF NOT EXISTS uq_protected_blocks_active_coords
    ON protected_blocks(world, x, y, z)
    WHERE COALESCE(active, 0) = 1;

CREATE INDEX IF NOT EXISTS idx_votes_election_round_candidate
    ON votes(election_id, round_no, candidate_uuid, created_at);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260724_014_rp_election_audit_hardening', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'admin-web')
ON CONFLICT (version) DO NOTHING;
