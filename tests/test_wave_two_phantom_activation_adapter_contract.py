"""Keep late Wave 2 Endermen behind their server-authored phantom warning."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _declaration(source: str, signature: str) -> str:
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
    raise AssertionError(f"unterminated production method: {signature}")


def test_late_enderman_group_defers_when_no_warning_can_be_started() -> None:
    source = EVENT.read_text(encoding="utf-8")
    body = _declaration(source, "private boolean spawnNextWaveGroup(")
    call = body.index("tryBeginWaveTwoPhantomActivation(")
    after_call = body[call:call + 120]

    assert re.match(
        r"tryBeginWaveTwoPhantomActivation\(world, core, group, groupNumber\);\s*return false;",
        after_call,
    ), "an unavailable mark, layout, or slot must defer the group instead of spawning its Enderman directly"
