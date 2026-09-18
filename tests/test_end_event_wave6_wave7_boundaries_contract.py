from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
SRC = PLUGIN / "src/me/copimine/endevent"
DOMAIN = SRC / "domain"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_wave6_live_objective_is_ritual_sphere_with_exact_server_policy() -> None:
    config = read(PLUGIN / "config.yml")
    objective = read(DOMAIN / "EndRiftObjective.java")
    root = read(SRC / "CopiMineEndEvent.java")
    scaling = read(DOMAIN / "RitualSphereScalingPolicy.java")
    health = read(DOMAIN / "RitualPrisonerHealthPolicy.java")
    assert "  wave-6:\n    type: RITUAL_SPHERE" in config
    assert "case 6 -> Objective.RITUAL_SPHERE" in objective
    assert "WAVE6_RITUAL_SPHERE_READY" in root
    assert "startRitualSphereObjective(world, core);" in root
    assert "tickCurrentRitualSphereObjective(now);" in root
    assert "RITUAL_SPHERE_ZONE_SIZE = 4" in root
    assert "RitualSphereEncounterSnapshot" in root
    assert "DRAIN_INTERVAL_MILLIS = 20_000L" in health
    assert "DRAIN_HEALTH = 2.0D" in health
    assert "MIN_HEALTH = 1.0D" in health
    ritual_start = root.index("private boolean startRitualSphereObjective")
    ritual_end = root.index("private void restorePersistedRitualSphereObjective", ritual_start)
    ritual_body = root[ritual_start:ritual_end]
    assert "Location combatCore = coreCombatAnchorLocation();" in ritual_body
    assert "if (combatCore != null) {" in ritual_body
    assert "core = combatCore;" in ritual_body
    for marker in (
        "new Profile(count, 4, 12, 1, 1, count == 2 ? 0 : 1, 13)",
        "new Profile(count, 4, 12, 2, 1, 1, 12)",
        "new Profile(count, 5, 15, 3, 2, 2, 11)",
        "new Profile(count, 5, 15, 4, 2, 2, 10)",
        "new Profile(count, 6, 18, 5, 3, 3, 9)",
    ):
        assert marker in scaling


def test_wave6_legacy_collapse_rings_are_not_a_live_execution_path() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private boolean startCanonicalObjective")
    end = root.index("private void startWaveObjective", start)
    start_body = root[start:end]
    assert "case COLLAPSE_RINGS -> getLogger().warning(\"WAVE6_LEGACY_COLLAPSE_RING_REFUSED" in start_body
    assert "case RITUAL_SPHERE ->" in start_body
    assert "startRitualSphereObjective(world, core);" in start_body

    tick_start = root.index("private boolean tickCurrentObjective")
    tick_end = root.index("private boolean tickWaveObjective", tick_start)
    tick_body = root[tick_start:tick_end]
    assert "case RITUAL_SPHERE -> tickCurrentRitualSphereObjective(now);" in tick_body
    assert "WAVE6_LEGACY_COLLAPSE_RING_NOT_TICKED" in tick_body

    render_start = root.index("private void renderWaveObjective")
    render_end = root.index("/** Render the same three radii", render_start)
    render_body = root[render_start:render_end]
    assert "renderCurrentRitualSphere(core, now);" in render_body
    assert "renderCurrentCollapseRings(core, now);" not in render_body

    containment_start = root.index("private CollapseRingEncounterPolicy.State activeCollapseRingForContainment")
    containment_end = root.index("private boolean collapseRingPlayerAssigned", containment_start)
    containment_body = root[containment_start:containment_end]
    assert "Objective.COLLAPSE_RINGS" in containment_body
    assert "activeWave == 6" in containment_body


def test_wave6_ritual_spawn_failure_is_transactional_and_diagnostic() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private boolean startRitualSphereObjective")
    end = root.index("private Location ritualSphereCenter", start)
    body = root[start:end]
    assert "placeRitualEntity(caster, casterLocation, \"CASTER\", casterSlot, -1)" in body
    assert "placeRitualEntity(guard, guardLocation, \"GUARD\", casterSlot, guardSlot)" in body
    assert "WAVE6_RITUAL_ENTITY_PLACEMENT_FAILED" in root
    placement_start = root.index("private boolean placeRitualEntity")
    placement_end = root.index("private Location ritualSphereCenter", placement_start)
    placement_body = root[placement_start:placement_end]
    assert "removeRitualEntity(entity.getUniqueId())" in placement_body
    assert "reason=spawn-refused" in placement_body
    assert "reason=no-safe-destination" in placement_body
    assert "reason=teleport-refused" in placement_body
    assert "ritualSphereVisualUuid == null" in body
    assert "!isLiveOwnedEntity(ritualSphereVisualUuid)" in body
    assert "waveObjectiveStartedMillis = 0L;" in body
    assert "waveObjectiveLastSecond = -1;" in body

    capture_start = root.index("private void attemptRitualPrisonerCapture")
    capture_end = root.index("private void ensureRitualPrisoner", capture_start)
    capture_body = root[capture_start:capture_end]
    assert "ritualSphereVisualUuid == null" in capture_body
    assert "!isLiveOwnedEntity(ritualSphereVisualUuid)" in capture_body


def test_wave6_failed_start_cannot_emit_started_marker_or_advance_objective() -> None:
    root = read(SRC / "CopiMineEndEvent.java")

    assert "private boolean startCanonicalObjective" in root
    assert "private boolean startRitualSphereObjective" in root

    canonical_start = root.index("private boolean startCanonicalObjective")
    canonical_end = root.index("private void startWaveObjective", canonical_start)
    canonical_body = root[canonical_start:canonical_end]
    marker = 'getLogger().info("WAVE_OBJECTIVE_STARTED'
    failure_guard = "if (!started) {"
    assert "started = startRitualSphereObjective(world, core);" in canonical_body
    assert failure_guard in canonical_body
    assert canonical_body.index(failure_guard) < canonical_body.index(marker)
    assert "return false;" in canonical_body[canonical_body.index(failure_guard):canonical_body.index(marker)]

    ritual_start = root.index("private boolean startRitualSphereObjective")
    ritual_end = root.index("private Location ritualSphereCenter", ritual_start)
    ritual_body = root[ritual_start:ritual_end]
    invalid_start = ritual_body.index(
        "if (world == null || core == null || generation <= 0L"
    )
    invalid_end = ritual_body.index(
        'clearRitualSphereObjective("new-start")', invalid_start
    )
    invalid_context = ritual_body[invalid_start:invalid_end]
    for marker_name in (
        "waveObjectiveStartedMillis = 0L;",
        "waveObjectiveLastSecond = -1;",
        "waveObjectiveMobCount = 0;",
        "waveObjectiveComplete = false;",
    ):
        assert marker_name in invalid_context
    assert "return false;" in invalid_context

    tick_start = root.index("private boolean tickCurrentObjective")
    tick_end = root.index("private boolean tickWaveObjective", tick_start)
    tick_body = root[tick_start:tick_end]
    retry_start = tick_body.index("if (waveObjectiveStartedMillis <= 0L)")
    now_offset = tick_body.index("long now = System.currentTimeMillis();", retry_start)
    retry_body = tick_body[retry_start:now_offset]
    start_call = "startCanonicalObjective(objectiveWave, world, core)"
    assert start_call in retry_body
    assert "return false;" in retry_body
    assert retry_body.index("return false;") > retry_body.index(
        start_call
    )


def test_wave7_has_one_block_journaled_boundaries_and_restore_paths() -> None:
    policy = read(DOMAIN / "RealitySplitBarrierPolicy.java")
    journal = read(SRC / "HazardMutationJournal.java")
    root = read(SRC / "CopiMineEndEvent.java")
    live_script = read(ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1")
    assert "HEIGHT = 5" in policy
    assert "MIN_RADIUS = 0.5D" in policy
    assert "MAX_RADIUS = 32.0D" in policy
    assert "MAX_CELLS = 1536" in policy
    assert "boundaryForPair" in policy
    assert "WALL_HALF_WIDTH = 0" in policy
    assert "REALITY_SPLIT_BARRIER" in journal
    assert "spawnRealitySplitBarriers(world, core)" in root
    assert "restoreRealitySplitBarriersAfterBootstrap()" in root
    assert "clearRealitySplitBarriers(\"wave-objective-reset\")" in root
    assert "openRealitySplitBoundary(" in root
    assert "Material.BARRIER" in root
    assert "REALITY_SPLIT_WALL_MATERIAL = Material.BARRIER" in root
    assert "value.setBlock(Material.AMETHYST_BLOCK.createBlockData())" in root
    assert "isRealitySplitBarrierBlock" in root
    assert ".setType(REALITY_SPLIT_WALL_MATERIAL, false)" in root
    assert "journaled=true" in root
    assert "localChamberRoster" in root
    assert "minecraft:barrier" in live_script
    assert "wall_material=barrier" in live_script
    assert "LIVE_WAVE7_ONE_BLOCK_WALL_PASS" in live_script
    assert "LIVE_WAVE7_COMMAND_CLEANUP_PASS" in live_script
    assert "LIVE_WAVE7_NATURAL_COMPLETION_CLEANUP_PASS" in live_script
    assert "LIVE_WAVE7_RESTART_RECOVERY_PASS" in live_script
    assert "-Action {" not in live_script
    assert "Join-Path $runtimeRoot 'end-rift.env'" in live_script
    assert "Join-Path $runtimeRoot 'local.env'" not in live_script
    assert "Wait-Log-MarkerIncrease" in live_script
    assert "BeforeLength" in live_script
    assert "restartLogLengthBefore" in live_script
    assert "weapon.mainhand with minecraft:netherite_sword" in live_script
    assert "minecraft:instant_health 1 10 true" in live_script
    assert " 20 250'" in live_script
    assert "function Restart-Bots" in live_script
    assert "Wait-BotsOffline -Names $Names" in live_script
    assert "Restart-Bots -Names $names -Core $core" in live_script
    assert "function Clear-LocalArenaAmbientMobs" in live_script
    ambient_start = live_script.index("function Clear-LocalArenaAmbientMobs")
    ambient_end = live_script.index("function Wait-BotsOnline", ambient_start)
    ambient_body = live_script[ambient_start:ambient_end]
    assert "execute positioned" in ambient_body
    for mob_type in ("minecraft:spider", "minecraft:enderman", "minecraft:skeleton"):
        assert f"'{mob_type}'" in ambient_body
    assert "type=$mobType,distance=..32" in ambient_body
    assert "function Set-LocalArenaMobSpawning" in live_script
    assert "gamerule doMobSpawning false" in live_script
    assert "previousLocalMobSpawning" in live_script
    wave6_start = live_script.index("$wave6Offset = Log-Length")
    assert "Set-LocalArenaMobSpawning -Enabled $false" in live_script[:wave6_start]
    finally_start = live_script.index("finally {")
    assert "Set-LocalArenaMobSpawning -Enabled ($previousLocalMobSpawning -eq 'true')" in live_script[finally_start:]
    assert "Clear-LocalArenaAmbientMobs -Core $core" in live_script[:wave6_start]
    wave7_start = live_script.index("$wave7Offset = Log-Length")
    assert "Clear-LocalArenaAmbientMobs -Core $core" in live_script[wave6_start:wave7_start]
    restart_start = live_script.index("$restartLogLengthBefore = Log-Length")
    assert "Clear-LocalArenaAmbientMobs" not in live_script[restart_start:]
    configure_start = live_script.index("function Configure-Bot")
    configure_end = live_script.index("function Wait-BotsOnline", configure_start)
    configure_body = live_script[configure_start:configure_end]
    assert "[switch]$SkipTeleport" in configure_body
    assert "attribute $name minecraft:generic.knockback_resistance base set 1" in configure_body
    assert "minecraft:item replace entity $name weapon.mainhand with minecraft:netherite_sword" in configure_body
    assert "minecraft:item replace entity $name hotbar.1 with minecraft:bow" in configure_body
    assert "give $name minecraft:arrow 64" in configure_body
    assert "data get entity $name SelectedItem" in configure_body
    assert "Boundary probe weapon setup failed" in configure_body
    assert "attribute $name minecraft:generic.attack_damage base set 1" in configure_body
    assert "function Wait-BotLoginSettle" in live_script
    assert live_script.count("Wait-BotLoginSettle") >= 3
    assert "function Wait-Until" in live_script
    assert "Wait-BotLoginSettle -Names $Names -AfterOffset $authOffset" in live_script
    assert "Wait-BotLoginSettle -Names $names -AfterOffset $restartAuthOffset" in live_script
    assert "function Get-BotUuid" in live_script
    assert "function Get-PositivePlayerDamageLedger" in live_script
    assert "LIVE_WAVE7_PLAYER_DAMAGE_LEDGER_PASS" in live_script
    assert "LIVE_WAVE7_CLEANUP_ZERO_STATE_PASS" in live_script
    assert "$cleanupFailures =" in live_script
    assert "catch { }" not in live_script
    assert "Start-Sleep -Seconds" not in live_script
    restart_start = live_script.index("Start-LocalMinecraft")
    restart_body = live_script[restart_start:]
    assert "Configure-Bot -Name $name -Core $core -SkipTeleport" in restart_body
    join_start = root.index("public void onPlayerJoin")
    join_end = root.index("public void onPlayerQuit", join_start)
    join_body = root[join_start:join_end]
    assert "teleportRealitySplitPlayerToChamberCenter" in join_body
    assert "reconnect-center-retry" in root
    center_start = root.index("private void teleportRealitySplitPlayerToChamberCenter")
    center_end = root.index("/**\n     * Keep every live Wave 7 participant", center_start)
    center_body = root[center_start:center_end]
    assert "100L" in center_body
    contain_start = root.index("private void containRealitySplitParticipant")
    contain_end = root.index("/**\n     * Walking, jumping and flight movement", contain_start)
    contain_body = root[contain_start:contain_end]
    assert "realitySplitChamberCenter" in contain_body
    leash_start = root.index("private void enforceCombatLeash")
    leash_end = root.index("private PotionEffectType narcoticPotionEffect", leash_start)
    leash_body = root[leash_start:leash_end]
    assert "int chamberForLeash = chamber >= 0" in leash_body
    assert "realitySplitLeashPreferred" in leash_body
    assert "findSafeCombatLocation(anchor, preferred, radius - 0.75D, minCoreDistance,\n                chamberForLeash, entity)" in leash_body
    assert '(chamberForLeash >= 0 ? " chamber=" + chamberForLeash : "")' in leash_body
    leash_preferred_start = root.index("private Location realitySplitLeashPreferred")
    leash_preferred_end = root.index("private void teleportRealitySplitPlayerToChamberCenter", leash_preferred_start)
    leash_preferred_body = root[leash_preferred_start:leash_preferred_end]
    assert "realitySplitChamberCombatPoint" in leash_preferred_body
    assert "mob.getTarget() instanceof Player target" in leash_preferred_body
    assert "realitySplitTargetAllowed(entity, target)" in leash_preferred_body
    assert "target.getLocation()" in leash_preferred_body
    target_start = root.index("private boolean realitySplitTargetAllowed")
    target_end = root.index("private Location constrainRealitySplitPreferred", target_start)
    target_body = root[target_start:target_end]
    assert "realitySplitLocalTargetAllowed" in target_body
    assert "END_RIFT_BOT_WAVE7_AUTOPILOT" in live_script
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "wave7AutopilotDefault" in bot
    assert "enterActiveMode(wave7AutopilotDefault)" in bot
    assert "if (!controlFile) enterActiveMode(wave7AutopilotDefault)" in bot
    assert "findWalkablePath" in bot
    assert "navigationPath" in bot
    assert "navigationRecoveryUntil" in bot
    assert "block.boundingBox === 'empty'" in bot
    assert "sameWave7ChamberPoint" in bot
    assert "wave7NavigationRadius" in bot
    assert "isWave7NavigationPoint" in bot
    assert "heldItemSyncSent" in bot
    assert "bot._client.write('held_item_slot'" in bot
    assert "slotId: 1" in bot
    assert "slotId: 0" in bot
    assert "END_RIFT_BOUNDARY_SYNC_HELD_ITEM" in bot
    assert "END_RIFT_BOUNDARY_SYNC_HELD_ITEM" in live_script
    assert "function Sync-BotsHeldItem" in live_script
    assert "wave7Autopilot && navigationPath.length === 0" in bot
    assert "bot.setControlState('jump', Boolean(" in bot
    assert "|| (waypoint && waypoint.y > Math.floor(currentPosition.y))" in bot
    assert "AI_WAVE7_STATE" in root
    assert "pdc_chamber" in root
    assert "target_distance" in root
    mob_path_start = root.index("private void maintainWaveMobPath")
    mob_path_end = root.index("private boolean requestBoundedCombatMovement", mob_path_start)
    mob_path_body = root[mob_path_start:mob_path_end]
    fallback_start = mob_path_body.index("if (destination == null || isCoreBlockPosition(destination))")
    fallback_body = mob_path_body[fallback_start:]
    assert "MIN_WAVE_CORE_DISTANCE_BLOCKS, realitySplitChamberId(mob), mob);" in fallback_body
    location_start = root.index("Location resolved = new Location", root.index("private Location findSafeCombatLocation"))
    location_end = root.index("BossMovementPolicy.Candidate policyCandidate", location_start)
    resolved_location_body = root[location_start:location_end]
    assert "ChamberIsolationPolicy.containsPoint(" in resolved_location_body
    assert "resolved.getX() - center.getX()" in resolved_location_body
    assert "resolved.getZ() - center.getZ()" in resolved_location_body


def test_wave6_boundary_harness_captures_a_real_prisoner_before_waiting_for_drain() -> None:
    live_script = read(ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1")
    wave6_start = live_script.index("$wave6Offset = Log-Length")
    wave7_start = live_script.index("$wave7Offset = Log-Length", wave6_start)
    wave6_body = live_script[wave6_start:wave7_start]
    capture = wave6_body.index("WAVE6_RITUAL_PRISONER_CAPTURED")
    drain = wave6_body.index("WAVE6_RITUAL_PRISONER_DRAIN")
    assert "Teleport-Player -Name $SecondBotName -X ($core[0] + 0.5D)" in wave6_body
    assert "-Z ($core[2] + 0.5D)" in wave6_body
    assert "Wait-Log -AfterOffset $wave6Offset" in wave6_body[:capture]
    assert capture < drain
    assert "first_drain_at" in wave6_body[:drain]
    assert "$prisonerMatch = [Regex]::Match($captureLog" in wave6_body
    assert "$prisonerMatch = [Regex]::Match($ritualLog" not in wave6_body


def test_wave7_collision_and_visual_layers_are_separate() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    policy = read(DOMAIN / "RealitySplitBarrierPolicy.java")
    assert "WALL_HALF_WIDTH = 0" in policy
    assert ".setType(REALITY_SPLIT_WALL_MATERIAL, false)" in root
    assert "value.setBlock(Material.AMETHYST_BLOCK.createBlockData())" in root
    assert "world border" not in root.lower()


def test_disposable_wave_completion_does_not_require_official_reward_roster() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private void tickWaveCompletion")
    end = root.index("private boolean spawnWaveCompletionLoot", start)
    body = root[start:end]
    assert "boolean disposableWave = testWaveFrontVisualMode && !isOfficialAttempt();" in body
    assert "if (!disposableWave && !spawnWaveCompletionLoot(completedWave)) {" in body
    assert "DISPOSABLE_WAVE_NATURAL_COMPLETE" in body


def test_event_wave_mobs_are_protected_from_wall_and_sun_environment_damage() -> None:
    root = read(SRC / "CopiMineEndEvent.java")

    sun_start = root.index("public void onEventEndermanSunDamage")
    sun_end = root.index("private Player playerDamageAttacker", sun_start)
    sun_body = root[sun_start:sun_end]
    assert "DamageCause.FIRE_TICK" in sun_body
    assert "ownedEntities.containsKey(enderman.getUniqueId())" in sun_body
    assert "isWaveCombatKind(kind)" in sun_body
    assert "!isOfficialEntity(enderman)" not in sun_body
    assert "event.setCancelled(true)" in sun_body

    suffocation_start = root.index("public void onEventMobSuffocation")
    suffocation_end = root.index("public void onWaveMobPlayerDamageAuthoritative", suffocation_start)
    suffocation_body = root[suffocation_start:suffocation_end]
    assert "DamageCause.SUFFOCATION" in suffocation_body
    assert "ownedEntities.containsKey(entity.getUniqueId())" in suffocation_body
    assert "isWaveCombatKind(readString(entity, keyKind))" in suffocation_body
    assert "event.setCancelled(true)" in suffocation_body
    assert "findSafeCombatLocation" in suffocation_body
    assert "teleportCombatEntity" in suffocation_body


def test_wave7_player_teleport_permits_are_destination_bound_and_scoped() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    assert "Map<UUID, Location> realitySplitPlayerTeleportPermits" in root
    assert "issueRealitySplitPlayerTeleportPermit" in root
    assert "clearRealitySplitPlayerTeleportPermit" in root
    assert "sameTeleportDestination" in root

    for method_name in (
        "private void teleportCurrentParticipantsToChambers",
        "private void teleportRealitySplitPlayerToChamberCenter",
        "private void containRealitySplitParticipant",
    ):
        start = root.index(method_name)
        end = root.find("\n    private ", start + len(method_name))
        if end < 0:
            end = root.find("\n    /**", start + len(method_name))
        body = root[start:end]
        if "issueRealitySplitPlayerTeleportPermit(playerId, destination)" not in body:
            start = root.index(method_name, start + len(method_name))
            end = root.find("\n    private ", start + len(method_name))
            if end < 0:
                end = root.find("\n    /**", start + len(method_name))
            body = root[start:end]
        assert "issueRealitySplitPlayerTeleportPermit(playerId, destination)" in body
        assert "clearRealitySplitPlayerTeleportPermit(playerId, destination)" in body

    handler_start = root.index("public void onRealitySplitPlayerTeleport")
    handler_end = root.index("private boolean isRealitySplitPlayerRuntimeActive", handler_start)
    handler_body = root[handler_start:handler_end]
    assert "isRealitySplitPlayerTeleportPermitted(event)" in handler_body
    assert "realitySplitPlayerTeleportPermits.remove(playerId)" not in handler_body


def test_wave7_rejected_player_move_sends_a_server_position_correction() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    move_start = root.index("public void onRealitySplitPlayerMove")
    move_end = root.index("/**\n     * Scope an internal player teleport", move_start)
    move_body = root[move_start:move_end]
    assert "event.setTo(from)" in move_body
    assert "issueRealitySplitPlayerTeleportPermit(player.getUniqueId(), from)" in move_body
    assert "player.teleport(from)" in move_body
    assert "clearRealitySplitPlayerTeleportPermit(player.getUniqueId(), from)" in move_body


def test_wave7_rejected_move_recovers_when_from_is_already_outside_room() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    move_start = root.index("public void onRealitySplitPlayerMove")
    move_end = root.index("/**\n     * Scope an internal player teleport", move_start)
    move_body = root[move_start:move_end]
    # A knockback packet can make both PlayerMoveEvent locations invalid for
    # the closed room. Replaying that invalid `from` forever leaves the
    # client at the outer edge and prevents it from reaching its own mobs.
    assert "boolean fromAllowed = isRealitySplitDestinationAllowed(" in move_body
    assert "if (!fromAllowed)" in move_body
    assert 'containRealitySplitParticipant(player, generation, "move-recovery")' in move_body
    assert "event.setTo(player.getLocation())" in move_body


def test_wave7_inward_inner_boundary_move_recenters_inside_assigned_room() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    policy = read(SRC / "domain" / "RealitySplitPlayerTeleportPolicy.java")
    move_start = root.index("public void onRealitySplitPlayerMove")
    move_end = root.index("/**\n     * Scope an internal player teleport", move_start)
    move_body = root[move_start:move_end]
    assert "requiresInnerBoundaryRecovery" in policy
    assert "requiresBoundaryRecovery" in policy
    assert "requiresBoundaryRecovery" in move_body
    assert 'teleportRealitySplitPlayerToChamberCenter(\n                    player, generation, "inner-boundary-recovery")' in move_body
    assert "WAVE7_PLAYER_BOUNDARY_RECOVERED" in move_body
    assert "event.setTo(player.getLocation())" in move_body


def test_wave7_velocity_fallback_keeps_mobs_inside_their_assigned_chamber() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    helper_start = root.index("private boolean requestBoundedCombatMovement")
    helper_end = root.index("private boolean isSafeCombatStep", helper_start)
    helper_body = root[helper_start:helper_end]
    assert "int chamberId" in helper_body
    assert "isSafeCombatStep(anchor, next, radius, minimumCoreDistance, chamberId, mob)" in helper_body

    wave_path_start = root.index("private void maintainWaveMobPath")
    wave_path_end = root.index("private boolean requestBoundedCombatMovement", wave_path_start)
    wave_path_body = root[wave_path_start:wave_path_end]
    assert "MIN_WAVE_CORE_DISTANCE_BLOCKS,\n                    realitySplitChamberId(mob)," in wave_path_body

    stuck_start = root.index("private void watchWaveMobProgress")
    stuck_end = root.index("/**\n     * Give each event role", stuck_start)
    stuck_body = root[stuck_start:stuck_end]
    assert "MIN_WAVE_CORE_DISTANCE_BLOCKS,\n                    realitySplitChamberId(mob)," in stuck_body


def test_wave7_mob_destinations_use_full_hitbox_and_target_separation() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    policy = read(DOMAIN / "RealitySplitCombatSeparationPolicy.java")
    assert "RealitySplitCombatSeparationPolicy" in root
    assert "DEFAULT_MIN_SEPARATION = 1.75D" in policy
    assert "isSafeCombatEntityLocation" in root
    assert "isSafeCombatParticipantLocation" in root
    assert "entity.getWidth()" in root
    assert "entity.getHeight()" in root
    assert "realitySplitCombatPreferred(anchor, preferred, target, mob)" in root
    assert "realitySplitCombatPreferred(anchor, preferred, player, entity)" in root
    assert "findSafeCombatLocation(anchor, preferred,\n                waveMovementRadius(),\n                MIN_WAVE_CORE_DISTANCE_BLOCKS, realitySplitChamberId(mob), mob)" in root


def test_wave7_player_containment_rejects_wall_intersection() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    containment_start = root.index("private void containRealitySplitParticipant")
    containment_end = root.index("/**\n     * Walking, jumping", containment_start)
    containment_body = root[containment_start:containment_end]
    assert "isSafeCombatParticipantLocation(player.getLocation(), player)" in containment_body
    assert "isRealitySplitDestinationAllowed(player.getUniqueId(), player.getLocation(), assignment)\n                && isSafeCombatParticipantLocation(player.getLocation(), player)" in containment_body
    assert "findSafeCombatLocation(anchor, preferred, radius,\n                MIN_WAVE_CORE_DISTANCE_BLOCKS, chamber, player)" in containment_body


def test_wave7_bot_serializes_aim_and_attack_against_navigation_race() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    # The probe used to await Mineflayer's asynchronous lookAt while its
    # navigation timer could send a second look packet. The server then
    # decoded a valid ATTACK for an in-range mob using the wrong server-side
    # view direction, so live damage disappeared without a plugin exception.
    assert "let meleeActionInFlight = false" in bot
    assert "if (meleeActionInFlight) return" in bot
    assert "lookAtServer(lookPoint)" in bot
    assert "lookAtServer(finalTarget.position.offset(0, 0.8, 0))" in bot
    assert "bot.look(yaw, pitch, true)" in bot
    assert "bot._client.write('look', {" in bot
    assert "yaw: Math.fround((Math.PI - yaw) * 180 / Math.PI)" in bot
    assert "pitch: Math.fround(-pitch * 180 / Math.PI)" in bot
    assert "bot._client.write('use_entity', {" in bot
    assert "hand: 0" in bot
    assert "bot._client.write('arm_animation', { hand: 0 })" in bot
    assert "fireRangedSkeleton" in bot
    assert "bot.activateItem()" in bot
    assert "bot.deactivateItem()" in bot
    assert "PLAYER_RANGED_ATTACK ${username}" in bot
    assert "if (bot.quickBarSlot !== 0)" in bot
    assert "bot.lookAt(" not in bot
    assert "const playerUuid = bot.entity?.uuid || bot.uuid || bot._client?.uuid || 'unknown'" in bot
    assert "PLAYER_JOIN ${username} uuid=${playerUuid}" in bot


def test_wave7_cleanup_reconciles_persisted_cells_even_when_live_maps_are_empty() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    start = root.index("private boolean clearRealitySplitBarriers")
    end = root.index("private boolean isRealitySplitBarrierBlock", start)
    body = root[start:end]
    assert "hazardJournal.load()" in body
    assert "isRealitySplitBarrierMutation()" in body
    assert "realitySplitBarrierCells.isEmpty()" in body
    assert "RESTORED" in body
    assert "hazardJournal.markRestored()" in root
    assert "restoreBlock(barrier, realitySplitBarrierOriginals.get(cell))" in root


def test_disposable_wave7_restart_state_is_explicitly_generation_bound() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    assert '"test-wave-generation"' in root
    assert '"test-wave", "7"' in root
    assert "persistedDisposableWave7" in root
    assert "testWaveFrontVisualMode && activeWave == 7" in root
    assert "END_RIFT_WAVE7_BARRIERS_REHYDRATED" in root
    assert "preserveWave7ForRestart" in root
    assert "cancelSessionTasks(preserveWave6ForRestart || preserveWave7ForRestart)" in root
    assert "cancelSessionTasks(boolean preserveCombatForRestart)" in root
    roster_start = root.index("List<UUID> localChamberRoster")
    roster_end = root.index("if (test)", roster_start)
    roster_body = root[roster_start:roster_end]
    assert "Bukkit.getOnlinePlayers().stream().filter(this::isCombatTarget)" in roster_body


def test_disposable_wave6_restart_state_preserves_the_ritual_snapshot() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    assert '"6".equals(snapshot.objectiveProgress().get("test-wave"))' in root
    assert "persistedDisposableWave6" in root
    assert "testWaveFrontVisualMode && activeWave == 6" in root
    assert "RitualSphereEncounterSnapshot.encode(ritualSphereState)" in root
    assert "restorePersistedRitualSphereObjective();" in root
    assert "END_RIFT_WAVE6_RESTART_PRESERVED" in root
    restore_start = root.index("private void restorePersistedRitualSphereObjective()")
    restore_end = root.index("private void cleanupLegacyWave6Entities()", restore_start)
    restore_body = root[restore_start:restore_end]
    assert "testWaveFrontVisualMode && activeWave == 6" in restore_body
    assert "WAVE6_RITUAL_REHYDRATED" in restore_body
    assert "WAVE6_RITUAL_RESTART_CONTINUED" in root
    assert "ritualSphereStateRehydrated" in root


def test_wave7_mini_boss_weakness_does_not_zero_normal_weapon_damage() -> None:
    root = read(SRC / "CopiMineEndEvent.java")
    snare_start = root.index("private void miniBossVoidSnare")
    snare_end = root.index("private void miniBossEchoPulse", snare_start)
    snare_body = root[snare_start:snare_end]
    echo_start = root.index("private void miniBossEchoPulse")
    echo_end = root.index("private void miniBossArrowSalvo", echo_start)
    echo_body = root[echo_start:echo_end]
    amplifier_start = root.index("private int abilityDebuffAmplifier")
    amplifier_end = root.index("/** Number used by bounded wave scaling", amplifier_start)
    amplifier_body = root[amplifier_start:amplifier_end]
    weakness_start = root.index("private int weaknessDebuffAmplifier")
    weakness_end = root.index("private int abilityDebuffAmplifier", weakness_start)
    weakness_body = root[weakness_start:weakness_end]

    # Weakness II is an eight-point native attack-damage penalty in Java
    # Edition. It made a normal weapon report zero damage in the live Wave 7
    # boundary probe, so the two mini-boss attacks must use the bounded level
    # rather than the generic movement/debuff amplifier.
    assert "weaknessDebuffAmplifier(\"mini-void-snare\")" in snare_body
    assert "weaknessDebuffAmplifier(\"mini-echo-pulse\")" in echo_body
    assert "private int weaknessDebuffAmplifier(String abilityId)" in root
    assert 'case "mini-void-snare", "mini-echo-pulse" -> 0;' in weakness_body
    assert 'case "mini-rift-step", "mini-void-snare", "mini-echo-pulse" -> 1;' in amplifier_body
