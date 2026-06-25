-- CopiMine FINAL_COPIMINE_GUI_BANK_SHOP_POSTGRES_FIX_RU_V1
-- Bank/ATM compatibility migration.
-- Safe to run repeatedly on PostgreSQL.

CREATE TABLE IF NOT EXISTS atm_audit (
    id BIGSERIAL PRIMARY KEY,
    created_at BIGINT NOT NULL DEFAULT 0,
    actor TEXT NOT NULL DEFAULT '',
    action TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT ''
);

ALTER TABLE atm_audit
    ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'atm_audit'
          AND column_name = 'time'
    ) THEN
        EXECUTE 'UPDATE atm_audit SET created_at = time WHERE COALESCE(created_at, 0) = 0 AND time IS NOT NULL';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_atm_audit_created_at
    ON atm_audit(created_at DESC, action);

ALTER TABLE atm_events
    ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_atm_events_atm_time
    ON atm_events(atm_id, created_at DESC);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'artifact_items_catalog'
          AND column_name = 'enabled'
          AND data_type <> 'boolean'
    ) THEN
        ALTER TABLE artifact_items_catalog
            ALTER COLUMN enabled TYPE BOOLEAN
            USING enabled::integer <> 0;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'artifact_shops'
          AND column_name = 'enabled'
          AND data_type <> 'boolean'
    ) THEN
        ALTER TABLE artifact_shops
            ALTER COLUMN enabled TYPE BOOLEAN
            USING enabled::integer <> 0;
    END IF;
END $$;

INSERT INTO cmv4_schema_migrations(version, applied_at, component)
VALUES ('20260611_003_bank_atm_audit_gui_fix', FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 'sql')
ON CONFLICT(version) DO NOTHING;
