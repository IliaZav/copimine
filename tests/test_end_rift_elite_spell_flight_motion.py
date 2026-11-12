"""Behavioural contract for elite spell flight animation.

The three elite spells used to fly as frozen meshes: only the spider's cobweb
block tumbled, so a rift bolt, a void burst and a dragon breath all slid across
the arena like static props. This compiles the real policy and pins the motion
each spell must have, so a spell cannot silently lose its animation again.
"""

import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "EliteSpellFlightMotionPolicy.java"
)

PROBE = textwrap.dedent(
    """
    import me.copimine.endevent.domain.EliteSpellFlightMotionPolicy;
    import me.copimine.endevent.domain.EliteSpellFlightMotionPolicy.Motion;

    public final class SpellMotionProbe {
        private static int failures = 0;

        private static void check(boolean condition, String message) {
            if (!condition) {
                failures++;
                System.out.println("FAIL: " + message);
            }
        }

        private static void near(double actual, double expected, double tolerance, String message) {
            check(Math.abs(actual - expected) <= tolerance,
                    message + " (expected " + expected + " got " + actual + ")");
        }

        public static void main(String[] args) {
            // 1. Every authored spell must actually animate.
            check(EliteSpellFlightMotionPolicy.animates("RIFT_BOLT"), "rift bolt animates");
            check(EliteSpellFlightMotionPolicy.animates("VOID_BURST"), "void burst animates");
            check(EliteSpellFlightMotionPolicy.animates("DRAGON_BREATH"), "dragon breath animates");

            // Identity is case and whitespace insensitive, because the server
            // reads it back out of persistent data.
            check(EliteSpellFlightMotionPolicy.animates(" rift_bolt "), "normalised identity");
            check(!EliteSpellFlightMotionPolicy.animates("WEB_SHOT"), "web shot keeps its own path");
            check(!EliteSpellFlightMotionPolicy.animates(null), "null is not animated");
            check(!EliteSpellFlightMotionPolicy.animates(""), "blank is not animated");

            // 2. An unknown spell still renders, simply unanimated.
            Motion unknown = EliteSpellFlightMotionPolicy.motionFor("SOMETHING_ELSE", 500L);
            near(unknown.spinRadians(), 0.0D, 1.0E-9D, "unknown spin is zero");
            near(unknown.scaleMultiplier(), 1.0F, 1.0E-6D, "unknown scale is one");

            // 3. A spear spins fast, so the fins flicker instead of freezing.
            Motion boltStart = EliteSpellFlightMotionPolicy.motionFor("RIFT_BOLT", 0L);
            Motion boltQuarter = EliteSpellFlightMotionPolicy.motionFor("RIFT_BOLT", 60L);
            near(boltStart.spinRadians(), 0.0D, 1.0E-9D, "bolt spin starts at zero");
            near(boltQuarter.spinRadians(), Math.PI / 2.0D, 1.0E-6D, "bolt spin is a quarter turn");
            near(boltStart.wobblePitch(), 0.0F, 1.0E-6D, "bolt does not wobble");
            check(EliteSpellFlightMotionPolicy.motionFor("RIFT_BOLT", 240L).spinRadians() < 1.0E-6D,
                    "bolt spin wraps at its period");

            // 4. An orb breathes rather than spins: its scale swing is the
            //    largest of the three, because the cast must look like it is
            //    about to burst.
            float orbLow = EliteSpellFlightMotionPolicy.motionFor("VOID_BURST", 0L).scaleMultiplier();
            float orbHigh = EliteSpellFlightMotionPolicy.motionFor("VOID_BURST", 210L).scaleMultiplier();
            near(orbLow, 1.0F, 1.0E-6D, "orb starts at base scale");
            check(orbHigh > 1.20F, "orb swells visibly");
            check(orbHigh - orbLow > 0.20F, "orb swing exceeds the spear swing");

            // 5. A flame gutters: it wobbles on both axes at once.
            Motion flame = EliteSpellFlightMotionPolicy.motionFor("DRAGON_BREATH", 65L);
            check(Math.abs(flame.wobblePitch()) > 0.01F, "flame wobbles on pitch");
            check(Math.abs(flame.wobbleYaw()) > 0.01F, "flame wobbles on yaw");
            check(Math.abs(flame.wobblePitch() - flame.wobbleYaw()) > 1.0E-4F,
                    "flame axes are out of phase, not a single tilt");

            // 6. The three signature motions must be distinguishable, otherwise
            //    the art change does not actually differentiate the spells.
            Motion boltAt = EliteSpellFlightMotionPolicy.motionFor("RIFT_BOLT", 100L);
            Motion orbAt = EliteSpellFlightMotionPolicy.motionFor("VOID_BURST", 100L);
            Motion flameAt = EliteSpellFlightMotionPolicy.motionFor("DRAGON_BREATH", 100L);
            check(Math.abs(boltAt.spinRadians() - orbAt.spinRadians()) > 0.1D,
                    "bolt and orb spin at different rates");
            check(Math.abs(orbAt.scaleMultiplier() - flameAt.scaleMultiplier()) > 0.005F,
                    "orb and flame pulse differently");

            // 7. Motion is a pure function of elapsed time and never wild.
            for (String spell : new String[] {"RIFT_BOLT", "VOID_BURST", "DRAGON_BREATH"}) {
                for (long t = 0L; t <= 4000L; t += 37L) {
                    Motion m = EliteSpellFlightMotionPolicy.motionFor(spell, t);
                    check(Double.isFinite(m.spinRadians())
                                    && m.spinRadians() >= 0.0D
                                    && m.spinRadians() < Math.PI * 2.0D + 1.0E-6D,
                            spell + " spin stays in one turn at " + t);
                    check(Float.isFinite(m.scaleMultiplier())
                                    && m.scaleMultiplier() >= 1.0F
                                    && m.scaleMultiplier() <= 1.30F,
                            spell + " scale stays readable at " + t);
                    check(Math.abs(m.wobblePitch()) < 0.5F && Math.abs(m.wobbleYaw()) < 0.5F,
                            spell + " wobble stays gentle at " + t);
                }
            }

            // 8. Negative time cannot happen, but must not break the maths.
            near(EliteSpellFlightMotionPolicy.motionFor("RIFT_BOLT", -500L).spinRadians(),
                    0.0D, 1.0E-9D, "negative elapsed clamps to start");

            if (failures > 0) {
                System.out.println("FAILURES=" + failures);
                System.exit(1);
            }
            System.out.println("OK");
        }
    }
    """
)


def test_elite_spells_have_distinct_flight_motion(tmp_path):
    folder = tmp_path / "me" / "copimine" / "endevent" / "domain"
    folder.mkdir(parents=True)
    (folder / "EliteSpellFlightMotionPolicy.java").write_text(
        SOURCE.read_text(encoding="utf-8"), encoding="utf-8"
    )
    probe = tmp_path / "SpellMotionProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stderr

    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "SpellMotionProbe"],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert "OK" in ran.stdout


PLUGIN_SOURCE = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def test_plugin_ticker_actually_applies_the_spell_motion():
    """The policy is worthless if the server never calls it.

    Keep the wiring pinned: the ticker must read the ability identity off the
    projectile, obtain a Motion from the real policy, and feed it into the
    ItemDisplay transformation. Also pin the spawn-time bookkeeping, otherwise
    elapsed time silently freezes at zero.
    """
    source = PLUGIN_SOURCE.read_text(encoding="utf-8")
    assert "EliteSpellFlightMotionPolicy.motionFor(abilityId, elapsed)" in source
    assert "itemDisplay.setTransformation(eliteSpellFlightTransformation(motion))" in source
    assert "activeEliteProjectileSpawnMillis.put(projectile.getUniqueId()," in source
    # All three cleanup paths must drop the spawn instant, or the map leaks.
    assert source.count("activeEliteProjectileSpawnMillis.remove(projectileId)") >= 3
    assert "activeEliteProjectileSpawnMillis.clear()" in source
    assert "import me.copimine.endevent.domain.EliteSpellFlightMotionPolicy;" in source
