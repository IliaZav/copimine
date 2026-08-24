from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")


def test_gate_point_selection_starts_a_generation_bound_particle_preview() -> None:
    start = MAIN.index('case "pos1", "pos2"')
    end = MAIN.index('case "info"', start)
    point_handler = MAIN[start:end]
    assert "startGateSelectionPreview" in point_handler
    assert "END_EVENT_GATE_SELECTION_PREVIEW" in MAIN
    assert "gateSelectionPreviewTask" in MAIN


def test_gate_preview_does_not_replace_vanilla_blocks() -> None:
    start = MAIN.index("private void previewGate")
    end = MAIN.index("private boolean openGate", start)
    preview = MAIN[start:end]
    assert "captureGateSnapshot" in preview
    assert "saveStateSync" in preview
    assert "startGateSelectionPreview" in preview
    assert "block.setType(Material.PURPLE_STAINED_GLASS" not in preview


def test_gate_selection_particles_cover_one_point_then_the_bounded_cuboid() -> None:
    for marker in (
        "private void startGateSelectionPreview",
        "private void cancelGateSelectionPreview",
        "private void drawGateSelectionPreview",
        "private void drawGateBlockOutline",
        "GateOpeningPlan.from",
        "Particle.END_ROD",
        "Particle.DUST",
        "generation != previewGeneration",
        "MAX_GATE_VOLUME",
    ):
        assert marker in MAIN


def test_second_gate_point_highlights_every_coordinate_staged_opening_processes() -> None:
    start = MAIN.index("private void drawGateSelectionPreview")
    end = MAIN.index("private void drawArenaBoundaryFrame", start)
    selection = MAIN[start:end]

    # The preview must describe the same complete coordinate set as the
    # opening plan. Boundary-only sampling hides interior blocks that will be
    # removed later and makes the two-point command misleading in-game.
    assert "for (GateOpeningPlan.Layer layer : plan.layersDescending())" in selection
    assert "drawGateBlockOutline(world, point.x(), point.y(), point.z())" in selection
    assert "!world.getBlockAt(point.x(), point.y(), point.z()).getType().isAir()" in selection
    assert "isGateBoundaryPoint" not in selection
    assert "MAX_GATE_SELECTION_BLOCK_HIGHLIGHTS" not in selection
    assert "solidBlocks" in MAIN
    assert "volume=" in MAIN
    assert "все заполненные блоки" in MAIN


def test_gate_selection_preview_is_cancelled_with_session_tasks() -> None:
    start = MAIN.index("private void cancelSessionTasks")
    end = MAIN.index("private void cancelCreativeTestTask", start)
    cancellation = MAIN[start:end]
    assert "cancelGateSelectionPreview()" in cancellation
