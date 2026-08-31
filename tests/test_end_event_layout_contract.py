from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
STORE = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventLayoutStore.java").read_text(encoding="utf-8")


def test_layout_commands_are_durable_and_bounded() -> None:
    for command in ("pos1", "pos2", "preview", "restore", "portalroom"):
        assert command in MAIN
    assert "event-layout.yml" in STORE
    assert "ATOMIC_MOVE" in STORE
    assert "16_384L" in MAIN
    assert "gateSnapshot" in MAIN


def test_gate_preview_has_snapshot_before_particle_preview_and_boot_recovery() -> None:
    capture = MAIN.index("Map<String, String> snapshot")
    preview = MAIN.index("startGateSelectionPreview", capture)
    assert capture < preview
    assert "block.setType(Material.PURPLE_STAINED_GLASS" not in MAIN[MAIN.index("private void previewGate"):MAIN.index("private boolean openGate")]
    assert "restorePersistedGateIfNeeded" in MAIN
    assert "RESTORED_ON_BOOT" in MAIN
    assert "restoreGateSnapshot" in MAIN
    assert "CLOSING" in MAIN


def test_boot_gate_recovery_refreshes_the_layout_write_cache() -> None:
    recovery = MAIN[MAIN.index("private void restorePersistedGateIfNeeded"):
                    MAIN.index("private String gateKey", MAIN.index("private void restorePersistedGateIfNeeded"))]
    assert "layoutStore.save(layoutState)" in recovery
    assert "persistedLayoutState = layoutState" in recovery


def test_portal_destination_uses_persisted_override_and_safe_validation() -> None:
    assert "layoutState.portalRoom()" in MAIN
    assert "portalRoom()" in MAIN
    assert "feet.isPassable()" in MAIN
    assert "head.isPassable()" in MAIN
    assert "floor.getType().isSolid()" in MAIN


def test_arena_and_gate_pos_commands_store_the_block_under_the_crosshair() -> None:
    layout = MAIN[MAIN.index("private void handleLayout"):MAIN.index("private EventLayoutState.Point pointAt")]

    assert layout.count("pointAtTargetBlock(player)") >= 2
    assert "pointAt(player.getLocation())" not in layout
    target_helper = MAIN[MAIN.index("private EventLayoutState.Point pointAtTargetBlock"):
                         MAIN.index("private boolean isConsoleSetupSender")]
    assert "player.getTargetBlockExact(8)" in target_helper
    assert "target.getX()" in target_helper
    assert "target.getY()" in target_helper
    assert "target.getZ()" in target_helper


def test_gate_points_live_probe_uses_player_look_packets_and_restores_layout() -> None:
    bot = (ROOT / "tests/LocalEndRiftGatePointsBot.js").read_text(encoding="utf-8")
    runner = (ROOT / "tests/RunEndRiftGatePointsLive.ps1").read_text(encoding="utf-8")
    for marker in (
        "await bot.lookAt(position.offset(0.5, 0.5, 0.5), false)",
        "bot.chat(`/cmend gate pos${index}`)",
        "GATE_TARGET_${index}",
        "resource_pack_receive",
    ):
        assert marker in bot
    for marker in (
        "GATE_POINTS_COMMANDS_SENT",
        "cmend gate info",
        "server-side-crosshair",
        "cmend gate delete confirm",
        "cmend core remove confirm",
        "finally",
    ):
        assert marker in runner
    # Paper cannot resolve a never-seen offline username when the probe is
    # opped before its first join.  Re-apply OP after the bot is online so the
    # player-side command really reaches the End Event permission guard.
    assert runner.count('Invoke-LocalRcon "op $BotName"') >= 2
    assert "Start-Sleep -Seconds 5" in runner


def test_core_removal_gui_probe_reapplies_op_after_authentication() -> None:
    runner = (ROOT / "tests/RunEndRiftCoreRemovalGuiLive.ps1").read_text(encoding="utf-8")
    assert runner.count('Invoke-LocalRcon "op $BotName"') >= 2
    assert "Start-Sleep -Seconds 5" in runner
