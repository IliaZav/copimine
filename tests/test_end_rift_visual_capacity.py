"""Client visual capacity: a bounded number of concurrent effects.

``RemoteVisualCapacityPolicy`` is the only thing stopping a burst of visual
commands from stacking unbounded renderers, and it is also the thing that could
silently drop an update to an effect that is already running. It had no test.

The policy is package-private, so the probe is declared in the same package and
run by fully qualified name.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
PACKAGE = "me.copimine.client"


def run_probe(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(CLIENT / "RemoteVisualCapacityPolicy.java"), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(
        ["java", "-cp", str(tmp_path), PACKAGE + "." + name],
        capture_output=True,
        text=True,
    )
    assert executed.returncode == 0, executed.stdout + executed.stderr
    return executed.stdout


def test_capacity_is_a_hard_bound_but_updates_always_pass(tmp_path):
    run_probe(
        tmp_path,
        "CapacityProbe",
        """
package me.copimine.client;

public final class CapacityProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        int cap = RemoteVisualCapacityPolicy.MAX_ACTIVE_VISUALS;
        check(cap > 0, "the cap must admit at least one visual, found " + cap);

        // Below the cap a new effect is admitted, at every slot.
        for (int active = 0; active < cap; active++) {
            check(RemoteVisualCapacityPolicy.accepts(active, false),
                    "a free slot at " + active + "/" + cap + " must be usable");
        }

        // At and beyond the cap a new effect is refused, so a burst cannot
        // stack unbounded renderers.
        check(!RemoteVisualCapacityPolicy.accepts(cap, false),
                "a full set must refuse a new visual");
        check(!RemoteVisualCapacityPolicy.accepts(cap + 1, false),
                "an over-full set must still refuse");
        check(!RemoteVisualCapacityPolicy.accepts(1000, false),
                "a wildly over-full set must still refuse");

        // An UPDATE to a sequence that is already active is always accepted,
        // even at the cap. This is the assertion that matters: refusing it
        // would drop a refresh to a running effect and leave the player
        // looking at a stale intensity or a permanently stuck overlay.
        for (int active = 0; active <= cap + 2; active++) {
            check(RemoteVisualCapacityPolicy.accepts(active, true),
                    "an update to an active sequence must pass at " + active + "/" + cap);
        }

        // A negative count is malformed but must not admit a new effect when
        // only an active-sequence update should pass.
        check(RemoteVisualCapacityPolicy.accepts(-1, true),
                "an active update is accepted regardless of a malformed count");
    }
}
""",
    )
