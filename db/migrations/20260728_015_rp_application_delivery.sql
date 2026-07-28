-- Record the origin of web applications so a submitted form can be traced
-- without exposing private account data in the public election payload.
ALTER TABLE candidate_applications
    ADD COLUMN IF NOT EXISTS submitted_via TEXT NOT NULL DEFAULT '';
ALTER TABLE candidate_applications
    ADD COLUMN IF NOT EXISTS submitted_by_account_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_candidate_applications_delivery
    ON candidate_applications(election_id, submitted_via, submitted_at DESC);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260728_015_rp_application_delivery', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'admin-web')
ON CONFLICT (version) DO NOTHING;
