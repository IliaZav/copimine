from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ECONOMY = (ROOT / "copimine-economy-core" / "src" / "me" / "copimine" / "economycore" / "CopiMineEconomyCore.java").read_text(encoding="utf-8")
NARCOTICS = (ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "narcotics" / "CopiMineNarcotics.java").read_text(encoding="utf-8")


def test_financial_executor_drains_before_shutdown():
    section = ECONOMY[ECONOMY.index("public void onDisable"):ECONOMY.index("public EconomyService economyService")]
    assert "dbExecutor.shutdown();" in section
    assert "awaitTermination" in section
    assert "shutdownNow();" in section


def test_narcotics_partial_start_is_safe_and_refunds_are_crash_idempotent():
    assert "if (cauldronService != null)" in NARCOTICS
    assert "if (database != null)" in NARCOTICS
    assert "pendingRefundKey" in NARCOTICS
    assert "hasPendingRefundMarker" in NARCOTICS
    assert "removePendingRefundMarkers" in NARCOTICS
    assert "database.completePendingRefund(row.id())" in NARCOTICS


def test_narcotics_visual_bridge_does_not_send_after_plugin_disable():
    bridge = (ROOT / "copimine-narcotics" / "src" / "me" / "copimine" / "clientbridge" / "ClientVisualEffectService.java").read_text(encoding="utf-8")
    clear_method = bridge[bridge.index("public void clearVisuals(Player player, String reason)"):bridge.index("public void shutdown()")]
    assert "player.isOnline() && plugin.isEnabled()" in clear_method
