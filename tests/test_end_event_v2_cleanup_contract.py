from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
JOURNAL = (ROOT / "copimine-end-event/src/me/copimine/endevent/HazardMutationJournal.java").read_text(
    encoding="utf-8"
)


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_v2_objective_cleanup_cancels_every_generation_scoped_runtime():
    body = _body("private void clearWaveObjectiveState()", "private void scheduleOfficialBossSpawn")
    for marker in (
        "cancelV2WaveSpawnTask();",
        "clearV2SafeZoneVisuals();",
        "v2RingVisuals",
        "v2RingVisualGroups",
        "v2SafeZoneCells",
        "v2BarrierCells",
        "v2CarrierChargeUuid = null",
        "v2FogPhase = V2FogPhase.COMBAT",
        "v2WaveSpawnSchedule = List.of()",
    ):
        assert marker in body


def test_hazard_journal_can_restore_v2_temporary_blocks():
    for marker in (
        "EMERALD_BARRIER",
        "BARRIER",
        "ICE",
        "isEmeraldBarrierMutation",
        "isBarrierMutation",
        "isIceMutation",
    ):
        assert marker in JOURNAL
    recovery = _body("private void recoverHazardJournal()", "private void applySnapshot")
    assert "entry.isEmeraldBarrierMutation()" in recovery
    assert "Material.EMERALD_BLOCK" in recovery
    assert "Material.BARRIER" in recovery
    assert "entry.isIceMutation()" in recovery
    assert "Material.ICE" in recovery
