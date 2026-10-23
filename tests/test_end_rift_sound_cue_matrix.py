import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MATRIX = ROOT / "docs" / "end-rift" / "final-26.3" / "SOUND_CUE_MATRIX.json"
SOURCE = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
REQUIREMENT_IDS = [f"SND-{index:02d}" for index in range(1, 11)]
CATEGORIES = {"AMBIENT", "BLOCKS", "HOSTILE", "MUSIC", "NEUTRAL", "PLAYERS", "MASTER", "UI", "VOICE"}
STAGES = {"ambient", "telegraph", "impact", "completion", "failure"}
PRIORITIES = {"ambient", "normal", "telegraph", "impacts"}
CUE_FIELDS = {
    "id", "requirementIds", "wave", "event", "stage", "sourceAnchor", "soundKey",
    "category", "audience", "position", "volume", "pitch", "priority", "cooldownTicks",
    "dedupKey", "emission", "loop", "stopPolicy", "respectsClientVolume",
}


def load_matrix() -> dict:
    return json.loads(MATRIX.read_text(encoding="utf-8"))


def test_sound_cue_matrix_schema_and_limits() -> None:
    matrix = load_matrix()
    assert matrix["schemaVersion"] == 1
    assert matrix["nativeAudioVerified"] is False
    assert matrix["coverage"] == {
        "waves": [1, 2, 3, 4, 5, 6, 7],
        "anvilVolleys": 2,
        "wave7Trials": ["WARDEN", "PERSONAL_ECHO", "MARKSMAN", "ARCHMAGE"],
    }

    requirements = matrix["requirements"]
    assert [entry["id"] for entry in requirements] == REQUIREMENT_IDS
    for entry in requirements:
        assert entry["status"] in {"PASS", "FAIL", "BLOCKED", "NOT RUN", "N/A"}
        if entry["status"] == "PASS":
            assert (ROOT / entry["checkedBy"]).is_file()
        if entry["status"] == "BLOCKED":
            assert entry["blockedReason"].strip()

    cues = matrix["cues"]
    assert len(cues) >= 24
    assert len({cue["id"] for cue in cues}) == len(cues)
    assert len({cue["dedupKey"] for cue in cues}) == len(cues)
    for cue in cues:
        assert CUE_FIELDS <= set(cue), cue["id"]
        assert cue["wave"] in {1, 2, 3, 4, 5, 6, 7}
        assert cue["stage"] in STAGES
        assert cue["category"] in CATEGORIES
        assert cue["priority"] in PRIORITIES
        assert 0.0 < cue["volume"] <= 1.0
        assert 0.5 <= cue["pitch"] <= 2.0
        assert cue["cooldownTicks"] >= 0
        assert cue["emission"] == "server"
        assert cue["loop"] is False
        assert cue["stopPolicy"] in {"one-shot", "event-cleanup"}
        assert cue["respectsClientVolume"] is True
        assert cue["audience"].strip()
        assert cue["position"].strip()


def test_sound_cues_are_wired_to_known_source_and_vanilla_sounds() -> None:
    matrix = load_matrix()
    source = SOURCE.read_text(encoding="utf-8")
    source_sounds = set(re.findall(r"Sound\.([A-Z0-9_]+)", source))
    assert len(source_sounds) > 30

    for cue in matrix["cues"]:
        assert cue["sourceAnchor"] in source, cue["id"]
        assert cue["soundKey"] in source_sounds, cue["id"]
        fallback = cue.get("fallbackSoundKey")
        if fallback is not None:
            assert fallback in source_sounds
            assert cue["soundKey"] != fallback or "bossCueSound" in source


def test_required_coverage_and_requirement_links() -> None:
    matrix = load_matrix()
    cues = matrix["cues"]
    assert {cue["wave"] for cue in cues} == {1, 2, 3, 4, 5, 6, 7}
    ids = {cue["id"] for cue in cues}
    assert {
        "W1-CARRIER-DELIVERED", "W2-PHANTOM-WARNING", "W2-PHANTOM-MATERIALIZE",
        "W3-CAPTURE-MILESTONE", "W3-PORTAL-AMBIENT", "W4-FIREBALL-LAUNCH",
        "W4-OBELISK-COLLAPSE", "W5-FOG-COMPLETE",
    } <= ids

    source = SOURCE.read_text(encoding="utf-8")
    assert 'playWaveFeedback("fog-complete"' in source
    assert 'case "hunt-complete", "fog-resume", "fog-complete"' in source
    assert "marked.playSound(marked.getLocation(), Sound.ENTITY_ENDERMAN_STARE" in source
    assert "marked.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT" in source

    linked = {requirement_id for cue in cues for requirement_id in cue["requirementIds"]}
    assert set(REQUIREMENT_IDS[:9]) <= linked
    assert all(requirement_id in REQUIREMENT_IDS for requirement_id in linked)

    volleys = [cue for cue in cues if cue["event"].startswith("anvil-volley-")]
    first = [cue for cue in volleys if cue["event"] == "anvil-volley-first-impact"]
    second = [cue for cue in volleys if cue["event"] == "anvil-volley-second-impact"]
    assert len(first) == 1 and len(second) == 1
    assert first[0]["soundKey"] == second[0]["soundKey"] == "BLOCK_ANVIL_LAND"
    assert first[0]["id"] != second[0]["id"]

    trials = {cue.get("trial") for cue in cues if cue["wave"] == 7}
    assert {"WARDEN", "PERSONAL_ECHO", "MARKSMAN", "ARCHMAGE"} <= trials

    # Warning cues must be emitted before their impact/completion counterpart.
    warning_stages = {
        cue["event"]: cue["stage"]
        for cue in cues
        if cue["stage"] in {"telegraph", "impact", "completion"}
    }
    assert warning_stages["gravity-anchor-warning"] == "telegraph"
    assert warning_stages["anvil-volley-warning"] == "telegraph"
    assert warning_stages["warden-warning"] == "telegraph"
    assert warning_stages["marksman-warning"] == "telegraph"
    assert warning_stages["archmage-warning"] == "telegraph"


def test_anvil_second_volley_warning_is_distinct() -> None:
    matrix = load_matrix()
    warnings = [cue for cue in matrix["cues"] if cue["event"].startswith("anvil-volley-warning")]
    assert len(warnings) == 2
    first, second = sorted(warnings, key=lambda cue: cue["pitch"])
    assert first["event"] == "anvil-volley-warning"
    assert second["event"] == "anvil-volley-warning-second"
    assert first["id"] != second["id"]
    assert first["dedupKey"] != second["dedupKey"]
    assert first["pitch"] < second["pitch"]
    assert first["volume"] == second["volume"] == 0.7
    for cue in warnings:
        assert cue["sourceAnchor"] == "showAnvilWarning"
        assert cue["soundKey"] == "BLOCK_ANVIL_PLACE"
    source = SOURCE.read_text(encoding="utf-8")
    assert "volley.volleyIndex() >= 2 ? 1.05F : 0.75F" in source


def test_no_client_duplicate_and_no_owned_loop() -> None:
    matrix = load_matrix()
    for cue in matrix["cues"]:
        assert cue["emission"] == "server"
        assert "client" not in cue["dedupKey"].lower()
        assert cue["loop"] is False
    # Music is a separate phase-track contract; gameplay cues never become loops.
    assert matrix["nativeAudioVerified"] is False
