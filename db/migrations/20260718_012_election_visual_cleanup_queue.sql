CREATE TABLE IF NOT EXISTS election_visual_cleanup_queue(
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    linked_id TEXT NOT NULL,
    world TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    created_at BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_election_visual_cleanup_queue_chunk
    ON election_visual_cleanup_queue(world, x, z);

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260718_012_election_visual_cleanup_queue', EXTRACT(EPOCH FROM NOW())::BIGINT * 1000, 'election-core')
ON CONFLICT (version) DO NOTHING;
