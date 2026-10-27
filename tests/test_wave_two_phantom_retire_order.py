"""A Wave 2 phantom may only be retired once the real Enderman exists."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_phantom_retirement_waits_for_a_confirmed_spawn():
    source = SOURCE.read_text(encoding="utf-8")
    finish = _method(source, "private void finishWaveTwoPhantomWarning(")
    assert "private boolean spawnNextWaveGroup(" in source, (
        "the spawn call must report whether the group really materialised"
    )
    spawn = finish.index("if (!spawnNextWaveGroup(world, core)) {")
    retire = finish.index("sendWaveTwoPhantomActivation(marked, false")
    assert spawn < retire, (
        "the client retired the illusion before the server confirmed the spawn"
    )
    deferred = finish[spawn : finish.index("}", spawn) + 1]
    assert "WAVE_TWO_PHANTOM_RETIRE_DEFERRED" in deferred and "return" in deferred, (
        "a failed spawn must log and stop without touching the illusion"
    )
    assert finish.count("sendWaveTwoPhantomActivation(marked, false") == 1, (
        "the retirement packet must not be sent more than once"
    )


def _method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start : cursor + 1]
    raise AssertionError(signature)
