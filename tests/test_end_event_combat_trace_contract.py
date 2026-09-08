from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_trace_observes_damage_before_and_after_all_event_handlers() -> None:
    assert "CombatTraceService" in MAIN
    assert "pendingCombatTraces" in MAIN
    assert "public void onCombatTraceOpen(EntityDamageEvent event)" in MAIN
    assert "public void onCombatTraceClose(EntityDamageEvent event)" in MAIN

    open_handler = _body(
        "public void onCombatTraceOpen(EntityDamageEvent event)",
        "public void onCombatTraceClose(EntityDamageEvent event)",
    )
    close_handler = _body(
        "public void onCombatTraceClose(EntityDamageEvent event)",
        "public void onCoreOverlayDamage",
    )
    assert "@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)\n    public void onCombatTraceOpen" in MAIN
    assert "CombatTraceRecord.open" in open_handler
    assert "event.isCancelled()" in open_handler
    assert "Bukkit.getCurrentTick()" in open_handler
    assert "getAverageTickTime" in open_handler
    assert "@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)\n    public void onCombatTraceClose" in MAIN
    assert "event.isCancelled()" in close_handler
    assert "combatTrace.record" in close_handler
    assert "runTask(this" in close_handler
    assert "toLogLine" in close_handler


def test_trace_keeps_boss_and_wave_damage_in_one_common_pipeline() -> None:
    common = _body("private boolean shouldTraceCombat", "@EventHandler(priority = EventPriority.LOWEST")
    assert "EVENT_KIND_BOSS" in common
    assert "isWaveCombatKind" in common
    assert "victim instanceof Player" in common
