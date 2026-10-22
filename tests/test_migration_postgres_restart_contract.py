import os
import re
import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]


def test_normal_server_start_uses_non_provisioning_postgres_reuse_path():
    launcher = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    assert "Ensure-MigrationPostgresReady" in launcher, (
        "ordinary server startup must use the existing-cluster ensure path"
    )
    assert "$databaseSetupOutput = @(& $postgresInitializer @databaseSetupArguments)" not in launcher, (
        "ordinary startup must not call the provisioning initializer"
    )


def test_paper_listener_is_checked_before_database_reuse_can_start_or_validate_services():
    launcher = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    listener_check = launcher.find("Test-MigrationPaperRuntimeListener")
    database_reuse = launcher.find("Ensure-MigrationPostgresReady")
    assert listener_check >= 0, "launcher needs an ownership-aware early Paper listener check"
    assert database_reuse >= 0, "launcher needs the non-provisioning PostgreSQL path"
    assert listener_check < database_reuse, (
        "an occupied Paper endpoint must be handled before any database service action"
    )


def test_initializer_rejects_existing_or_partial_data_before_creating_credentials():
    initializer = (ROOT / "scripts/minecraft/InitializeMigrationPostgres.ps1").read_text(encoding="utf-8")
    data_guard = initializer.find("Assert-MigrationPostgresNewClusterDirectory")
    credential_creation = initializer.find("Initialize-MigrationPostgresCredentialFiles")
    assert data_guard >= 0, "provisioning needs an explicit new-empty-cluster guard"
    assert credential_creation >= 0, "initializer credential creation call is missing"
    assert data_guard < credential_creation, (
        "partial or populated cluster state must be rejected before private credentials are written"
    )


def test_initializer_refuses_initialized_fixture_before_changing_credentials(tmp_path):
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise the PostgreSQL initializer guard.")

    fake_repo = tmp_path / "repo"
    scripts = fake_repo / "scripts" / "minecraft"
    scripts.mkdir(parents=True)
    runtime = fake_repo / "local-runtime" / "end-rift-server-26.3"
    data = runtime / "postgres-data"
    data.mkdir(parents=True)
    (data / "PG_VERSION").write_text("16\n", encoding="ascii")
    shutil.copy2(ROOT / "scripts/minecraft/InitializeMigrationPostgres.ps1", scripts)
    shutil.copy2(ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1", scripts)
    shutil.copy2(ROOT / "scripts/minecraft/MigrationPostgresLifecycle.psm1", scripts)

    admin_env = runtime / "migration-postgres-admin.env"
    plugin_env = runtime / "migration-isolated-postgres.env"
    temporary_password_file = runtime / "postgres-initdb-password.tmp"
    admin_bytes = b"POSTGRES_SUPERUSER_PASSWORD=dummy-admin-sentinel\n"
    plugin_bytes = (
        b"POSTGRES_HOST=127.0.0.1\nPOSTGRES_PORT=55434\n"
        b"POSTGRES_DB=copimine_migration_test\nPOSTGRES_SCHEMA=copimine_migration_26_3_candidate\n"
        b"POSTGRES_USER=copimine_migration_test\nPOSTGRES_PASSWORD=dummy-plugin-sentinel\n"
    )
    temporary_bytes = b"dummy-initdb-sentinel\n"
    admin_env.write_bytes(admin_bytes)
    plugin_env.write_bytes(plugin_bytes)
    temporary_password_file.write_bytes(temporary_bytes)

    initializer = scripts / "InitializeMigrationPostgres.ps1"
    escaped_initializer = str(initializer).replace("'", "''")
    escaped_runtime = str(runtime).replace("'", "''")
    command = f"""
try {{
  & '{escaped_initializer}' -RuntimeDirectory '{escaped_runtime}' -PostgresBin 'missing-postgres-toolchain'
  exit 0
}} catch {{
  Write-Output $_.Exception.Message
  exit 73
}}
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )

    assert result.returncode == 73, result.stdout + result.stderr
    assert "An initialized PostgreSQL cluster already exists" in result.stdout
    assert b"dummy-admin-sentinel" not in result.stdout.encode() + result.stderr.encode()
    assert b"dummy-plugin-sentinel" not in result.stdout.encode() + result.stderr.encode()
    assert admin_env.read_bytes() == admin_bytes
    assert plugin_env.read_bytes() == plugin_bytes
    assert temporary_password_file.read_bytes() == temporary_bytes


def test_read_only_mode_never_starts_a_stopped_postgres_cluster():
    launcher = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    assert "Ensure-MigrationPostgresReady" in launcher, "read-only mode must use the reuse/validate path"
    assert "-StartIfStopped:(-not $ValidateOnly)" in launcher, (
        "ValidateOnly must validate an owned running cluster without starting services"
    )


def test_startup_settings_validator_errors_are_not_mislabeled_as_server_properties():
    launcher = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    assert 'throw "Migration runtime validator failed:' in launcher, (
        "the startup-settings command also checks runtime plugin inventory, so its failures need a generic runtime label"
    )
    assert "Migration server properties are unsafe or invalid:" not in launcher, (
        "plugin inventory or other startup validation failures must not be reported as unsafe server properties"
    )


def test_startup_module_import_order_keeps_environment_cleanup_command_visible():
    powershell = shutil.which("powershell.exe") or shutil.which("pwsh")
    if powershell is None:
        pytest.skip("PowerShell is required to exercise migration module import behavior.")

    environment_module = ROOT / "scripts/minecraft/MigrationDatabaseEnvironment.psm1"
    lifecycle_module = ROOT / "scripts/minecraft/MigrationPostgresLifecycle.psm1"
    escaped_environment = str(environment_module).replace("'", "''")
    escaped_lifecycle = str(lifecycle_module).replace("'", "''")
    launcher = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    cleanup_import = launcher.find("MigrationDatabaseEnvironment.psm1")
    lifecycle_import = launcher.find("MigrationPostgresLifecycle.psm1")
    assert cleanup_import >= 0 and lifecycle_import >= 0
    imports = [
        f"'{escaped_environment}'" if cleanup_import < lifecycle_import else f"'{escaped_lifecycle}'",
        f"'{escaped_lifecycle}'" if cleanup_import < lifecycle_import else f"'{escaped_environment}'",
    ]
    command = f"""
$ErrorActionPreference = 'Stop'
Import-Module -Name {imports[0]} -Force
Import-Module -Name {imports[1]} -Force
if (-not (Get-Command Restore-MigrationDatabaseOverrides -ErrorAction SilentlyContinue)) {{ exit 23 }}
"""
    result = subprocess.run(
        [powershell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
        env=os.environ.copy(),
    )
    assert result.returncode == 0, (
        "The startup module import order hides Restore-MigrationDatabaseOverrides: "
        + result.stderr
    )


def test_validate_only_does_not_contend_for_the_foreground_runtime_mutex():
    launcher = (ROOT / "scripts/minecraft/StartMigrationTestServer.ps1").read_text(encoding="utf-8")
    assert re.search(
        r"if\s*\(-not\s+\$ValidateOnly\)\s*\{\s*try\s*\{\s*\$mutexHeld\s*=\s*\$mutex\.WaitOne\(0\)",
        launcher,
    ), "read-only validation must inspect a server whose foreground start owns the lifecycle mutex"
