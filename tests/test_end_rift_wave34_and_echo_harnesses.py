"""Run the Wave 3/4 indicator harness and the Echo personalisation harness.

Both are production classes, so a failure here means the shipped code stopped
honouring a contract rather than a scratch probe disagreeing with itself.
"""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event" / "src"


def _run_harness(tmp_path_factory, harness, extra_sources):
    out = tmp_path_factory.mktemp(harness.lower())
    sources = [ROOT / "tests" / f"{harness}.java", *(ROOT / item for item in extra_sources)]
    built = subprocess.run(
        ["javac", "-proc:none", "-encoding", "UTF-8", "-sourcepath", str(SOURCE),
         "-d", str(out), *map(str, sources)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    run = subprocess.run(["java", "-cp", str(out), harness], capture_output=True, text=True)
    assert run.returncode == 0, run.stdout + run.stderr
    assert "OK" in run.stdout, run.stdout
    return run.stdout


@pytest.fixture(scope="module")
def wave34(tmp_path_factory):
    return _run_harness(
        tmp_path_factory,
        "EndRiftWave34IndicatorsTest",
        [
            "copimine-end-event/src/me/copimine/endevent/domain/PortalCapturePolicy.java",
            "copimine-end-event/src/me/copimine/endevent/domain/PortalCaptureIndicatorPolicy.java",
            "copimine-end-event/src/me/copimine/endevent/domain/ObeliskPlacementPolicy.java",
            "copimine-end-event/src/me/copimine/endevent/domain/ObeliskRepelPolicy.java",
        ],
    )


@pytest.fixture(scope="module")
def personalisation(tmp_path_factory):
    return _run_harness(
        tmp_path_factory,
        "EchoBehaviorPersonalisationTest",
        [
            "copimine-end-event/src/me/copimine/endevent/domain/wave7/EchoBehaviorStrategy.java",
            "copimine-end-event/src/me/copimine/endevent/domain/wave7/EchoDuelCombatPolicy.java",
            "copimine-end-event/src/me/copimine/endevent/domain/wave7/EchoLoadoutState.java",
            "copimine-end-event/src/me/copimine/endevent/domain/ChamberIsolationPolicy.java",
            "copimine-end-event/src/me/copimine/endevent/runtime/RealitySplitTrialController.java",
            "copimine-end-event/src/me/copimine/endevent/runtime/RealitySplitEchoDuelState.java",
            "copimine-end-event/src/me/copimine/endevent/runtime/RealitySplitTrialSnapshot.java",
            "copimine-end-event/src/me/copimine/endevent/runtime/wave7/EchoDuelCombatRuntimeAdapter.java",
        ],
    )


def test_wave3_and_wave4_indicators_are_authoritative(wave34):
    """W3-003/W3-008/W4-009..013: indicators and repulsion follow real progress."""
    assert "EndRiftWave34IndicatorsTest OK" in wave34


def test_echo_copy_is_personalised_not_decorative(personalisation):
    """BRAIN-013/BRAIN-015/BRAIN-016: styles must change the produced decision."""
    assert "EchoBehaviorPersonalisationTest OK" in personalisation