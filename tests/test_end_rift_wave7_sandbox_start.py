"""Wave 7 must be startable in the disposable sandbox.

/cmend wave spawn 7 used to fail with the generic
reason=wave-mechanics-initialization-failed. The real cause was the Personal
Echo preflight: it requires every owner to have a live CopiMine client bridge
and a replicated loadout. A sandbox has neither, so the wave could never start
locally and the failure reason named no stage at all.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def body(path, signature):
    text = path.read_text(encoding="utf-8")
    start = text.index(signature)
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace:index + 1]
    raise AssertionError("unterminated " + signature)


def test_the_echo_preflight_accepts_a_sandbox_start():
    echo = body(SERVER, "private boolean preflightNamedEchoDuelRooms(boolean sandbox)")
    assert "if (sandbox)" in echo, (
        "a sandbox has no replicated loadout, so the per-owner echo rules "
        "cannot be satisfied there")
    sandbox_escape = echo[echo.index("if (sandbox)"):echo.index("if (sandbox)") + 120]
    assert "return true" in sandbox_escape, (
        "the sandbox branch must actually admit the start")

def test_the_echo_preflight_still_guards_the_official_path():
    echo = body(SERVER, "private boolean preflightNamedEchoDuelRooms(boolean sandbox)")
    after = echo[echo.index("if (sandbox)"):]
    for guard in ("echo-client-not-ready", "duel-record-missing", "owner-dead"):
        assert guard in after, (
            "the official path must keep refusing on " + guard)


def test_the_echo_preflight_receives_the_sandbox_flag():
    text = SERVER.read_text(encoding="utf-8")
    assert "preflightNamedEchoDuelRooms(sandbox)" in text, (
        "the sandbox flag has to reach the echo preflight or the bypass never runs")


def test_a_wave_initialisation_failure_names_the_absent_stage():
    text = SERVER.read_text(encoding="utf-8")
    marker = text.index("reason=wave-mechanics-initialization-failed")
    tail = text[marker:marker + 400]
    assert "hint=" in tail, (
        "a bare 'initialization failed' hides which preflight refused; the "
        "operator must be pointed at the exact stage")
