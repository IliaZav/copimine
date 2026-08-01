from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")
FRONTEND = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "cabinet-runtime.js").read_text(encoding="utf-8")
ELECTION_PLUGIN = (ROOT / "copimine-election-core" / "src" / "me" / "copimine" / "electioncore" / "CopiMineElectionCore.java").read_text(encoding="utf-8")


def test_backend_exposes_two_separate_destructive_maintenance_actions():
    assert "def election_maintenance_sync" in BACKEND
    assert "wipe_test_data" in BACKEND
    assert "clear_custom_blocks" in BACKEND
    assert '@app.post("/api/elections/rp/maintenance")' in BACKEND
    assert "ELECTION_RP_WIPE_TEST_DATA" in BACKEND
    assert "ELECTION_RP_CLEAR_CUSTOM_BLOCKS" in BACKEND


def test_wipe_contract_keeps_non_election_data_out_of_destructive_sql():
    assert "ELECTION_WIPE_TABLES" in BACKEND
    assert '"site_accounts"' not in BACKEND.split("ELECTION_WIPE_TABLES", 1)[1].split("]", 1)[0]
    assert '"artifact_shops"' not in BACKEND.split("ELECTION_WIPE_TABLES", 1)[1].split("]", 1)[0]
    assert '"cmv4_bank_transfers"' not in BACKEND.split("ELECTION_WIPE_TABLES", 1)[1].split("]", 1)[0]


def test_frontend_has_separate_confirmed_actions_and_result_refresh():
    assert "rpElectionMaintenance('wipe_test_data')" in FRONTEND
    assert "rpElectionMaintenance('clear_custom_blocks')" in FRONTEND
    assert "ELECTION_RP_WIPE_TEST_DATA" in FRONTEND
    assert "ELECTION_RP_CLEAR_CUSTOM_BLOCKS" in FRONTEND
    assert "/api/elections/rp/maintenance" in FRONTEND


def test_plugin_processes_cleanup_queue_without_waiting_for_a_new_chunk_load():
    assert "runTaskTimerAsynchronously(this, this::cleanupQueuedElectionVisualsSafe" in ELECTION_PLUGIN
    assert "private void cleanupQueuedElectionVisualsSafe()" in ELECTION_PLUGIN


def test_plugin_reconciles_official_items_after_a_web_wipe():
    assert "boolean lookupSucceeded" in ELECTION_PLUGIN
    assert 'removeOfficialItemsFromPlayer(player, "APPLICATION_BOOK")' in ELECTION_PLUGIN
    assert 'removeOfficialItemsFromPlayer(player, "BALLOT")' in ELECTION_PLUGIN
