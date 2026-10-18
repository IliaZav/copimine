from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/Wave7BarrierPreflightPolicy.java"


def test_barrier_preflight_accepts_safe_cells_and_rejects_unsafe_world_observations(tmp_path):
    probe = tmp_path / "Wave7BarrierPreflightProbe.java"
    probe.write_text('''
import java.util.*;
import me.copimine.endevent.domain.Wave7BarrierPreflightPolicy;
public class Wave7BarrierPreflightProbe {
    static Wave7BarrierPreflightPolicy.Candidate candidate(boolean location, boolean gate,
            boolean floorSolid, boolean floorLiquid, boolean barrierLiquid, boolean tile,
            boolean barrierPassable, boolean fullWall) {
        return new Wave7BarrierPreflightPolicy.Candidate(location, gate, floorSolid,
                floorLiquid, barrierLiquid, tile, barrierPassable, fullWall);
    }
    static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var air = candidate(true, false, true, false, false, false, true, false);
        var existingGlass = candidate(true, false, true, false, false, false, false, true);
        require(Wave7BarrierPreflightPolicy.acceptsAll(List.of(air, existingGlass)),
                "passable cells and existing full collision walls are usable");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(false, false, true, false,
                false, false, true, false)), "out-of-bounds barrier locations fail closed");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(true, true, true, false,
                false, false, true, false)), "the gate cannot be overwritten");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(true, false, false, false,
                false, false, true, false)), "a non-solid floor cannot support a wall");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(true, false, true, true,
                false, false, true, false)), "liquid floor cannot support a wall");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(true, false, true, false,
                true, false, true, false)), "liquid barrier cells fail closed");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(true, false, true, false,
                false, true, true, false)), "tile entities cannot be replaced");
        require(!Wave7BarrierPreflightPolicy.accepts(candidate(true, false, true, false,
                false, false, false, false)), "partial existing blocks leave uncovered cells");
        require(!Wave7BarrierPreflightPolicy.acceptsAll(Arrays.asList(air, null)),
                "an incomplete observation set fails closed");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(POLICY), str(probe)],
        capture_output=True, text=True, timeout=30)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), "Wave7BarrierPreflightProbe"],
        capture_output=True, text=True, timeout=30)
    assert executed.returncode == 0, executed.stdout + executed.stderr
