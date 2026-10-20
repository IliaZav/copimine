from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "ArtifactCatalogMigrationPolicy.java"
PROBE = ROOT / "tests" / "ArtifactCatalogMigrationPolicyTest.java"


def test_old_catalog_upgrade_adds_approved_items_and_preserves_operator_rows() -> None:
    with tempfile.TemporaryDirectory(prefix="copimine-catalog-migration-") as output:
        compile_result = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", output, str(SRC), str(PROBE)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

        run_result = subprocess.run(
            ["java", "-cp", output, "ArtifactCatalogMigrationPolicyTest"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        assert run_result.returncode == 0, run_result.stdout + run_result.stderr
        assert run_result.stdout.strip() == "ArtifactCatalogMigrationPolicyTest OK"
