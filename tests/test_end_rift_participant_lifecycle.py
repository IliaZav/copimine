"""Participant lifecycle: death, disconnect, rejoin and full party wipe are deterministic.

Contract 2026-10-31: a full party wipe ends the attempt on every wave (the Wave 7
exception is revoked), keep-inventory is personal and never duplicates a drop, and
a returning participant rejoins through an explicit, generation-scoped admission.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"

_HARNESS = """import java.util.*;
import me.copimine.endevent.runtime.*;
public class {name} {{
    static void require(boolean value, String reason) {{ if (!value) throw new AssertionError(reason); }}
    public static void main(String[] args) {{
{body}
    }}
}}
"""


def _run_probe(tmp_path: Path, name: str, body: str) -> str:
    probe = tmp_path / f"{name}.java"
    probe.write_text(_HARNESS.format(name=name, body=body), encoding="utf-8")
    files = [
        PLUGIN / "runtime/AttemptLifecycleController.java",
        PLUGIN / "runtime/EncounterContext.java",
        PLUGIN / "domain/EventPhase.java",
        PLUGIN / "domain/BlackFogTimingPolicy.java",
        PLUGIN / "domain/EndRiftObjective.java",
    ]
    result = subprocess.run(
        [
            "javac",
            str(ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/EncounterGenerationPolicy.java"),
            "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe),
        ],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), name], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def test_death_offline_and_full_wipe_are_tracked_per_generation(tmp_path):
    output = _run_probe(tmp_path, "ParticipantLifecycleProbe", r'''
        var a = new UUID(0, 1);
        var b = new UUID(0, 2);
        var life = new AttemptLifecycleController();
        life.begin(43, Set.of(a, b));
        require(life.living().size() == 2, "both participants start alive");
        require(life.markDead(a, 43), "a living participant can be marked dead");
        require(life.living().size() == 1, "death removes the participant from the living set");
        require(!life.markDead(a, 43), "a second death is not recorded twice");
        require(life.markAlive(a, 43), "a respawn restores the participant");
        require(life.markOffline(b, 43), "a participant can leave mid-attempt");
        require(!life.status(b).online(), "the participant is offline after a disconnect");
        // A repeated disconnect is idempotent: the state is already offline, so the
        // call must be a harmless re-assert rather than a second transition.
        require(life.markOffline(b, 43), "a repeated disconnect is idempotent, not an error");
        require(!life.status(b).online(), "the participant stays offline after a repeat");
        require(life.markOnline(b, 43), "a participant can return online");
        // A full wipe: every registered participant is dead at once.
        require(life.markDead(a, 43) && life.markDead(b, 43), "the whole party can die");
        require(life.living().isEmpty(), "a full party wipe leaves nobody living");
        require(life.activeLivingOnlineRoster().isEmpty(),
                "the active living roster is empty after a wipe");
        System.out.println("participant lifecycle OK");
''')
    assert "participant lifecycle OK" in output


def test_callbacks_from_a_finished_attempt_are_rejected(tmp_path):
    output = _run_probe(tmp_path, "ParticipantFenceProbe", r'''
        var a = new UUID(0, 1);
        var life = new AttemptLifecycleController();
        life.begin(43, Set.of(a));
        require(life.acceptsCallback(43), "the live generation accepts callbacks");
        life.begin(44, Set.of());
        require(!life.acceptsCallback(43), "a finished attempt can no longer mutate state");
        require(!life.markDead(a, 43), "a stale death callback is refused rather than applied");
        require(!life.markOffline(a, 43), "a stale disconnect is refused rather than applied");
        require(!life.isRegistered(a, 44), "the old participant is not silently carried forward");
        System.out.println("participant fence OK");
''')
    assert "participant fence OK" in output


def test_full_party_wipe_is_not_exempted_on_wave_seven():
    """The Wave 7 all-dead exception is revoked: a wipe ends the attempt."""
    source = (PLUGIN / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    # The wave-7 return grace must not be usable to keep a wiped attempt alive.
    forbidden = [
        "activeWave != 7 && attemptLifecycle.allDead",
        "activeWave == 7) return; // wipe allowed",
    ]
    for pattern in forbidden:
        assert pattern not in source, f"wave 7 wipe exemption still present: {pattern}"
    assert "attemptLifecycle" in source, "the attempt lifecycle owner must stay wired"