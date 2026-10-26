from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ECONOMY = (ROOT / "copimine-economy-core" / "src" / "me" / "copimine" / "economycore" / "CopiMineEconomyCore.java").read_text(encoding="utf-8")
NARCOTICS = (ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "narcotics" / "CopiMineNarcotics.java").read_text(encoding="utf-8")


def test_financial_executor_drains_before_shutdown():
    section = ECONOMY[ECONOMY.index("public void onDisable"):ECONOMY.index("public EconomyService economyService")]
    assert "dbExecutor.shutdown();" in section
    assert "awaitTermination" in section
    assert "shutdownNow();" in section


def test_managed_account_transfers_reject_the_same_account_before_writing_balances():
    player_to_managed = ECONOMY[
        ECONOMY.index("private TxnResult transferPlayerToAccount"):ECONOMY.index(
            "private TxnResult transferFromAccount"
        )
    ]
    managed_to_player = ECONOMY[
        ECONOMY.index("private TxnResult transferFromAccount"):ECONOMY.index(
            "private TxnResult stealFromPlayerAccount"
        )
    ]

    assert "if (fromId.equals(resolvedToId))" in player_to_managed
    assert player_to_managed.index("if (fromId.equals(resolvedToId))") < player_to_managed.index("long fromBefore;")
    assert "if (resolvedFromId.equals(toId))" in managed_to_player
    assert managed_to_player.index("if (resolvedFromId.equals(toId))") < managed_to_player.index("long fromBefore;")


def test_narcotics_partial_start_is_safe_and_refunds_are_crash_idempotent():
    assert "if (cauldronService != null)" in NARCOTICS
    assert "if (database != null)" in NARCOTICS
    assert "pendingRefundKey" in NARCOTICS
    assert "hasPendingRefundMarker" in NARCOTICS
    assert "removePendingRefundMarkers" in NARCOTICS
    assert "database.completePendingRefund(row.id())" in NARCOTICS
