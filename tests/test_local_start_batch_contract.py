from pathlib import Path
import re
import shutil
import subprocess
import sys
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
    assert "-AdminNickname \"ilia228008\"" not in text
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
        "$sourcePluginDir",
        "Join-Path $sourcePluginDir $fileName",
        "Sync-CurrentPlugins",
        "Expected 30 current server plugins",
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
        "--skip-server-properties",
        "Assert-LocalStartupPrerequisites",
        "Assert-TrackedProductionPropertiesClean",
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


def test_local_runner_restarts_verified_paper_when_the_local_server_is_already_open() -> None:
    text = RUNNER.read_text(encoding="utf-8")
    stop_function = text[text.index("function Stop-ExistingLocalPaper"):text.index("function Normalize-LocalServerProperties")]

    assert "Stop-ExistingLocalPaper" in text
    assert "Start-LocalPaper" in text
    assert text.index("Stop-ExistingLocalPaper\n") < text.index("Start-LocalPaper\n")
    assert "Invoke-LocalRcon -CommandText 'stop'" in stop_function
    assert "Wait-TcpPort -HostName '127.0.0.1' -Port $serverPort -Expected $false" in stop_function
    assert "Stop-Process -Id $processId -Force" in stop_function


def test_local_runner_allows_the_cold_paper_start_to_finish() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "-ReadyTimeoutSeconds 180" in text


def test_local_paper_startup_timeout_cleans_up_the_started_process() -> None:
    text = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    timeout_block = text[text.index("if (-not $ready)"):text.index("Write-Output \"Started isolated local Paper", text.index("if (-not $ready)"))]

    assert "$paper.Kill()" in timeout_block
    assert "$paper.WaitForExit(10000) | Out-Null" in timeout_block


def test_local_runner_grants_op_only_to_whitelisted_accounts_present_in_local_authme_db() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "function Grant-LocalWhitelistOperators" in text
    assert "$whitelistPath = Join-Path $serverDir 'whitelist.json'" in text
    assert "$authmeDb = Join-Path $serverDir 'plugins\\AuthMe\\authme.db'" in text
    grant_function = text[text.index("function Grant-LocalWhitelistOperators"):text.index("function Get-MinecraftLauncherPath")]
    for marker in (
        "whitelist.json",
        "sqlite3",
        "SELECT username, realname FROM authme",
        "op $name",
        "^[A-Za-z0-9_]{1,16}$",
        "local-whitelist-authme.py",
        "[IO.File]::WriteAllText($queryPath",
        "& $python $queryPath $authmeDb $whitelistPath",
    ):
        assert marker in grant_function
    assert "-c $queryScript" not in grant_function
    assert text.rindex("Grant-LocalWhitelistOperators") > text.index("Start-LocalPaper\n")


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


def test_local_batch_supports_noninteractive_invocation_without_forced_pause() -> None:
    text = BATCH.read_text(encoding="utf-8")

    assert "COPIMINE_NO_PAUSE" in text
    assert "COPIMINE_NO_CLIENT" in text
    assert "where powershell.exe" in text


def test_local_runner_builds_pack_without_mutating_tracked_server_properties(tmp_path: Path) -> None:
    source_pack = ROOT / "resourcepacks"
    isolated_root = tmp_path / "project"
    isolated_pack = isolated_root / "resourcepacks"
    shutil.copytree(source_pack / "src", isolated_pack / "src")
    for name in ("build-resourcepack.py", "models_manifest.json", "item_texture_sources.json"):
        shutil.copy2(source_pack / name, isolated_pack / name)
    isolated_catalog = isolated_root / "copimine-artifacts" / "items.yml"
    isolated_catalog.parent.mkdir(parents=True)
    shutil.copy2(ROOT / "copimine-artifacts" / "items.yml", isolated_catalog)

    isolated_properties = isolated_root / "minecraft" / "server" / "server.properties"
    isolated_properties.parent.mkdir(parents=True)
    tracked_properties = ROOT / "minecraft" / "server" / "server.properties"
    original = tracked_properties.read_bytes()
    isolated_original = re.sub(
        rb"resource-pack-sha1=[0-9a-f]{40}",
        b"resource-pack-sha1=" + (b"0" * 40),
        original,
    )
    assert isolated_original != original
    isolated_properties.write_bytes(isolated_original)

    result = subprocess.run(
        [sys.executable, str(isolated_pack / "build-resourcepack.py"), "--skip-server-properties"],
        cwd=isolated_pack,
        capture_output=True,
        text=True,
    )

    assert result.returncode == 0, result.stderr
    assert isolated_properties.read_bytes() == isolated_original


def test_local_runner_uses_only_current_worktree_plugin_sources() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "mainCheckoutRoot" not in text
    assert "voiceChatCandidates" not in text
    assert "minecraft\\server\\plugins" in text
    assert "--skip-server-properties" in text


def test_local_runner_rejects_dirty_tracked_production_properties_before_stopping_paper() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "Assert-TrackedProductionPropertiesClean" in text
    startup = text.index("$currentPluginSources = @(Get-CurrentPluginSources)")
    guard_call = text.index("$trackedProductionPropertiesSha256 = Assert-TrackedProductionPropertiesClean", startup)
    paper_stop_call = text.index("Stop-ExistingLocalPaper\n", startup)
    assert guard_call < paper_stop_call


def test_local_runner_checks_isolated_postgres_before_stopping_paper() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    first_postgres_call = text.index("Ensure-LocalPostgres\n", text.index("$currentPluginSources = @(Get-CurrentPluginSources)"))
    first_paper_stop_call = text.index("Stop-ExistingLocalPaper\n")
    assert first_postgres_call < first_paper_stop_call


def test_local_runner_detaches_postgres_start_and_waits_for_readiness() -> None:
    text = RUNNER.read_text(encoding="utf-8")
    postgres_function = text[text.index("function Ensure-LocalPostgres"):text.index("function Ensure-ResourcePackHttpServer")]

    assert "Start-Process -FilePath $pgCtl" in postgres_function
    assert "RedirectStandardOutput $pgCtlOut" in postgres_function
    assert "RedirectStandardError $pgCtlErr" in postgres_function
    assert "-l `\"$postgresLog`\"" in postgres_function
    assert "Wait-TcpPort -HostName '127.0.0.1' -Port $postgresPort -Expected $true" in postgres_function
    assert "$pgCtlProcess.WaitForExit(5000) | Out-Null" in postgres_function
    assert "([string](Get-Content -LiteralPath $pgCtlErr -Raw" in postgres_function
    assert "$statusOutput = @(& $pgCtl -D $pgDataDir status 2>&1)" in postgres_function
    assert "$statusExit = $LASTEXITCODE" in postgres_function
    assert "$pgCtlProcess.ExitCode" not in postgres_function
    assert "& $pgCtl -D $pgDataDir -o '-p 55433 -h 127.0.0.1' -w start" not in postgres_function


def test_local_runner_moves_only_a_proven_stale_postgres_pid_file() -> None:
    text = RUNNER.read_text(encoding="utf-8")
    postgres_function = text[text.index("function Ensure-LocalPostgres"):text.index("function Ensure-ResourcePackHttpServer")]

    assert "$postmasterPidPath = Join-Path $pgDataDir 'postmaster.pid'" in postgres_function
    assert "Get-Process -Id $stalePid" in postgres_function
    assert "Get-NetTCPConnection -State Listen -LocalPort $postgresPort" in postgres_function
    assert "([string]$pidLine).Trim()" in postgres_function
    assert "Move-Item -LiteralPath $postmasterPidPath" in postgres_function
    assert "$postmasterPidPath.stale-" in postgres_function


def test_local_runner_checks_local_baseline_and_website_environment_before_stopping_paper() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "serverPropertiesBaseline" in text
    first_environment_call = text.index("Ensure-LocalWebsiteEnvironment\n", text.index("$currentPluginSources = @(Get-CurrentPluginSources)"))
    first_paper_stop_call = text.index("Stop-ExistingLocalPaper\n")
    assert first_environment_call < first_paper_stop_call


def test_local_runner_preflights_resource_pack_and_website_port_owners() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "Assert-LocalResourcePackPortSafe" in text
    assert "Assert-LocalWebsitePortSafe" in text
    first_pack_check = text.index("Assert-LocalResourcePackPortSafe\n", text.index("$currentPluginSources = @(Get-CurrentPluginSources)"))
    first_website_check = text.index("Assert-LocalWebsitePortSafe\n", text.index("$currentPluginSources = @(Get-CurrentPluginSources)"))
    first_paper_stop_call = text.index("Stop-ExistingLocalPaper\n")
    assert first_pack_check < first_paper_stop_call
    assert first_website_check < first_paper_stop_call


def test_local_runner_suppresses_resource_pack_download_progress_noise() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "$ProgressPreference = 'SilentlyContinue'" in text


def test_local_runner_discovers_minecraft_launcher_and_does_not_report_false_success() -> None:
    text = RUNNER.read_text(encoding="utf-8")

    assert "COPIMINE_LAUNCHER_PATH" in text
    assert "function Get-MinecraftLauncherPath" in text
    assert "D:\\.minecraft\\TLauncher.exe" in text
    assert "TLauncher.exe" in text
    assert "throw 'Minecraft launcher was requested but no supported launcher was found.'" in text
