"""ECHO-002/ECHO-005/ECHO-007: one shared Echo region, no owner cells.

The personal Echo stage must not split owners into individual walled cells.
Isolation is a server-side owner-pair rule; the plan itself is a single
shared region. This harness runs the production policy classes.
"""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event" / "src"


@pytest.fixture(scope="module")
def shared_region(tmp_path_factory):
    out = tmp_path_factory.mktemp("echoshared")
    sources = [
        ROOT / "tests" / "EndRiftEchoSharedRegionTest.java",
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/Wave7EchoDuelCellPolicy.java",
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ChamberIsolationPolicy.java",
    ]
    built = subprocess.run(
        ["javac", "-proc:none", "-encoding", "UTF-8", "-sourcepath", str(SOURCE),
         "-d", str(out), *map(str, sources)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    run = subprocess.run(
        # The staging host is memory tight; the harness only needs a small heap.
        ["java", "-Xmx128m", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-cp", str(out),
         "me.copimine.endevent.domain.EndRiftEchoSharedRegionTest"],
        capture_output=True, text=True,
    )
    assert run.returncode == 0, run.stdout + run.stderr
    assert "EndRiftEchoSharedRegionTest OK" in run.stdout, run.stdout
    return run.stdout


def test_echo_owners_share_one_region_without_individual_cells(shared_region):
    assert "EndRiftEchoSharedRegionTest OK" in shared_region