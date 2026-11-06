"""Wave 2 phantoms must fill the arena as a scattered field, never as rings or clusters.

The player's own description of wave 2 is "a lot of semi-transparent Endermen scattered across the
arena, all looking at me". Two things follow from that, and the layout policy could break either:

RINGS. An earlier design placed phantoms on concentric rings. That reads as an ornament, not as a
field of figures standing where they like. The scatter must draw BOTH the angle and the radius
uniformly, so no ring or spoke structure can appear.

CLUSTERS. Uniform sampling on its own still lets two candidates land almost on top of each other -
the density is about one figure per 800 square blocks, so close pairs happen often. Two figures
standing shoulder to shoulder read as a single clump, which is the opposite of "scattered".

This test drives the real policy through a throwaway main: it compiles the actual
WaveTwoPhantomLayoutPolicy and asserts the field is scattered on both counts, for several seeds.
"""
from __future__ import annotations

import re
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
POLICY = (ROOT / 'copimine-end-event' / 'src' / 'me' / 'copimine' / 'endevent'
          / 'domain' / 'WaveTwoPhantomLayoutPolicy.java')

PROBE = textwrap.dedent(
    '''
    import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy;
    import me.copimine.endevent.domain.WaveTwoPhantomLayoutPolicy.Position;
    import java.util.List;
    import java.util.function.Function;

    public final class ScatterProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        public static void main(String[] args) {
            Function<Position, Position> pass = p -> p;
            Position anchor = new Position(0.0, 68.0, 0.0);
            double radius = 96.0;

            for (long seed : new long[]{1L, 7L, 42L, 2024L, 99999L}) {
                List<WaveTwoPhantomLayoutPolicy.Phantom> field =
                        WaveTwoPhantomLayoutPolicy.selectSafe(anchor, seed, radius, pass);
                String tag = "seed " + seed + ": ";

                check(tag + "fills the field", field.size() == WaveTwoPhantomLayoutPolicy.TARGET_PHANTOM_COUNT);

                double minGap = Double.MAX_VALUE;
                int pairsTooClose = 0;
                for (int i = 0; i < field.size(); i++) {
                    for (int j = i + 1; j < field.size(); j++) {
                        double dx = field.get(i).position().x() - field.get(j).position().x();
                        double dz = field.get(i).position().z() - field.get(j).position().z();
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d < minGap) minGap = d;
                        if (d < 4.0) pairsTooClose++;
                    }
                }
                check(tag + "no clusters", pairsTooClose == 0);
                check(tag + "visible separation", minGap >= 4.0);
            }

            // Determinism: the same seed must give the same field, or the figures would jump
            // every time the layout is rebuilt.
            List<WaveTwoPhantomLayoutPolicy.Phantom> a =
                    WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 42L, radius, pass);
            List<WaveTwoPhantomLayoutPolicy.Phantom> b =
                    WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 42L, radius, pass);
            check("same seed is stable", a.equals(b));

            // No rings: with a ring layout every radius would land on a handful of fixed values.
            List<WaveTwoPhantomLayoutPolicy.Phantom> field =
                    WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 42L, radius, pass);
            java.util.Set<Integer> roundedRadii = new java.util.LinkedHashSet<>();
            for (WaveTwoPhantomLayoutPolicy.Phantom p : field) {
                double dx = p.position().x() - anchor.x();
                double dz = p.position().z() - anchor.z();
                roundedRadii.add((int) Math.round(Math.sqrt(dx * dx + dz * dz)));
            }
            check("radii are spread, not ringed", roundedRadii.size() >= field.size() / 2);

            // Rings also hide in a narrower test: a layout that pins one axis (say, a fixed
            // depth for every figure) still spreads the RADIUS around, because the radius is
            // measured from the anchor. Check that both axes actually vary.
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (WaveTwoPhantomLayoutPolicy.Phantom p : field) {
                minX = Math.min(minX, p.position().x());
                maxX = Math.max(maxX, p.position().x());
                minZ = Math.min(minZ, p.position().z());
                maxZ = Math.max(maxZ, p.position().z());
            }
            check("x axis is used", (maxX - minX) > radius);
            check("z axis is used", (maxZ - minZ) > radius);

            // Every figure keeps the anchor height, so nobody floats or sinks.
            boolean sameHeight = true;
            for (WaveTwoPhantomLayoutPolicy.Phantom p : field) {
                if (p.position().y() != anchor.y()) sameHeight = false;
            }
            check("every figure keeps the anchor height", sameHeight);

            // A cramped field still has to place figures, not collapse to nothing.
            List<WaveTwoPhantomLayoutPolicy.Phantom> cramped =
                    WaveTwoPhantomLayoutPolicy.selectSafe(anchor, 5L, 8.0, pass);
            check("a cramped arena still scatters", cramped.size() >= 1);

            if (failures > 0) {
                System.out.println("FAILURES " + failures);
                System.exit(1);
            }
            System.out.println("OK");
        }
    }
    '''
)


def test_wave_two_phantoms_scatter_without_rings_or_clusters(tmp_path: Path) -> None:
    assert POLICY.is_file(), f'the scatter policy is missing: {POLICY}'
    source = POLICY.read_text(encoding='utf-8')

    # The scatter must draw the radius and the angle independently and uniformly.
    assert 'random.nextDouble(0.0D, Math.PI * 2.0D)' in source, (
        'the angle must be drawn uniformly, or the figures line up instead of scattering'
    )
    assert 'random.nextDouble() * (radiusSquared - innerRadiusSquared)' in source, (
        'the radius must be drawn uniformly, or every figure lands on the same ring'
    )
    assert 'minimumSeparation(' in source, (
        'a separation floor is what keeps two figures from standing shoulder to shoulder'
    )
    assert 'tooClose(resolved, distinct, minSeparationSquared)' in source, (
        'the floor has to be applied to each accepted candidate, not merely computed'
    )

    probe = tmp_path / 'ScatterProbe.java'
    probe.write_text(PROBE, encoding='utf-8')
    classes = tmp_path / 'classes'
    classes.mkdir()

    compile_result = subprocess.run(
        ['javac', '-d', str(classes), str(POLICY), str(probe)],
        capture_output=True, text=True, timeout=180,
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

    run_result = subprocess.run(
        ['java', '-cp', str(classes), 'ScatterProbe'],
        capture_output=True, text=True, timeout=120,
    )
    assert run_result.returncode == 0, run_result.stdout + run_result.stderr
    assert 'OK' in run_result.stdout
