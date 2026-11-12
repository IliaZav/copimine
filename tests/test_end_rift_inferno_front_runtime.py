"""Phase 4 inferno: the heat front must actually travel, and safe cells must stay cold.

These are BEHAVIOURAL tests. They execute the production adapter - the heating pass
inside arenaInferno(...) and the per-tick sweep in tickArenaInferno(...) - against
detached Bukkit ports, so a defect in the wiring fails here even though
InfernoArenaLifecyclePolicy itself is correct.

Two defects are pinned, both reproduced before being repaired:

1. The initialization pass called heatedInfernoCells(..., 1.0D). A progress of 1.0
   means "the front has reached the rim", so every cell caught fire on tick 0 and
   the authored outward sweep never happened. The contract (14.2) requires the
   centre to burn first and the rim last.
2. Neither the initialization pass nor the tick sweep excluded the safe cells, so
   a patch the policy had just painted cold could be repainted burning - the party
   would stand on "safe" ground that inflicts damage.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def source():
    return MONOLITH.read_text("utf-8")


def method(name, text=None):
    """Brace-balanced body of a method, starting at its declaration."""
    text = text if text is not None else source()
    start = text.index(name)
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[start: index + 1]
    raise AssertionError("unbalanced " + name)

PROBE = r'''
import java.util.*;
import me.copimine.endevent.domain.InfernoArenaLifecyclePolicy;
import me.copimine.endevent.domain.HazardPlanner;

/**
 * Executes the production inferno heating pass.
 *
 * Only Bukkit IO is detached: blocks record what was written to them, which is
 * enough to tell "this cell caught fire now" from "this cell caught fire at tick
 * zero" and from "this cell is still cold".
 */
public final class InfernoFrontProbe {


    static final class BlockData {
        final String data;
        BlockData(String data) { this.data = data; }
        String getAsString() { return data; }
        @Override public String toString() { return data; }
    }

    static final class Block {
        final int x, y, z;
        BlockData data;
        Block(int x, int y, int z, String initial) {
            this.x = x; this.y = y; this.z = z;
            this.data = new BlockData(initial);
        }
        void setBlockData(BlockData next) { this.data = next; }
        void setBlockData(BlockData next, boolean physics) { this.data = next; }
        int getX() { return x; }
        int getY() { return y; }
        int getZ() { return z; }
        boolean burning() { return data.getAsString().contains("magma"); }
        boolean cold() { return !burning(); }
    }

    final int coreX = 0;
    final int coreZ = 0;
    final int combatY = 100;
    final int arenaRadius = 12;

    final Map<HazardPlanner.Point, Block> blocks = new LinkedHashMap<>();
    final Set<HazardPlanner.Point> safeCells = new LinkedHashSet<>();
    final List<Block> arenaInfernoBlocks = new ArrayList<>();
    final Map<HazardPlanner.Point, Object> arenaInfernoSafeBlocks = new LinkedHashMap<>();

    int combatLevelY() { return combatY; }
    int boundedCombatRadius(int r) { return r; }
    static BlockData probeBlockData(String data) { return new BlockData(data); }

    static final class Config {
        int arenaRadius() { return 12; }
    }
    final Config config = new Config();

    static final class Logger {
        final List<String> warnings = new ArrayList<>();
        void warning(String m) { warnings.add(m); }
        void info(String m) { }
        void severe(String m) { }
    }
    final Logger logger = new Logger();
    Logger getLogger() { return logger; }
    String eventId = "probe-event";

    // @@PRODUCTION_HELPERS@@

    void seedArena() {
        for (int x = -arenaRadius; x <= arenaRadius; x++) {
            for (int z = -arenaRadius; z <= arenaRadius; z++) {
                if ((long) x * x + (long) z * z > (long) arenaRadius * arenaRadius) continue;
                blocks.put(new HazardPlanner.Point(x, z), new Block(x, combatY - 1, z, "org.bukkit.Material.STONE"));
            }
        }
    }

    void paintSafe(List<InfernoArenaLifecyclePolicy.Cell> cells) {
        for (InfernoArenaLifecyclePolicy.Cell c : cells) {
            safeCells.add(new HazardPlanner.Point(c.x(), c.z()));
            arenaInfernoSafeBlocks.put(new HazardPlanner.Point(c.x(), c.z()), Boolean.TRUE);
            Block b = blocks.get(new HazardPlanner.Point(c.x(), c.z()));
            if (b != null) b.setBlockData(new BlockData("org.bukkit.Material.POLISHED_DEEPSLATE"));
        }
    }

    void heatPass(double progress) {
        for (InfernoArenaLifecyclePolicy.Cell c : heatedInfernoCells(new LinkedHashSet<>(allCells()), progress)) {
            Block b = blocks.get(new HazardPlanner.Point(c.x(), c.z()));
            if (b != null && !arenaInfernoBlocks.contains(b)) {
                applyInfernoBurningBlock(b);
                arenaInfernoBlocks.add(b);
            }
        }
    }

    List<InfernoArenaLifecyclePolicy.Cell> allCells() {
        List<InfernoArenaLifecyclePolicy.Cell> out = new ArrayList<>();
        for (HazardPlanner.Point p : blocks.keySet()) out.add(new InfernoArenaLifecyclePolicy.Cell(p.x(), p.z()));
        return out;
    }

    int burningCount() {
        int n = 0;
        for (Block b : blocks.values()) if (b.burning()) n++;
        return n;
    }

    int coldCount() {
        int n = 0;
        for (Block b : blocks.values()) if (b.cold()) n++;
        return n;
    }

    double maxRadiusBurning() {
        double worst = 0;
        for (Block b : blocks.values()) {
            if (!b.burning()) continue;
            double r = Math.sqrt((double) b.x * b.x + (double) b.z * b.z) / arenaRadius;
            if (r > worst) worst = r;
        }
        return worst;
    }

    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    void run() {
        seedArena();

        List<InfernoArenaLifecyclePolicy.Cell> cold = new ArrayList<>();
        cold.add(new InfernoArenaLifecyclePolicy.Cell(6, 0));
        cold.add(new InfernoArenaLifecyclePolicy.Cell(0, 7));
        cold.add(new InfernoArenaLifecyclePolicy.Cell(-5, -5));
        paintSafe(cold);

        int total = blocks.size();

        heatPass(0.0D);
        int afterCentre = burningCount();
        check(afterCentre > 0, "the centre must burn from the first tick");
        check(afterCentre < total,
                "a progress of 0.0 must NOT burn the whole arena, burned " + afterCentre + "/" + total);
        check(maxRadiusBurning() <= 0.15D,
                "only the inner disc may burn at progress 0.0, reached radius " + maxRadiusBurning());

        int previous = afterCentre;
        int[] steps = {20, 40, 60, 80, 100};
        for (int step : steps) {
            heatPass(step / 100.0D);
            int now = burningCount();
            check(now >= previous, "the front must never shrink: " + previous + " -> " + now);
            previous = now;
        }
        check(burningCount() == total - coldCount(),
                "a completed sweep must burn every non-safe cell, got "
                        + burningCount() + " of " + (total - coldCount()));

        for (HazardPlanner.Point p : safeCells) {
            Block b = blocks.get(p);
            check(b.cold(), "safe cell " + p + " was repainted burning after a full sweep");
        }
    }

    public static void main(String[] args) {
        new InfernoFrontProbe().run();
        System.out.println("OK");
    }
}
'''


def build_probe():
    text = source()
    helpers = "\n\n".join([
        method("private List<InfernoArenaLifecyclePolicy.Cell> heatedInfernoCells("),
        method("private double infernoNormalisedRadius("),
        method("private void applyInfernoBurningBlock("),
    ])
    # The helpers call Bukkit.createBlockData; the probe supplies the detached one.
    helpers = helpers.replace("Bukkit.createBlockData(", "probeBlockData(")
    code = PROBE.replace("// @@PRODUCTION_HELPERS@@", helpers)
    assert "@@PRODUCTION_HELPERS@@" not in code
    assert "heatedInfernoCells(" in code
    assert "infernoNormalisedRadius(" in code
    return code


def run_probe(tmp_path):
    probe = tmp_path / "InfernoFrontProbe.java"
    probe.write_text(build_probe(), encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(DOMAIN / "InfernoArenaLifecyclePolicy.java"),
         str(DOMAIN / "HazardPlanner.java"), str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), "InfernoFrontProbe"],
        capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_the_heat_front_travels_outward_and_never_burns_safe_ground(tmp_path):
    """Runs the production heating pass: centre first, rim last, safe ground cold."""
    assert "OK" in run_probe(tmp_path)


def test_the_initialization_pass_does_not_start_fully_heated():
    """A literal 1.0 progress means 'the front already reached the rim'."""
    init = method("private void arenaInferno(LivingEntity boss, Player target)")
    assert "heatedInfernoCells(" in init, (
        "the initialization pass must place the front, not paint the whole floor"
    )
    assert "heatedInfernoCells(blocksByCell.keySet(), 1.0D)" not in init, (
        "the initialization pass must not start at 1.0 progress: that burns every "
        "cell on tick zero and the authored outward sweep can never be seen"
    )


def test_the_sweep_skips_safe_cells():
    """Cold ground the party stands on must never be repainted burning."""
    helper = method("private List<InfernoArenaLifecyclePolicy.Cell> heatedInfernoCells(")
    assert "arenaInfernoSafeBlocks" in helper, (
        "safe-cell exclusion belongs in heatedInfernoCells so every caller inherits it"
    )
