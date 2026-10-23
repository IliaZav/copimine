from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
MATRIX = ROOT / "docs" / "end-rift" / "final-26.3" / "SOUND_CUE_MATRIX.json"


def method_body(name: str, source: str) -> str:
    start = source.index(f"private void {name}(")
    end = source.index("\n    private ", start + 20)
    return source[start:end]


def test_named_trial_completion_sound_fires_only_on_first_completion() -> None:
    source = EVENT.read_text(encoding="utf-8")
    body = method_body("finishNamedRealitySplitTrial", source)

    assert "boolean newlyCompleted = realitySplitChamberController.markChamberComplete" in body
    assert "if (!saveStateSync())" in body
    assert body.index("if (!saveStateSync())") < body.index("if (newlyCompleted)")
    assert "if (phase == EventPhase.RECOVERY_REQUIRED) return;" in body
    assert body.index("if (phase == EventPhase.RECOVERY_REQUIRED) return;") < body.index("if (newlyCompleted)")
    assert "for (UUID owner : trial.owners())" in body
    assert "player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE" in body
    assert "SoundCategory.PLAYERS, 0.7F, 1.2F" in body
    assert body.count("playSound(") == 1


def test_sound_matrix_matches_the_named_trial_completion_cue() -> None:
    import json

    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    cue = next(item for item in matrix["cues"] if item["id"] == "W7-TRIAL-COMPLETE")
    assert cue["sourceAnchor"] == "finishNamedRealitySplitTrial"
    assert cue["soundKey"] == "BLOCK_BEACON_ACTIVATE"
    assert cue["audience"] == "room-owners"
    assert cue["volume"] == 0.7
    assert cue["pitch"] == 1.2
