"""Keep delayed Wave 2 phantom callbacks from clearing a newer activation."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_stale_warning_callback_returns_before_releasing_shared_task_reference() -> None:
    source = EVENT.read_text(encoding="utf-8")
    callback = method(source, "private void finishWaveTwoPhantomWarning(")

    ownership_guard = callback.index("if (!ownsCurrentWaveTwoPhantomActivation(")
    task_release = callback.index("waveTwoPhantomWarningTask = null;")
    assert ownership_guard < task_release, (
        "an old timer must not detach the current activation's cancellable task"
    )


def test_cancellation_requires_matching_event_player_cycle_and_phantom_owner() -> None:
    source = EVENT.read_text(encoding="utf-8")
    cancellation = method(source, "private void cancelWaveTwoPhantomActivation(")

    assert cancellation.index("if (!ownsCurrentWaveTwoPhantomActivation(") < cancellation.index(
        "sendWaveTwoPhantomActivation(player, false"
    ), "a stale callback must not send CLEAR in the current bridge context"
    assert "waveTwoPhantomActivationPlayerUuid = null;" in cancellation

    owner = method(source, "private boolean ownsCurrentWaveTwoPhantomActivation(")
    for owner_check in (
        "WaveTwoPhantomMaterializationPolicy.ownsActivation",
        "Objects.equals(eventId, fence.eventId())",
        "generation == fence.generation()",
        "waveTwoPhantomActivationGroup == fence.groupNumber()",
        "Objects.equals(waveTwoPhantomActivationPlayerUuid, playerUuid)",
        "waveTwoPhantomActivationCycle == cycle",
        "waveTwoMarkCycle == cycle",
        "Objects.equals(waveTwoMarkedPlayerUuid, playerUuid)",
    ):
        assert owner_check in owner


def test_gravity_anchor_requires_projected_step_inside_configured_arena() -> None:
    source = EVENT.read_text(encoding="utf-8")
    movement = method(source, "private boolean hasClearAnchorMovement(")

    assert "Location current = player.getLocation();" in movement
    assert "Location projected = current.clone().add(velocity);" in movement
    assert "isArenaLocation(projected)" in movement
    assert "GravityAnchorLandingPolicy.shouldApplyImpulse" in movement
