from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)


def test_debug_command_exposes_bounded_runtime_diagnostics() -> None:
    assert 'case "debug" -> handleDebug(sender, args);' in MAIN
    start = MAIN.index("private void handleDebug")
    end = MAIN.index("private void handleStatus", start)
    debug = MAIN[start:end]
    for marker in (
        '"packets".equals(section)',
        '"objectives".equals(section)',
        '"hazards".equals(section)',
        '"perf".equals(section)',
        '"ai".equals(section)',
        "CLIENT_BINDINGS",
        "OBJECTIVE_DIAGNOSTICS",
        "HAZARD_DIAGNOSTICS",
        "realFire=",
        "arenaInfernoOriginalBlocks",
        "PERF_DIAGNOSTICS",
        "AI_DIAGNOSTICS",
        "aiEnabled=",
        "targeted=",
        "coreObjective=",
        "outside=",
        "eventAudience().size()",
        "entityBindingInstances.size()",
        "activeRiftProjectiles.size()",
        "activeVoidMarkCenters.size()",
        "hazardJournal.path()",
    ):
        assert marker in debug


def test_ai_debug_reports_the_exact_entity_that_breaches_the_combat_bounds() -> None:
    start = MAIN.index("private void handleDebug")
    end = MAIN.index("private void handleStatus", start)
    debug = MAIN[start:end]
    for marker in (
        "AI_OUTSIDE",
        "EVENT_KIND_BOSS.equals(kind)",
        "config.containmentRadius()",
        "horizontalDistanceSquared(entity.getLocation(), anchor)",
        "outsideCombatVertical(entity.getLocation(), anchor)",
    ):
        assert marker in debug


def test_ai_debug_reports_the_phase_combat_profile() -> None:
    start = MAIN.index("private void handleDebug")
    end = MAIN.index("private void handleStatus", start)
    debug = MAIN[start:end]
    for marker in (
        "AI_PROFILE",
        "absorptionCompleted=",
        "movementSpeed=",
        "spellCooldownMultiplier=",
        "teleportCooldownMultiplier=",
        "targetRotationMultiplier=",
        "meleeDamageBonus=",
        "nextMeleeAttackBonus=",
        "summonCap=",
    ):
        assert marker in debug


def test_debug_subcommands_are_tab_completed() -> None:
    completion = MAIN[MAIN.index("public List<String> onTabComplete") :]
    assert 'case "debug" -> List.of("packets", "objectives", "hazards", "perf", "ai")' in completion


def test_local_diagnostics_failure_probe_exercises_the_wave_transition_catch() -> None:
    for marker in (
        '"diagnostics".equalsIgnoreCase(args[1])',
        "handleTestDiagnosticsFailure",
        "diagnosticsFailureInjection",
        "local diagnostics failure probe",
        "spawnWave(wave, true)",
        "environment=local",
    ):
        assert marker in MAIN


def test_live_diagnostics_probe_is_local_only_and_checks_full_failure_record() -> None:
    script = (ROOT / "tests/RunEndRiftDiagnosticsFailureLive.ps1").read_text(encoding="utf-8")
    for marker in (
        "environment:\\s*local",
        "server-port=25566",
        "rcon\\.port=25576",
        "codex/end-rift-event",
        "local-runtime directory",
        "cmend test diagnostics fail",
        "WAVE_TRANSITION_STARTED",
        "toWave",
        "WAVE_TRANSITION_FAILED",
        "activeTasks",
        "operationMillis",
        "errorType",
        "errorMessage",
        "stackTrace",
        "baselineState = Get-OfficialState",
        "Assert-CleanOfficialState -Expected $baselineState",
        "officialState={5}",
        "eventMobs=0",
        "boss=none",
        "current.Pads -ne $Expected.Pads",
        "pads={6}",
    ):
        assert marker in script


def test_repeating_per_mob_path_details_are_not_default_info_log_spam() -> None:
    for marker in (
        'getLogger().fine((skeleton ? "SKELETON_STATS"',
    ):
        assert marker in MAIN
    assert 'String targetMessage = "WAVE_AI_TARGET' in MAIN
    assert 'String message = "WAVE_AI_PATH' in MAIN
    # Spawn-time role/behavior and the first successful posture are state
    # transitions; only their repeating refreshes remain debug-only.
    for marker in (
        'getLogger().info("WAVE_SKELETON_BEHAVIOR',
        'getLogger().info("WAVE_AI_TACTIC',
        'getLogger().info(message);',
        'getLogger().fine(message);',
    ):
        assert marker in MAIN


def test_repeating_wave_animation_frames_are_debug_only() -> None:
    assert 'getLogger().fine("WAVE_FRONT_FRAME' in MAIN


def test_debug_packets_reports_a_five_second_runtime_budget_window() -> None:
    start = MAIN.index("private void handleDebug")
    end = MAIN.index("private void handleStatus", start)
    debug = MAIN[start:end]
    for marker in (
        "RUNTIME_DIAGNOSTICS",
        "packetQualityMode=",
        "pluginMessagesPerSecond=",
        "estimatedParticlePacketsPerSecond=",
        "particleBatchesPerSecond=",
        "sampleIntervalMs=",
        "ownedLiving=",
        "temporaryDisplays=",
        "activeProjectiles=",
        "gcCollections=",
        "gcPauseMs=",
        "serverThreadCpuPercent=",
        "lastPlayerPings=",
    ):
        assert marker in debug


def test_runtime_sampler_is_windowed_and_not_a_second_repeating_task() -> None:
    assert "RUNTIME_DIAGNOSTICS_WINDOW_MILLIS = 5_000L" in MAIN
    assert "sampleRuntimeDiagnostics();" in MAIN
    assert "now - runtimeDiagnosticsWindowStartedAtMillis < RUNTIME_DIAGNOSTICS_WINDOW_MILLIS" in MAIN
    assert "ManagementFactory.getGarbageCollectorMXBeans()" in MAIN
    assert "getCurrentThreadCpuTime()" in MAIN
    tick_start = MAIN.index("private void tick()")
    tick_end = MAIN.index("private void updatePadOccupancy", tick_start)
    tick = MAIN[tick_start:tick_end]
    assert tick.count("runTaskTimer") == 0


def test_local_visual_test_command_reports_uuid_role_binding_and_asset_path() -> None:
    assert '"visuals".equalsIgnoreCase(args[1])' in MAIN
    start = MAIN.index("private void handleTestVisuals")
    end = MAIN.index("private void startCreativeTest", start)
    visuals = MAIN[start:end]
    for marker in (
        "clientVisualId",
        "entity.getUniqueId()",
        "clientVisual=",
        "resource=assets/copimineclient/textures/entity/",
        "bossPhase=",
        "bossBinding=",
        "official phase/roster/victory не изменены",
    ):
        assert marker in visuals


def test_full_local_boss_visual_probe_is_bounded_and_runs_all_required_checks() -> None:
    script_path = ROOT / "tests/RunEndRiftBossVisualLive.ps1"
    assert script_path.is_file()
    script = script_path.read_text(encoding="utf-8")
    for marker in (
        "environment:\\s*local",
        "server-port=25566",
        "rcon\\.port=25576",
        "codex/end-rift-event",
        "local-runtime",
        "RunEndRiftVisualFivePlayerLive.ps1",
        "RunEndRiftPerformanceFivePlayerLive.ps1",
        "RunEndRiftDiagnosticsFailureLive.ps1",
        "BOSS_VISUAL_CUE",
        "PORTAL_VISUAL_LAYER",
        "ZONE_VISUAL_STATE",
        "FINAL_ARENA_SCENE",
        "FINAL_ARENA_CLEANUP",
        "RUNTIME_DIAGNOSTICS",
        "PERF_PASS",
        "Get-FileHash",
        "finally",
        "cmend wave clear",
        "cmend boss kill cleanup",
    ):
        assert marker in script
    assert "25565" not in script
    assert "localhost" not in script.lower()
    assert "production" not in script.lower()


def test_local_final_scene_probe_is_display_only_and_has_an_explicit_clear_path() -> None:
    start = MAIN.index("private void handleTestScene")
    end = MAIN.index("private void handleTestDiagnosticsFailure", start)
    scene = MAIN[start:end]
    for marker in (
        '"local".equalsIgnoreCase(config.environment())',
        "startFinalArenaScene(scene)",
        "renderFinalArenaScene(scene, liveBoss(), elapsedTicks[0])",
        "finalArenaSceneTestTask",
        '"clear".equals(requested)',
        'clearFinalArenaScene("local scene test clear")',
        "display_only=true",
    ):
        assert marker in scene
    assert "setType(" not in scene
