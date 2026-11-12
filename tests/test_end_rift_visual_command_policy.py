"""Client visual commands: STOP and CLEAR_ALL must survive a disabled preference.

``ClientVisualCommandPolicy`` decides whether an authenticated remote visual
command is applied. The interesting case is the user turning server visuals off:
starts must be refused, but a stop or a clear-all must still pass, otherwise an
effect that is already on screen can never be removed and the player is left
with a stuck overlay. It had no test.

The policy is package-private, so the probe lives in the same package.
"""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client"
PROTOCOL = ROOT / "tools/minecraft-26.3/protocol/src/main/java"
PACKAGE = "me.copimine.client"


def run_probe(tmp_path, name, body):
    probe = tmp_path / (name + ".java")
    probe.write_text(body, encoding="utf-8")
    # The codec pulls in the signing policy, which pulls in the envelope: the
    # hand-maintained source list is a trap here (see AGENTS.md), so compile the
    # whole protocol package rather than tracking its internal dependencies.
    sources = [
        CLIENT / "ClientVisualCommandPolicy.java",
        *sorted(PROTOCOL.rglob("*.java")),
        probe,
    ]
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, sources)],
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


def test_starts_are_gated_but_stops_are_not(tmp_path):
    run_probe(
        tmp_path,
        "CommandProbe",
        """
package me.copimine.client;

import me.copimine.protocol.NarcoticsBridgeBodyCodec.Type;

public final class CommandProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    public static void main(String[] args) {
        // A start is applied only when the player allows server visuals.
        check(ClientVisualCommandPolicy.shouldApply(true, Type.VISUAL_START),
                "a start passes when server visuals are allowed");
        check(!ClientVisualCommandPolicy.shouldApply(false, Type.VISUAL_START),
                "a start must be refused when server visuals are disabled");

        // A stop or a clear-all ALWAYS passes. This is the pair that prevents a
        // permanently stuck overlay after the player turns visuals off.
        check(ClientVisualCommandPolicy.shouldApply(true, Type.VISUAL_STOP),
                "a stop passes when visuals are allowed");
        check(ClientVisualCommandPolicy.shouldApply(false, Type.VISUAL_STOP),
                "a stop must pass even when visuals are disabled");
        check(ClientVisualCommandPolicy.shouldApply(true, Type.VISUAL_CLEAR_ALL),
                "a clear-all passes when visuals are allowed");
        check(ClientVisualCommandPolicy.shouldApply(false, Type.VISUAL_CLEAR_ALL),
                "a clear-all must pass even when visuals are disabled");

        // Every declared type must be decided, so a future type is a conscious
        // choice rather than silently defaulting to allowed.
        for (Type type : Type.values()) {
            boolean disabled = ClientVisualCommandPolicy.shouldApply(false, type);
            boolean allowed = ClientVisualCommandPolicy.shouldApply(true, type);
            check(allowed, "every type passes when visuals are allowed: " + type);
            if (type != Type.VISUAL_STOP && type != Type.VISUAL_CLEAR_ALL) {
                check(!disabled, "only stop and clear-all pass when disabled: " + type);
            } else {
                check(disabled, type + " must pass when disabled");
            }
        }

        // A null type is refused rather than throwing.
        check(!ClientVisualCommandPolicy.shouldApply(true, null),
                "a null type is refused even when allowed");
        check(!ClientVisualCommandPolicy.shouldApply(false, null),
                "a null type is refused when disabled");
    }
}
""",
    )
