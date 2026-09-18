from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
POLICY = (SRC / "domain" / "RitualSphereEncounterPolicy.java").read_text(encoding="utf-8")
SNAPSHOT = (SRC / "domain" / "RitualSphereEncounterSnapshot.java").read_text(encoding="utf-8")
ENCOUNTER = (SRC / "runtime" / "encounter" / "RitualSphereEncounter.java").read_text(encoding="utf-8")
EVENT = (SRC / "CopiMineEndEvent.java").read_text(encoding="utf-8")


def test_ritual_sphere_policy_has_one_waiting_to_capture_state_boundary() -> None:
    assert "public static State waiting(long generation, int participants)" in POLICY
    assert "public static State capture(State state, UUID prisoner, long nowMillis)" in POLICY
    assert "public static State reassignCapturedPrisoner(State state, UUID prisoner)" in POLICY
    assert "public static boolean hasCaptured(State state)" in POLICY
    assert "return capture(waiting(generation, participants), prisoner, nowMillis);" in POLICY
    assert "prisoner == null" in POLICY
    assert "lastDrainMillis < 0L" in POLICY
    assert "return hasCaptured(state) &&" in POLICY


def test_ritual_sphere_adapters_transition_and_complete_from_policy_state() -> None:
    assert "state = RitualSphereEncounterPolicy.waiting(" in ENCOUNTER
    assert "state = RitualSphereEncounterPolicy.capture(state, prisoner, nowMillis);" in ENCOUNTER
    assert "!RitualSphereEncounterPolicy.hasCaptured(state)" in ENCOUNTER
    assert "RitualSphereEncounterPolicy.hasCaptured(state)" in ENCOUNTER

    assert "ritualSphereState = RitualSphereEncounterPolicy.waiting(" in EVENT
    assert "ritualSphereState = RitualSphereEncounterPolicy.capture(" in EVENT
    assert "ritualSphereState = RitualSphereEncounterPolicy.reassignCapturedPrisoner(" in EVENT
    assert "!RitualSphereEncounterPolicy.hasCaptured(ritualSphereState)" in EVENT
    assert "RitualSphereEncounterPolicy.hasCaptured(ritualSphereState)" in EVENT
    assert "private UUID ritualPrisonerUuid" not in EVENT
    assert "ritualPrisonerUuid =" not in EVENT
    assert "return ritualSphereState == null ? null : ritualSphereState.prisoner();" in EVENT


def test_ritual_sphere_snapshot_codec_preserves_waiting_and_captured_forms() -> None:
    assert "state.prisoner() == null ? \"\" : state.prisoner().toString()" in SNAPSHOT
    assert "parseNullableUuid" in SNAPSHOT
    assert "RitualSphereEncounterPolicy.waiting(generation, participants)" in SNAPSHOT
    assert "RitualSphereEncounterPolicy.State" in SNAPSHOT
