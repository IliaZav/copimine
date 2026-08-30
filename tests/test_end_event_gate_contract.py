from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")


def test_gate_command_exposes_open_and_info_for_two_point_passage() -> None:
    assert "gate open" in MAIN
    assert "gate close" in MAIN
    assert 'case "info"' in MAIN
    assert "open" in PLUGIN


def test_gate_open_contract_mentions_layered_top_down_execution() -> None:
    assert "GateOpeningPlan" in MAIN
    assert "OPENING" in MAIN
    assert "OPENED" in MAIN
    assert "layersDescending" in MAIN


def test_gate_close_contract_mentions_reverse_layered_animation_and_durable_state() -> None:
    assert "closeGate" in MAIN
    assert "tickGateClosing" in MAIN
    assert "finishGateClosing" in MAIN
    assert "layersAscending" in MAIN
    assert '"CLOSING"' in MAIN
    assert '"RESTORED"' in MAIN
    assert "BLOCK_RESPAWN_ANCHOR_CHARGE" in MAIN


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
    assert 'case "UNSET", "PREVIEW", "OPENING", "OPENED", "CLOSING", "RESTORED", "RESTORED_ON_BOOT"' in layout_state
    assert 'yaml.getString("gate.status", "UNSET")' in layout_store
    assert "Gate points must be in one world" in MAIN
    assert "previous.gatePos2()" in MAIN


def test_gate_open_emits_bounded_purple_particle_feedback() -> None:
    assert "Particle.DUST" in MAIN
    assert "DustOptions" in MAIN
    assert "END_EVENT_GATE_LAYER" in MAIN


def test_official_victory_starts_the_same_staged_gate_opening() -> None:
    start = MAIN.index("private void checkVictoryRewardCompletion")
    end = MAIN.index("private void resumeVictorySaga", start)
    victory = MAIN[start:end]

    assert 'openGate(null, DEFAULT_GATE_TICKS_PER_LAYER, "official-victory", true)' in victory
    assert "victoryGatePending" in victory
    assert "VICTORY_GATE_OPENING" in MAIN
    assert "tickGateOpening" in MAIN
    assert "finishGateOpening(openingForVictory, snapshot)" in MAIN


def test_victory_gate_is_locked_open_against_manual_close_or_restore() -> None:
    assert "isVictoryGateLockedOpen" in MAIN
    close_start = MAIN.index("private boolean closeGate")
    close_end = MAIN.index("private void tickGateClosing", close_start)
    restore_start = MAIN.index("private void restoreGate", close_end)
    restore_end = MAIN.index("private void restorePersistedGateIfNeeded", restore_start)
    assert "isVictoryGateLockedOpen()" in MAIN[close_start:close_end]
    assert "isVictoryGateLockedOpen()" in MAIN[restore_start:restore_end]
    assert 'DEFAULT_GATE_TICKS_PER_LAYER = 30' in MAIN


def test_each_gate_layer_is_snapshot_checked_before_air_mutation_and_persisted() -> None:
    start = MAIN.index("private void tickGateOpening")
    end = MAIN.index("private void finishGateOpening", start)
    opening = MAIN[start:end]

    assert "String expected = snapshot.get(gateKey(block))" in opening
    assert "sameBlockData(block, expected)" in opening
    assert "block.setType(Material.AIR, false)" in opening
    assert "saveStateSync()" in opening
    assert "abortGateOpening" in opening
