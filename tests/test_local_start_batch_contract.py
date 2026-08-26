from pathlib import Path
import json


ROOT = Path(__file__).resolve().parents[1]
BATCH = ROOT / "tools" / "CopiMine-local-start.bat"
RUNNER = ROOT / "tests" / "StartEndRiftLocalUserSession.ps1"
SCENE = ROOT / "tests" / "SetupEndRiftLocalScene.ps1"
PLUGIN_MANIFEST = ROOT / "deploy" / "plugin_versions.json"


def test_local_start_batch_points_only_to_the_end_rift_worktree() -> None:
    text = BATCH.read_text(encoding="utf-8")

    assert r".worktrees\end-rift-event" in text
    assert "StartEndRiftLocalUserSession.ps1" in text
    assert "-ExecutionPolicy Bypass" in text
    assert "-AdminNickname \"SudoKillDash9\"" in text
    assert "-LaunchClient" in text
    assert "Get-CopiMineRadminAddress.ps1" in text
    assert "25566" in text
    assert "8093" in text
    assert "55433" in text
    assert "8092" in text
    assert "minecraft\\server" not in text.lower()


def test_local_session_syncs_current_plugins_and_serves_verified_pack() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    for marker in (
        "codex/end-rift-event",
        "deploy\\plugin_versions.json",
        "Get-CurrentPluginSources",
        "Sync-CurrentPlugins",
        "Expected 30 current server plugins",
        "CopiMineElectionCore.jar",
        "CopiMineNarcotics.jar",
        "CopiMineUltimateAdminPlus.jar",
        "voicechat-bukkit-2.6.16.jar",
        "Get-FileDigest",
        "Get-FileSha256",
        "Get-FileSha1",
        "CopiMineEndEvent",
        "http.server",
        "resourcePackUrlHost",
        "resourcePackBindAddress",
        "Start-LocalWebsite",
        "backend.main:app",
        "copimine-local-web.env",
        "127.0.0.1:$websitePort",
        "POSTGRES_PORT=$postgresPort",
        "/api/health",
        "/api/config",
        "/resourcepacks/CopiMineResourcePack.zip",
        "localadmin123",
        "resource-pack-sha1",
        "runes=2/2",
        "coreOverlay=true",
        "ops.json",
    ):
        assert marker in text


def test_local_scene_uses_namespaced_vanilla_time_probe_with_essentials_loaded() -> None:
    text = SCENE.read_text(encoding="utf-8")

    assert text.count("minecraft:time query gametime") >= 5


def test_current_server_plugin_manifest_contains_the_complete_pinned_set() -> None:
    manifest = json.loads(PLUGIN_MANIFEST.read_text(encoding="utf-8"))
    plugins = manifest["plugins"]

    assert len(plugins) == 30
    assert plugins["CopiMineEndEvent.jar"] == "0.1.0"
    assert plugins["voicechat-bukkit-2.6.16.jar"] == "2.6.16"
    assert "voicechat-bukkit-2.6.11.jar" not in plugins
    assert {
        "CopiMineElectionCore.jar",
        "CopiMineNarcotics.jar",
        "CopiMineUltimateAdminPlus.jar",
        "CoreProtect-CE-23.0.jar",
        "EssentialsX-2.22.0.jar",
        "ProtocolLib.jar",
        "worldedit-bukkit-7.3.9.jar",
        "worldguard-bukkit-7.0.12-dist.jar",
    }.issubset(plugins)


def test_local_session_never_targets_production_ports_or_server_directory() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "25565" not in text
    assert "25575" not in text
    assert "55432" not in text
    assert "8090" not in text
    assert "local-runtime" in text
    assert "end-rift-server" in text
    assert "minecraft\\server" in text


def test_local_session_stops_paper_before_rewriting_server_properties() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert text.index("Stop-ExistingLocalPaper") < text.index("Normalize-LocalServerProperties")
    assert text.index("Normalize-LocalServerProperties") < text.index("Build-And-Sync-ResourcePack")
    assert "Wait-Process -Id $processId" in text


def test_local_session_normalizes_paper_resource_pack_prompt_growth() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "function Normalize-LocalServerProperties" in text
    assert "server.properties.pre-local-resourcepack" in text
    assert "resource-pack-prompt" in text
    assert text.index("Normalize-LocalServerProperties") < text.index("Build-And-Sync-ResourcePack")


def test_local_rcon_reads_only_the_password_line_from_a_corrupt_properties_file() -> None:
    text = (ROOT / "tests" / "InvokeEndRiftLocalRcon.ps1").read_text(encoding="utf-8")

    assert "[IO.File]::ReadLines($propertiesPath)" in text
    assert "rcon.password" in text


def test_local_properties_are_read_as_utf8_before_they_are_rewritten() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "Get-Content -LiteralPath $serverProperties -Encoding UTF8" in text


def test_local_resource_pack_is_bound_to_the_current_radmin_vpn_address() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "function Get-RadminVpnAddress" in text
    assert "InterfaceAlias 'Radmin VPN'" in text
    assert "$resourcePackBindAddress" in text
    assert "--bind', $resourcePackBindAddress" in text
    assert "$resourcePackUrlHost" in text


def test_local_session_validates_uvicorn_listener_children_before_stopping() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "verifiedWebsiteProcessIds" in text
    assert "listenerProcess" in text
    assert "Get-CimInstance Win32_Process -Filter" in text
