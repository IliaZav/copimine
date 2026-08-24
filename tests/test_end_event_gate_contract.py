from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")


def test_gate_command_exposes_open_and_info_for_two_point_passage() -> None:
    assert "gate open" in MAIN
    assert 'case "info"' in MAIN
    assert "open" in PLUGIN


def test_gate_open_contract_mentions_layered_top_down_execution() -> None:
    assert "GateOpeningPlan" in MAIN
    assert "OPENING" in MAIN
    assert "OPENED" in MAIN
    assert "layersDescending" in MAIN


def test_gate_open_keeps_a_durable_snapshot_before_block_mutation() -> None:
    assert "gateSnapshot" in MAIN
    assert "saveStateSync" in MAIN
    assert "setType(Material.AIR" in MAIN


def test_gate_layout_uses_explicit_unset_status_and_rejects_cross_world_capture() -> None:
    layout_state = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventLayoutState.java").read_text(encoding="utf-8")
    layout_store = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventLayoutStore.java").read_text(encoding="utf-8")
    assert 'gateStatus = normalizeGateStatus(gateStatus)' in layout_state
    assert 'String normalized = status == null ? "" : status.trim().toUpperCase' in layout_state
    assert 'Map.of(), "UNSET", null' in layout_state
    assert 'case "UNSET", "PREVIEW", "OPENING", "OPENED", "RESTORED", "RESTORED_ON_BOOT"' in layout_state
    assert 'yaml.getString("gate.status", "UNSET")' in layout_store
    assert "Gate points must be in one world" in MAIN
    assert "previous.gatePos2()" in MAIN


def test_gate_open_emits_bounded_purple_particle_feedback() -> None:
    assert "Particle.DUST" in MAIN
    assert "DustOptions" in MAIN
    assert "END_EVENT_GATE_LAYER" in MAIN
