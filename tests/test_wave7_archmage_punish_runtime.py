"""MAGE7-005: the Archmage punish window and Step gate must be LIVE, not policy-only.

The policy existed with no runtime caller, which is the exact failure mode this
test guards: a Step cooldown and a punish window that only a unit test ever
observed. Two independent proofs are required here.

1. Wiring: the production actor tick really consults the gate and really
   records the tool use. Removing either call must fail this test.
2. Behaviour: the policy that production now calls is executed for real (javac
   + java), so the window it builds from the persisted close tick is proven
   identical to the window a cast opens.
"""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / (
    "copimine-end-event/src/me/copimine/endevent/domain/Wave7ArchmagePunishPolicy.java"
)


def _source() -> str:
    return SOURCE.read_text(encoding="utf-8")


def test_archmage_tick_gates_step_through_the_punish_policy():
    source = _source()
    tick = extract(source, "boolean tickNamedTrialActor(")
    assert "Wave7ArchmagePunishPolicy.stepAllowed(" in tick, (
        "the actor tick must refuse Step through the punish policy"
    )
    assert "Wave7ArchmagePunishPolicy.windowEndingAt(" in tick, (
        "the tick must rebuild the persisted punish window"
    )
    assert "Wave7NamedTrialActorPolicy.attackForExcluding(" in tick, (
        "a refused Step must fall through to the next authored spell"
    )
    # A hidden target can only be answered by Step; when the gate refuses it the
    # actor must hold instead of blinking anyway.
    cover_start = tick.index("if (repositionForCover) {")
    cover_step = tick.index("attack = Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_STEP;")
    assert cover_start < cover_step, "the cover branch must choose the Step"
    assert "return true;" in tick[cover_start:cover_step], (
        "a refused cover Step must hold the actor, not bypass the gate"
    )


def test_archmage_casts_record_their_tool_cost():
    source = _source()
    resolve = extract(source, "boolean resolveNamedTrialAttack(")
    # The outcome has to be recorded by the caller of the resolution, which is
    # the tick path; check the resolution commit site inside the tick.
    tick = extract(source, "boolean tickNamedTrialActor(")
    assert "recordArchmageToolUse(" in tick, (
        "a resolved Archmage cast must record its cost, or the window never opens"
    )
    # And the recorder must actually persist both tools.
    recorder = extract(source, "void recordArchmageToolUse(")
    assert "keyWave7ArchmageStepTick" in recorder, "Step must persist its own cooldown tick"
    assert "keyWave7ArchmagePunishUntil" in recorder, (
        "a major cast must persist the punish window close tick"
    )
    assert "Wave7ArchmagePunishPolicy.punishUntilTick(" in recorder, (
        "the persisted close tick must come from the policy"
    )
    assert resolve  # sanity: the extraction helper found the resolver


def test_persisted_tool_state_is_generation_fenced():
    source = _source()
    reader = extract(source, "long archmageToolTick(")
    assert "Wave7ArchmagePunishPolicy.NEVER" in reader, (
        "unreadable tool state must mean 'never used', not a fabricated window"
    )
    assert "!eventId.toString().equals(parts[0])" in reader, (
        "a previous event's tool tick must not leak into this attempt"
    )
    assert "!= generation" in reader, (
        "a previous generation's tool tick must not leak into this attempt"
    )


def test_the_called_policy_builds_the_identical_window_for_real(tmp_path):
    probe = tmp_path / "ArchmagePunishProbe.java"
    probe.write_text(
        """
import me.copimine.endevent.domain.Wave7ArchmagePunishPolicy;
import me.copimine.endevent.domain.Wave7ArchmagePunishPolicy.PunishWindow;

public final class ArchmagePunishProbe {
    public static void main(String[] args) {
        long castEnd = 7_000L;
        long close = Wave7ArchmagePunishPolicy.punishUntilTick(castEnd);
        PunishWindow opened = Wave7ArchmagePunishPolicy.afterMajorCast(castEnd);
        if (close != opened.closesAt()) throw new AssertionError("close tick mismatch");

        // This is exactly what the adapter does after a restart.
        PunishWindow rebuilt = Wave7ArchmagePunishPolicy.windowEndingAt(close);
        if (rebuilt == null || rebuilt.opensAt() != opened.opensAt()
                || rebuilt.closesAt() != opened.closesAt()) {
            throw new AssertionError("restart did not rebuild the identical window");
        }

        long never = Wave7ArchmagePunishPolicy.NEVER;
        if (Wave7ArchmagePunishPolicy.windowEndingAt(0L) != null) {
            throw new AssertionError("no close tick must mean no window");
        }
        if (!Wave7ArchmagePunishPolicy.stepAllowed(castEnd - 1L, never, rebuilt)) {
            throw new AssertionError("Step must be legal before the window opens");
        }
        if (Wave7ArchmagePunishPolicy.stepAllowed(castEnd + 1L, never, rebuilt)) {
            throw new AssertionError("Step must be refused inside the window");
        }
        long afterWindow = rebuilt.closesAt();
        if (!Wave7ArchmagePunishPolicy.stepAllowed(afterWindow, never, rebuilt)) {
            throw new AssertionError("Step must return once the window closes");
        }
        if (Wave7ArchmagePunishPolicy.stepAllowed(afterWindow,
                afterWindow - 1L, null)) {
            throw new AssertionError("a fresh Step must hold its own cooldown");
        }
        System.out.println("ArchmagePunishProbe OK");
    }
}
""",
        encoding="utf-8",
    )
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(POLICY), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "ArchmagePunishProbe"],
        capture_output=True,
        text=True,
    )
    assert run.returncode == 0, run.stderr
    assert "ArchmagePunishProbe OK" in run.stdout