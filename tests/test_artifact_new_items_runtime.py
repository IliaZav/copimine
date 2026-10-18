from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts"


def test_new_artifact_runtime_policies() -> None:
    production_sources = [
        SRC / "BerserkerHeartPolicy.java",
        SRC / "DonationItemIdentityPolicy.java",
        SRC / "NightCloakPolicy.java",
        SRC / "SignalBellPolicy.java",
        SRC / "VeinMinerPolicy.java",
    ]
    probe = ROOT / "tests" / "ArtifactNewItemsPolicyTest.java"

    with tempfile.TemporaryDirectory(prefix="copimine-artifact-runtime-") as output:
        compile_result = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", output, *map(str, production_sources), str(probe)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

        run_result = subprocess.run(
            ["java", "-cp", output, "ArtifactNewItemsPolicyTest"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        assert run_result.returncode == 0, run_result.stdout + run_result.stderr
        assert run_result.stdout.strip() == "ArtifactNewItemsPolicyTest OK"
