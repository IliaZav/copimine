from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_full_plugin_server_is_disposable_and_staging_guarded() -> None:
    script = read("scripts/prepare_copimine_full_plugin_local_server.ps1")

    assert "artifacts\\local-validation" in script
    assert "The full-plugin server may only be created below" in script
    assert "environment=staging" in script
    assert "copimine_test" in script
    assert "POSTGRES_HOST = '127.0.0.1'" in script
    assert "[int] $DatabasePort = 55434" in script
    assert "[int] $VoiceChatPort = 24455" in script
    assert "[string] $DatabasePassword = 'copimine_test'" in script
    assert "The full-plugin server accepts only the synthetic copimine_test staging database password." in script
    assert "Do not copy worlds, playerdata, databases, or plugin data" in script


def test_full_plugin_inventory_uses_jar_files_only_and_excludes_end_rift_event() -> None:
    script = read("scripts/prepare_copimine_full_plugin_local_server.ps1")

    assert "Get-ChildItem -LiteralPath $resolvedPluginSourceRoot -Filter '*.jar' -File" in script
    assert "CopiMineEndEvent.jar" in script
    assert "AdminPluginOverridePath" in script
    assert "excluded_end_rift_event" in script
    assert "Get-ChildItem -LiteralPath $resolvedPluginSourceRoot -Recurse" not in script
    assert "playerdata" in script
    assert "worlds" in script


def test_full_plugin_inventory_prefers_the_release_voicechat_version() -> None:
    script = read("scripts/prepare_copimine_full_plugin_local_server.ps1")

    assert "voicechat-bukkit-2.6.16.jar" in script
    assert "voicechat-bukkit-2.6.11.jar" in script
    assert "excludedLegacyVoicechat" in script
    assert "excluded_legacy_voicechat" in script
    assert "EXCLUDED_LEGACY_VOICECHAT" in script


def test_full_plugin_server_records_test_only_runtime_probe_separately() -> None:
    script = read("scripts/prepare_copimine_full_plugin_local_server.ps1")

    assert "test_plugins" in script
    assert "CopiMineRuntimeProbe.jar" in script
    assert "testOnly" in script


def test_full_plugin_server_defaults_to_the_current_checkout_plugin_inventory() -> None:
    script = read("scripts/prepare_copimine_full_plugin_local_server.ps1")

    assert "Join-Path $repoRoot 'minecraft\\server\\plugins'" in script
    assert "Join-Path $repoRoot '..\\..\\minecraft\\server\\plugins'" not in script


def test_binding_staging_defaults_to_the_launcher_loopback_port() -> None:
    script = read("scripts/run_copimine_launcher_binding_staging.ps1")

    assert "[int] $Port = 8090" in script


def test_binding_staging_resolves_default_paths_after_script_initialization() -> None:
    script = read("scripts/run_copimine_launcher_binding_staging.ps1")

    assert '[string] $ValidationRoot = ""' in script
    assert '[string] $PythonPath = ""' in script
    assert "$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path" in script
    assert "if ([string]::IsNullOrWhiteSpace($PythonPath))" in script
    assert "New-Item -ItemType Directory -Path $ValidationRoot -Force" in script


def test_binding_staging_runner_can_start_launcher_against_loopback_origin() -> None:
    script = read("scripts/run_copimine_launcher_binding_staging.ps1")

    assert '[string] $LauncherPath = ""' in script
    assert "COPIMINE_LAUNCHER_STAGING_BASE_URL" in script
    assert "COPIMINE_LAUNCHER_LOCAL_BASE_URL" in script
    assert "ProcessStartInfo" in script
    assert "LAUNCHER_STARTED=" in script


def test_full_plugin_server_has_a_separate_port_and_real_startup_gate() -> None:
    script = read("scripts/prepare_copimine_full_plugin_local_server.ps1")

    assert "[int] $Port = 25568" in script
    assert "server-port" in script
    assert "DisposableCopiMineFullPlugins" in script
    assert "voicechat-server.properties" in script
    assert "Done \\(" in script
    assert "CLIENT_GATE_SERVER_READY" in script
    assert "Start-Process" in script


def test_full_plugin_runtime_verifier_rejects_partial_plugin_initialization() -> None:
    script = read("scripts/verify_copimine_full_plugin_local_server.ps1")

    assert "InvalidPluginException" in script
    assert "NoClassDefFoundError" in script
    assert "ClassNotFoundException" in script
    assert "Failed to register events" in script
    assert "Simple Voice Chat registration failed" in script


def test_full_plugin_gate_matrix_waits_for_cold_start_before_reject_timeout() -> None:
    script = read("scripts/run_copimine_client_gate_matrix.ps1")

    # The full plugin stack can take more than 30 seconds to reach the join
    # screen. The reject evidence appears only after the 15-second READY
    # timeout, so a 45-second process budget races the cold start.
    assert "COPIMINE_SERVER_SMOKE_WAIT_SECONDS = if ($expectedGate -eq 'REJECT') { '90' }" in script


def test_fresh_install_gate_matrix_does_not_require_optional_cosmetic_fixture() -> None:
    script = read("scripts/run_copimine_client_gate_matrix.ps1")

    assert "LocalSkin\\skins\\SmokePlayer.png" in script
    assert "LocalSkin\\capes\\SmokePlayer.png" in script
    assert "Test-Path -LiteralPath $localSkinPath" in script
    assert "COPIMINE_SERVER_SMOKE_EXPECT_LOCAL_COSMETICS" in script
