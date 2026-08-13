"""Pure policy tests run before Paper integration tests."""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts"


class NewArtifactPolicyRuntimeTest(unittest.TestCase):
    def _compile_and_run(self, source: str, main: str) -> None:
        with tempfile.TemporaryDirectory() as output:
            compile_result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", output, str(SRC / source), str(ROOT / "tests" / f"{main}.java")],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)
            run_result = subprocess.run(
                ["java", "-cp", output, main],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(run_result.returncode, 0, run_result.stdout + run_result.stderr)

    def test_vein_policy(self) -> None:
        self._compile_and_run("VeinMinerPolicy.java", "VeinMinerPolicyTest")

    def test_night_cloak_policy(self) -> None:
        self._compile_and_run("NightCloakPolicy.java", "NightCloakPolicyTest")


if __name__ == "__main__":
    unittest.main()
