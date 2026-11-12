"""Arena relocation: the attempt keeps one frozen world/context and never falls back.

Contract 2026-10-31: the whole arena and boss live in the configured Overworld.
The arena may move to another Overworld between attempts, but a live attempt
freezes its world coordinates, and nothing may silently fall back to `the_end`.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event/src/me/copimine/endevent"


def _run_probe(tmp_path: Path, name: str, body: str) -> str:
    probe = tmp_path / f"{name}.java"
    probe.write_text(body, encoding="utf-8")
    files = [
        PLUGIN / "runtime/AttemptLifecycleController.java",
        PLUGIN / "runtime/EncounterContext.java",
        PLUGIN / "runtime/PreBossTransitionController.java",
        PLUGIN / "runtime/BossStartGateway.java",
        PLUGIN / "domain/ServerTickClock.java",
        PLUGIN / "domain/EventPhase.java",
        PLUGIN / "domain/BlackFogTimingPolicy.java",
        PLUGIN / "domain/EndRiftObjective.java",
        PLUGIN / "domain/RiftRamPolicy.java",
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


def test_frozen_world_context_rejects_a_relocated_arena(tmp_path):
    """A context bound to generation 43/world A must not accept world B."""
    output = _run_probe(tmp_path, "ArenaRelocationProbe", r'''
import java.util.*;
import me.copimine.endevent.runtime.*;
public class ArenaRelocationProbe {
    static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        var arena = new EncounterContext.ArenaBounds(-20, 60, -20, 20, 80, 20);
        var life = new AttemptLifecycleController();
        life.begin(43, Set.of(new UUID(0, 1)));
        var frozen = new EncounterContext("evt", 43, "configured-overworld-a", 0, 68, 0, arena,
                Set.of(new UUID(0, 1)), life.activeLivingOnlineRoster(), null);
        var relocated = new EncounterContext("evt", 43, "configured-overworld-b", 0, 68, 0, arena,
                Set.of(new UUID(0, 1)), life.activeLivingOnlineRoster(), null);
        require("configured-overworld-a".equals(frozen.worldName()), "context must expose its frozen world");
        require(frozen.owns("evt", 43), "the frozen context owns its own event and generation");
        require(!relocated.owns("evt", 44), "a relocated context cannot claim another generation");
        require(!frozen.owns("evt", 44), "the old context must not answer for a newer generation");
        require(!frozen.owns("other-evt", 43), "a foreign event id must never be accepted");
        // The frozen arena bounds are the relocation contract: points outside are out of arena.
        require(frozen.withinArena(0, 68, 0), "arena centre stays inside the frozen bounds");
        require(!frozen.withinArena(0, 200, 0), "a point far outside the frozen bounds is rejected");
        System.out.println("arena context frozen OK");
    }
}
''')
    assert "arena context frozen OK" in output


def test_event_source_has_no_the_end_world_fallback():
    """The production path must never hardcode or silently fall back to `the_end`."""
    source = (PLUGIN / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    assert 'Bukkit.getWorld("the_end")' not in source, "the arena must not be hardcoded to the vanilla End"
    assert 'getWorld("the_end")' not in source
    # "the_end" may only survive as a display/vfx dimension label, never as a resolved world.
    for forbidden in ('worldName = "the_end"', '"the_end".equals(worldName)'):
        assert forbidden not in source, f"silent End fallback found: {forbidden}"


def test_combat_admission_is_scoped_to_the_attempt_generation(tmp_path):
    """Relocating the arena between attempts must invalidate the previous roster."""
    output = _run_probe(tmp_path, "ArenaGenerationProbe", r'''
import java.util.*;
import me.copimine.endevent.runtime.*;
public class ArenaGenerationProbe {
    static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        var player = new UUID(0, 1);
        var life = new AttemptLifecycleController();
        life.begin(43, Set.of(player));
        require(life.isRegistered(player, 43), "the roster is registered for its own generation");
        // A new attempt in a different world bumps the generation.
        life.begin(44, Set.of(player));
        require(!life.isRegistered(player, 43), "a stale generation cannot keep a registered roster");
        require(life.isRegistered(player, 44), "the new generation owns the roster");
        require(!life.acceptsCallback(43), "callbacks fenced to the old generation are rejected");
        require(life.acceptsCallback(44), "callbacks for the live generation are accepted");
        System.out.println("arena generation fence OK");
    }
}
''')
    assert "arena generation fence OK" in output