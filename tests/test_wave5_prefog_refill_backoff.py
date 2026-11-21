"""Wave 5 pre-fog refill must back off instead of retrying every tick.

``replenishWaveFiveCombatIfNeeded`` used to advance the deadline only on success.
A spawn that kept being refused therefore left ``wave5PreFogRefillAtMillis`` in
the past and the caller retried on every tick. The policy now yields a bounded
exponential delay, and the caller records the failure.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/Wave5PreFogRefillPolicy.java"

PROBE = r'''
import me.copimine.endevent.domain.Wave5PreFogRefillPolicy;

public final class Wave5RefillBackoffProbe {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(0) == 0L, "no delay before the first failure");
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(1) == 1_000L, "first failure backoff");
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(2) == 2_000L, "second failure doubles");
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(3) == 4_000L, "third failure doubles");
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(6) == 30_000L, "cap reached");
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(10_000) == 30_000L, "cap holds");
        check(Wave5PreFogRefillPolicy.failureBackoffMillis(-4) == 0L, "defensive input");
        System.out.println("OK");
    }
}
'''


def test_failure_backoff_is_bounded_and_monotonic(tmp_path):
    probe = tmp_path / "Wave5RefillBackoffProbe.java"
    probe.write_text(PROBE, encoding="utf-8")
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(POLICY), str(probe)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "Wave5RefillBackoffProbe"],
        capture_output=True, text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr


def test_refill_failure_updates_the_deadline_and_is_reported():
    source = EVENT.read_text(encoding="utf-8")
    start = source.index("private void replenishWaveFiveCombatIfNeeded(long now)")
    end = source.index("private void reinforceWaveFiveAfterFog(long now)", start)
    refill = source[start:end]
    assert "wave5PreFogRefillFailures++" in refill
    assert "Wave5PreFogRefillPolicy.failureBackoffMillis(" in refill
    assert "wave5PreFogRefillAtMillis = now + backoff" in refill
    assert "END_RIFT_WAVE5_PRE_FOG_REFILL_BACKOFF" in refill
    assert "wave5PreFogRefillFailures = 0" in refill
    # The debt must survive a refused attempt: after the failure counter is
    # incremented there is no refill increment in that branch.
    failure_branch = refill[refill.index("wave5PreFogRefillFailures++"):]
    assert "wave5PreFogRefills++" not in failure_branch