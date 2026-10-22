import os
import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]


def test_environment_validation_does_not_emit_dummy_credentials(tmp_path):
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the migration environment module.")

    env_path = tmp_path / "migration.env"
    env_path.write_text(
        "\n".join(
            [
                "POSTGRES_HOST=127.0.0.1",
                "POSTGRES_PORT=55434",
                "POSTGRES_DB=copimine_migration_test",
                "POSTGRES_SCHEMA=copimine_migration_26_3_candidate",
                "POSTGRES_USER=copimine_migration_test",
                "POSTGRES_PASSWORD=DUMMY_SENTINEL_DO_NOT_EMIT_84a6",
                "DATABASE_URL=postgresql://copimine_migration_test:DUMMY_SENTINEL_DO_NOT_EMIT_84a6@127.0.0.1:55434/copimine_migration_test",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    module_path = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    escaped_module = str(module_path).replace("'", "''")
    escaped_env = str(env_path).replace("'", "''")
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name '{escaped_module}' -Force
$required = @(
  'POSTGRES_HOST=127.0.0.1',
  'POSTGRES_PORT=55434',
  'POSTGRES_DB=copimine_migration_test',
  'POSTGRES_SCHEMA=copimine_migration_26_3_candidate',
  'POSTGRES_USER=copimine_migration_test'
)
$diagnostics = @(& {{ Assert-MigrationDatabaseEnvironment -Path '{escaped_env}' -RequiredSettings $required -Verbose 4>&1 }} | ForEach-Object {{ $_ | ConvertTo-Json -Compress -Depth 6 }})
$diagnostics | Write-Output
try {{
  $wrong = @('POSTGRES_HOST=192.0.2.1') + $required[1..4]
  Assert-MigrationDatabaseEnvironment -Path '{escaped_env}' -RequiredSettings $wrong -Verbose 4>&1 | Out-Null
}} catch {{
  Write-Output ('VALIDATION_ERROR=' + $_.Exception.Message)
}}
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
        env=os.environ.copy(),
    )

    assert result.returncode == 0, "PowerShell validation probe failed to execute."
    emitted_secret = "DUMMY_SENTINEL_DO_NOT_EMIT_84a6" in (result.stdout + result.stderr)
    assert emitted_secret is False, "database validation exposed its dummy credential sentinel"
