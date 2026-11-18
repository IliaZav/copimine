"""The echo's animation clocks are per-tick, not per-frame.

``EchoAnimationTickGuard`` is the small policy the renderer uses to make sure
gait and the hurt flash advance once per game tick even though both the tick
loop and the render pass call the same drive method. It also owns the first
frame's swing/hurt serial baseline: the server numbers receipts from zero, and
zero is the initial state, not an event.

The class imports no Minecraft types, so this module compiles the real class and
drives it with fake ticks and receipts. A source-text test could not tell a
correct per-tick gate from one that advances on every call.
"""

from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[1]
GUARD = (ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/"
                "EchoAnimationTickGuard.java")

HARNESS = """
import me.copimine.client.EchoAnimationTickGuard;

public class EchoAnimationTickGuardChecks {
    static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        // One advance per distinct game tick, however many frames draw in it.
        EchoAnimationTickGuard clock = new EchoAnimationTickGuard();
        require(clock.advance(100), "the first call for a tick advances");
        require(!clock.advance(100), "a second frame in the same tick must not advance");
        require(!clock.advance(100), "a third frame in the same tick must not advance");
        require(clock.advance(101), "the next tick advances");
        require(!clock.advance(101), "a repeat in the next tick is collapsed");

        // A fresh copy: serial zero is the initial state, not a receipt.
        EchoAnimationTickGuard fresh = new EchoAnimationTickGuard();
        fresh.prime(0L, 0L);
        require(!fresh.swingDue(0L), "serial zero must not fire a swing");
        require(!fresh.hurtDue(0L), "serial zero must not fire a hurt flash");
        require(!fresh.swingDue(0L), "a repeated zero still fires no swing");
        require(!fresh.hurtDue(0L), "a repeated zero still flashes no hurt");

        // A server that already counted before the bind must not replay history.
        EchoAnimationTickGuard late = new EchoAnimationTickGuard();
        late.prime(4L, 3L);
        require(!late.swingDue(4L), "the bind frame's swing serial is the baseline");
        require(!late.hurtDue(3L), "the bind frame's hurt serial is the baseline");

        // Real receipts fire exactly once each.
        require(fresh.swingDue(1L), "a new swing serial fires");
        require(!fresh.swingDue(1L), "the same swing serial fires once");
        require(fresh.swingDue(2L), "the next swing serial fires");
        require(fresh.hurtDue(1L), "a new hurt serial fires");
        require(!fresh.hurtDue(1L), "the same hurt serial fires once");
        require(fresh.hurtDue(2L), "the next hurt serial fires");

        // Swing and hurt are independent counters.
        EchoAnimationTickGuard independent = new EchoAnimationTickGuard();
        independent.prime(0L, 0L);
        require(independent.swingDue(1L), "swing advances");
        require(!independent.hurtDue(0L), "a swing must not consume the hurt baseline");
        require(independent.hurtDue(1L), "hurt advances independently");

        // Priming is idempotent and cannot rewind a baseline.
        EchoAnimationTickGuard primed = new EchoAnimationTickGuard();
        primed.prime(0L, 0L);
        require(primed.swingDue(1L), "the first real swing after bind fires");
        primed.prime(0L, 0L);
        require(!primed.swingDue(1L), "re-priming must not rewind the swing baseline");
        require(primed.swingDue(2L), "the baseline keeps moving forward");
        require(!primed.swingDue(1L), "a stale lower serial must not fire again");
        require(!primed.hurtDue(0L), "a stale zero must not fire again");

        System.out.println("EchoAnimationTickGuard OK");
    }
}
"""


@pytest.fixture(scope="module")
def compiled(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-animation-tick-guard")
    source = directory / "EchoAnimationTickGuardChecks.java"
    source.write_text(HARNESS, encoding="utf-8")
    build = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory),
         str(GUARD), str(source)],
        capture_output=True, text=True,
    )
    assert build.returncode == 0, build.stdout + build.stderr
    return directory


def test_gait_and_hurt_advance_once_per_tick_and_serials_do_not_replay(compiled: Path):
    run = subprocess.run(["java", "-cp", str(compiled), "EchoAnimationTickGuardChecks"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "EchoAnimationTickGuard OK" in run.stdout
