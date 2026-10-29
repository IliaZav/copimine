"""Execute Wave 4 adapter methods with an in-memory entity boundary (not Paper)."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"


def method(source, signature):
    start = source.index("    private " + signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def pack_probe(tmp_path_factory):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    directory = tmp_path_factory.mktemp("wave4-pack-runtime")
    template = (ROOT / "tests/fixtures/Wave4PackRuntimeProbe.java.template").read_text(encoding="utf-8")
    signatures = [
        "Wave4PressurePackResult spawnWave4PressurePack(",
        "Entity spawnWave4PressureSlot(",
        "<T extends Entity> T spawnLiveRingEntity(",
        "boolean rollbackWave4PressureSpawnAttempt(",
        "boolean isCurrentWave4PressureSpawnEntity(",
        "int clearWave4PressurePackEntities(", "boolean isWave4PressurePackEntity(",
        "void reconcileWave4PressureSpawnSlots(", "String inferredWave4Role(",
        "void configureWave4PressureEntity(",
        "void tagWave4Role(", "void tagWave4PressureSlot(",
        "boolean tickWave4ObeliskAssault()", "void applyRestoredObeliskReceipt(",
        "void clearWave4Obelisks(",
        "boolean isLiveOwnedEntity(",
    ]
    # Exercise the exact completion gate; later Wave 7 / phase-transition services
    # are outside this fixture. announceWaveComplete records reaching this gate.
    completion = method(source, "void tickWaveCompletion()")
    completion = completion[:completion.index("        if (completedWave == 7)")] + "\n    }\n"
    java = directory / "Wave4PackRuntimeProbe.java"
    java.write_text(template.replace("// PRODUCTION_METHODS", "\n".join(
        method(source, signature) for signature in signatures) + "\n" + completion), encoding="utf-8")
    policies = ["Wave4PressurePackPolicy", "WaveMechanicsPolicy", "WaveScalingPolicy",
                "WaveDamagePolicy", "PressureBudgetController",
                "ObeliskScalingPolicy", "ObeliskFireDirectorPolicy",
                "ObeliskGeometryPolicy", "Wave4ObeliskRecoverySnapshot",
                "Wave4ObeliskRecoveryPolicy", "EventPhase"]
    result = subprocess.run(["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(directory),
                             *[str(DOMAIN / (name + ".java")) for name in policies], str(java)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "pressure-matrix", "restore-emergence", "restore-timing",
    "success", "first", "middle", "last", "all", "backoff", "exhaustion", "permanent",
    "lost-slot", "registration-refused", "ring-spawn-relocation", "duplicate-slot", "restart", "reset", "obsolete-generation", "obelisk-complete",
    "synchronous-slot-loss", "throw-after-partial-success", "throw-preserves-prior-slot",
])
def test_wave4_pack_lifecycle(pack_probe, scenario):
    result = subprocess.run(["java", "-Xms8m", "-Xmx64m", "-cp", str(pack_probe),
                             "Wave4PackRuntimeProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
