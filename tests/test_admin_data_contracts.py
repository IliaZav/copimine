from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")


def test_reports_use_the_admin_request_queue_as_a_canonical_source():
    assert "def load_report_rows_sync" in BACKEND
    helper = BACKEND.split("def load_report_rows_sync", 1)[1].split("def save_collection_item_sync", 1)[0]
    assert "FROM admin_requests" in helper
    assert "merge_report_rows" in helper
    assert "def report_timestamp" in BACKEND
    assert BACKEND.count("load_report_rows_sync") >= 6


def test_player_session_hydrates_the_canonical_minecraft_link():
    assert "def hydrate_player_account_link" in BACKEND
    account_lookup = BACKEND.split("def player_account_by_id", 1)[1].split("def player_account_by_username", 1)[0]
    assert "hydrate_player_account_link" in account_lookup
    assert "FROM minecraft_account_links" in BACKEND
    assert "FROM whitelist_account_links" in BACKEND


def test_election_schema_bootstraps_round_candidates_without_a_migration_race():
    schema_section = BACKEND.split("def _ensure_v4_schema", 1)[1].split("def ensure_v4_schema", 1)[0]
    assert "CREATE TABLE IF NOT EXISTS round_candidates" in schema_section
    assert "PRIMARYKEY(election_id,round_no,candidate_uuid)" in schema_section.replace(" ", "")


def test_single_ar_purchase_rejects_reused_keys_for_another_owner_or_item():
    purchase = BACKEND.split("def purchase_ar_item_sync", 1)[1].split("CART_IDEMPOTENCY_KEY_RE", 1)[0]
    assert "player_uuid,item_id,status,price_ar" in purchase
    assert "idempotency_key уже используется другой покупкой" in purchase
    assert "idempotency_key уже привязан к другому предмету" in purchase
