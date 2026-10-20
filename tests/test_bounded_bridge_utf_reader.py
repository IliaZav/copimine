from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "BoundedBridgeUtfReader.java"
)
PROBE = ROOT / "tests" / "BoundedBridgeUtfReaderTest.java"


def test_bridge_modified_utf_is_bounded_before_decoding() -> None:
    production_sources = [str(SOURCE)] if SOURCE.exists() else []
    with tempfile.TemporaryDirectory(prefix="copimine-bridge-utf-") as output:
        compile_result = subprocess.run(
            ["javac", "-encoding", "UTF-8", "-d", output, *production_sources, str(PROBE)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr

        run_result = subprocess.run(
            ["java", "-cp", output, "me.copimine.endevent.BoundedBridgeUtfReaderTest"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        assert run_result.returncode == 0, run_result.stdout + run_result.stderr
        assert run_result.stdout.strip() == "BoundedBridgeUtfReaderTest OK"
