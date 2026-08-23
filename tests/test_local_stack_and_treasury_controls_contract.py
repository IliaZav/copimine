from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")
FRONTEND = (ROOT / "admin-web" / "frontend" / "assets" / "js" / "admin" / "commerce-pages.js").read_text(encoding="utf-8")
LOCAL_STACK = (ROOT / "scripts" / "local-stack.ps1").read_text(encoding="utf-8")


def test_local_stack_uses_isolated_database_and_waits_for_slow_minecraft_rcon():
    assert "$PostgresPort = 55432" in LOCAL_STACK
    assert "DATABASE_URL=postgresql://copimine:" in LOCAL_STACK
    assert "PostgresData = Join-Path $Runtime 'postgres-data'" in LOCAL_STACK
    assert "function Wait-PostgresQuery" in LOCAL_STACK
    assert "Wait-PostgresQuery -BinDir $bin" in LOCAL_STACK
    assert re.search(
        r"Wait-TcpPort -TargetHost '127\.0\.0\.1' -Port \$RconPort -Expected \$true -TimeoutSeconds (?:120|180|240)",
        LOCAL_STACK,
    )


def test_local_stack_retries_postgres_consistent_recovery_state():
    """A normal PostgreSQL crash-recovery probe must stay inside the retry window."""
    assert "function Wait-PostgresReady" in LOCAL_STACK
    assert "database system is not yet accepting connections" in LOCAL_STACK
    assert "consistent recovery state has not been reached" in LOCAL_STACK


def test_local_stack_serves_the_built_resource_pack_to_minecraft():
    assert "CopiMineResourcePack.zip" in LOCAL_STACK
    assert "resourcepacks\\build" in LOCAL_STACK
    assert "Set-ServerProperty -Key 'resource-pack' -Value \"http://127.0.0.1:$WebsitePort/resourcepacks/CopiMineResourcePack.zip\"" in LOCAL_STACK
    assert "Set-ServerProperty -Key 'resource-pack-sha1' -Value $resourcePackSha1" in LOCAL_STACK


def test_admin_treasury_controls_have_confirmed_balance_edit_and_ledger_removal():
    for marker in (
        "class AdminTreasuryBalanceSetIn(BaseModel):",
        "class AdminTreasuryLedgerDeleteIn(BaseModel):",
        "def admin_set_treasury_balance_sync(",
        "def admin_delete_treasury_ledger_sync(",
        '@app.post("/api/admin/economy/treasury/set-balance")',
        '@app.delete("/api/admin/economy/treasury/ledger/{tx_id}")',
        'require_sensitive_confirm(request, "TREASURY_BALANCE_SET")',
        'require_sensitive_confirm(request, "TREASURY_LEDGER_DELETE")',
        "status='VOIDED'",
        'audit_event(actor, "treasury.ledger.delete"',
    ):
        assert marker in BACKEND, marker

    for marker in (
        "adminSetTreasuryBalance",
        "adminDeleteTreasuryLedger",
        "treasuryBalanceValue",
        "treasuryLedger",
        "/api/admin/economy/treasury/set-balance",
        "/api/admin/economy/treasury/ledger/",
        "TREASURY_BALANCE_SET",
        "TREASURY_LEDGER_DELETE",
    ):
        assert marker in FRONTEND, marker
