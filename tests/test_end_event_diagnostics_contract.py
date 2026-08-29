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
