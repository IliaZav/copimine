CREATE SCHEMA IF NOT EXISTS copimine;
SET search_path TO copimine, public;

-- Release reset for a fresh game season. This removes gameplay/economy data,
-- but deliberately keeps site accounts, admin users, password/PIN hashes,
-- Minecraft identity links and whitelist links. The world seed is handled by
-- the deployment script and remains unchanged.
DO $$
DECLARE
    table_name TEXT;
    reset_tables TEXT[] := ARRAY[
        -- Sessions and short-lived identity state (accounts remain).
        'cm_admin_sessions','login_sessions','one_time_link_codes',
        'temporary_pin_resets','failed_pin_attempts','account_lockouts',
        -- AR economy, bank accounts, ledgers, transfers and ATM runtime.
        'ar_operations','ar_transfers','ar_deposits','ar_withdrawals',
        'ar_suspicious_items','ar_ground_expiration_log','ar_ledger',
        'ar_batches','ar_accounts','ar_money_supply_snapshots','ar_atms',
        'atm_events','atm_sessions','atm_audit',
        'cmv4_bank_transfers','cmv4_bank_ledger','cmv4_bank_accounts',
        -- Donation balances and purchase state (payment remains disabled).
        'donation_payment_sessions','donation_purchases',
        'donation_item_claims','donation_balance_ledger','donation_accounts',
        -- Elections, presidents, laws, taxes and voting state.
        'election_visual_cleanup_queue','election_application_reviews',
        'election_votes','election_ballots','election_results',
        'election_audit','election_decrees','election_petitions',
        'election_presidents','election_stations','election_phases',
        'election_stages','round_candidates','rounds','candidates',
        'candidate_applications','election_candidates','election_applications',
        'ballots','votes','polling_stations','cik_seals','cik_chairs',
        'president_tax_payments','president_tax_exemptions','president_taxes',
        'president_law_reviews','president_laws','president_broadcasts',
        'president_terms','elections',
        -- Custom-item shops, purchases, repairs and deferred deliveries.
        'artifact_revenue_payouts','artifact_pending_deliveries',
        'artifact_purchases','artifact_repairs','artifact_suspicious_events',
        'artifact_audit_log','artifact_item_instances','artifact_shops',
        -- Narcotics runtime effects and usage counters. Recipes/config stay.
        'narcotics_admin_audit','narcotics_player_overdose',
        'narcotics_player_usage_window','narcotics_brewing_states',
        -- World-bound displays, protected blocks and plugin runtime state.
        'protected_blocks','protected_block_visuals','text_display_links','plugin_events',
        'bridge_events','status_channel_snapshots','discord_notifications_log',
        'discord_status_state','player_profile_cache','player_last_seen',
        'admin_actions','moderation_actions','prank_audit','smoke_results',
        'system_checks','repair_actions'
    ];
BEGIN
    FOREACH table_name IN ARRAY reset_tables LOOP
        IF to_regclass(table_name) IS NOT NULL THEN
            EXECUTE format('DELETE FROM %I', table_name);
        END IF;
    END LOOP;

    IF to_regclass('plugin_events') IS NOT NULL THEN
        INSERT INTO plugin_events(source, event_type, actor, target, created_at, details)
        VALUES (
            'deploy', 'clean_gameplay_state', 'installer', 'postgres',
            FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
            'Reset elections, AR, taxes, shops and world runtime; accounts and whitelist preserved'
        );
    END IF;
END $$;
