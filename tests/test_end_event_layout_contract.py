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


def test_gate_preview_has_snapshot_before_mutating_and_boot_recovery() -> None:
    capture = MAIN.index("Map<String, String> snapshot")
    mutate = MAIN.index("block.setType(Material.PURPLE_STAINED_GLASS", capture)
    assert capture < mutate
    assert "restorePersistedGateIfNeeded" in MAIN
    assert "RESTORED_ON_BOOT" in MAIN
    assert "restoreGateSnapshot" in MAIN


def test_portal_destination_uses_persisted_override_and_safe_validation() -> None:
    assert "layoutState.portalRoom()" in MAIN
    assert "portalRoom()" in MAIN
    assert "feet.isPassable()" in MAIN
    assert "head.isPassable()" in MAIN
    assert "floor.getType().isSolid()" in MAIN
