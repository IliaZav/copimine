from __future__ import annotations

import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "run_copimine_launcher_staging.ps1"


def test_launcher_staging_runner_is_valid_powershell() -> None:
    command = (
        "$source = Get-Content -Raw -LiteralPath $env:COPIMINE_STAGING_SCRIPT; "
        "[scriptblock]::Create($source) | Out-Null"
    )
    result = subprocess.run(
        ["powershell.exe", "-NoProfile", "-Command", command],
        cwd=ROOT,
        env={**__import__("os").environ, "COPIMINE_STAGING_SCRIPT": str(SCRIPT)},
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_launcher_staging_runner_prints_a_copyable_stop_command() -> None:
    source = SCRIPT.read_text(encoding="utf-8")

    assert 'Write-Output "STOP_COMMAND=Stop-Process -Id $($server.Id)"' in source
