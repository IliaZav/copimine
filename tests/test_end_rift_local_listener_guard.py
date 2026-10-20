from __future__ import annotations

import os
import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]


def test_local_listener_guard_retries_only_empty_snapshots_and_fails_closed() -> None:
    if os.name != "nt":
        pytest.skip("The local Paper listener guard uses Windows networking APIs.")

    powershell = shutil.which("pwsh") or shutil.which("powershell.exe") or shutil.which("powershell")
    if powershell is None:
        pytest.skip("PowerShell is not installed.")

    probe = ROOT / "tests" / "EndRiftLocalAuthModeListenerProbe.ps1"
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(probe)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        timeout=45,
        check=False,
    )

    assert result.returncode == 0, f"{result.stdout}\n{result.stderr}"
    assert "END_RIFT_LOCAL_LISTENER_PROBE_PASS" in result.stdout
