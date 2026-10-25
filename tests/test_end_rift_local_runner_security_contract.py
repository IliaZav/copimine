from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def test_local_starter_preserves_stdout_path_while_validating_both_logs() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    validation_loop = re.search(
        r"foreach\s*\(\s*\$(?P<variable>[A-Za-z_]\w*)\s+in\s+@\(\$LogPath,\s*\$ErrPath\)\s*\)\s*\{(?P<body>.*?)\n\}",
        source,
        re.DOTALL,
    )

    assert validation_loop is not None, "starter must validate both configured log paths"
    assert validation_loop.group("variable").casefold() != "logpath", (
        "PowerShell variable names are case-insensitive, so the loop variable must not overwrite stdout path"
    )
    assert "RedirectStandardOutput $LogPath" in source
    assert "RedirectStandardError $ErrPath" in source


def test_local_starter_checks_properties_before_start_and_listeners_after_readiness() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

    property_path_guard = source.find("Test-EndRiftLocalRuntimePath -Path $propertiesPath")
    preflight = source.find("Test-EndRiftLocalServerBind -ServerPropertiesLines")
    resource_pack_sync = source.find("\nSync-LocalResourcePack\n")
    paper_start = source.find("$paper = Start-Process")
    ready_failure = source.find("if (-not $ready)")
    listener_assertion = source.find("Assert-EndRiftLocalServerBind")
    started_message = source.find('Write-Output "Started isolated local Paper')

    assert min(property_path_guard, preflight) >= 0, "starter must guard and validate server.properties"
    assert property_path_guard < preflight < resource_pack_sync < paper_start, (
        "property bind check must happen before pack synchronization and Paper startup"
    )
    assert paper_start < ready_failure < listener_assertion < started_message, (
        "active loopback listener assertion must happen after readiness and before success"
    )


def test_local_starter_places_compressed_oops_heap_above_zero_based_range() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

    assert "'-XX:HeapBaseMinAddress=34g'" in source, (
        "The fatal Temurin 25 report still showed zero-based compressed oops in the "
        "first 32 GiB with a 4 GiB heap base; move this 2 GiB local heap above that range."
    )


def test_local_starter_limits_memory_pressure_for_desktop_event_probes() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

    assert "'-Xms128M'" in source
    assert "'-Xmx512M'" in source
    assert "'-XX:CICompilerCount=2'" in source
    assert "'-XX:TieredStopAtLevel=1'" in source
    assert "'-Xss512k'" in source


def test_local_starter_uses_the_pinned_local_temurin_25_runtime() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

    assert "Join-Path $localRuntimeRoot 'toolchains\\temurin-25.0.4.1\\jdk-25.0.4.1+1'" in source, (
        "The default local probe must keep using the pinned Java home rather than PATH."
    )
    assert "Join-Path $javaHomePath 'bin\\java.exe'" in source
    assert "openjdk version \"25\\.0\\.4\\.1" in source, (
        "Fail closed if the pinned local executable is not the expected Temurin 25 build."
    )


def test_local_starter_fails_fast_if_paper_exits_before_readiness() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    readiness = source.split("for ($second = 0; $second -lt $ReadyTimeoutSeconds; $second++)", 1)[1]

    assert "$paper.Refresh()" in readiness
    assert "if ($paper.HasExited)" in readiness
    assert "$paper.ExitCode" in readiness


def test_official_two_player_probe_uses_guarded_rcon_without_child_powershell() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    rcon_function = source.split("function Invoke-LocalRcon {", 1)[1].split("function Read-PaperLog", 1)[0]

    assert "& $rconScript -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText" in rcon_function
    assert "powershell -NoLogo -NoProfile" not in rcon_function


def test_official_probe_returns_passive_bots_to_runes_before_transition_wait() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    transition = source.split("function Wait-Transition {", 1)[1].split("function Wait-WaveComplete", 1)[0]
    first_wait = transition.index("Wait-Log -AfterOffset $offset -Pattern")

    assert "Set-PlayerBotMode -Mode 'PASSIVE'" in transition[:first_wait]
    assert "Teleport-PlayersToPads -Pads $Pads" in transition[:first_wait]
    assert "Set-PlayerBotMode -Mode 'ACTIVE'" in transition


def test_wave_four_short_roster_is_positioned_before_first_fireball() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    wave_four = source.split("$obeliskCount = Get-ObeliskCount $playerNames.Count", 1)[1].split(
        "$waveFourTransitionOffset = Wait-WaveComplete", 1
    )[0]
    first_launch_wait = wave_four.index("Wait-Log -AfterOffset $w4Offset -Pattern 'RIFT_FIREBALL_LAUNCH '")

    assert "Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core" in wave_four[:first_launch_wait]
    assert "Teleport-PlayersToObeliskRing -Core $core" not in wave_four[:first_launch_wait]
    assert "-Action { Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core }" in wave_four


def test_official_probe_holds_bots_passive_through_wave_three_portal_capture() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    wave_three_start = source.index("$waveThreeStartOffset = Wait-Transition")
    portal_loop = source.index("for ($portalIndex = 0; $portalIndex -lt 3; $portalIndex++)", wave_three_start)
    wave_three_complete = source.index("$waveThreeTransitionOffset = Wait-WaveComplete", portal_loop)
    passive = source.index("Set-PlayerBotMode -Mode 'PASSIVE'", wave_three_start)
    active = source.index("Set-PlayerBotMode -Mode 'ACTIVE'", portal_loop)

    assert passive < portal_loop < active < wave_three_complete


def test_rune_wait_music_bot_child_cannot_inherit_host_port_or_chat_actions() -> None:
    source = (ROOT / "tests" / "RunEndRiftRuneWaitMusicLive.ps1").read_text(encoding="utf-8")
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")
    scope = source.find("Invoke-EndRiftLocalBotEnvironment")
    host = source.find("-BotHost '127.0.0.1'", scope)
    port = source.find("-BotPort 25566", scope)
    bot_start = source.find("$process.Start()", scope)
    clear_inherited = helper.find(
        "if (Test-EndRiftLocalBotEnvironmentVariableToRemove -Name ([string]$environmentVariableName))"
    )
    remove_inherited = helper.find("SetEnvironmentVariable([string]$environmentVariableName, $null, 'Process')", clear_inherited)
    action = helper.find("& $Action")
    restore_actions = helper.find(
        "SetEnvironmentVariable($environmentVariableName, $previousValues[$environmentVariableName], 'Process')"
    )

    assert min(host, port, clear_inherited, remove_inherited, bot_start, action, restore_actions) >= 0, (
        "Rune runner must use a local-only child environment that drops inherited controls and credentials"
    )
    assert scope < host < port < bot_start, (
        "Rune child must start only after the local-only environment is applied"
    )
    assert clear_inherited < remove_inherited < action < restore_actions, (
        "The scoped helper must clear inherited credentials and controls before launch and restore env afterward"
    )


def test_rune_wait_music_runner_reads_fresh_bot_output_while_clients_are_running() -> None:
    source = (ROOT / "tests" / "RunEndRiftRuneWaitMusicLive.ps1").read_text(encoding="utf-8")
    truncate_logs = source.find("[IO.File]::WriteAllText($botLog, ''")
    process_start = source.find("$process.Start()")
    line_reader = source.find("ReadLineAsync()")
    pump_function = source.find("function Receive-EndRiftRuneBotOutput")
    online_wait_pump = source.find("Receive-EndRiftRuneBotOutput", source.find("function Wait-PlayersOnline"))
    main_wait_pump = source.find("Receive-EndRiftRuneBotOutput", source.find("$deadline = (Get-Date).AddSeconds($TimeoutSeconds)"))
    live_evidence = source.find("Get-Content -LiteralPath $path -Raw")

    assert min(truncate_logs, process_start, line_reader, pump_function, online_wait_pump, main_wait_pump, live_evidence) >= 0
    assert truncate_logs < process_start, "per-bot output files must be cleared before starting a new client"
    assert source.count("Receive-EndRiftRuneBotOutput") >= 3, (
        "bot stdout/stderr must be consumed while the runner waits for clients and for the music marker"
    )
    assert main_wait_pump < live_evidence, "fresh client output must reach the music marker check in the active wait loop"
    assert "ReadToEndAsync()" not in source, "full-stream reads only complete after bot shutdown and cannot prove a live marker"


def test_local_rcon_uses_canonical_descendant_path_guard() -> None:
    source = (ROOT / "tests" / "InvokeEndRiftLocalRcon.ps1").read_text(encoding="utf-8")
    identity = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert ". (Join-Path $scriptRoot 'EndRiftLocalAuthMode.ps1')" in source
    assert "[IO.Path]::GetFullPath((Resolve-Path -LiteralPath $ServerDir).ProviderPath)" in source
    assert "Test-EndRiftLocalRuntimePath -Path $ServerDir -LocalRuntimeRoot $localRuntimeRoot" in source
    assert "Test-EndRiftLocalRuntimePath -Path $propertiesPath -LocalRuntimeRoot $localRuntimeRoot" in source
    assert "foreach ($line in [IO.File]::ReadLines($propertiesPath))" in source
    assert "Assert-EndRiftLocalServerBind -ServerDir $ServerDir `" in source
    assert "$serverReceipt = Get-EndRiftLocalServerReceipt" in source
    assert "-ExpectedProcessId $serverReceipt.ProcessId" in source
    assert "-ExpectedProcessStartTimeUtcTicks $serverReceipt.ProcessStartTimeUtcTicks" in source
    assert "$rconPasswordCount -ne 1" in source
    assert "StartsWith($localRuntimeRoot," not in source
    assert "CommandLineToArgvW" in identity
    assert "-jar" in identity and "[IO.Path]::IsPathRooted($jarArgument)" in identity
    assert "-not $paperJarArgumentMatches" in identity


def test_local_rcon_resolves_relative_jar_only_from_verified_server_directory() -> None:
    identity = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert "$resolvedPaperJarPath = if ([IO.Path]::IsPathRooted($jarArgument))" in identity
    assert "Join-Path $expectedWorkingDirectory $jarArgument" in identity
    assert "$resolvedPaperJarPath, $safeJarPath" in identity


def test_local_rcon_selects_a_receipt_bound_to_the_requested_server_port() -> None:
    source = (ROOT / "tests" / "InvokeEndRiftLocalRcon.ps1").read_text(encoding="utf-8")

    assert "$portReceiptPath = Join-Path $ServerDir" in source
    assert "'.end-rift-process-receipt-' + $ServerPort + '.json'" in source
    assert "Get-EndRiftLocalServerReceipt -ServerDir $ServerDir -LocalRuntimeRoot $localRuntimeRoot -ReceiptPath $receiptPath" in source


def test_local_server_identity_normalizes_subst_paths_before_comparing_receipt() -> None:
    identity = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert "function Resolve-EndRiftLocalDriveSubstitutions" in identity
    assert "& $commandProcessor /d /c subst" in identity
    assert "Resolve-EndRiftLocalDriveSubstitutions -Path $PaperJarPath" in identity
    assert "Resolve-EndRiftLocalDriveSubstitutions -Path ([string]$receipt.WorkingDirectory)" in identity
    assert "Resolve-EndRiftLocalDriveSubstitutions -Path $ExpectedWorkingDirectory" in identity


def test_local_rcon_normalizes_receipt_jar_path_before_server_directory_check() -> None:
    source = (ROOT / "tests" / "InvokeEndRiftLocalRcon.ps1").read_text(encoding="utf-8")

    assert "$expectedPaperJarPath = Join-Path $ServerDir 'paper-26.3-205.jar'" in source
    assert "Join-Path $ServerDir 'purpur.jar'" in source
    assert "$canonicalReceiptPaperJarPath = Resolve-EndRiftLocalDriveSubstitutions -Path ([string]$serverReceipt.PaperJarPath)" in source
    assert "[string]::Equals($canonicalReceiptPaperJarPath, $expectedPaperJarPath" in source


def test_local_runtime_identity_supports_windows_extended_length_paths() -> None:
    source = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")
    hard_link_probe = source[source.index("function GetHardLinkCount"):source.index("function Assert-EndRiftLocalServerTreeIsolated")]

    assert "'\\\\?\\'" in hard_link_probe
    assert "[EndRiftLocalFileIdentityNative]::CreateFileW(" in hard_link_probe
    assert "$nativePath, [uint32]0" in hard_link_probe


def test_wave7_rcon_forwards_the_configured_server_port() -> None:
    source = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    rcon_script = source.index("function Invoke-LocalRcon")
    rcon_body_end = source.index("\n}", rcon_script)
    rcon_body = source[rcon_script:rcon_body_end]

    assert "-ServerPort $ServerPort" in rcon_body, (
        "Wave 6/7 local RCON must preserve the configurable Minecraft port when invoking the canonical client"
    )


def test_wave7_restart_starts_the_jar_by_its_absolute_isolated_path() -> None:
    source = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    start = source.index("function Start-LocalMinecraft")
    end = source.index("function Wait-Log-MarkerIncrease", start)
    restart = source[start:end]

    assert "$paperJarPath = Assert-EndRiftLocalRuntimePath" in restart
    assert "-Path (Join-Path $serverDir 'purpur.jar') -LocalRuntimeRoot $localRuntimeRoot" in restart
    assert "-ArgumentList @(" in restart
    assert "'-jar', ('\"' + $paperJarPath + '\"'), 'nogui'" in restart, (
        "Wave 7 restart must pass the resolved absolute server JAR as one quoted argument"
    )


def test_wave7_runner_rejects_reparse_paths_before_writing_control_files() -> None:
    source = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    server_guard = source.find("Assert-EndRiftLocalRuntimePath -Path $serverDir -LocalRuntimeRoot $localRuntimeRoot")
    runtime_guard = source.find("Resolve-EndRiftLocalRuntimePath `\n  -Path $RuntimeDataDir -LocalRuntimeRoot $localRuntimeRoot")
    control_guard = source.find("Assert-EndRiftLocalRuntimePath -Path $controlDirectory -LocalRuntimeRoot $localRuntimeRoot")
    create_controls = source.find("New-Item -ItemType Directory -Path $controlDirectory -Force")

    assert min(server_guard, runtime_guard, control_guard, create_controls) >= 0, (
        "Wave 7 runner must reject reparse paths for the server, runtime, and control directory"
    )
    assert server_guard < create_controls and runtime_guard < create_controls and control_guard < create_controls, (
        "All runtime path guards must run before the runner writes its control directory"
    )


def test_wave7_runner_allows_exact_default_runtime_root_through_reparse_aware_resolver() -> None:
    runner = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    default_runtime_root = runner.find("$RuntimeDataDir = Split-Path -Parent $serverDir")
    runtime_resolver = runner.find("Resolve-EndRiftLocalRuntimePath `\n  -Path $RuntimeDataDir -LocalRuntimeRoot $localRuntimeRoot")
    root_equality = helper.find("[string]::Equals($fullPath, $fullRuntimeRootName")
    exact_root_guard = helper.find("if ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint)", root_equality)
    child_guard = helper.find("return Assert-EndRiftLocalRuntimePath -Path $fullPath -LocalRuntimeRoot $fullRuntimeRoot", root_equality)

    assert min(default_runtime_root, runtime_resolver, root_equality, exact_root_guard, child_guard) >= 0
    assert default_runtime_root < runtime_resolver, "default RuntimeDataDir must pass through the dual-mode resolver"
    assert exact_root_guard < child_guard, "exact-root acceptance must still reject a reparse root before child handling"


def test_paper_runners_scan_server_trees_and_guard_the_wave7_log_path() -> None:
    starter = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    wave7 = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    starter_tree_guard = starter.find("Assert-EndRiftLocalServerTreeIsolated `\n  -ServerDir $ServerDir")
    starter_paper_start = starter.find("$paper = Start-Process")
    wave7_tree_guard = wave7.find("Assert-EndRiftLocalServerTreeIsolated `\n  -ServerDir $serverDir")
    wave7_paper_log_guard = wave7.find("$paperLog = Assert-EndRiftLocalRuntimePath")
    wave7_log_reader = wave7.find("function Read-Log")
    wave7_log_read = wave7.find("Get-Content -LiteralPath $validatedPaperLog", wave7_log_reader)

    assert min(starter_tree_guard, starter_paper_start, wave7_tree_guard, wave7_paper_log_guard, wave7_log_reader, wave7_log_read) >= 0
    assert starter_tree_guard < starter_paper_start
    assert wave7_tree_guard < wave7_log_read
    assert wave7_paper_log_guard < wave7_log_read
    assert wave7_log_reader < wave7_log_read
    assert "[IO.Directory]::EnumerateFileSystemEntries($currentPath)" in helper
    assert "[IO.FileAttributes]::ReparsePoint" in helper
    assert "GetHardLinkCount" in helper and "NumberOfLinks" in helper


def test_local_paper_boot_and_wave7_restart_share_environment_file_parsing() -> None:
    starter = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    wave7 = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert "Import-EndRiftLocalEnvironment -Path $envPath" in starter
    assert "Import-EndRiftLocalEnvironment -Path $path" in wave7
    assert "function Import-EndRiftLocalEnvironment" in helper
    assert ".Trim().Trim('\"').Trim(\"'\")" in helper
    assert "Set-Item -Path (\"Env:\" + $name) -Value $value" in helper


def test_end_rift_bots_use_pinned_via_backwards_protocol_route_to_paper_26_3() -> None:
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")
    bot = (ROOT / "tests" / "LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    runner = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")

    assert "mc-bot-26.3-complete-probe\\node_modules" in helper
    assert "END_RIFT_BOT_NODE_MODULES" in helper
    assert "END_RIFT_BOT_PROTOCOL_VERSION" in helper
    assert "'26.1'" in helper
    assert "ViaVersion-5.12.1.jar" in helper
    assert "ViaBackwards-5.12.1.jar" in helper
    assert "b8033807e7182c35003a45962352dd642c1e91880ca281da3a43cb6f481a95a2" in helper
    assert "29d94d3a26c85c8a327574b2322db4078992ae457f0173325431a12aac384911" in helper
    assert "Assert-EndRiftLocalBotProtocolGateway" in helper
    assert "process.env.END_RIFT_BOT_NODE_MODULES" in bot
    assert "process.env.END_RIFT_BOT_PROTOCOL_VERSION" in bot
    assert "minecraftProtocol.supportedVersions.includes(protocolVersion)" in bot
    assert "mineflayer.testedVersions.includes(protocolVersion)" in bot
    assert "protocolVersion !== '26.1'" in bot
    assert "bot.on('physicsTick'" in bot
    assert "queueMicrotask" in bot
    assert "bot._client.write('tick_end', {})" in bot
    assert "if (host !== '127.0.0.1') {" in bot
    assert "End Rift protocol bots are restricted to loopback" in bot
    assert "client=26.1 backend=Paper-26.3-205 gateway=ViaVersion-5.12.1" in bot
    assert "version: '1.21.1'" not in bot

    assert "Add-LocalProbeWhitelistEntries" in runner
    assert "Remove-LocalProbeWhitelistEntries" in runner
    assert "whitelist add $name" in runner
    assert "whitelist remove $name" in runner


def test_local_bot_gateway_keeps_the_isolated_allowlist_enabled() -> None:
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert "white-list=true" in helper
    assert "online-mode=false" in helper
    assert "Refused a bot protocol route outside isolated port 25566" in helper
    assert "InvokeEndRiftLocalRcon.ps1" in helper


def test_local_starter_accepts_only_locked_event_artifact_and_pinned_paper() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

    assert "[string]$PluginArtifactPath" in source
    assert "server-plugins.lock.json" in source
    assert "$endEventCandidate.sha256" in source
    assert "paper-26.3-205.jar" in source
    assert "profileLock.paper.sha256" in source
    assert "[string]$EnvironmentFilePath" in source


def test_local_starter_confines_explicit_java_home_to_isolated_server() -> None:
    source = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")

    assert "[string]$JavaHomePath = ''" in source
    assert "Test-EndRiftLocalRuntimePath" in source
    assert "$javaHomePath.StartsWith($serverJavaPrefix" in source
    assert "An explicit local End Rift Java home must be inside the isolated server directory." in source
    assert "openjdk version \"25\\.0\\.4\\.1" in source


def test_mob_and_spell_bots_receive_only_explicit_loopback_environment() -> None:
    for name in ("RunEndRiftMobCombatLive.ps1", "RunEndRiftSpellMatrixLive.ps1"):
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        helper = source.find("Set-EndRiftLocalBotChildEnvironment")
        process_start = source.find("$process.Start()")
        if name == "RunEndRiftSpellMatrixLive.ps1":
            process_start = source.find("$process.Start()", source.find("function Start-SpellMatrixBot"))
        assert min(helper, process_start) >= 0, f"{name} must pin bot child environment before launch"
        assert helper < process_start, f"{name} must sanitize bot env before launching the client"
        assert "-BotHost '127.0.0.1' -BotPort 25566" in source


def test_shard_runner_guards_the_shared_postgres_client_against_its_own_runtime_root() -> None:
    source = (ROOT / "tests" / "RunEndRiftShardPassivesLive.ps1").read_text(encoding="utf-8")

    assert "$repoLocalRuntimeRoot = Join-Path $repoRoot 'local-runtime'" in source
    psql_guard = source.find("Assert-EndRiftLocalRuntimePath -Path $psqlPath -LocalRuntimeRoot $repoLocalRuntimeRoot")
    worktree_guards = source.find("foreach ($runtimePath in @(")
    assert min(psql_guard, worktree_guards) >= 0
    assert "$psqlPath" not in source[worktree_guards:psql_guard]


def test_official_two_player_probe_forwards_only_its_explicit_child_environment() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    helper = source.find("Set-EndRiftLocalBotChildEnvironment -StartInfo $startInfo")
    overrides = source.find("Set-EndRiftLocalBotChildEnvironmentOverrides -StartInfo $startInfo")
    launch = source.find("$process.Start()", helper)

    assert min(helper, overrides, launch) >= 0
    assert helper < overrides < launch
    for name in (
        "END_RIFT_REFLECT_ENABLED", "END_RIFT_REFLECT_START_MS", "END_RIFT_REFLECT_DIAGNOSTICS",
        "END_RIFT_OBELISK_TARGETS", "END_RIFT_WAVE7_CHAMBERS", "END_RIFT_GUARDIAN_PROBE_NAMES",
        "END_RIFT_ATTACK_INTERVAL_MS",
    ):
        assert name in source[overrides:launch]
    assert "'20', '900'" not in source[helper:launch]


def test_official_two_player_probe_keeps_reflection_available_in_every_wave() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    bot = (ROOT / "tests" / "LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    start_bot = source.split("function Start-PlayerBot", 1)[1].split("function ", 1)[0]
    attack = bot.split("function attackNearest()", 1)[1].split("function ", 1)[0]
    reflect = bot.split("async function reflectFireball(entity)", 1)[1].split("function scheduleFireballReflection", 1)[0]
    schedule = bot.split("function scheduleFireballReflection(entity)", 1)[1].split("function scanRiftFireballs", 1)[0]
    navigation = bot.split("function tickWave7Navigation()", 1)[1].split("function navigateWave7", 1)[0]

    assert "END_RIFT_REFLECT_ENABLED = [string]$env:END_RIFT_REFLECT_ENABLED" in start_bot
    assert "$Name -eq $FirstBotName" not in start_bot
    assert "if (reflectEnabled && reflectionInFlight.size > 0) return" in attack
    assert "if (meleeActionInFlight) return false" in reflect
    assert "isAimWithinReflectionCone" in reflect
    assert "aim-outside-reflection-cone" in reflect
    assert "createReflectionActionQueue" in bot
    assert "runReflectionAction(() => reflectFireball(current))" in schedule
    assert "if (reflectEnabled && reflectionInFlight.size > 0)" in navigation
    assert "stopNavigation()" in navigation


def test_wave_one_mode_acknowledgement_uses_correlated_control_file() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    bot = (ROOT / "tests" / "LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    wait = source.split("function Wait-PlayerBotModeAcknowledgement", 1)[1].split("function ", 1)[0]
    setter = source.split("function Set-PlayerBotModeForName", 1)[1].split("function ", 1)[0]
    poll = bot.split("function pollControlMode ()", 1)[1].split("function ", 1)[0]

    assert "modeRequestId" in setter
    assert "|$modeRequestId" in setter
    assert '$script:botModeRequestIds["$Name|$Mode"]' in setter
    assert ".mode.ack" in wait
    assert '$script:botModeRequestIds["$Name|$Mode"]' in wait
    assert "Get-Content -LiteralPath $safeBotLogPath -Raw" not in wait
    assert "requestedRequestId" in poll
    assert "fs.appendFileSync(modeAckFile" in poll


def test_wave_four_small_roster_stays_near_center_instead_of_rotating_under_obelisks() -> None:
    source = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    lane = source.split("function Teleport-PlayersToWave4ReflectionLaneIfDue {", 1)[1].split(
        "function Get-OfflinePlayerUuid", 1
    )[0]

    assert "if ($playerNames.Count -ge $obeliskCount)" in lane
    assert "$smallRosterRadius = 2.5D" in lane
    assert "2.0D * [Math]::PI * $index / $playerNames.Count" in lane
    assert "Teleport-Player $playerNames[$index] $targetX $y $targetZ" in lane
    assert "$wave4LaneDwellMs" not in lane
    assert "$script:wave4ReflectionLane" not in lane


def test_live_wave_probes_use_minecraft_26_3_attribute_identifiers() -> None:
    names = (
        "RunEndRiftOfficialTwoPlayerLive.ps1",
        "RunEndRiftObeliskLive.ps1",
        "RunEndRiftAiPhasesLive.ps1",
        "RunEndRiftMobCombatLive.ps1",
        "RunEndRiftSpellMatrixLive.ps1",
        "RunEndRiftWave6Wave7BoundariesLive.ps1",
    )
    for name in names:
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        assert "minecraft:generic.max_health" not in source, name
        assert "minecraft:generic.knockback_resistance" not in source, name


def test_every_environment_configurable_local_bot_uses_shared_loopback_guard() -> None:
    names = (
        "LocalEndRiftBot.js",
        "LocalEndRiftMobCombatBot.js",
        "LocalEndRiftCoreRemovalGuiBot.js",
        "LocalEndRiftBossPacketBot.js",
        "LocalBedRespawnMineflayerBot.js",
        "LocalEndRiftBossCombatBot.js",
        "LocalBedRespawnBot.js",
        "LocalEndRiftObeliskBot.js",
        "LocalEndRiftGatePointsBot.js",
        "LocalEndRiftShardProbe.js",
    )
    for name in names:
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        guard = source.find("resolveLocalEndRiftEndpoint")
        client = min(
            (position for position in (source.find("createBot("), source.find("createClient(")) if position >= 0),
            default=-1,
        )
        assert guard >= 0, f"{name} must validate its configured host through the shared helper"
        assert client < 0 or guard < client, f"{name} must validate the host before transport creation"


def test_wave6_wave7_diagnostic_artifacts_and_bot_controls_guard_each_write_path() -> None:
    source = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    artifact_helper = source.split("function Get-DiagnosticArtifactPath", 1)[1].split("function Write-RunMetadata", 1)[0]
    bot_start = source.split("function Start-Bot", 1)[1].split("function Set-BotMode", 1)[0]
    bot_mode = source.split("function Set-BotMode", 1)[1].split("function Stop-Bots", 1)[0]

    assert "Assert-EndRiftLocalOutputPath" in artifact_helper
    for artifact in ("metadata.json", "live-test-steps.log", "end-rift-events.jsonl", "artifact-hashes.txt", "evidence-index.json", "summary.json", "report.md"):
        assert artifact in artifact_helper
    assert "$output = Assert-EndRiftLocalRuntimePath" in bot_start
    assert "$errorOutput = Assert-EndRiftLocalRuntimePath" in bot_start
    assert "$path = Assert-EndRiftLocalRuntimePath" in bot_mode


def test_wave6_wave7_held_item_sync_uses_the_isolated_bot_control_channel() -> None:
    runner = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    bot = (ROOT / "tests" / "LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    sync = runner.split("function Sync-BotsHeldItem", 1)[1].split("function Wait-BotsHeldItemSynced", 1)[0]
    control_poll = bot.split("function pollControlMode ()", 1)[1].split("function distance(", 1)[0]

    assert ".held-item-sync" in runner
    assert ".held-item-sync" in bot
    assert "pollHeldItemSyncRequest()" in control_poll
    assert "WriteAllText" in sync
    assert "END_RIFT_BOUNDARY_SYNC_HELD_ITEM" not in sync


def test_visual_five_player_child_guards_its_direct_evidence_and_bot_outputs() -> None:
    source = (ROOT / "tests" / "RunEndRiftVisualFivePlayerLive.ps1").read_text(encoding="utf-8")

    assert ". (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')" in source
    for guard in (
        "$evidenceDirectory = Assert-EndRiftLocalRuntimePath",
        "$botLogDirectory = Assert-EndRiftLocalRuntimePath",
        "$controlDirectory = Assert-EndRiftLocalRuntimePath",
        "$evidencePath = Assert-EndRiftLocalRuntimePath",
        "$outputPath = Assert-EndRiftLocalRuntimePath",
        "$errorPath = Assert-EndRiftLocalRuntimePath",
        "$controlPath = Assert-EndRiftLocalRuntimePath",
    ):
        assert guard in source


def test_evidence_output_guards_reject_reparse_points_and_hard_links() -> None:
    required_checks = {
        "RunEndRiftPerformanceLive.ps1": ("$botOut = Assert-EndRiftLocalRuntimePath", "$botErr = Assert-EndRiftLocalRuntimePath"),
        "RunEndRiftPerformanceFivePlayerLive.ps1": ("$botOut = Assert-EndRiftLocalRuntimePath", "$botErr = Assert-EndRiftLocalRuntimePath"),
        "RunEndRiftBossHitboxLive.ps1": ("$stdoutPath = Assert-EndRiftLocalRuntimePath", "$stderrPath = Assert-EndRiftLocalRuntimePath"),
        "RunEndRiftBossVisualLive.ps1": ("Assert-UnderRoot $evidenceDirectory", "Assert-UnderRoot $EvidencePath"),
        "RunEndRiftWave6Wave7BoundariesLive.ps1": (
            "$diagnosticRoot = Assert-EndRiftLocalOutputPath",
            "$runDir = Assert-EndRiftLocalOutputPath",
            "function Get-DiagnosticArtifactPath",
            "$output = Assert-EndRiftLocalRuntimePath",
            "$errorOutput = Assert-EndRiftLocalRuntimePath",
            "$path = Assert-EndRiftLocalRuntimePath",
        ),
        "RunEndRiftVisualFivePlayerLive.ps1": (
            ". (Join-Path $PSScriptRoot 'EndRiftLocalAuthMode.ps1')",
            "$botLogDirectory = Assert-EndRiftLocalRuntimePath",
            "$controlDirectory = Assert-EndRiftLocalRuntimePath",
            "$evidencePath = Assert-EndRiftLocalRuntimePath",
            "$outputPath = Assert-EndRiftLocalRuntimePath",
            "$errorPath = Assert-EndRiftLocalRuntimePath",
        ),
        "RunEndRiftTentacleLive.ps1": (
            "$botLogDirectory = Assert-EndRiftLocalRuntimePath",
            "$botStdout = Assert-EndRiftLocalRuntimePath",
            "$botStderr = Assert-EndRiftLocalRuntimePath",
            "$botControlDirectory = Assert-EndRiftLocalRuntimePath",
            "$botControlFile = Assert-EndRiftLocalRuntimePath",
            "$botTargetFile = Assert-EndRiftLocalRuntimePath",
        ),
        "RunEndRiftRuneWaitMusicLive.ps1": (
            "$evidencePath = Assert-EndRiftLocalRuntimePath",
            "$botLogDirectory = Assert-EndRiftLocalRuntimePath",
            "$botLog = Assert-EndRiftLocalRuntimePath",
            "$botErr = Assert-EndRiftLocalRuntimePath",
        ),
        "RunEndRiftOfficialTwoPlayerLive.ps1": (
            "$evidencePath = Assert-EndRiftLocalRuntimePath",
            "$botLogDirectory = Assert-EndRiftLocalRuntimePath",
            "$controlDirectory = Assert-EndRiftLocalRuntimePath",
            "$modePath = Assert-EndRiftLocalRuntimePath",
            "$out = Assert-EndRiftLocalRuntimePath",
            "$err = Assert-EndRiftLocalRuntimePath",
            "Assert-EndRiftLocalRuntimePath `\n      -Path $_.FullName",
        ),
        "RunEndRiftBossMultiPlayerDamageLive.ps1": (
            "$logDirectory = Assert-EndRiftLocalRuntimePath",
            "$attackBarrier = Assert-EndRiftLocalRuntimePath",
            "$stdoutPath = Assert-EndRiftLocalRuntimePath",
            "$stderrPath = Assert-EndRiftLocalRuntimePath",
        ),
    }
    for name, checks in required_checks.items():
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        guard_start = source.find("function Assert-Under")
        if name in ("RunEndRiftWave6Wave7BoundariesLive.ps1", "RunEndRiftVisualFivePlayerLive.ps1"):
            for check in checks:
                assert check in source, f"{name} must validate every redirected or controlled write path: {check}"
            continue
        assert guard_start >= 0, f"{name} must retain its local-runtime output guard"
        guard_end = source.find("\n}", guard_start)
        assert guard_end > guard_start, f"{name} must define a complete output guard"
        guard = source[guard_start:guard_end]
        assert "Assert-EndRiftLocalRuntimePath" in guard, (
            f"{name} must use the shared reparse-point and hard-link aware path validator"
        )
        for check in checks:
            assert check in source, f"{name} must validate every redirected or controlled write path: {check}"


def test_every_local_paper_start_records_a_server_directory_and_process_receipt() -> None:
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")
    starter = (ROOT / "tests" / "StartEndRiftLocal.ps1").read_text(encoding="utf-8")
    wave7 = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")

    assert "function Write-EndRiftLocalServerReceipt" in helper
    assert "ProcessStartTimeUtcTicks" in helper
    assert "WorkingDirectory" in helper
    starter_start = starter.find("$paper = Start-Process")
    starter_receipt = starter.find("Write-EndRiftLocalServerReceipt", starter_start)
    starter_success = starter.find('Write-Output "Started isolated local Paper', starter_start)
    assert min(starter_start, starter_receipt, starter_success) >= 0
    assert starter_start < starter_receipt < starter_success

    restart_start = wave7.find("function Start-LocalMinecraft")
    restart_end = wave7.find("function Wait-Log-MarkerIncrease", restart_start)
    restart = wave7[restart_start:restart_end]
    assert "Write-EndRiftLocalServerReceipt" in restart
    assert "-ProcessId $process.Id" in restart


def test_bot_environment_helpers_remove_non_end_rift_credentials_and_restore_parent() -> None:
    helper = (ROOT / "tests" / "EndRiftLocalAuthMode.ps1").read_text(encoding="utf-8")

    assert "function Test-EndRiftLocalBotEnvironmentVariableToRemove" in helper
    assert "function Get-EndRiftLocalBotSafeEnvironmentVariableNames" in helper
    safe_names = helper.split("function Get-EndRiftLocalBotSafeEnvironmentVariableNames", 1)[1].split("function Test-EndRiftLocalBotEnvironmentVariableToRemove", 1)[0]
    assert "'PATH'" in safe_names and "'SYSTEMROOT'" in safe_names and "'TEMP'" in safe_names
    assert "JDBC_URL" not in safe_names and "MONGO_URI" not in safe_names and "NPM_CONFIG__AUTH" not in safe_names
    assert "Test-EndRiftLocalBotEnvironmentVariableToRemove -Name ([string]$environmentVariableName)" in helper
    assert "SetEnvironmentVariable($environmentVariableName, $previousValues[$environmentVariableName], 'Process')" in helper


def test_ai_and_no_auth_probes_fail_closed_before_mutation_and_isolate_bot_environment() -> None:
    ai = (ROOT / "tests" / "RunEndRiftAiPhasesLive.ps1").read_text(encoding="utf-8")
    ai_guard = ai.find("$null = Assert-EndRiftLocalAuthMeDisabled -PluginListOutput (Invoke-LocalRcon 'plugins')")
    ai_try = ai.find("\ntry {", ai_guard)
    ai_wave_clear = ai.find("Invoke-LocalRcon 'cmend wave clear'", ai_try)
    ai_bot_launch = ai.find("$process = Start-ProbeBot $core", ai_try)
    assert min(ai_guard, ai_try, ai_wave_clear, ai_bot_launch) >= 0
    assert ai_guard < ai_try < min(ai_wave_clear, ai_bot_launch), (
        "AI smoke must establish disabled AuthMe before its try/finally can clear waves or launch a client"
    )
    assert "Assert-EndRiftLocalServerTreeIsolated" in ai
    assert "Assert-EndRiftLocalServerBind -ServerDir $serverDir -ServerPort 25566 -RconPort 25576" in ai
    ai_start = ai.split("function Start-ProbeBot", 1)[1].split("function Set-ProbeBotMode", 1)[0]
    assert "Invoke-EndRiftLocalBotEnvironment -BotHost '127.0.0.1' -BotPort 25566" in ai_start
    assert "END_RIFT_BOT_SKIP_REGISTER = '1'" in ai_start
    assert "END_RIFT_BOT_SKIP_AUTH = '1'" in ai_start
    assert "END_RIFT_TEST_CLIENT_MOD_HANDSHAKE = '1'" in ai_start
    assert "END_RIFT_TEST_CLIENT_MOD_VERSION = '0.1.3+26.3'" in ai_start
    assert ai_start.find("Invoke-EndRiftLocalBotEnvironment") < ai_start.find("Start-Process")

    smoke = (ROOT / "tests" / "RunEndRiftLocalNoAuthSmoke.ps1").read_text(encoding="utf-8")
    smoke_guard = smoke.find("$null = Assert-EndRiftLocalAuthMeDisabled -PluginListOutput $pluginList")
    smoke_try = smoke.find("\ntry {", smoke_guard)
    smoke_bot_launch = smoke.find("Start-SmokeBot -Name $name -Core $core", smoke_try)
    evidence_write = smoke.find("Set-Content -LiteralPath $evidencePath")
    assert min(smoke_guard, smoke_try, smoke_bot_launch, evidence_write) >= 0
    assert smoke_guard < evidence_write < smoke_try < smoke_bot_launch, (
        "no-auth smoke must reject AuthMe before writing probe files or entering cleanup scope"
    )
    assert "codex/end-rift-26-3-integrated-final" in smoke
    assert "Assert-EndRiftLocalServerBind" in smoke
    assert "Assert-EndRiftLocalServerTreeIsolated" in smoke
    smoke_start = smoke.split("function Start-SmokeBot", 1)[1].split("$activeBindings = Assert-LocalOnly", 1)[0]
    assert "Invoke-EndRiftLocalBotEnvironment -BotHost '127.0.0.1' -BotPort $ServerPort" in smoke_start
    assert "END_RIFT_BOT_SKIP_REGISTER = '1'" in smoke_start
    assert "END_RIFT_BOT_SKIP_AUTH = '1'" in smoke_start
    assert smoke_start.find("Invoke-EndRiftLocalBotEnvironment") < smoke_start.find("Start-Process")
    assert "$env:END_RIFT_BOT_HOST" not in smoke
    assert "$previousBotEnvironment" not in smoke

    boss = (ROOT / "tests" / "RunEndRiftBossMultiPlayerDamageLive.ps1").read_text(encoding="utf-8")
    boss_auth_guard = boss.find("Assert-EndRiftLocalAuthMeDisabled -PluginListOutput (Invoke-LocalRcon -CommandText 'plugins')")
    boss_try = boss.find("\ntry {", boss_auth_guard)
    boss_mutation = boss.find("cmend boss kill cleanup")
    assert min(boss_auth_guard, boss_try, boss_mutation) >= 0
    assert boss_auth_guard < boss_try < boss_mutation
    assert "Wait-LocalAuthentication" not in boss
    assert "logged in" not in boss


def test_changed_mineflayer_live_runners_pin_child_host_and_port() -> None:
    process_start_runners = (
        "RunEndRiftBossMultiPlayerDamageLive.ps1",
        "RunEndRiftBossHitboxLive.ps1",
    )
    for name in process_start_runners:
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        assert "Set-EndRiftLocalBotChildEnvironment -StartInfo $startInfo -BotHost '127.0.0.1' -BotPort 25566" in source

    start_process_runners = (
        "RunEndRiftPerformanceLive.ps1",
        "RunEndRiftPerformanceFivePlayerLive.ps1",
    )
    for name in start_process_runners:
        source = (ROOT / "tests" / name).read_text(encoding="utf-8")
        invoke = source.find("Invoke-EndRiftLocalBotEnvironment -BotHost '127.0.0.1' -BotPort 25566")
        launch = source.find("Start-Process", invoke)
        assert min(invoke, launch) >= 0 and invoke < launch, name


def test_mob_and_spell_probes_reject_wrong_branch_and_authme_before_cleanup() -> None:
    mob = (ROOT / "tests" / "RunEndRiftMobCombatLive.ps1").read_text(encoding="utf-8")
    mob_branch_guard = mob.find("$branch -cne $ExpectedBranch")
    mob_auth_guard = mob.find("Assert-EndRiftLocalAuthMeDisabled -PluginListOutput (Rcon 'plugins')")
    mob_try = mob.find("\ntry {")
    mob_mutation = mob.find("Rcon 'cmend wave clear'", mob_try)
    assert min(mob_branch_guard, mob_auth_guard, mob_try, mob_mutation) >= 0
    assert mob_branch_guard < mob_auth_guard < mob_try < mob_mutation
    assert "Assert-EndRiftLocalServerBind -ServerDir $serverDir -ServerPort 25566 -RconPort 25576" in mob
    assert "Set-EndRiftLocalBotChildEnvironment -StartInfo $start -BotHost '127.0.0.1' -BotPort 25566" in mob

    spell = (ROOT / "tests" / "RunEndRiftSpellMatrixLive.ps1").read_text(encoding="utf-8")
    spell_branch_guard = spell.find("$branch -cne $ExpectedBranch")
    spell_auth_guard = spell.find("Assert-EndRiftLocalAuthMeDisabled -PluginListOutput $plugins | Out-Null")
    spell_try = spell.find("\ntry {", spell_auth_guard)
    spell_mutation = spell.find("Invoke-LocalRcon -CommandText 'cmend wave clear'", spell_try)
    assert min(spell_branch_guard, spell_auth_guard, spell_try, spell_mutation) >= 0
    assert spell_branch_guard < spell_auth_guard < spell_try < spell_mutation
    assert "Assert-EndRiftLocalServerTreeIsolated" in spell
