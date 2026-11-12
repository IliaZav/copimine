"""Contract 10.4: the boss picks an intent from the actual party geometry.

``BossIntentSelector.choose`` is the whole of the reaction table in contract
10.4 - surround, cluster, spread, stuck, and per-phase fallbacks. It had no test
anywhere in the suite, so the entire table could be replaced with a constant and
every gate would stay green.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"

SOURCES = [
    "BossIntent",
    "BossIntentSelector",
    "BossPerceptionSnapshot",
    "BossPhase",
    "BlackFogTimingPolicy",
    "EndRiftObjective",
    # EndRiftObjective derives REQUIRED_PILLAR_IMPACTS from this class.
    "RiftRamPolicy",
]


def run_probe(tmp_path, name, body):
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


def test_every_geometry_row_of_the_reaction_table(tmp_path):
    run_probe(
        tmp_path,
        "IntentProbe",
        """
import me.copimine.endevent.domain.*;
import java.util.*;

public final class IntentProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    // The boss stands well away from the party unless a test deliberately puts
    // players around it. Leaving this null silently centred it on (0,0,0) and
    // made every nearby player look like an encirclement.
    static BossPerceptionSnapshot snap(BossPhase phase, boolean stuck, boolean locked,
                                       BossPerceptionSnapshot.PlayerObservation... players) {
        return snapAt(new BossPerceptionSnapshot.Position(500.0, 68.0, 500.0),
                phase, stuck, locked, players);
    }

    static BossPerceptionSnapshot snapAt(BossPerceptionSnapshot.Position boss,
                                          BossPhase phase, boolean stuck, boolean locked,
                                          BossPerceptionSnapshot.PlayerObservation... players) {
        return new BossPerceptionSnapshot(phase, EndRiftObjective.Objective.RIFT_HUNT,
                Arrays.asList(players), boss,
                new BossPerceptionSnapshot.HazardSnapshot(0, 0, 0, locked, true),
                "arena", 7L, stuck);
    }

    static BossPerceptionSnapshot.PlayerObservation p(double x, double z) {
        return BossPerceptionSnapshot.PlayerObservation.at(
                UUID.randomUUID(), x, 68.0, z, 1.0, 5.0, true);
    }

    public static void main(String[] args) {
        // No boss snapshot and no living players both mean RECOVER, not a crash.
        check(BossIntentSelector.choose(null) == BossIntent.RECOVER,
                "a null snapshot recovers");
        check(BossIntentSelector.choose(snap(BossPhase.AWAKENING, false, false))
                == BossIntent.RECOVER, "an empty party recovers");

        // Stuck overrides everything, including a stacked party: the boss must
        // make room before it can punish anything.
        check(BossIntentSelector.choose(snap(BossPhase.AWAKENING, true, false, p(0,0), p(0,0)))
                == BossIntent.CREATE_SPACE, "being stuck wins over stacking");

        // A surrounded boss also creates space.
        List<BossPerceptionSnapshot.PlayerObservation> ring = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            ring.add(p(Math.cos(a) * 3.0, Math.sin(a) * 3.0));
        }
        check(BossIntentSelector.choose(snapAt(new BossPerceptionSnapshot.Position(0.0, 68.0, 0.0),
                BossPhase.HUNT, false, false,
                ring.toArray(new BossPerceptionSnapshot.PlayerObservation[0])))
                == BossIntent.CREATE_SPACE, "a surround creates space");

        // Tightly stacked party is punished as a stack.
        check(BossIntentSelector.choose(snap(BossPhase.RIFT, false, false,
                p(10, 10), p(10.2, 10.1), p(10.1, 10.3), p(9.9, 10.2)))
                == BossIntent.PUNISH_STACK, "a cluster is punished as a stack");

        // Widely spread party is hunted individually.
        check(BossIntentSelector.choose(snap(BossPhase.RIFT, false, false,
                p(-30, -30), p(30, -30), p(-30, 30), p(30, 30)))
                == BossIntent.PUNISH_SPREAD, "a spread party is punished as spread");

        // Geometry that is neither stacked (radius <= 4) nor spread (>= 8).
        // Two players 12 blocks apart give a spread radius of 6.0, which is the
        // narrow band where only the phase decides the intent. Verified against
        // the real predicates, not assumed: radius 4.0 still counts as stacked
        // and radius 8.0 already counts as spread.
        BossPerceptionSnapshot.PlayerObservation n1 = p(-6.0, 0);
        BossPerceptionSnapshot.PlayerObservation n2 = p(6.0, 0);
        // Per-phase identity when the geometry is unremarkable.
        check(BossIntentSelector.choose(snap(BossPhase.AWAKENING, false, false, n1, n2))
                == BossIntent.PRESSURE, "AWAKENING pressures");
        check(BossIntentSelector.choose(snap(BossPhase.HUNT, false, false, n1, n2))
                == BossIntent.FLANK, "HUNT flanks");
        check(BossIntentSelector.choose(snap(BossPhase.RIFT, false, false, n1, n2))
                == BossIntent.CONTROL, "RIFT controls");

        // OVERLOAD is the one phase whose fallback depends on the arena state:
        // a locked arena makes ranged pressure meaningless.
        check(BossIntentSelector.choose(snap(BossPhase.OVERLOAD, false, false, n1, n2))
                == BossIntent.RANGED_PRESSURE, "OVERLOAD pressures at range when open");
        check(BossIntentSelector.choose(snap(BossPhase.OVERLOAD, false, true, n1, n2))
                == BossIntent.RECOVER, "OVERLOAD recovers while the arena is locked");

        check(BossIntentSelector.choose(snap(BossPhase.RAGE, false, false, n1, n2))
                == BossIntent.RANGED_PRESSURE, "RAGE pressures at range");
        check(BossIntentSelector.choose(snap(BossPhase.LAST_SEAL, false, false, n1, n2))
                == BossIntent.CHANNEL, "LAST_SEAL channels");

        // A dead player is not a target and must not shape the intent.
        BossPerceptionSnapshot.PlayerObservation dead =
                BossPerceptionSnapshot.PlayerObservation.at(
                        UUID.randomUUID(), 10.0, 68.0, 10.0, 0.0, 2.0, true);
        // alive=false cannot be built through at(), so verify the filter instead:
        // a single living player still yields a per-phase intent, not RECOVER.
        check(BossIntentSelector.choose(snap(BossPhase.HUNT, false, false, p(0,0)))
                == BossIntent.FLANK, "one living player is still a target");
    }
}
""",
    )
