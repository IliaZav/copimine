"""Echo frames must survive the real signed bridge codec.

``END_ECHO_BIND``/``END_ECHO_STATE``/``END_ECHO_REMOVE`` go through
``EndEventBridgeV4Sender.send`` -> ``EndEventBridgeBodyCodec.encode``. That
codec refuses any nonzero ``durationMillis`` on an Echo body, so a producer that
passes a lease duration silently drops every frame at encode and the copy never
renders.

This test extracts the duration literals the two production producers actually
pass, feeds them into the real codec with a real ``EchoPresentationProbeState``
frame, and proves both that they encode and that the old value is rejected. It
also pins the Phase 5 copy band: the signed body forbids non-empty reserved
strings, so copy-ness has to ride the frame epoch, never a ``reason`` marker.
"""

from pathlib import Path
import re
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
PROTOCOL = ROOT / "tools/minecraft-26.3/protocol/src/main/java"
END_EVENT_SRC = ROOT / "copimine-end-event/src"


def _duration_literals(source):
    """The duration argument each Echo producer hands to sendClientPacket."""
    patterns = {
        "sendEchoCloneView": r'"END_ECHO_STATE",\s*frame\.duel\(\)\.toString\(\),\s*([0-9_]+L)',
        "sendNamedEchoView": r'type,\s*pair\.duel\(\)\.toString\(\),\s*([0-9_]+L)',
    }
    found = {}
    for name, pattern in patterns.items():
        match = re.search(pattern, source)
        if match is None:
            raise AssertionError(f"{name} no longer sends an Echo frame in the expected shape")
        found[name] = int(match.group(1).rstrip("L").replace("_", ""))
    return found


def _probe_source(clone_duration, named_duration):
    return f"""
import java.util.UUID;
import me.copimine.protocol.EndEventBridgeBodyCodec;
import me.copimine.endevent.domain.wave7.EchoPresentationProbeState;
import me.copimine.endevent.domain.Phase5EchoCopyPresentationPolicy;

public class EchoWireProbe {{
    static final UUID EVENT = new UUID(0x1111L, 0x2222L);
    static final UUID DUEL = new UUID(0x3333L, 0x4444L);
    static final UUID ACTOR = new UUID(0x5555L, 0x6666L);
    static final UUID OWNER = new UUID(0x7777L, 0x8888L);
    static int accepted;

    /** Mirrors EndEventBridgeV4Sender.send: controlId is `source` (empty),
     *  reservedOne is `reason` (empty for every Echo body). */
    static boolean send(long duration, long epoch) {{
        EchoPresentationProbeState view = new EchoPresentationProbeState(
                EVENT, 1L, epoch, DUEL, ACTOR, OWNER, "minecraft:the_end", 100L);
        EchoPresentationProbeState.Frame frame = view.nextFrame(120L);
        if (frame == null) throw new AssertionError("no frame");
        EndEventBridgeBodyCodec.Body body = new EndEventBridgeBodyCodec.Body(
                EndEventBridgeBodyCodec.Type.END_ECHO_STATE, 1L, 1000L, EVENT,
                frame.duel().toString(), "", "END_RIFT_ECHO_V1", duration, 0.0F, 0, 0,
                frame.actor().toString(), frame.fields(), "", "", frame.dimension());
        EndEventBridgeBodyCodec.Body decoded =
                EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(body));
        if (!decoded.equals(body)) throw new AssertionError("echo body did not round-trip");
        String[] fields = decoded.clearPolicy().split("\\\\|", -1);
        if (fields.length != 12) throw new AssertionError("echo frame field count");
        if (!fields[1].equals(Long.toString(epoch))) throw new AssertionError("epoch field drift");
        accepted++;
        return true;
    }}

    public static void main(String[] args) {{
        // Exactly the durations the production producers pass.
        if (!send({clone_duration}L, 3L)) throw new AssertionError("clone frame refused");
        if (!send({named_duration}L, Phase5EchoCopyPresentationPolicy.epochFor(1L)))
            throw new AssertionError("named frame refused");
        if (accepted != 2) throw new AssertionError("expected two accepted frames, saw " + accepted);

        // The copy band must survive the wire verbatim and stay distinguishable.
        if (!Phase5EchoCopyPresentationPolicy.isCopyEpoch(
                Phase5EchoCopyPresentationPolicy.epochFor(1L)))
            throw new AssertionError("Phase 5 epoch is not in the copy band");
        if (Phase5EchoCopyPresentationPolicy.isCopyEpoch(3L))
            throw new AssertionError("a Wave 7 duel epoch became a copy");

        // The regression: a lease duration makes the codec refuse the frame, so
        // every Echo packet is dropped before it is signed.
        boolean refused = false;
        try {{
            send(2000L, 3L);
        }} catch (IllegalArgumentException expected) {{
            refused = true;
        }}
        if (!refused) throw new AssertionError("codec accepted a nonzero Echo duration");
    }}
}}
"""


def test_echo_producers_pass_a_codec_accepted_duration(tmp_path):
    durations = _duration_literals(MONOLITH.read_text(encoding="utf-8"))
    probe = tmp_path / "EchoWireProbe.java"
    probe.write_text(_probe_source(durations["sendEchoCloneView"], durations["sendNamedEchoView"]),
                     encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-sourcepath", f"{PROTOCOL}{__import__('os').pathsep}{END_EVENT_SRC}",
         "-d", str(tmp_path), str(probe)],
        capture_output=True, text=True)
    assert compile_result.returncode == 0, compile_result.stderr
    run = subprocess.run(["java", "-cp", str(tmp_path), "EchoWireProbe"],
                         capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr


@pytest.mark.parametrize("producer", ["sendEchoCloneView", "sendNamedEchoView"])
def test_echo_producers_do_not_pass_a_lease(producer):
    durations = _duration_literals(MONOLITH.read_text(encoding="utf-8"))
    assert durations[producer] == 0, (
        f"{producer} must pass 0: the signed Echo body carries no lease and "
        f"EndEventBridgeBodyCodec refuses a nonzero duration, saw {durations[producer]}")
