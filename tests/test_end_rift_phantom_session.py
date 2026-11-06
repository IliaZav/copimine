"""The wave 2 phantom layout must stay bound to one hunt mark, and its seed must be stable.

Complaint (4) is that wave 2 has no phantoms. WaveTwoPhantomSession is the client object that
holds one phantom layout for the lifetime of a single hunt mark: if it regenerated the layout
on every frame the phantoms would jitter, and if it kept the layout across marks they would
appear before the server ever marked anyone. It had no behavioural test.

The MarkKey seed matters for the same reason: the layout is deterministic FROM the seed, so two
different marks must not share a seed, or two separate hunts would get the same phantom ring.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
SESSION = CLIENT_SRC / "me/copimine/client/WaveTwoPhantomSession.java"
LAYOUT = CLIENT_SRC / "me/copimine/client/WaveTwoPhantomLayout.java"

PROBE = textwrap.dedent(
    """
    package me.copimine.client;

    import java.util.ArrayList;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;
    import java.util.UUID;
    import java.util.function.Supplier;

    public final class PhantomSessionProbe {
        private static int failures = 0;

        private static void check(String name, boolean condition) {
            if (!condition) {
                System.out.println("FAIL " + name);
                failures++;
            }
        }

        private static WaveTwoPhantomLayout.Position pos(double x, double z) {
            return new WaveTwoPhantomLayout.Position(x, 70.0D, z);
        }

        private static Supplier<List<WaveTwoPhantomLayout.Position>> layout(int count) {
            return () -> {
                List<WaveTwoPhantomLayout.Position> out = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    out.add(pos(index, index * 2.0D));
                }
                return out;
            };
        }

        public static void main(String[] args) {
            UUID player = UUID.fromString("11111111-2222-3333-4444-555555555555");
            WaveTwoPhantomSession session = new WaveTwoPhantomSession();
            WaveTwoPhantomSession.MarkKey markA =
                    new WaveTwoPhantomSession.MarkKey("event-a", 3L, player, 1000L);
            WaveTwoPhantomSession.MarkKey markB =
                    new WaveTwoPhantomSession.MarkKey("event-b", 3L, player, 1000L);

            // --- Nothing before a mark ----------------------------------------------------
            check("a fresh session has no active mark", !session.hasActiveMark());
            check("positions for a null mark are empty",
                    session.positionsFor(null, layout(5)).isEmpty());

            // --- One layout per mark, reused while the mark is unchanged -------------------
            List<WaveTwoPhantomLayout.Position> first = session.positionsFor(markA, layout(4));
            check("the first layout is produced", first.size() == 4);
            check("the session now has an active mark", session.hasActiveMark());

            // A second call with the same mark must NOT rebuild.
            List<WaveTwoPhantomLayout.Position> again = session.positionsFor(markA, () -> {
                throw new IllegalStateException("layout must not be rebuilt for the same mark");
            });
            check("the same mark reuses the stored layout", again.equals(first));

            // A different mark rebuilds.
            List<WaveTwoPhantomLayout.Position> forB = session.positionsFor(markB, layout(2));
            check("a new mark rebuilds the layout", forB.size() == 2);

            // --- The layout is bounded and non-finite positions are dropped ---------------
            WaveTwoPhantomSession bounded = new WaveTwoPhantomSession();
            WaveTwoPhantomSession.MarkKey markC =
                    new WaveTwoPhantomSession.MarkKey("event-c", 1L, player, 0L);
            List<WaveTwoPhantomLayout.Position> many = bounded.positionsFor(markC, () -> {
                List<WaveTwoPhantomLayout.Position> out = new ArrayList<>();
                for (int index = 0; index < 200; index++) {
                    out.add(pos(index, index));
                }
                return out;
            });
            check("the stored layout is capped like the layout itself",
                    many.size() == WaveTwoPhantomLayout.TARGET_PHANTOM_COUNT);
            check("no duplicate positions survive",
                    new HashSet<>(many).size() == many.size());

            WaveTwoPhantomSession cleaned = new WaveTwoPhantomSession();
            WaveTwoPhantomSession.MarkKey markD =
                    new WaveTwoPhantomSession.MarkKey("event-d", 1L, player, 0L);
            List<WaveTwoPhantomLayout.Position> withBad = cleaned.positionsFor(markD, () -> {
                List<WaveTwoPhantomLayout.Position> out = new ArrayList<>();
                out.add(pos(1.0D, 1.0D));
                out.add(pos(Double.NaN, 2.0D));
                out.add(pos(3.0D, Double.POSITIVE_INFINITY));
                out.add(pos(4.0D, 4.0D));
                return out;
            });
            check("non-finite positions are dropped", withBad.size() == 2);
            for (WaveTwoPhantomLayout.Position position : withBad) {
                check("kept positions are finite", WaveTwoPhantomLayout.finite(position));
            }

            // A null or empty factory result yields no positions but still holds the mark.
            WaveTwoPhantomSession nullLayout = new WaveTwoPhantomSession();
            WaveTwoPhantomSession.MarkKey markE =
                    new WaveTwoPhantomSession.MarkKey("event-e", 1L, player, 0L);
            check("a null factory result yields no positions",
                    nullLayout.positionsFor(markE, () -> null).isEmpty());
            check("a null factory result still holds the mark", nullLayout.hasActiveMark());

            // --- clear() forgets everything ------------------------------------------------
            session.clear();
            check("clear forgets the active mark", !session.hasActiveMark());
            check("clear drops the positions",
                    session.positionsFor(markA, layout(3)).size() == 3);

            // --- MarkKey validity -----------------------------------------------------------
            check("a normal mark is valid", markA.valid());
            check("a blank event id is invalid",
                    !new WaveTwoPhantomSession.MarkKey("   ", 1L, player, 0L).valid());
            check("a null event id is invalid",
                    !new WaveTwoPhantomSession.MarkKey(null, 1L, player, 0L).valid());
            check("a zero generation is invalid",
                    !new WaveTwoPhantomSession.MarkKey("event", 0L, player, 0L).valid());
            check("a null marked player is invalid",
                    !new WaveTwoPhantomSession.MarkKey("event", 1L, null, 0L).valid());
            check("a negative start is invalid",
                    !new WaveTwoPhantomSession.MarkKey("event", 1L, player, -1L).valid());

            // --- Seeds separate marks --------------------------------------------------------
            check("the same mark yields the same seed", markA.stableSeed() == markA.stableSeed());
            check("a different event id yields a different seed",
                    markA.stableSeed() != markB.stableSeed());
            check("a different generation yields a different seed",
                    markA.stableSeed()
                            != new WaveTwoPhantomSession.MarkKey("event-a", 4L, player, 1000L).stableSeed());
            check("a different start yields a different seed",
                    markA.stableSeed()
                            != new WaveTwoPhantomSession.MarkKey("event-a", 3L, player, 1001L).stableSeed());
            check("a different player yields a different seed",
                    markA.stableSeed()
                            != new WaveTwoPhantomSession.MarkKey("event-a", 3L, UUID.randomUUID(), 1000L)
                                    .stableSeed());

            // Every field must participate: sweep one field at a time and require no repeats.
            Set<Long> seeds = new HashSet<>();
            int collisions = 0;
            for (int index = 0; index < 256; index++) {
                long seed = new WaveTwoPhantomSession.MarkKey("event-" + index, 3L, player, 1000L)
                        .stableSeed();
                if (!seeds.add(seed)) collisions++;
            }
            check("256 distinct event ids give 256 distinct seeds", collisions == 0);

            Set<Long> byStart = new HashSet<>();
            int startCollisions = 0;
            for (int index = 0; index < 256; index++) {
                long seed = new WaveTwoPhantomSession.MarkKey("event-a", 3L, player, index).stableSeed();
                if (!byStart.add(seed)) startCollisions++;
            }
            check("256 distinct start times give 256 distinct seeds", startCollisions == 0);

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


def test_wave_two_phantom_session_binds_one_layout_per_mark(tmp_path: Path) -> None:
    assert SESSION.is_file(), f"phantom session source is missing: {SESSION}"
    assert LAYOUT.is_file(), f"phantom layout source is missing: {LAYOUT}"
    javac = _javac()
    java = javac.with_name("java.exe")

    package_dir = tmp_path / "src" / "me" / "copimine" / "client"
    package_dir.mkdir(parents=True, exist_ok=True)
    probe = package_dir / "PhantomSessionProbe.java"
    probe.write_text(PROBE, encoding="utf-8")

    compile_result = subprocess.run(
        [str(javac), "-proc:none", "-encoding", "UTF-8", "-d", str(tmp_path / "out"),
         str(probe), str(SESSION), str(LAYOUT)],
        capture_output=True, text=True, timeout=300,
    )
    assert compile_result.returncode == 0, (
        "probe failed to compile:\n" + compile_result.stdout + compile_result.stderr
    )

    run_result = subprocess.run(
        [str(java), "-cp", str(tmp_path / "out"), "me.copimine.client.PhantomSessionProbe"],
        capture_output=True, text=True, timeout=300,
    )
    combined = run_result.stdout + run_result.stderr
    assert run_result.returncode == 0, "phantom session probe failed:\n" + combined
    assert "OK" in run_result.stdout, combined
