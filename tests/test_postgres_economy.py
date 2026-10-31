"""Legacy economy on real PostgreSQL: PIN lockout, atomic transfer, replay, checkout.

SQLite cannot show what these rules depend on: `pg_advisory_xact_lock`,
`SELECT ... FOR UPDATE` ordering, and a rollback that really undoes a debit.
Everything here runs only when TEST_POSTGRES_URL points at a disposable
database (see tests/test_postgres_contract.py for the connection contract);
without it the module skips.

The legacy store is redirected to the same PostgreSQL server by patching the
`POSTGRES_*` module globals, resetting the cached pool and the once-only schema
flag, and moving every file path it writes into `tmp_path`.
"""
import os
import uuid
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import unquote, urlparse

import pytest

from backend.legacy import main as legacy

URL = os.environ.get("TEST_POSTGRES_URL", "")

pytestmark = pytest.mark.skipif(
    not URL,
    reason="TEST_POSTGRES_URL is not set; the economy contract needs real PostgreSQL",
)

# Two cheap items; the shop file itself must never be read from production.
SHOP = {
    "items": [],
    "byId": {
        "test-pickaxe": {"item_id": "test-pickaxe", "display_name": "Тестовая кирка",
                         "price_ar": 40, "enabled": True, "category": "TOOLS"},
        "test-lamp": {"item_id": "test-lamp", "display_name": "Тестовая лампа",
                      "price_ar": 60, "enabled": True, "category": "DECOR"},
    },
}

WORLD = uuid.UUID("6f9619ff-8b86-d011-b42d-00c04fc964ff")

# The schema is shared between tests, so every identity is derived from its own
# name: a fixed counter would hand the second test the first test's locked wallet.
ECONOMY_TABLES = (
    "failed_pin_attempts", "account_lockouts", "security_events", "bank_pin_hashes",
    "bank_account_pins", "cmv4_bank_ledger", "cmv4_bank_transfers", "artifact_purchases",
    "artifact_item_instances", "artifact_pending_deliveries", "artifact_revenue_payouts",
    "site_accounts",
)


def identity(name: str) -> str:
    return str(uuid.uuid5(WORLD, name))


@pytest.fixture
def pg(tmp_path, monkeypatch):
    """Legacy store pointed at the disposable PostgreSQL, nothing else touched."""
    parsed = urlparse(URL)
    monkeypatch.setattr(legacy, "POSTGRES_HOST", parsed.hostname or "127.0.0.1")
    monkeypatch.setattr(legacy, "POSTGRES_PORT", parsed.port or 5432)
    monkeypatch.setattr(legacy, "POSTGRES_DB", (parsed.path or "").lstrip("/"))
    monkeypatch.setattr(legacy, "POSTGRES_USER", unquote(parsed.username or ""))
    monkeypatch.setattr(legacy, "POSTGRES_PASSWORD", unquote(parsed.password or ""))
    monkeypatch.setattr(legacy, "POSTGRES_SCHEMA", "copimine")
    monkeypatch.setattr(legacy, "AUTH_STORAGE_MODE", "postgresql")
    monkeypatch.setattr(legacy, "_PG_POOL", None)
    monkeypatch.setattr(legacy, "V4_SCHEMA_READY", False)
    monkeypatch.setattr(legacy, "AUTH_SCHEMA_READY", False)
    monkeypatch.setattr(legacy, "AUDIT_LOG_FILE", tmp_path / "audit.jsonl")
    monkeypatch.setattr(legacy, "EVENT_LOG_FILE", tmp_path / "events.jsonl")
    monkeypatch.setattr(legacy, "BACKUPS_DIR", tmp_path / "backups")
    monkeypatch.setattr(legacy, "AUTH_DB_FILE", tmp_path / "auth.db")
    # The shop catalogue and the plugin's own balance file are not part of this
    # database; reading either would pull production data into the test.
    monkeypatch.setattr(legacy, "ar_catalog_snapshot_sync", lambda: SHOP)
    monkeypatch.setattr(legacy, "live_plugin_ar_balance_sync", lambda *a, **k: {})
    with legacy.auth_conn() as conn:
        legacy.ensure_v4_schema(conn)
        # One schema is reused by every test in this module, so each test starts
        # from an empty economy instead of inheriting another test's lockout.
        for table in ECONOMY_TABLES:
            conn.execute("DELETE FROM " + table)
        conn.commit()
    yield legacy
    if legacy._PG_POOL is not None:
        legacy._PG_POOL.close()
        legacy._PG_POOL = None


def make_player(pg, name: str, pin: str = "1234") -> dict:
    """A site account with a linked Minecraft identity and a known PIN."""
    uuid_value = identity(name)
    now = pg.donation_now_ms()
    with pg.auth_conn() as conn:
        conn.execute(
            "INSERT INTO site_accounts(id,username,username_norm,password_hash,role,enabled,"
            "minecraft_uuid,minecraft_name,created_at,updated_at,last_login_at,registration_ip)"
            " VALUES(%s,%s,%s,'external-website-session','player',1,%s,%s,%s,%s,0,'')",
            ("site-" + name, name, name.lower(), uuid_value, name, now, now),
        )
        conn.commit()
    account = {"id": "site-" + name, "username": name, "minecraft_uuid": uuid_value, "minecraft_name": name}
    pg.set_player_pin_sync(account, pg.PlayerPinSetIn(new_pin=pin))
    return account


def fund(pg, account: dict, amount: int) -> None:
    with pg.auth_conn() as conn:
        bank = pg.ensure_player_bank_account(conn, account["minecraft_uuid"], account["minecraft_name"])
        conn.execute("UPDATE cmv4_bank_accounts SET balance=%s WHERE account_id=%s",
                     (amount, bank["account_id"]))
        conn.commit()


def balance(pg, uuid: str) -> int:
    with pg.auth_conn() as conn:
        row = conn.execute(
            "SELECT balance FROM cmv4_bank_accounts WHERE owner_uuid=%s AND currency='AR' AND status='ACTIVE'",
            (uuid,),
        ).fetchone()
        return int(row["balance"] or 0) if row else 0


def transfer(pg, account: dict, recipient: str, amount: int, key: str, pin: str = "1234"):
    data = pg.PlayerBankTransferIn(recipient=recipient, amount=amount, pin=pin, note="тест",
                                   idempotency_key=key)
    return pg.transfer_player_bank_sync(account, data)


def checkout(pg, account: dict, items: list, total: int, key: str):
    data = pg.PlayerCartCheckoutIn(item_ids=items, pin="1234", expected_total=total,
                                   idempotency_key=key)
    return pg.checkout_ar_cart_sync(account, data)


def treasury_balance(pg) -> int:
    with pg.auth_conn() as conn:
        row = pg.ensure_treasury_bank_account(conn)
        conn.commit()
        return int(row["balance"] or 0)


def test_five_wrong_pins_lock_the_wallet_until_the_lockout_expires(pg):
    owner = make_player(pg, "LockOwner")
    target = make_player(pg, "LockTarget")
    fund(pg, owner, 100)

    for attempt in range(5):
        with pytest.raises(legacy.HTTPException) as wrong:
            transfer(pg, owner, target["minecraft_name"], 10, "lock-attempt-%d" % attempt, pin="9999")
        assert wrong.value.status_code == 403, wrong.value.detail

    with pg.auth_conn() as conn:
        attempts = conn.execute(
            "SELECT COUNT(*) AS n FROM failed_pin_attempts WHERE minecraft_uuid=%s",
            (owner["minecraft_uuid"],),
        ).fetchone()
        lock = conn.execute("SELECT locked_until FROM account_lockouts WHERE account_id=%s",
                            (pg.bank_pin_lockout_key(owner["minecraft_uuid"]),)).fetchone()
        events = conn.execute(
            "SELECT COUNT(*) AS n FROM security_events WHERE action='PIN_LOCKOUT' AND details LIKE %s",
            (f"%{owner['minecraft_uuid']}%",),
        ).fetchone()
        rows = conn.execute("SELECT COUNT(*) AS n FROM cmv4_bank_transfers WHERE idempotency_key LIKE %s",
                            ("player-bank-transfer-lock-attempt%",)).fetchone()
    assert int(attempts["n"]) == 5
    assert lock is not None and int(lock["locked_until"]) > pg.now_ts()
    assert int(events["n"]) == 1, "одно событие PIN_LOCKOUT на блокировку"
    assert int(rows["n"]) == 0, "ни одна отклонённая попытка не создала перевод"

    with pytest.raises(legacy.HTTPException) as locked:
        transfer(pg, owner, target["minecraft_name"], 10, "lock-attempt-6", pin="1234")
    assert locked.value.status_code == 429, "даже верный PIN отклоняется активной блокировкой"

    # The lock is time based; expiring it is the documented recovery path.
    with pg.auth_conn() as conn:
        conn.execute("UPDATE account_lockouts SET locked_until=%s WHERE account_id=%s",
                     (pg.now_ts() - 1, pg.bank_pin_lockout_key(owner["minecraft_uuid"])))
        conn.commit()
    moved = transfer(pg, owner, target["minecraft_name"], 10, "lock-after-expiry")
    assert moved["ok"] is True and moved["idempotent"] is False
    assert balance(pg, owner["minecraft_uuid"]) == 90
    assert balance(pg, target["minecraft_uuid"]) == 10
    with pg.auth_conn() as conn:
        assert conn.execute("SELECT 1 FROM account_lockouts WHERE account_id=%s",
                            (pg.bank_pin_lockout_key(owner["minecraft_uuid"]),)).fetchone() is None


def test_insufficient_funds_leaves_no_row_and_no_debit(pg):
    owner = make_player(pg, "PoorOwner")
    target = make_player(pg, "RichTarget")
    fund(pg, owner, 30)

    with pytest.raises(legacy.HTTPException) as poor:
        transfer(pg, owner, target["minecraft_name"], 100, "atomic-insufficient-1")
    assert poor.value.status_code == 409
    assert "Insufficient" in str(poor.value.detail)

    assert balance(pg, owner["minecraft_uuid"]) == 30
    assert balance(pg, target["minecraft_uuid"]) == 0
    with pg.auth_conn() as conn:
        assert conn.execute("SELECT 1 FROM cmv4_bank_transfers WHERE idempotency_key=%s",
                            ("player-bank-transfer-atomic-insufficient-1",)).fetchone() is None
        assert conn.execute("SELECT 1 FROM cmv4_bank_ledger WHERE player_uuid=%s",
                            (owner["minecraft_uuid"],)).fetchone() is None


def test_a_failure_between_debit_and_commit_rolls_the_balance_back(pg, monkeypatch):
    owner = make_player(pg, "RollbackOwner")
    target = make_player(pg, "RollbackTarget")
    fund(pg, owner, 200)

    def explode(self):
        raise RuntimeError("simulated failure between debit and commit")

    with monkeypatch.context() as patch:
        patch.setattr(legacy._PooledPgConnection, "commit", explode, raising=False)
        with pytest.raises(RuntimeError):
            transfer(pg, owner, target["minecraft_name"], 50, "atomic-rollback-1")

    assert balance(pg, owner["minecraft_uuid"]) == 200, "списание откатилось"
    assert balance(pg, target["minecraft_uuid"]) == 0
    with pg.auth_conn() as conn:
        assert conn.execute("SELECT 1 FROM cmv4_bank_transfers WHERE idempotency_key=%s",
                            ("player-bank-transfer-atomic-rollback-1",)).fetchone() is None
        assert conn.execute("SELECT 1 FROM cmv4_bank_ledger WHERE player_uuid=%s",
                            (owner["minecraft_uuid"],)).fetchone() is None

    # The connection went back to the pool clean, so the next transfer works.
    retried = transfer(pg, owner, target["minecraft_name"], 50, "atomic-rollback-2")
    assert retried["ok"] is True
    assert balance(pg, owner["minecraft_uuid"]) == 150


def test_the_same_key_replays_once_and_a_changed_payload_conflicts(pg):
    owner = make_player(pg, "ReplayOwner")
    target = make_player(pg, "ReplayTarget")
    fund(pg, owner, 300)

    first = transfer(pg, owner, target["minecraft_name"], 70, "replay-key-1")
    assert first["idempotent"] is False and first["balance"] == 230

    again = transfer(pg, owner, target["minecraft_name"], 70, "replay-key-1")
    assert again["idempotent"] is True
    assert again["txId"] == first["txId"]
    assert again["balance"] == 230
    assert balance(pg, owner["minecraft_uuid"]) == 230, "повтор не списывает второй раз"

    with pytest.raises(legacy.HTTPException) as conflict:
        transfer(pg, owner, target["minecraft_name"], 80, "replay-key-1")
    assert conflict.value.status_code == 409
    assert balance(pg, owner["minecraft_uuid"]) == 230
    with pg.auth_conn() as conn:
        rows = conn.execute("SELECT COUNT(*) AS n FROM cmv4_bank_transfers WHERE idempotency_key=%s",
                            ("player-bank-transfer-replay-key-1",)).fetchone()
    assert int(rows["n"]) == 1


def test_two_simultaneous_transfers_cannot_overdraw_one_balance(pg):
    owner = make_player(pg, "RaceOwner")
    target = make_player(pg, "RaceTarget")
    fund(pg, owner, 100)

    def run(key):
        try:
            return transfer(pg, owner, target["minecraft_name"], 100, key)
        except legacy.HTTPException as error:
            return error

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(run, ("race-key-1", "race-key-2")))

    accepted = [item for item in results if isinstance(item, dict)]
    refused = [item for item in results if isinstance(item, legacy.HTTPException)]
    assert len(accepted) == 1, results
    assert len(refused) == 1 and refused[0].status_code == 409, results
    assert balance(pg, owner["minecraft_uuid"]) == 0
    assert balance(pg, target["minecraft_uuid"]) == 100, "ровно одно списание"


def test_checkout_replays_the_same_purchases_and_conserves_money(pg):
    owner = make_player(pg, "ShopOwner")
    fund(pg, owner, 500)
    before_player = balance(pg, owner["minecraft_uuid"])
    before_treasury = treasury_balance(pg)

    first = checkout(pg, owner, ["test-pickaxe", "test-lamp"], 100, "cart-key-0001")
    assert first["ok"] is True and not first.get("idempotent")
    assert first["totalPriceAr"] == 100
    purchases = {item["itemId"]: item["purchaseId"] for item in first["items"]}
    assert len(set(purchases.values())) == 2
    assert all(item["status"] == "PENDING_DELIVERY" for item in first["items"])

    again = checkout(pg, owner, ["test-lamp", "test-pickaxe"], 100, "cart-key-0001")
    assert again["idempotent"] is True
    assert {item["itemId"]: item["purchaseId"] for item in again["items"]} == purchases

    with pytest.raises(legacy.HTTPException) as other_cart:
        checkout(pg, owner, ["test-pickaxe"], 100, "cart-key-0001")
    assert other_cart.value.status_code == 409

    after_player = balance(pg, owner["minecraft_uuid"])
    after_treasury = treasury_balance(pg)
    assert before_player - after_player == 100
    assert after_treasury - before_treasury == 100, "деньги не появляются и не исчезают"
    with pg.auth_conn() as conn:
        rows = conn.execute("SELECT COUNT(*) AS n FROM artifact_purchases WHERE player_uuid=%s",
                            (owner["minecraft_uuid"],)).fetchone()
        pending = conn.execute("SELECT COUNT(*) AS n FROM artifact_pending_deliveries WHERE player_uuid=%s",
                               (owner["minecraft_uuid"],)).fetchone()
        transfers = conn.execute("SELECT COUNT(*) AS n FROM cmv4_bank_transfers WHERE idempotency_key=%s",
                                 ("web-ar-cart-cart-key-0001",)).fetchone()
    assert int(rows["n"]) == 2, "повтор не создаёт вторую покупку"
    assert int(pending["n"]) == 2, "недоставленное остаётся видимым и повторяемым"
    assert int(transfers["n"]) == 1, "одно списание на корзину, даже после повтора"


def test_a_stale_cart_total_is_refused_before_any_charge(pg):
    owner = make_player(pg, "StaleOwner")
    fund(pg, owner, 500)

    with pytest.raises(legacy.HTTPException) as stale:
        checkout(pg, owner, ["test-pickaxe", "test-lamp"], 90, "cart-key-0002")
    assert stale.value.status_code == 409
    assert balance(pg, owner["minecraft_uuid"]) == 500
    with pg.auth_conn() as conn:
        assert conn.execute("SELECT 1 FROM artifact_purchases WHERE player_uuid=%s",
                            (owner["minecraft_uuid"],)).fetchone() is None