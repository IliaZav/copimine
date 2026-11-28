"""Execute the real directed RAGE → LAST SEAL arena-merge scene contract.

V6 master §13.2 is explicit: the 5→6 hand-off is *one* directed 12–16 second
five-shot scene with a single physical-reveal commit point, not a generic short
phase timer and not several independent ``runTaskLater`` calls. The retired
runtime fell back to a ~5 s last-stand intro and then claimed the arena had
transformed.

This suite compiles the real pure policies (``ArenaMergeCinematicPolicy``,
``BossImpactGeometryPolicy`` alongside the two animation/cue catalogues) and
then checks the runtime wiring, so a regression to the short intro, a missing
shot, a shared geometry, or a reveal that fires outside the wall-collapse shot
all fail here rather than on stream.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
SRC = ROOT / "copimine-end-event/src/me/copimine/endevent"
POLICY = DOMAIN / "ArenaMergeCinematicPolicy.java"
GEOMETRY = DOMAIN / "BossImpactGeometryPolicy.java"
PRESENTATION = SRC / "BossPresentationEffects.java"
RUNTIME = SRC / "CopiMineEndEvent.java"

PURE_SOURCES = [
    DOMAIN / "BossAnimationId.java",
    DOMAIN / "BossVisualCuePolicy.java",
    POLICY,
    GEOMETRY,
]


def _constant(source: str, name: str) -> int:
    match = re.search(rf"static final long {name} = ([0-9_]+)L;", source)
    assert match, f"missing production constant {name}"
    return int(match.group(1).replace("_", ""))


def _run_probe(tmp_path):
    for source in PURE_SOURCES:
        assert source.is_file(), f"missing production source {source}"
    probe = tmp_path / "ArenaMergeProbe.java"
    probe.write_text(
        "import java.util.*;\n"
        "import me.copimine.endevent.domain.*;\n"
        "public class ArenaMergeProbe {\n"
        "  static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }\n"
        "  public static void main(String[] args) {\n"
        "    var beats = ArenaMergeCinematicPolicy.beats();\n"
        "    require(beats.size() == 5, \"the merge scene must stage exactly five shots\");\n"
        "    long cursor = 0L;\n"
        "    Set<String> signatures = new HashSet<>();\n"
        "    Set<String> sounds = new HashSet<>();\n"
        "    Set<Integer> colours = new HashSet<>();\n"
        "    for (int i = 0; i < beats.size(); i++) {\n"
        "      var beat = beats.get(i);\n"
        "      require(beat.shot() == ArenaMergeCinematicPolicy.Shot.values()[i], \"shot order drift\");\n"
        "      require(beat.startMillis() == cursor, \"shots must be contiguous\");\n"
        "      cursor = beat.endMillis();\n"
        "      require(signatures.add(beat.signature()), \"duplicate merge signature\");\n"
        "      require(sounds.add(beat.soundKey()), \"duplicate merge sound\");\n"
        "      require(colours.add(beat.rgb()), \"duplicate merge colour\");\n"
        "      require(!beat.title().isBlank() && !beat.subtitle().isBlank(), \"banner text missing\");\n"
        "      require(beat.progress(beat.startMillis()) == 0.0D, \"shot progress must start at 0\");\n"
        "      require(beat.progress(beat.endMillis()) == 1.0D, \"shot progress must clamp at 1\");\n"
        "    }\n"
        "    require(cursor == ArenaMergeCinematicPolicy.totalMillis(), \"scene must close on its own timeline\");\n"
        "    require(ArenaMergeCinematicPolicy.totalMillis() >= 12_000L\n"
        "        && ArenaMergeCinematicPolicy.totalMillis() <= 16_000L, \"scene must stay in the 12-16 s window\");\n"
        "    require(ArenaMergeCinematicPolicy.revealCommitMillis() == 2_000L, \"reveal commits at wall collapse\");\n"
        "    require(ArenaMergeCinematicPolicy.ascensionMillis() == 8_000L, \"ascension starts at shot 4\");\n"
        "    require(ArenaMergeCinematicPolicy.sealIgniteMillis() == 12_000L, \"seal ignites at shot 5\");\n"
        "    require(!ArenaMergeCinematicPolicy.mayCommitReveal(1_999L), \"reveal too early\");\n"
        "    require(ArenaMergeCinematicPolicy.mayCommitReveal(2_000L), \"reveal must commit at the boundary\");\n"
        "    require(!ArenaMergeCinematicPolicy.mayCommitReveal(16_000L), \"reveal after the scene is over\");\n"
        "    require(ArenaMergeCinematicPolicy.beatAt(0L).shot() == ArenaMergeCinematicPolicy.Shot.BOSS_FRACTURE, \"beat 0\");\n"
        "    require(ArenaMergeCinematicPolicy.beatAt(1_999L).shot() == ArenaMergeCinematicPolicy.Shot.BOSS_FRACTURE, \"beat 0 end\");\n"
        "    require(ArenaMergeCinematicPolicy.beatAt(2_000L).shot() == ArenaMergeCinematicPolicy.Shot.WALL_COLLAPSE, \"beat 1\");\n"
        "    require(ArenaMergeCinematicPolicy.beatAt(15_999L).shot() == ArenaMergeCinematicPolicy.Shot.LAST_SEAL_IGNITES, \"beat 4\");\n"
        "    require(ArenaMergeCinematicPolicy.completed(16_000L), \"scene must report completion\");\n"
        "    require(!ArenaMergeCinematicPolicy.grantsInvulnerability(), \"the scene must never shield the boss\");\n"
        "    for (var beat : beats) {\n"
        "      var points = BossImpactGeometryPolicy.arenaMerge(beat.signature(), 12.0D, 0.5D);\n"
        "      require(!points.isEmpty(), \"shot draws no geometry: \" + beat.signature());\n"
        "      for (var point : points) {\n"
        "        require(Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z()),\n"
        "            \"non-finite point in \" + beat.signature());\n"
        "      }\n"
        "    }\n"
        "    require(BossImpactGeometryPolicy.arenaMerge(\"merge-unknown\", 12.0D, 0.5D).isEmpty(),\n"
        "        \"an unknown merge signature must draw nothing, not a generic ring\");\n"
        "    System.out.println(\"PASS arena merge scene is a directed 12-16 s five-shot rig\");\n"
        "  }\n"
        "}\n",
        encoding="utf-8",
    )
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *[str(s) for s in PURE_SOURCES], str(probe)],
        capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "ArenaMergeProbe"],
        capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    return run.stdout


def test_merge_scene_is_a_directed_five_shot_timeline(tmp_path):
    output = _run_probe(tmp_path)
    assert "PASS" in output


def test_runtime_owns_one_merge_scene_and_commits_the_reveal_once():
    runtime = RUNTIME.read_text(encoding="utf-8")
    presentation = PRESENTATION.read_text(encoding="utf-8")

    entry = runtime[runtime.index("contains(BossPhase.LAST_SEAL)"):]
    entry = entry[:entry.index("bossLastStandIntroUntilMillis = bossPhasePresentationUntilMillis;")]
    assert "startArenaMergeScene(boss)" in entry, (
        "the LAST_SEAL hand-off must launch the directed merge scene")
    assert "ArenaMergeCinematicPolicy.totalMillis()" in entry, (
        "the presentation window must be the directed scene length")
    assert "LAST_STAND_INTRO_MILLIS" not in entry, (
        "the retired short last-stand intro must not stand in for the transformation")

    start = runtime.index("private void startArenaMergeScene(")
    body = runtime[start:runtime.index("private void playArenaMergeCue(", start)]
    assert "BossPresentationEffects.playArenaMerge(" in body, "scene must draw through the presentation rig"
    assert "taskRegistry.owns(ownerGeneration)" in body, "scene must be generation-fenced"
    assert "bossPhase == ownerPhase" in body, "scene must be phase-fenced"

    beat = runtime[start:runtime.index("private void playArenaMergeCue(", start)]
    wall = beat.index("ArenaMergeCinematicPolicy.Shot.WALL_COLLAPSE")
    assert "startLastSealArenaReveal(boss, allowsDisposableLastSealReveal(boss))" in beat[wall:beat.index("ArenaMergeCinematicPolicy.Shot.LAST_SEAL_IGNITES")], (
        "the approved physical reveal must commit at the wall-collapse shot")
    assert "revealCommitted.compareAndSet(false, true)" in beat, (
        "the physical reveal must be exactly-once")
    ignite = beat.index("ArenaMergeCinematicPolicy.Shot.LAST_SEAL_IGNITES")
    assert "spawnLastSealAscensionBurst(boss)" in beat[ignite:], (
        "the seal-ignite shot must own the ascension read when no plan fired one")

    play = presentation[presentation.index("static void playArenaMerge("):]
    play = play[:play.index("private static Sound mergeSound(")]
    assert "beatHook.accept(beat)" in play, "the runtime hook must fire once per shot"
    assert "beatAt(elapsed)" in play, "the rig must read the authored beat timeline"
    assert "arenaMerge(beat.signature()" in play, "each shot must draw its own signature"
    for forbidden in ("setType(", "setBlockData(", "setBlock(", "damage("):
        assert forbidden not in play, f"presentation must not mutate the world or damage: {forbidden}"
