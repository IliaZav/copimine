CREATE SCHEMA IF NOT EXISTS copimine;
SET search_path TO copimine, public;

-- Clean only runtime objects tied to an old Minecraft world. Player accounts,
-- whitelist links, LuckPerms data, balances and ledgers are intentionally
-- preserved so a world wipe does not erase ownership or money.
DO $$
DECLARE
    table_name TEXT;
    runtime_tables TEXT[] := ARRAY[
        'atm_sessions',
        'atm_events',
        'atm_audit',
        'ar_atms',
        'artifact_shops',
        'candidates',
        'round_candidates',
        'candidate_applications',
        'ballots',
        'votes',
        'cik_seals',
        'cik_chairs',
        'cik_chair_removal_requests',
        'polling_stations',
        'cmv7_polling_stations',
        'election_results',
        'elections',
        'protected_blocks',
        'protected_block_visuals',
        'text_display_links',
        'narcotics_brewing_states',
        'one_time_link_codes'
    ];
BEGIN
    FOREACH table_name IN ARRAY runtime_tables LOOP
        IF to_regclass(table_name) IS NOT NULL THEN
            EXECUTE format('DELETE FROM %I', table_name);
        END IF;
    END LOOP;

    IF to_regclass('plugin_events') IS NOT NULL THEN
        INSERT INTO plugin_events(source, event_type, actor, target, created_at, details)
        VALUES (
            'deploy',
            'clean_world_state',
            'installer',
            'postgres',
            FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT,
            'Removed world-bound runtime objects after world wipe'
        );
    END IF;
END $$;
