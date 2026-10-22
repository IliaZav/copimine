"""Wave 6 admin diagnostic must retire a selected guard through the real death path."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_wave6_admin_can_kill_a_selected_guard() -> None:
    source = read(SERVER)
    command = declaration(source, "private void handleTestRitual(")
    assert '"killguard".equalsIgnoreCase(args[2])' in command
    assert "killguard <caster 1-5> <guard 1-3>" in command
    assert "killRitualGuardForTest(" in command
    assert "parseInt(args, 3" in command and "parseInt(args, 4" in command

    kill = declaration(source, "private boolean killRitualGuardForTest(")
    assert "testWaveFrontVisualMode" in kill
    assert "ritualWaveActive()" in kill
    assert "RitualSphereScalingPolicy.GUARDS_PER_CASTER" in kill
    assert "ritualGuardCasters" in kill
    assert "ritualCasterSlots" in kill
    assert "keyRitualGuardSlot" in kill
    assert "guard.setHealth(0.0D)" in kill, "the guard must die through the normal owned-entity death path"
    assert "remove()" not in kill
