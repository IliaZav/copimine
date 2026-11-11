"""M5: responsive Guardian melee.


The 11-November master contract (section 10.5) supersedes the old
eight-second auxiliary melee cooldown and the blanket ``LAST_SEAL`` melee ban.
Basic hit cadence must sit in roughly 1.8-2.4 s, and the final phase must
explicitly permit melee instead of disabling it.

The probes compile the real policy classes, so the assertions describe
behaviour rather than the presence of a source string.
"""

from pathlib import Path
import os
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "domain"
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"

# Contract 10.5: "approximately 1.8-2.4 seconds as an initial basic-hit tuning
# range".
MELEE_FASTEST_MILLIS = 1800
MELEE_SLOWEST_MILLIS = 2400


def _tool(name):
    java_home = os.environ.get("JAVA_HOME")
    executable = name + (".exe" if os.name == "nt" else "")
    if java_home:
        candidate = Path(java_home) / "bin" / executable
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    assert found, f"{name} is required to compile the boss melee probe"
    return found


def _run_probe(tmp_path, name, probe_source, domain_types=()):
    probe = tmp_path / f"{name}.java"
    probe.write_text(probe_source, encoding="utf-8")
    compiled = subprocess.run(
        [_tool("javac"), "-encoding", "UTF-8", "-d", str(tmp_path),
         *(str(DOMAIN / (type_name + ".java")) for type_name in domain_types),
         str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        [_tool("java"), "-cp", str(tmp_path), name],
        capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


MELEE_CADENCE_PROBE = r"""
import me.copimine.endevent.domain.*;

public class BossMeleeCadenceProbe {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static final long FASTEST = 1800L;
    static final long SLOWEST = 2400L;

    public static void main(String[] args) {
        // ---- Basic cadence sits inside the authored tuning range ---------
        for (BossPhase phase : BossPhase.values()) {
            long cadence = BossCloseCombatPolicy.cadenceMillis(phase);
            check(cadence >= FASTEST, "phase " + phase + " melee is too fast: " + cadence);
            check(cadence <= SLOWEST, "phase " + phase + " melee is too slow: " + cadence);
        }
        // SUPERSEDED: the old policy hard-coded COOLDOWN_MILLIS = 8000, which is
        // the cadence the user reported as "he barely ever swings".
        check(BossCloseCombatPolicy.cadenceMillis(null) >= FASTEST
                        && BossCloseCombatPolicy.cadenceMillis(null) <= SLOWEST,
                "an unknown phase must fall back to a legal cadence");

        // Later phases are allowed to be faster, never slower.
        check(BossCloseCombatPolicy.cadenceMillis(BossPhase.RAGE)
                        <= BossCloseCombatPolicy.cadenceMillis(BossPhase.AWAKENING),
                "fury must not slow the claws down");

        // ---- Melee is never unconditionally disabled in the final phase --
        for (BossPhase phase : BossPhase.values()) {
            check(BossCloseCombatPolicy.meleePermitted(phase),
                    "melee must be available in phase " + phase);
        }
        check(!BossCloseCombatPolicy.meleePermitted(null),
                "an unknown phase must not invent melee eligibility");

        // ---- Eligibility: gate, not a blanket ban ------------------------
        long now = 100000L;
        long ready = now - 1L;
        double close = BossContactStrikePolicy.MELEE_SWIPE_RANGE - 0.5D;

        check(BossCloseCombatPolicy.canStrike(BossPhase.LAST_SEAL, now, ready,
                        0L, now + 60000L, close, true, false),
                "LAST_SEAL must be able to swing when everything else permits it");
        check(BossCloseCombatPolicy.canStrike(BossPhase.AWAKENING, now, ready,
                        0L, now + 60000L, close, true, false),
                "a legal close hit must be admitted");

        // A target that is still in crowd control is not a melee target.
        check(!BossCloseCombatPolicy.canStrike(BossPhase.AWAKENING, now, ready,
                        0L, now + 60000L, close, true, true),
                "a controlled target must not be re-hit");
        // A target outside the authored reach is not a melee target.
        check(!BossCloseCombatPolicy.canStrike(BossPhase.AWAKENING, now, ready,
                        0L, now + 60000L,
                        BossContactStrikePolicy.MELEE_SWIPE_RANGE + 0.01D, true, false),
                "a target out of reach must not be hit");
        // No line of sight is not a melee target.
        check(!BossCloseCombatPolicy.canStrike(BossPhase.AWAKENING, now, ready,
                        0L, now + 60000L, close, false, false),
                "a target behind a wall must not be hit");
        // The committed cooldown still blocks a fresh swing.
        check(!BossCloseCombatPolicy.canStrike(BossPhase.AWAKENING, now, now + 1L,
                        0L, now + 60000L, close, true, false),
                "the melee cooldown must still be respected");

        // ---- Cooldown / retry decisions ---------------------------------
        check(BossCloseCombatPolicy.retryMillis() > 0L,
                "a refused swing must schedule a bounded retry, not a full cadence");
        check(BossCloseCombatPolicy.retryMillis() < FASTEST,
                "a refused swing must retry sooner than the full cadence");
        long next = BossCloseCombatPolicy.nextStrikeMillis(BossPhase.HUNT, now);
        check(next - now == BossCloseCombatPolicy.cadenceMillis(BossPhase.HUNT),
                "the committed next strike must use the phase cadence");

        System.out.println("BossMeleeCadenceProbe OK");
    }
}
"""


def test_melee_cadence_and_final_phase_eligibility(tmp_path):
    """Contract 10.5: 1.8-2.4 s cadence; melee never banned in the last phase."""
    output = _run_probe(tmp_path, "BossMeleeCadenceProbe", MELEE_CADENCE_PROBE,
                        ("BossCloseCombatPolicy", "BossContactStrikePolicy", "BossPhase",
                         "EndRiftAiPolicy"))
    assert "BossMeleeCadenceProbe OK" in output


def test_adapter_uses_the_phase_cadence_instead_of_eight_seconds():
    """Contract 10.5: the live loop must ask the policy, not a frozen constant."""
    source = EVENT.read_text(encoding="utf-8")
    # SUPERSEDED: the adapter used to add BossCloseCombatPolicy.COOLDOWN_MILLIS
    # (8000L). The cadence now comes from the policy per phase.
    # The adapter wraps the qualified call over two lines, so remove all
    # whitespace before matching a qualified name.
    flat = "".join(source.split())
    assert "BossCloseCombatPolicy.nextStrikeMillis(" in flat, (
        "the live melee loop must commit the phase cadence from the policy")
    assert "BossCloseCombatPolicy.retryMillis()" in flat, (
        "a refused swing must schedule a bounded retry")
    assert "BossCloseCombatPolicy.COOLDOWN_MILLIS" not in flat, (
        "the frozen eight-second melee cooldown must be gone from the adapter")
