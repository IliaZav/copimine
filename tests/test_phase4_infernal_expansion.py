"""Phase 4 (OVERLOAD) infernal expansion - owner-requested new mechanics.

These probes compile the real, Bukkit-free domain policies and execute them, so
a regression in the arena's cold-patch decay, the rotating Rift Fire Ring, the
Infernal Forge ("Адская кузня") or the once-per-HP-threshold cast gate fails a
test rather than silently changing a live fight.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

SOURCES = [
    "InfernoArenaLifecyclePolicy",   # the Cell record + party patch count
    "InfernoDecayPolicy",
    "RiftFireRingPolicy",
    "InfernalForgePolicy",
    "BossSignatureCastGatePolicy",
]


def compile_and_run(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    files = [DOMAIN / (stem + ".java") for stem in SOURCES]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), name], capture_output=True, text=True
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_cold_patch_decay_and_reachable_coverage(tmp_path):
    output = compile_and_run(
        tmp_path,
        "InfernoDecayProbe",
        """
import me.copimine.endevent.domain.*;
import me.copimine.endevent.domain.InfernoArenaLifecyclePolicy.Cell;
import java.util.*;

public class InfernoDecayProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // Patch count falls as the fight drags but never empties the arena.
        int full = InfernoDecayPolicy.safePatchCount(1.0D, 8);
        int mid = InfernoDecayPolicy.safePatchCount(0.5D, 8);
        int low = InfernoDecayPolicy.safePatchCount(0.05D, 8);
        require(full >= mid && mid >= low, "patches shrink with health");
        require(low >= InfernoDecayPolicy.MIN_PATCHES, "a near-dead Guardian keeps refuges");
        require(InfernoDecayPolicy.alwaysPresent(0.0D), "a refuge always exists");
        for (double f = 0.0D; f <= 1.0D; f += 0.05D)
            require(InfernoDecayPolicy.safePatchCount(f, 20) <= InfernoDecayPolicy.MAX_PATCHES,
                    "patches never exceed the cap");

        // Relocation gets more frequent (shorter interval) with lower health,
        // and never relocates before the five-second minimum lifetime.
        int healthyGap = InfernoDecayPolicy.relocateIntervalTicks(1.0D);
        int dyingGap = InfernoDecayPolicy.relocateIntervalTicks(0.0D);
        require(healthyGap > dyingGap, "the fire churns faster as the fight drags");
        require(dyingGap >= InfernoDecayPolicy.MIN_SAFE_LIFETIME_TICKS,
                "a cold patch lives at least five seconds");
        require(InfernoDecayPolicy.patchRadius(1.0D) >= InfernoDecayPolicy.patchRadius(0.1D),
                "patches shrink with health");

        // Reachable coverage on a small grid: after selection, every walkable
        // cell is within the max gap of some patch, and never zero patches.
        List<Cell> arena = new ArrayList<>();
        for (int x = 0; x < 21; x++)
            for (int z = 0; z < 21; z++) arena.add(new Cell(x, z));
        List<Cell> safe = InfernoDecayPolicy.selectCovering(arena, Set.of(),
                InfernoDecayPolicy.MAX_PATCHES, InfernoDecayPolicy.MAX_SAFE_SPACING_BLOCKS, 12345L);
        require(!safe.isEmpty(), "coverage selection always yields at least one patch");
        require(safe.size() <= InfernoDecayPolicy.MAX_PATCHES, "coverage respects the cap");
        require(InfernoDecayPolicy.coverageSatisfied(arena, safe,
                        InfernoDecayPolicy.MAX_SAFE_SPACING_BLOCKS),
                "every walkable cell is within reach of a cold patch");

        // Protected cells are never chosen as refuges.
        Cell core = new Cell(10, 10);
        List<Cell> safe2 = InfernoDecayPolicy.selectCovering(arena, Set.of(core),
                InfernoDecayPolicy.MAX_PATCHES, InfernoDecayPolicy.MAX_SAFE_SPACING_BLOCKS, 7L);
        require(!safe2.contains(core), "the protected core is never a refuge");
        require(InfernoDecayPolicy.covered(10, 12, safe2, InfernoDecayPolicy.MAX_SAFE_SPACING_BLOCKS),
                "no standable cell is stranded");

        System.out.println("PASS inferno decay kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS inferno decay" in output


def test_rotating_fire_ring_shape_and_timing(tmp_path):
    output = compile_and_run(
        tmp_path,
        "FireRingProbe",
        """
import me.copimine.endevent.domain.RiftFireRingPolicy;
import me.copimine.endevent.domain.RiftFireRingPolicy.Phase;

public class FireRingProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(RiftFireRingPolicy.phaseFor(0) == Phase.GATHER, "a ring starts by gathering");
        require(RiftFireRingPolicy.phaseFor(RiftFireRingPolicy.GATHER_TICKS) == Phase.FLIGHT,
                "the ring flies once gathering ends");
        require(RiftFireRingPolicy.inFlight(RiftFireRingPolicy.GATHER_TICKS + 1),
                "the ring is in flight mid-cast");
        require(RiftFireRingPolicy.phaseFor(RiftFireRingPolicy.GATHER_TICKS
                        + RiftFireRingPolicy.FLIGHT_TICKS_MAX + 1) == Phase.BURST,
                "an unobstructed ring still bursts");
        require(RiftFireRingPolicy.phaseFor(100000) == Phase.ENDED, "a ring never lives forever");

        // The ring keeps spinning the whole flight: no jump cut.
        double a0 = RiftFireRingPolicy.rotationDegrees(0);
        double a1 = RiftFireRingPolicy.rotationDegrees(1);
        require(a1 != a0, "the ring rotates as it flies");
        require(RiftFireRingPolicy.rotationDegrees(-5) == 0.0D, "a negative tick is clamped");

        // Segments sit on the authored ring radius.
        for (int i = 0; i < RiftFireRingPolicy.SEGMENTS; i++) {
            double[] offset = RiftFireRingPolicy.segmentOffset(i, 0.0D);
            double radius = Math.hypot(offset[0], offset[1]);
            require(Math.abs(radius - RiftFireRingPolicy.RING_RADIUS) < 1.0e-6,
                    "each segment sits on the ring radius");
        }

        require(!RiftFireRingPolicy.mayReachRing(0.0D, 0.0D, .45D, .26D), "the ring has a safe centre");
        require(RiftFireRingPolicy.mayReachRing(5.0D, 0.0D, .45D, .26D), "the visible shell enters the broad phase");
        require(!RiftFireRingPolicy.mayReachRing(7.0D, 0.0D, .45D, .26D), "outside the ring stays outside");
        require(RiftFireRingPolicy.planeHeight(5, 0, 1, 0) > 0.5D, "leading rim rises slightly");
        require(RiftFireRingPolicy.planeHeight(0, 5, 1, 0) == 0D, "side rim stays level");

        // Once-per-threshold plus a real cooldown.
        require(RiftFireRingPolicy.mayCast(-1L, 500L), "a first ring may always fire");
        require(!RiftFireRingPolicy.mayCast(100L, 100L + RiftFireRingPolicy.COOLDOWN_TICKS - 1L),
                "a ring respects its cooldown");
        require(RiftFireRingPolicy.mayCast(100L, 100L + RiftFireRingPolicy.COOLDOWN_TICKS),
                "a ring may fire again after the cooldown");

        System.out.println("PASS fire ring kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS fire ring" in output


def test_infernal_forge_sequence_and_vulnerability(tmp_path):
    output = compile_and_run(
        tmp_path,
        "InfernalForgeProbe",
        """
import me.copimine.endevent.domain.InfernalForgePolicy;
import me.copimine.endevent.domain.InfernalForgePolicy.Phase;
import java.util.List;

public class InfernalForgeProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(InfernalForgePolicy.phaseFor(0) == Phase.GATHER, "the forge starts by pulling fire in");
        require(InfernalForgePolicy.phaseFor(InfernalForgePolicy.GATHER_TICKS) == Phase.OVERHEAT,
                "gathering then overheat");
        require(InfernalForgePolicy.phaseFor(InfernalForgePolicy.slamWindupTick()) == Phase.SLAM,
                "the strike series closes with a slam windup");
        require(InfernalForgePolicy.phaseFor(InfernalForgePolicy.slamImpactTick()) == Phase.SLAM,
                "the slam impact is still in the slam phase");
        require(InfernalForgePolicy.phaseFor(InfernalForgePolicy.vulnerableStartTick()) == Phase.VULNERABLE,
                "the broken shell opens the vulnerable window");
        require(InfernalForgePolicy.phaseFor(InfernalForgePolicy.totalTicks()) == Phase.ENDED,
                "the forge ends after the window");

        // The strike series is ordered and ends before the slam.
        List<Long> strikes = InfernalForgePolicy.strikeTicks();
        require(strikes.size() == InfernalForgePolicy.STRIKE_COUNT, "every strike is scheduled");
        for (int i = 1; i < strikes.size(); i++)
            require(strikes.get(i) > strikes.get(i - 1), "strike ticks increase");
        require(strikes.get(strikes.size() - 1) < InfernalForgePolicy.slamImpactTick(),
                "the slam lands after the last strike");

        // Counterplay: vulnerable only inside the recovery window, softer outside.
        require(InfernalForgePolicy.isVulnerable(InfernalForgePolicy.vulnerableStartTick()),
                "the recovery window is vulnerable");
        require(!InfernalForgePolicy.isVulnerable(0), "the gather is not vulnerable");
        require(InfernalForgePolicy.incomingDamageMultiplier(InfernalForgePolicy.vulnerableStartTick())
                        > InfernalForgePolicy.incomingDamageMultiplier(0),
                "the broken shell takes more damage");
        require(InfernalForgePolicy.incomingDamageMultiplier(-999) == 1.0D,
                "outside the window damage is unchanged");

        // A directed impulse is a cone; the slam is radial.
        require(InfernalForgePolicy.impulseConnects(5.0D, 0.0D), "a target ahead is hit");
        require(InfernalForgePolicy.impulseConnects(5.0D, 30.0D), "a target in the cone is hit");
        require(!InfernalForgePolicy.impulseConnects(5.0D, 90.0D), "a target beside is safe");
        require(!InfernalForgePolicy.impulseConnects(99.0D, 0.0D), "out of reach is safe");
        require(InfernalForgePolicy.slamConnects(3.0D), "the slam hits nearby");
        require(!InfernalForgePolicy.slamConnects(50.0D), "the slam is bounded");

        // Medium difficulty: no extra mobs, and a real cooldown.
        require(!InfernalForgePolicy.spawnsExtraMobs(), "the forge adds no bodies");
        require(!InfernalForgePolicy.mayCast(0L, InfernalForgePolicy.COOLDOWN_TICKS - 1L),
                "the forge respects its cooldown");
        require(InfernalForgePolicy.mayCast(0L, InfernalForgePolicy.COOLDOWN_TICKS),
                "the forge may fire after the cooldown");

        System.out.println("PASS infernal forge kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS infernal forge" in output


def test_signature_gate_fires_once_per_threshold(tmp_path):
    output = compile_and_run(
        tmp_path,
        "SignatureGateProbe",
        """
import me.copimine.endevent.domain.BossSignatureCastGatePolicy;
import me.copimine.endevent.domain.BossSignatureCastGatePolicy.Gate;
import java.util.*;

public class SignatureGateProbe {
    static int checks = 0;
    static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        List<Double> thresholds = BossSignatureCastGatePolicy.normaliseThresholds(
                Arrays.asList(0.26D, 0.40D, 0.33D, 0.40D));
        require(thresholds.equals(Arrays.asList(0.40D, 0.33D, 0.26D)),
                "thresholds normalise descending and deduped");

        Set<Double> spent = new LinkedHashSet<>();
        // Above every threshold nothing fires.
        require(!BossSignatureCastGatePolicy.mayFire(0.9D, thresholds, spent),
                "nothing fires while healthy");
        // Crossing 0.40 fires exactly once, consuming that threshold.
        Gate first = BossSignatureCastGatePolicy.gate(0.39D, thresholds, spent);
        require(first.allowed(), "crossing a threshold fires the signature");
        require(first.threshold() == 0.40D, "the spent threshold is the one crossed");
        spent = BossSignatureCastGatePolicy.consume(spent, first.threshold());
        require(!BossSignatureCastGatePolicy.mayFire(0.39D, thresholds, spent),
                "the same signature never fires twice at one threshold");
        // Dropping further arms the next threshold only.
        Gate second = BossSignatureCastGatePolicy.gate(0.30D, thresholds, spent);
        require(second.allowed() && second.threshold() == 0.33D,
                "the next threshold is spent next");
        spent = BossSignatureCastGatePolicy.consume(spent, second.threshold());
        Gate third = BossSignatureCastGatePolicy.gate(0.01D, thresholds, spent);
        require(third.allowed() && third.threshold() == 0.26D, "the last threshold fires last");
        spent = BossSignatureCastGatePolicy.consume(spent, third.threshold());
        require(!BossSignatureCastGatePolicy.mayFire(0.0D, thresholds, spent),
                "no threshold remains: the signature is done for the phase");

        // Malformed input is treated as absent, never guessed.
        require(!BossSignatureCastGatePolicy.mayFire(0.5D, null, null), "no thresholds, no fire");
        require(Double.isNaN(BossSignatureCastGatePolicy.firstUnconsumedThreshold(
                0.5D, Arrays.asList(Double.NaN, null), null)), "bad thresholds are ignored");

        System.out.println("PASS signature gate kept " + checks + " checks");
    }
}
""",
    )
    assert "PASS signature gate" in output
