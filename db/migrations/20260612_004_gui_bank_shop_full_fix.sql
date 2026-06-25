-- CopiMine FINAL_COPIMINE_GUI_BANK_SHOP_POSTGRES_FIX_RU_V1
-- Full GUI/bank/shop/PostgreSQL compatibility migration.
-- This migration is intentionally idempotent and safe to run after 20260611_003.

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
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'atm_audit'
          AND column_name = 'time'
    ) THEN
        EXECUTE 'UPDATE atm_audit SET created_at = time WHERE COALESCE(created_at, 0) = 0 AND time IS NOT NULL';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_atm_audit_created_at
    ON atm_audit(created_at DESC, action);

DO $$
DECLARE
    target record;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('cmv4_audit_events', 'time'),
            ('plugin_events', 'created_at'),
            ('cmv4_players', 'first_seen'),
            ('cmv4_players', 'last_seen'),
            ('cmv4_players', 'last_quit'),
            ('cmv4_players', 'updated_at'),
            ('cmv4_account_links', 'linked_at'),
            ('cmv4_account_links', 'updated_at'),
            ('cmv4_bank_accounts', 'created_at'),
            ('cmv4_bank_accounts', 'updated_at'),
            ('cmv4_bank_ledger', 'created_at'),
            ('cmv4_bank_transfers', 'created_at'),
            ('bank_pin_hashes', 'created_at'),
            ('bank_pin_hashes', 'updated_at'),
            ('failed_pin_attempts', 'attempted_at'),
            ('account_lockouts', 'locked_until'),
            ('account_lockouts', 'updated_at'),
            ('ar_atms', 'created_at'),
            ('ar_atms', 'archived_at'),
            ('atm_events', 'created_at'),
            ('atm_sessions', 'created_at'),
            ('atm_sessions', 'updated_at'),
            ('artifact_items_catalog', 'updated_at'),
            ('artifact_shops', 'created_at'),
            ('artifact_shops', 'updated_at'),
            ('artifact_purchases', 'created_at'),
            ('artifact_purchases', 'updated_at'),
            ('artifact_repairs', 'created_at'),
            ('artifact_suspicious_events', 'created_at'),
            ('artifact_audit_log', 'created_at'),
            ('artifact_pending_deliveries', 'created_at'),
            ('artifact_pending_deliveries', 'updated_at'),
            ('elections', 'started_at'),
            ('elections', 'ended_at'),
            ('elections', 'scheduled_end_at'),
            ('applications', 'submitted_at'),
            ('applications', 'reviewed_at'),
            ('applications', 'deleted_at'),
            ('cmv7_ballot_issues', 'issued_at'),
            ('cmv7_application_issues', 'issued_at'),
            ('cmv7_polling_stations', 'created_at'),
            ('cmv7_polling_stations', 'archived_at'),
            ('cmv7_election_settings', 'updated_at'),
            ('cmv7_election_curators', 'added_at'),
            ('cmv7_ar_balances', 'updated_at'),
            ('cmv7_ar_events', 'time'),
            ('cmv7_ar_assets', 'created_at'),
            ('cmv7_ar_assets', 'updated_at'),
            ('cmv7_ar_transactions', 'time'),
            ('cmv7_ar_guard_incidents', 'time'),
            ('cmv7_ar_scan_reports', 'time'),
            ('cmv7_ar_placed_blocks', 'placed_at'),
            ('cmv7_president_state', 'assigned_at'),
            ('cmv7_president_state', 'removed_at'),
            ('cmv7_president_cooldowns', 'last_announce_at'),
            ('cmv7_official_item_bindings', 'issued_at'),
            ('cmv7_official_item_bindings', 'revoked_at'),
            ('cmv7_player_checks', 'time'),
            ('cmv731_votes', 'time'),
            ('cmv731_vote_sessions', 'updated_at'),
            ('cmv731_vote_sessions', 'selected_at'),
            ('admin_requests', 'created_at'),
            ('admin_requests', 'updated_at'),
            ('cmv7_player_activity', 'time'),
            ('cmv7_inventory_snapshots', 'time'),
            ('cmv7_ar_economy_snapshots', 'time'),
            ('cmv8_startup_checks', 'time')
        ) AS fields(table_name, column_name)
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = target.table_name
              AND column_name = target.column_name
              AND data_type IN ('integer', 'smallint')
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I ALTER COLUMN %I TYPE BIGINT USING %I::BIGINT',
                target.table_name,
                target.column_name,
                target.column_name
            );
        END IF;
    END LOOP;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
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
        SELECT 1 FROM information_schema.columns
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
VALUES ('20260612_004_gui_bank_shop_full_fix', FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT, 'sql')
ON CONFLICT(version) DO NOTHING;
