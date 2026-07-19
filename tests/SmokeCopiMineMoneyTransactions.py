"""Small dependency-free transaction smoke tests for the web money flows.

The production backend uses PostgreSQL on Ubuntu.  These tests replace only
the connection boundary, then assert that the real transaction functions
insert their durable rows and call commit before returning.
"""
from __future__ import annotations

import importlib.util
import os
import sys
from contextlib import contextmanager
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "admin-web" / "backend"
spec = importlib.util.spec_from_file_location(
    "backend",
    BACKEND / "__init__.py",
    submodule_search_locations=[str(BACKEND)],
)
assert spec and spec.loader
package = importlib.util.module_from_spec(spec)
sys.modules["backend"] = package
spec.loader.exec_module(package)
import backend.main as app  # type: ignore  # noqa: E402


class Cursor:
    def __init__(self, row=None):
        self.row = row

    def fetchone(self):
        return self.row

    def fetchall(self):
        return []


class FakeConn:
    def __init__(self, *, donation_balance=100, ar_balance=20, session_row=None):
        self.donation_balance = donation_balance
        self.ar_balance = ar_balance
        self.session_row = session_row
        self.inserts: list[str] = []
        self.commits = 0

    def execute(self, sql, params=()):
        text = " ".join(str(sql).split()).lower()
        if text.startswith("select id,player_uuid,item_id,status"):
            return Cursor(None)
        if "from donation_payment_sessions where id=%s for update" in text:
            return Cursor(self.session_row)
        if "select status from donation_item_claims" in text:
            return Cursor(None)
        if "select coalesce(balance,0) as balance from donation_accounts" in text:
            return Cursor({"balance": self.donation_balance})
        if "select * from cmv4_bank_accounts where account_id" in text:
            return Cursor({"account_id": "ar:test-uuid", "balance": self.ar_balance})
        if "select tx_id,balance_after from cmv4_bank_ledger" in text:
            return Cursor(None)
        if "select id,balance_after from donation_balance_ledger" in text:
            return Cursor(None)
        if text.startswith("insert"):
            self.inserts.append(text)
        return Cursor(None)

    def commit(self):
        self.commits += 1


def install_common_stubs(conn: FakeConn):
    app.pg_ready = lambda: True
    app.donation_now_ms = lambda: 1_700_000_000_000
    app.normalize_donation_player_target = lambda uuid, name: ("test-uuid", name or "TestPlayer")
    app.donation_catalog_snapshot_sync = lambda: {
        "byId": {
            "test_item": {
                "item_id": "test_item",
                "enabled": True,
                "price_donation": 25,
            }
        }
    }
    app.admin_gift_catalog_snapshot_sync = lambda: {
        "categories": {
            "DONATION": [{"item_id": "test_item", "source": "DONATION_SHOP"}],
            "AR": [],
            "HIDDEN": [],
        }
    }
    app.verify_bank_pin = lambda *_args: None
    app.lock_donation_idempotency_sync = lambda *_args: None
    app.lock_donation_entitlement_sync = lambda *_args: None
    app.donation_entitlement_conflict_sync = lambda *_args: False
    app.ensure_donation_account_row = lambda *_args: None
    app.ensure_v4_schema = lambda *_args: None
    app.advisory_lock = lambda *_args: None
    app.ensure_player_bank_account = lambda *_args: {"account_id": "ar:test-uuid"}
    app.audit_event = lambda *_args, **_kwargs: None
    app.append_panel_event = lambda *_args, **_kwargs: None

    @contextmanager
    def fake_auth_conn():
        yield conn

    app.auth_conn = fake_auth_conn


def test_donation_purchase_and_test_purchase_commit():
    conn = FakeConn(donation_balance=100)
    install_common_stubs(conn)
    result = app.purchase_donation_item_sync(
        "test-uuid", "TestPlayer", "test_item", "1234", "TestPlayer", "test", "purchase-key-1"
    )
    assert result["status"] == "CLAIM_PENDING"
    assert conn.commits == 1, "player purchase must commit before returning"
    assert sum("insert into donation_purchases" in row for row in conn.inserts) == 1
    assert sum("insert into donation_item_claims" in row for row in conn.inserts) == 1

    conn = FakeConn()
    install_common_stubs(conn)
    app.DONATION_PROVIDER = "MOCK_SBP"
    session = app.create_donation_session_sync("test-uuid", "TestPlayer", 50, "TestPlayer", "smoke", "session-key-1")
    assert session["status"] == "PENDING"
    assert conn.commits == 1, "payment session must commit before a status refresh"
    assert sum("insert into donation_payment_sessions" in row for row in conn.inserts) == 1

    conn = FakeConn(
        session_row={
            "id": "don-session-1", "player_uuid": "test-uuid", "player_name": "TestPlayer",
            "provider": "MOCK_SBP", "status": "PENDING", "expires_at": 0,
            "donation_units": 50, "amount": 50, "paid_at": 0,
        },
        donation_balance=10,
    )
    install_common_stubs(conn)
    paid = app.mark_donation_session_paid_sync("don-session-1", "admin", "smoke")
    assert paid["status"] == "PAID"
    assert conn.commits == 1, "payment settlement must commit the ledger and session status"

    conn = FakeConn(
        session_row={
            "id": "don-session-2", "player_uuid": "test-uuid", "player_name": "TestPlayer",
            "provider": "MOCK_SBP", "status": "PENDING", "expires_at": 0,
        },
    )
    install_common_stubs(conn)
    cancelled = app.cancel_donation_session_sync("don-session-2", "admin", "smoke")
    assert cancelled["status"] == "CANCELLED"
    assert conn.commits == 1, "payment cancellation must commit the session status"

    conn = FakeConn()
    install_common_stubs(conn)
    result = app.admin_create_donation_test_purchase_sync("", "TestPlayer", "test_item", "admin")
    assert result["status"] == "CLAIM_PENDING"
    assert conn.commits == 1, "admin test purchase must commit before returning"
    assert sum("insert into donation_purchases" in row for row in conn.inserts) == 1
    assert sum("insert into donation_item_claims" in row for row in conn.inserts) == 1


def test_combined_admin_topup_is_atomic():
    conn = FakeConn(donation_balance=5, ar_balance=20)
    install_common_stubs(conn)
    result = app.admin_topup_balances_sync(
        "", "TestPlayer", 7, 3, "ar-test", "don-test", "admin", "combined-key-1"
    )
    assert result["arBalanceAfter"] == 27
    assert result["donationBalanceAfter"] == 8
    assert conn.commits == 1, "AR and donation topup must share one commit"
    assert sum("insert into cmv4_bank_ledger" in row for row in conn.inserts) == 1
    assert sum("insert into donation_balance_ledger" in row for row in conn.inserts) == 1

    conn = FakeConn()
    install_common_stubs(conn)
    gift = app.admin_create_artifact_gift_sync("", "TestPlayer", "test_item", "DONATION", "admin", "smoke", "gift-key-1")
    assert gift["status"] == "CLAIM_PENDING"
    assert conn.commits == 1, "administrative donation gift must commit its pending claim"


if __name__ == "__main__":
    test_donation_purchase_and_test_purchase_commit()
    test_combined_admin_topup_is_atomic()
    print("CopiMine money transaction smoke tests passed.")
