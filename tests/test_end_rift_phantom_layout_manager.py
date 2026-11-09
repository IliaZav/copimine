"""The Wave 2 phantom layout cache decides which server marks a player is allowed to see.

Complaint (4) is that no phantoms appear in wave 2. The positions come from the server as a set
of mark updates and per-phantom position items, and this cache is the only thing standing
between a hostile or badly ordered stream and what the player sees. It fences by event
identity, generation, dimension, cycle and timestamp, caps the layout at 35 entries, and
refuses to mix entries from a previous hunt.

Nothing tested it, so a regression that silently drops every mark would look exactly like the
reported symptom: no phantoms, no errors.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
MANAGER = CLIENT_SRC / "me/copimine/client/WaveTwoPhantomLayoutManager.java"
LAYOUT = CLIENT_SRC / "me/copimine/client/WaveTwoPhantomLayout.java"

PROBE = textwrap.dedent(
    r"""
    package me.copimine.client;

    import me.copimine.protocol.EndEventBridgeBodyCodec;

    import java.util.List;
    import java.util.UUID;

    public final class PhantomManagerProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        private static final UUID EVENT = UUID.fromString("11111111-2222-3333-4444-555555555555");
        private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");
        private static final long NOW = 5_000_000L;

        /** An ACTIVE mark update carrying no position. */
        private static EndEventBridgeBodyCodec.Body mark(UUID event, long generation, int cycle,
                                                         long timestamp, String dimension) {
            return body(event, generation, timestamp, "ACTIVE", 8_000L, 0.25F,
                    dimension + "|wave2|" + cycle, "", "", "");
        }

        /** An ACTIVE position item for one phantom. */
        private static EndEventBridgeBodyCodec.Body position(UUID event, long generation, int cycle,
                                                             long timestamp, String dimension,
                                                             String phantomId, String coords) {
            return body(event, generation, timestamp, "ACTIVE", 8_000L, 0.25F,
                    dimension + "|wave2|" + cycle, coords, phantomId, "");
        }

        /** A CLEAR packet for the same cycle. */
        private static EndEventBridgeBodyCodec.Body clear(UUID event, long generation, int cycle,
                                                          long timestamp, String dimension) {
            return body(event, generation, timestamp, "CLEAR", 0L, 0.0F,
                    dimension + "|wave2|" + cycle, "", "", "");
        }

        private static EndEventBridgeBodyCodec.Body body(UUID event, long generation, long timestamp,
                                                         String status, long duration, float progress,
                                                         String mode, String controlId,
                                                         String reservedOne, String reservedTwo) {
            return new EndEventBridgeBodyCodec.Body(
                    EndEventBridgeBodyCodec.Type.END_PLAYER_STATE,
                    generation, timestamp, event, "wave2-hunt-mark", controlId,
                    "WAVE2_HUNT_MARK_V1", duration, progress, 0L, 0L, mode,
                    "ACTIVE".equals(status) ? "intro-age=3000" : "",
                    reservedOne, reservedTwo, status);
        }

        /** Phantom ids are two digits: the server regex accepts phantom-00 through phantom-69. */
        private static String phantomId(int index) {
            return "phantom-" + (index < 10 ? "0" + index : Integer.toString(index));
        }

        private static String coords(double x) {
            return x + ",64.0,0.0";
        }

        /** Fill the layout to the given number of phantoms, all at distinct positions. */
        private static void fill(WaveTwoPhantomLayoutManager manager, int count, int firstIndex,
                                 long timestamp) {
            for (int i = 0; i < count; i++) {
                int index = firstIndex + i;
                manager.apply(position(EVENT, 1L, 1, timestamp, "overworld",
                        phantomId(index), coords(index)), NOW);
            }
        }

        /**
         * The same fill, but inserted in reverse so insertion order cannot stand in for the
         * sort. Without this the layout list is already ascending and the ordering check passes
         * even with the sort removed.
         */
        private static void fillOutOfOrder(WaveTwoPhantomLayoutManager manager, int count,
                                           long timestamp) {
            for (int index = count - 1; index >= 0; index--) {
                manager.apply(position(EVENT, 1L, 1, timestamp, "overworld",
                        phantomId(index), coords(index)), NOW);
            }
        }

        public static void main(String[] args) {
            // Exercise the real codec with the root's signed intro-age metadata
            // on BOTH the base mark and all seventy coordinate packets.
            WaveTwoPhantomLayoutManager wire = new WaveTwoPhantomLayoutManager();
            check("age-bearing mark survives actual codec and client cache", wire.apply(
                    EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(
                            mark(EVENT, 1L, 1, 1_000L, "overworld"))), NOW));
            for (int index = 0; index < WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT; index++) {
                var item = position(EVENT, 1L, 1, 1_100L + index, "overworld",
                        phantomId(index), coords(index));
                check("age-bearing coordinate survives codec " + index,
                        wire.apply(EndEventBridgeBodyCodec.decode(EndEventBridgeBodyCodec.encode(item)), NOW));
            }
            check("all seventy wire positions are available", wire.layout(
                    "overworld", EVENT.toString(), 1L, 1).size() == 70);
            // --- A fresh manager exposes nothing -------------------------------------------
            WaveTwoPhantomLayoutManager fresh = new WaveTwoPhantomLayoutManager();
            check("a fresh manager has no layout",
                    fresh.layout("overworld", EVENT.toString(), 1L, 1).isEmpty());
            check("a fresh manager is not complete",
                    !fresh.isComplete("overworld", EVENT.toString(), 1L, 1));
            check("a fresh manager reports no cycle",
                    fresh.cycle("overworld", EVENT.toString(), 1L) == -1);

            // --- A mark followed by positions builds a layout ------------------------------
            WaveTwoPhantomLayoutManager manager = new WaveTwoPhantomLayoutManager();
            check("the mark update is accepted",
                    manager.apply(mark(EVENT, 1L, 1, 1_000L, "overworld"), NOW));
            check("the mark alone is not a complete layout",
                    !manager.isComplete("overworld", EVENT.toString(), 1L, 1));
            fillOutOfOrder(manager, WaveTwoPhantomLayout.MIN_PHANTOM_COUNT, 1_100L);
            check("the layout reaches the completion threshold",
                    manager.isComplete("overworld", EVENT.toString(), 1L, 1));
            check("the layout holds every accepted phantom",
                    manager.layout("overworld", EVENT.toString(), 1L, 1)
                            .size() == WaveTwoPhantomLayout.MIN_PHANTOM_COUNT);
            check("the cycle is reported for the active hunt",
                    manager.cycle("overworld", EVENT.toString(), 1L) == 1);

            // Ordering is by phantom index, not by arrival order.
            List<WaveTwoPhantomLayoutManager.Phantom> ordered =
                    manager.layout("overworld", EVENT.toString(), 1L, 1);
            int previousIndex = -1;
            boolean ascending = true;
            for (WaveTwoPhantomLayoutManager.Phantom phantom : ordered) {
                int index = Integer.parseInt(phantom.id().substring("phantom-".length()));
                if (index <= previousIndex) ascending = false;
                previousIndex = index;
            }
            check("the layout is ordered by phantom index", ascending);

            // --- Entries are fenced by dimension, event, generation and cycle -------------
            check("another dimension sees nothing",
                    manager.layout("the_end", EVENT.toString(), 1L, 1).isEmpty());
            check("another event sees nothing",
                    manager.layout("overworld", OTHER.toString(), 1L, 1).isEmpty());
            check("another generation sees nothing",
                    manager.layout("overworld", EVENT.toString(), 2L, 1).isEmpty());
            check("another cycle sees nothing",
                    manager.layout("overworld", EVENT.toString(), 1L, 2).isEmpty());

            // --- Malformed and hostile packets are refused -------------------------------
            WaveTwoPhantomLayoutManager rejects = new WaveTwoPhantomLayoutManager();
            check("a null body is refused", !rejects.apply(null, NOW));
            check("negative now is refused", !rejects.apply(mark(EVENT, 1L, 1, 1_000L, "overworld"), -1L));
            check("a zero generation is refused",
                    !rejects.apply(mark(EVENT, 0L, 1, 1_000L, "overworld"), NOW));
            check("a zero timestamp is refused",
                    !rejects.apply(mark(EVENT, 1L, 1, 0L, "overworld"), NOW));
            check("an unknown dimension is refused",
                    !rejects.apply(mark(EVENT, 1L, 1, 1_000L, "moon"), NOW));
            check("a mode without the wave2 tag is refused",
                    !rejects.apply(body(EVENT, 1L, 1_000L, "ACTIVE", 8_000L, 0.25F,
                            "overworld|wave3|1", "", "", ""), NOW));
            check("a mode with a zero cycle is refused",
                    !rejects.apply(mark(EVENT, 1L, 0, 1_000L, "overworld"), NOW));
            check("a CLEAR that still carries a position is refused",
                    !rejects.apply(body(EVENT, 1L, 1_000L, "CLEAR", 0L, 0.0F,
                            "overworld|wave2|1", "0,0,0", "phantom-00", ""), NOW));
            check("an ACTIVE mark with a position but no id is refused",
                    !rejects.apply(body(EVENT, 1L, 1_000L, "ACTIVE", 8_000L, 0.25F,
                            "overworld|wave2|1", "0,0,0", "", ""), NOW));
            check("an out-of-range phantom id is refused",
                    !rejects.apply(position(EVENT, 1L, 1, 1_000L, "overworld", "phantom-70", "0,0,0"), NOW));
            check("a non-numeric phantom id is refused",
                    !rejects.apply(position(EVENT, 1L, 1, 1_000L, "overworld", "phantom-x", "0,0,0"), NOW));
            check("coordinates with the wrong arity are refused",
                    !rejects.apply(position(EVENT, 1L, 1, 1_000L, "overworld", "phantom-00", "0,0"), NOW));
            check("coordinates outside the world are refused",
                    !rejects.apply(position(EVENT, 1L, 1, 1_000L, "overworld",
                            "phantom-00", "30000000.0,64.0,0.0"), NOW));
            // The vertical bound is the authored +/-4096, not the vanilla build height, so a
            // mark at 1337 is legal and only beyond 4096 is refused - a phantom marked far above
            // the build limit must still be rejected rather than rendered out of the world.
            check("a mark beyond the vertical bound is refused",
                    !rejects.apply(body(EVENT, 1L, 1_000L, "ACTIVE", 8_000L, 0.25F,
                            "overworld|wave2|1", "0,4097.0,0", "phantom-00", ""), NOW));
            check("a mark at the vertical bound is accepted",
                    rejects.apply(body(EVENT, 1L, 1_000L, "ACTIVE", 8_000L, 0.25F,
                            "overworld|wave2|1", "0,4096.0,0", "phantom-00", ""), NOW));

            // --- The layout is capped at the target count ----------------------------------
            WaveTwoPhantomLayoutManager capped = new WaveTwoPhantomLayoutManager();
            capped.apply(mark(EVENT, 1L, 1, 1_000L, "overworld"), NOW);
            fill(capped, WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT + 5, 0, 1_100L);
            // NOTE: the "positions.size() >= TARGET_PHANTOM_COUNT" cap is not independently
            // observable - the phantom id pattern only admits 00..34, which is exactly the
            // target count, so a 36th distinct entry cannot be constructed. This assertion pins
            // the behaviour; it is NOT evidence that the cap itself fires. The cap is redundant
            // defence against a future widening of the id pattern, not dead code to remove.
            check("the layout never exceeds the target count",
                    capped.layout("overworld", EVENT.toString(), 1L, 1)
                            .size() == WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT);

            // --- A CLEAR empties the presentation but keeps the fences --------------------
            WaveTwoPhantomLayoutManager cleared = new WaveTwoPhantomLayoutManager();
            cleared.apply(mark(EVENT, 1L, 1, 1_000L, "overworld"), NOW);
            fill(cleared, WaveTwoPhantomLayout.MIN_PHANTOM_COUNT, 0, 1_100L);
            check("the layout is complete before the clear",
                    cleared.isComplete("overworld", EVENT.toString(), 1L, 1));
            check("the CLEAR is accepted", cleared.apply(clear(EVENT, 1L, 1, 1_500L, "overworld"), NOW));
            check("the CLEAR empties the layout",
                    cleared.layout("overworld", EVENT.toString(), 1L, 1).isEmpty());
            check("the CLEAR ends completion",
                    !cleared.isComplete("overworld", EVENT.toString(), 1L, 1));
            // The clear must actually EMPTY the stored positions, not merely hide them: the
            // layout list is already empty while inactive, so re-activating the same hunt
            // exposes whether the entries were dropped or only masked.
            check("a mark re-activates the same hunt",
                    cleared.apply(mark(EVENT, 1L, 1, 2_000L, "overworld"), NOW));
            check("the CLEAR really dropped the stored positions",
                    cleared.layout("overworld", EVENT.toString(), 1L, 1).isEmpty());
            check("the cleared hunt is no longer complete",
                    !cleared.isComplete("overworld", EVENT.toString(), 1L, 1));
            // A position from the same hunt arriving after its own clear must not resurrect it:
            // the record is still the active one, so an older timestamp is refused.
            check("a stale position after the clear is refused",
                    !cleared.apply(position(EVENT, 1L, 1, 1_200L, "overworld", "phantom-00", "0,64,0"), NOW));

            // --- Older generations and cycles cannot overwrite a newer hunt --------------
            WaveTwoPhantomLayoutManager fenced = new WaveTwoPhantomLayoutManager();
            fenced.apply(mark(EVENT, 3L, 2, 2_000L, "overworld"), NOW);
            fenced.apply(position(EVENT, 3L, 2, 2_100L, "overworld", "phantom-00", "0,64,0"), NOW);
            check("a newer hunt holds its position",
                    fenced.layout("overworld", EVENT.toString(), 3L, 2).size() == 1);
            check("an older generation is refused",
                    !fenced.apply(mark(EVENT, 2L, 2, 2_200L, "overworld"), NOW));
            check("an older cycle in the same generation is refused",
                    !fenced.apply(mark(EVENT, 3L, 1, 2_300L, "overworld"), NOW));
            check("the newer hunt survives the stale writes",
                    fenced.layout("overworld", EVENT.toString(), 3L, 2).size() == 1);

            // A clearPresentation keeps the identity fence but drops the picture.
            fenced.clearPresentation();
            check("clearPresentation drops the layout",
                    fenced.layout("overworld", EVENT.toString(), 3L, 2).isEmpty());
            check("clearPresentation keeps the cycle fence",
                    fenced.cycle("overworld", EVENT.toString(), 3L) == -1);

            // A full reset is the only way back to a blank slate.
            fenced.reset();
            check("reset forgets the event",
                    fenced.layout("overworld", EVENT.toString(), 3L, 2).isEmpty());
            check("a mark is accepted again after reset",
                    fenced.apply(mark(EVENT, 1L, 1, 9_000_000L, "overworld"), NOW));

            // --- A second event retires the first -----------------------------------------
            WaveTwoPhantomLayoutManager rotated = new WaveTwoPhantomLayoutManager();
            rotated.apply(mark(EVENT, 1L, 1, 1_000L, "overworld"), NOW);
            fill(rotated, 3, 0, 1_100L);
            check("the first hunt is live",
                    rotated.layout("overworld", EVENT.toString(), 1L, 1).size() == 3);
            rotated.apply(mark(OTHER, 1L, 1, 2_000L, "overworld"), NOW);
            rotated.apply(position(OTHER, 1L, 1, 2_100L, "overworld", "phantom-05", "5,64,0"), NOW);
            check("the second hunt takes over",
                    rotated.layout("overworld", OTHER.toString(), 1L, 1).size() == 1);
            check("the retired hunt is forgotten",
                    rotated.layout("overworld", EVENT.toString(), 1L, 1).isEmpty());
            check("a retired event cannot come back",
                    !rotated.apply(mark(EVENT, 5L, 5, 3_000L, "overworld"), NOW));

            System.out.println(failures == 0 ? "OK" : "FAILURES " + failures);
            if (failures != 0) System.exit(1);
        }
    }
    """
)


def _jdk_bin(name: str) -> Path:
    """Find a JDK tool on CI as well as in this checkout.

    CI installs Temurin into the runner's tool cache and exposes it through
    JAVA_HOME; only this workstation keeps a JDK under local-runtime/toolchains.
    Searching that directory alone made every probe here fail on CI with
    "no local JDK found".
    """
    executable = name + (".exe" if os.name == "nt" else "")
    for variable in ("JAVA_HOME", "JAVA_HOME_25_X64", "JAVA_HOME_21_X64"):
        configured = os.environ.get(variable)
        if configured:
            candidate = Path(configured) / "bin" / executable
            if candidate.is_file():
                return candidate
    candidates = sorted(ROOT.glob("local-runtime/toolchains/temurin-*/jdk-*/bin/" + executable))
    if candidates:
        return candidates[0]
    found = shutil.which(name)
    assert found, f"{name} is required to compile this probe but was not found"
    return Path(found)


def _javac() -> Path:
    return _jdk_bin("javac")


def test_phantom_layout_manager_fences_and_caps(tmp_path: Path) -> None:
    assert MANAGER.is_file(), f"phantom layout manager is missing: {MANAGER}"
    assert LAYOUT.is_file(), f"phantom layout is missing: {LAYOUT}"
    javac = _javac()

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    (package_dir / "PhantomManagerProbe.java").write_text(PROBE, encoding="utf-8")
    # The manager formats messages through the codec's Body record, so the real protocol
    # record is compiled in rather than stubbed - a stub would hide a signature drift.
    codec_src = ROOT / "tools/minecraft-26.3/protocol/src/main/java"
    sources = [
        str(package_dir / "PhantomManagerProbe.java"),
        str(MANAGER),
        str(LAYOUT),
        str(CLIENT_SRC / "me/copimine/client/WaveTwoCinematicTimeline.java"),
        str(codec_src / "me" / "copimine" / "protocol" / "EndEventBridgeBodyCodec.java"),
        str(codec_src / "me" / "copimine" / "protocol" / "BridgeSigningPolicy.java"),
        str(codec_src / "me" / "copimine" / "protocol" / "BridgeEnvelope.java"),
    ]

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-nowarn",
         "-d", str(tmp_path / "out")] + sources,
        capture_output=True, text=True, timeout=400,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(Path(javac).with_name("java.exe")), "-cp", str(tmp_path / "out"),
         "me.copimine.client.PhantomManagerProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "phantom manager probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
