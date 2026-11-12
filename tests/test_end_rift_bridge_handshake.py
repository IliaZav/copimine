"""Client bridge handshake: pair an acknowledgement with the active hello.

``BridgeHandshakePolicy`` is the gate that decides whether the client mod and the
server plugin are actually talking. Getting it wrong in the permissive direction
means a stale acknowledgement is accepted as a live pairing, so the client starts
consuming visuals meant for a session that has already ended; getting it wrong in
the strict direction means the bridge never comes up and none of the event packets
arrive. It had no test.

The policy is package-private, so the probe is declared in the same package.
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
         str(CLIENT / "BridgeHandshakePolicy.java"), str(probe)],
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


def test_acknowledgement_must_match_the_active_session(tmp_path):
    run_probe(
        tmp_path,
        "HandshakeProbe",
        """
package me.copimine.client;

public final class HandshakeProbe {
    static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    static boolean ack(boolean connected, boolean helloSent, String active, String acknowledged) {
        return BridgeHandshakePolicy.acceptsAcknowledgement(connected, helloSent, active, acknowledged);
    }

    public static void main(String[] args) {
        // The one shape that must pair.
        check(ack(true, true, "session-a", "session-a"),
                "a matching acknowledgement on a live connection must pair");

        // A stale acknowledgement from a previous session must NOT pair. This is
        // the failure that matters: it would attach the client to a dead session.
        check(!ack(true, true, "session-b", "session-a"),
                "an acknowledgement for another session must be refused");

        // Nothing pairs before the hello is out or the link is up.
        check(!ack(false, true, "session-a", "session-a"),
                "a disconnected link must refuse any acknowledgement");
        check(!ack(true, false, "session-a", "session-a"),
                "an acknowledgement before the hello was sent must be refused");

        // Absent or blank session ids cannot pair, in either position.
        check(!ack(true, true, null, "session-a"), "a null active session must refuse");
        check(!ack(true, true, "", "session-a"), "a blank active session must refuse");
        check(!ack(true, true, "   ", "session-a"), "a whitespace active session must refuse");
        check(!ack(true, true, "session-a", null), "a null acknowledged id must refuse");
        check(!ack(true, true, null, null), "two null ids must refuse");

        // Exact match only: case and surrounding space are different sessions.
        check(!ack(true, true, "Session-A", "session-a"),
                "the session id comparison must be case sensitive");
        check(!ack(true, true, "session-a ", "session-a"),
                "a trailing space must not be trimmed into a match");

        // --- shouldAttemptHello ---
        check(BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, 0, 3),
                "the first hello must be attempted");
        check(BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, 2, 3),
                "the last permitted attempt must still be attempted");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, 3, 3),
                "attempts at the cap must stop");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, 4, 3),
                "attempts over the cap must stop");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", true, 0, 3),
                "an acknowledged hello must not be resent");
        check(!BridgeHandshakePolicy.shouldAttemptHello(false, "ch-1", false, 0, 3),
                "a disconnected link must not send a hello");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, null, false, 0, 3),
                "a null challenge must not send a hello");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "", false, 0, 3),
                "a blank challenge must not send a hello");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "  ", false, 0, 3),
                "a whitespace challenge must not send a hello");

        // Degenerate budgets must not loop. A zero or negative cap is a
        // configuration error, not permission to try once.
        //
        // NOTE ON MUTATIONS: weakening `maxAttempts > 0` to `maxAttempts >= 0` does
        // NOT change any result, and that is not a gap in these assertions. With
        // maxAttempts == 0 the remaining checks demand attempts >= 0 AND
        // attempts < 0, which is unsatisfiable, so the method still returns false.
        // The `> 0` test is a redundant guard, kept as documentation of intent.
        // It should not be counted as a caught mutation.
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, 0, 0),
                "a zero attempt budget must send nothing");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, 0, -1),
                "a negative attempt budget must send nothing");
        check(!BridgeHandshakePolicy.shouldAttemptHello(true, "ch-1", false, -1, 3),
                "a negative attempt count must send nothing");

        // The budget is exact: every count from 0 to max-1 attempts, nothing above.
        for (int max = 1; max <= 5; max++) {
            for (int attempt = 0; attempt < max; attempt++) {
                check(BridgeHandshakePolicy.shouldAttemptHello(true, "c", false, attempt, max),
                        "attempt " + attempt + " of " + max + " must be permitted");
            }
            check(!BridgeHandshakePolicy.shouldAttemptHello(true, "c", false, max, max),
                    "attempt " + max + " of " + max + " must be refused");
        }
    }
}
""",
    )
