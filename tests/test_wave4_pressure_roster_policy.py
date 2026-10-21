"""Focused Wave 4 roster contract against the production composition policy.

This probe extracts the real player-scaled composition method and invokes the
real pure pressure-pack planner. It does not substitute the roster policy or
claim that Bukkit entities/materialization were exercised.
"""
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def _method(source: str, signature: str) -> str:
    declaration = "    private " + signature
    start = source.index(declaration)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_max_roster_replaces_pressure_slots_with_zombie_and_bounded_rift_giant(tmp_path):
    source = EVENT.read_text(encoding="utf-8")
    scaled_counts = _method(
        source,
        "WaveMechanicsPolicy.WaveCounts currentWave4MobCounts(int players)",
    )
    probe = tmp_path / "Wave4PressureRosterProbe.java"
    probe.write_text(
        """
import java.util.List;
import me.copimine.endevent.domain.ObeliskScalingPolicy;
import me.copimine.endevent.domain.Wave4PressurePackPolicy;
import me.copimine.endevent.domain.WaveMechanicsPolicy;

public final class Wave4PressureRosterProbe {
"""
        + scaled_counts
        + """
    public static void main(String[] args) {
        var profile = ObeliskScalingPolicy.profileForPlayers(20);
        var counts = new Wave4PressureRosterProbe().currentWave4MobCounts(20);
        List<Wave4PressurePackPolicy.Slot> slots = Wave4PressurePackPolicy.plan(counts);
        long zombies = slots.stream().filter(slot -> slot.role().equals("ZOMBIE")).count();
        long giants = slots.stream().filter(slot -> slot.role().equals("RIFT_GIANT")).count();
        check(slots.size() == profile.mobCount(),
                "replacement pressure must stay within the roster's existing mob slots");
        check(zombies >= 1,
                "the maximum roster must include ordinary zombie pressure");
        check(giants >= 1,
                "the maximum roster must include bounded Rift Giant replacement pressure");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
""",
        encoding="utf-8",
    )
    production = [
        DOMAIN / "WaveMechanicsPolicy.java",
        DOMAIN / "PressureBudgetController.java",
        DOMAIN / "ObeliskFireDirectorPolicy.java",
        DOMAIN / "ObeliskScalingPolicy.java",
        DOMAIN / "Wave4PressurePackPolicy.java",
    ]
    compiled = subprocess.run(
        ["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(tmp_path),
         *map(str, production), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(
        ["java", "-Xms8m", "-Xmx64m", "-cp", str(tmp_path), "Wave4PressureRosterProbe"],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
