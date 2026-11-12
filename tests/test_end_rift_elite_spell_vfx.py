"""Behavioural contract for elite spell particle signatures.

Every elite spell used to burst into the same vanilla DRAGON_BREATH puff and
emit nothing at all in flight. The policy under test gives each spell its own
trail and its own impact burst, so this test pins:
  * that each spell really has a signature,
  * that the three signatures differ from one another,
  * that particle counts stay inside a bounded, performance-safe range,
  * that a dust colour is only attached to DUST passes, which is the only
    vanilla particle that accepts DustOptions.
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
    / "EliteSpellVfxPolicy.java"
)

PROBE = textwrap.dedent(
    """
    import me.copimine.endevent.domain.EliteSpellVfxPolicy;
    import me.copimine.endevent.domain.EliteSpellVfxPolicy.Pass;
    import java.util.List;

    public final class SpellVfxProbe {
        private static int failures = 0;

        private static void check(boolean condition, String message) {
            if (!condition) {
                failures++;
                System.out.println("FAIL: " + message);
            }
        }

        private static String signature(List<Pass> passes) {
            StringBuilder builder = new StringBuilder();
            for (Pass pass : passes) {
                builder.append(pass.particle()).append(':').append(pass.count()).append(';');
            }
            return builder.toString();
        }

        public static void main(String[] args) {
            String[] spells = {"RIFT_BOLT", "VOID_BURST", "DRAGON_BREATH"};

            // 1. Every authored spell has both a trail and an impact burst.
            for (String spell : spells) {
                check(EliteSpellVfxPolicy.hasSignature(spell), spell + " has a signature");
                check(!EliteSpellVfxPolicy.flightTrail(spell).isEmpty(),
                        spell + " has a flight trail");
                check(!EliteSpellVfxPolicy.impactBurst(spell).isEmpty(),
                        spell + " has an impact burst");
            }

            // 2. The identity is normalised, because the server reads it back
            //    out of persistent data.
            check(EliteSpellVfxPolicy.hasSignature(" rift_bolt "), "normalised identity");
            check(!EliteSpellVfxPolicy.hasSignature(null), "null has no signature");
            check(!EliteSpellVfxPolicy.hasSignature("WEB_SHOT"), "web shot uses its block display");
            check(EliteSpellVfxPolicy.flightTrail("NOPE").isEmpty(), "unknown has no trail");

            // 3. The three spells must not share one puff: that was the defect.
            String bolt = signature(EliteSpellVfxPolicy.impactBurst("RIFT_BOLT"));
            String orb = signature(EliteSpellVfxPolicy.impactBurst("VOID_BURST"));
            String breath = signature(EliteSpellVfxPolicy.impactBurst("DRAGON_BREATH"));
            check(!bolt.equals(orb), "bolt and orb bursts differ");
            check(!bolt.equals(breath), "bolt and breath bursts differ");
            check(!orb.equals(breath), "orb and breath bursts differ");
            check(!signature(EliteSpellVfxPolicy.flightTrail("RIFT_BOLT"))
                            .equals(signature(EliteSpellVfxPolicy.flightTrail("VOID_BURST"))),
                    "trails differ between bolt and orb");

            // 4. No spell may fall back to a single shared vanilla puff.
            for (String spell : spells) {
                List<Pass> burst = EliteSpellVfxPolicy.impactBurst(spell);
                check(burst.size() >= 2, spell + " burst is layered, not one puff");
                boolean onlyDragonBreath = true;
                for (Pass pass : burst) {
                    if (!"DRAGON_BREATH".equals(pass.particle())) onlyDragonBreath = false;
                }
                check(!onlyDragonBreath, spell + " does not reuse the old shared puff");
            }

            // 5. Budgets stay bounded: this runs per tick and per viewer.
            int trailTotal = 0;
            for (String spell : spells) {
                int impactTotal = 0;
                for (Pass pass : EliteSpellVfxPolicy.impactBurst(spell)) {
                    check(pass.count() > 0 && pass.count() <= 24,
                            spell + " impact count is bounded");
                    impactTotal += pass.count();
                }
                check(impactTotal <= 48, spell + " impact total stays under budget");
                for (Pass pass : EliteSpellVfxPolicy.flightTrail(spell)) {
                    check(pass.count() > 0 && pass.count() <= 3,
                            spell + " trail count is tiny per tick");
                    check(pass.chance() > 0.0F && pass.chance() <= 1.0F,
                            spell + " trail chance is a probability");
                    trailTotal += pass.count();
                }
            }
            check(trailTotal <= 12, "combined trail cost per tick stays tiny");

            // 6. Dust colour is only attached to DUST, the one vanilla particle
            //    whose data type is DustOptions. Anything else throws at spawn.
            for (String spell : spells) {
                for (Pass pass : EliteSpellVfxPolicy.impactBurst(spell)) {
                    if ("DUST".equals(pass.particle())) {
                        check(pass.dustHex() != null && pass.dustHex().length() == 6,
                                spell + " dust pass carries a 6-digit colour");
                    } else {
                        check(pass.dustHex() == null,
                                spell + " non-dust pass " + pass.particle() + " carries no colour");
                    }
                }
            }

            // 7. Spreads are finite and non-negative, so a stray value cannot
            //    fling particles across the arena.
            for (String spell : spells) {
                for (Pass pass : EliteSpellVfxPolicy.impactBurst(spell)) {
                    check(pass.spreadX() >= 0.0D && pass.spreadX() <= 1.0D
                                    && pass.spreadY() >= 0.0D && pass.spreadY() <= 1.0D
                                    && pass.spreadZ() >= 0.0D && pass.spreadZ() <= 1.0D,
                            spell + " spread stays local to the impact");
                    check(Double.isFinite(pass.extra()) && pass.extra() >= 0.0D && pass.extra() <= 0.25D,
                            spell + " extra stays bounded");
                }
            }

            if (failures > 0) {
                System.out.println("FAILURES=" + failures);
                System.exit(1);
            }
            System.out.println("OK");
        }
    }
    """
)


def test_elite_spells_have_distinct_particle_signatures(tmp_path):
    folder = tmp_path / "me" / "copimine" / "endevent" / "domain"
    folder.mkdir(parents=True)
    (folder / "EliteSpellVfxPolicy.java").write_text(
        SOURCE.read_text(encoding="utf-8"), encoding="utf-8"
    )
    probe = tmp_path / "SpellVfxProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(probe)],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stderr

    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "SpellVfxProbe"],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert "OK" in ran.stdout


PLUGIN_SOURCE = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def test_plugin_emits_the_authored_trail_and_exactly_one_impact_burst():
    """The signature is worthless unless the server actually spawns it.

    Also pins the single-burst invariant: the Void Burst path draws its own
    effect above a finally block, so a second unconditional burst there would
    double every void impact.
    """
    source = PLUGIN_SOURCE.read_text(encoding="utf-8")
    assert "import me.copimine.endevent.domain.EliteSpellVfxPolicy;" in source

    # Trail follows the projectile.
    assert "spawnEliteSpellFlightTrail(position, abilityId);" in source
    assert "EliteSpellVfxPolicy.flightTrail(abilityId)" in source

    # Exactly one impact burst call site, resolved from the real ability name.
    assert source.count("spawnEliteSpellImpactBurst(") == 2, (
        "one declaration and exactly one call site"
    )
    assert "spawnEliteSpellImpactBurst(impact, ability.name());" in source
    assert "EliteSpellVfxPolicy.impactBurst(abilityId)" in source

    # The old shared puff must be gone from the impact path.
    assert "Particle.DRAGON_BREATH, 18" not in source

    # Dust is only ever attached through the guarded helper.
    assert "spellDust(" in source
    assert "Particle.DustOptions" in source
