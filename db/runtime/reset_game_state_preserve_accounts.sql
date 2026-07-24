\set ON_ERROR_STOP on

-- This file is intentionally destructive.  It is executed by the guarded
-- Ubuntu wrapper only after a database dump has been written.
-- Site accounts, admin sessions, Minecraft links and whitelist requests are
-- deliberately absent from the wipe list and must survive a game reset.

-- Keep the selected schema for the whole psql session.  SET LOCAL would be
-- cleared at the end of the implicit transaction and would make the reset
-- silently inspect public instead of the configured CopiMine schema.
SELECT format('SET search_path TO %I, public', :'copimine_schema') \gexec

DO $$
DECLARE
    protected_name text;
    wipe_name text;
    protected_names constant text[] := ARRAY[
        'site_accounts', 'cm_admin_users', 'cm_admin_sessions',
        'cm_refresh_sessions', 'minecraft_account_links',
        'whitelist_account_links', 'whitelist_requests',
        'auth_users_imported', 'auth_migration_state', 'auth_whitelist_sync'
    ];
    wipe_names constant text[] := ARRAY[
        -- New RP elections and their protected visuals.
        'votes', 'ballots', 'round_candidates', 'candidates',
        'candidate_applications', 'cik_chair_removal_requests',
        'cik_seals', 'cik_chairs', 'election_voting_blocks',
        'polling_stations', 'rounds', 'election_stages', 'elections',
        'president_law_reviews', 'president_laws', 'president_broadcasts',
        'president_tax_payment_ops', 'president_tax_payments',
        'president_tax_exemptions', 'president_taxes', 'president_terms',
        'protected_blocks', 'protected_block_visuals',
        'text_display_links', 'election_visual_cleanup_queue',
        -- Historical election rows are reset as game state, not accounts.
        'applications', 'election_candidates', 'election_applications',
        'election_presidents', 'election_decrees', 'election_petitions',
        'cmv7_election_curators', 'cmv7_election_settings',
        'cmv7_election_applications', 'cmv7_election_candidates',
        'cmv7_election_votes', 'cmv731_elections', 'cmv731_rounds',
        'cmv731_candidates', 'cmv731_votes',
        -- AR, Donation, ATM and shop state.  Catalog rows remain so the
        -- release can repopulate the same assortment after the reset.
        'cmv4_bank_ledger', 'cmv4_bank_transfers', 'cmv4_pending_ar_settlements',
        'cmv4_bank_accounts', 'ar_accounts', 'ar_batches', 'ar_ledger',
        'ar_operations', 'ar_transfers', 'ar_deposits', 'ar_withdrawals',
        'ar_admin_issues', 'ar_suspicious_items', 'ar_ground_expiration_log',
        'ar_money_supply_snapshots', 'ar_atms', 'atm_events', 'atm_sessions',
        'atm_audit', 'donation_accounts', 'donation_balance_ledger',
        'donation_payment_sessions', 'donation_purchases', 'donation_item_claims',
        'artifact_shops', 'artifact_purchases', 'artifact_item_instances',
        'artifact_pending_deliveries', 'artifact_repairs',
        'artifact_purchase_limit_resets', 'artifact_revenue_payouts',
        'artifact_audit_log', 'artifact_suspicious_events',
        'artifact_repair_events',
        -- Temporary player/cauldron state is gameplay data and must not leak
        -- into a clean season.  Recipe/configuration tables stay intact.
        'narcotics_brewing_states', 'narcotics_player_overdose',
        'narcotics_player_usage_window', 'narcotics_admin_audit'
    ];
BEGIN
    FOREACH protected_name IN ARRAY protected_names LOOP
        IF protected_name = ANY(wipe_names) THEN
            RAISE EXCEPTION 'wipe list contains protected account table: %', protected_name;
        END IF;
    END LOOP;

    FOREACH wipe_name IN ARRAY wipe_names LOOP
        IF to_regclass(format('%I.%I', current_schema(), wipe_name)) IS NOT NULL THEN
            EXECUTE format('DELETE FROM %I', wipe_name);
        END IF;
    END LOOP;
END $$;

-- A successful reset must leave the identity boundary intact.  Fail rather
-- than silently reporting success if an account/whitelist table disappeared.
DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'site_accounts', 'minecraft_account_links',
        'whitelist_account_links', 'whitelist_requests'
    ] LOOP
        IF to_regclass(format('%I.%I', current_schema(), table_name)) IS NULL THEN
            RAISE EXCEPTION 'protected account table is missing after wipe: %', table_name;
        END IF;
    END LOOP;
END $$;
