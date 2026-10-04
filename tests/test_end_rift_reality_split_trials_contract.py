from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def section(source: str, start: str, end: str) -> str:
    left = source.index(start)
    return source[left : source.index(end, left)]


def test_wave_seven_uses_generation_scoped_trial_completion_not_generic_mob_clear():
    source = EVENT.read_text(encoding="utf-8")
    start_wave = section(source, "private boolean spawnWaveForObjectiveInternal", "private WaveMechanicsPolicy.WaveCounts ritualWaveCounts")
    chamber_tick = section(source, "private void tickCurrentChamberObjective", "private int countLiveWaveEntitiesForChamber")

    assert "private final RealitySplitTrialController realitySplitTrialController" in source
    assert "realitySplitTrialController.begin(generation" in start_wave
    assert "startRealitySplitTrials(world, core)" in start_wave
    assert "wave == 7" in start_wave
    assert "realitySplitTrialController.trial(chamber)" in chamber_tick
    assert "countLiveWaveEntitiesForChamber(chamber)" not in chamber_tick
    assert "wave6Complete = true" not in chamber_tick
    assert "realitySplitTrialController.allComplete(generation)" in chamber_tick


def test_wave_seven_has_four_distinct_trial_runtime_paths_and_scoped_cleanup():
    source = EVENT.read_text(encoding="utf-8")
    controller = (
        ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
        / "runtime" / "RealitySplitTrialController.java"
    ).read_text(encoding="utf-8")
    assert "RIFT_SPLIT_TRIAL" in source
    for trial in ("WARDEN", "RIFT_REFLECTION", "JUGGERNAUT", "RIFT_HUNTER"):
        assert trial in controller
        assert f"case {trial}" in source
    assert "keyGeneration" in source
    assert "keyChamberId" in source
    assert "realitySplitTrialController.clear()" in source
    assert "clearRealitySplitTrialEntities" in source
    assert "realitySplitChamberController.openBoundary(generation" in source


def test_reflection_and_juggernaut_progress_require_server_verified_actions():
    source = EVENT.read_text(encoding="utf-8")
    compact = re.sub(r"\s+", "", source)
    assert re.search(r"hitReflectionSeal\(generation,chamber,seal,true\)", compact)
    assert "projectile.reflected()" in source
    assert re.search(r"hitJuggernautAnchor\(generation,state.chamber,trial.progress\(\),true\)", compact)
    assert "mayDamageJuggernaut(generation, chamber)" in source
    assert "distanceSquaredToSegment(anchor.getLocation().toVector()" in source


def test_reflection_is_visibly_an_eye_and_hunter_hunts_between_attacks():
    source = EVENT.read_text(encoding="utf-8")
    spawn = section(source, "private boolean ensureRealitySplitTrialRoom", "private Entity spawnRealitySplitTrialEntity")
    trial_spawn = section(source, "private Entity spawnRealitySplitTrialEntity", "private Location realitySplitTrialObjectiveLocation")
    hunter = section(source, "private void tickRealitySplitHunter", "private void applyRealitySplitDirectionalDamage")

    assert "case RIFT_REFLECTION -> EntityType.ARMOR_STAND" in spawn
    assert "new ItemStack(Material.ENDER_EYE)" in trial_spawn
    assert "setVisible(false)" in trial_spawn
    assert "moveRealitySplitHunterTowardTarget(hunter, target)" in hunter
    assert "isRealitySplitDestinationAllowed(target.getUniqueId(), next, assignment)" in source
    assert "isSafeCombatEntityLocation(next, hunter" in source


def test_reflection_trial_initializes_its_first_fireball_deadline_once():
    source = EVENT.read_text(encoding="utf-8")
    reflection = section(
        source,
        "private void tickRealitySplitReflection",
        "private void tickRealitySplitJuggernaut",
    )

    assert "realitySplitTrialNextFireballTick.computeIfAbsent(" in reflection
    assert "ignored -> eventTickCounter + 24L" in reflection
    assert "eventTickCounter + REALITY_SPLIT_REFLECTION_INTERVAL_TICKS" in reflection


def test_wave7_reflection_seals_are_elevated_clear_of_the_hostile_fireball_lane():
    source = EVENT.read_text(encoding="utf-8")
    room = section(source, "private boolean ensureRealitySplitTrialRoom", "private Entity spawnRealitySplitTrialEntity")
    seal_location = section(
        source,
        "private Location realitySplitReflectionSealLocation",
        "private Location realitySplitTrialObjectiveLocation",
    )

    assert "realitySplitReflectionSealLocation(state.chamber(),seal)" in re.sub(
        r"\s+", "", room
    )
    assert "REALITY_SPLIT_REFLECTION_SEAL_LIFT_BLOCKS" in seal_location
    assert "REALITY_SPLIT_REFLECTION_SEAL_ANGLE_STEP_RADIANS" in seal_location
    assert "center+(index-2.0D)*REALITY_SPLIT_REFLECTION_SEAL_ANGLE_STEP_RADIANS" in re.sub(
        r"\s+", "", seal_location
    )
    assert re.search(
        r"REALITY_SPLIT_REFLECTION_SEAL_LIFT_BLOCKS\s*=\s*3\.0?D",
        source,
    )
    assert re.search(
        r"REALITY_SPLIT_REFLECTION_SEAL_ANGLE_STEP_RADIANS\s*=\s*0\.12D",
        source,
    )


def test_wave_seven_reflected_fireballs_run_through_the_authoritative_sweep():
    source = EVENT.read_text(encoding="utf-8")
    trial_tick = section(
        source,
        "private void tickRealitySplitTrialRuntimes",
        "private void tickRealitySplitWarden",
    )
    projectile_sweep = section(
        source,
        "private void tickRiftFireballs",
        "public void onRiftFireballReflect",
    )

    assert "tickRiftFireballs();" in trial_tick
    assert "activeWave7Reflection" in projectile_sweep


def test_wave7_chamber_completion_log_reports_the_count_required_by_the_live_gate():
    source = EVENT.read_text(encoding="utf-8")
    completion = section(source, "END_RIFT_CHAMBERS_COMPLETE event=", "if (checkpoint)")

    assert '" chambers=" + realitySplitChamberController.assignment().chamberCount()' in completion


def test_wave7_barrier_plan_is_a_required_start_resource_and_final_seal_core_stays_clear():
    source = EVENT.read_text(encoding="utf-8")
    canonical = section(
        source,
        "private WaveObjectiveStartResult startCanonicalObjective",
        "private WaveObjectiveStartResult startWaveObjective",
    )
    barriers = section(
        source,
        "private boolean spawnRealitySplitBarriers",
        "private void openRealitySplitBoundary",
    )
    policy = (
        ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
        / "domain" / "RealitySplitBarrierPolicy.java"
    ).read_text(encoding="utf-8")

    assert "started = spawnRealitySplitBarriers(world, core);" in canonical
    assert 'if (!clearRealitySplitBarriers("wave7-rebuild"))' in barriers
    assert "coreClearance = finalSealBarrierContext()" in barriers
    assert "return false;" in barriers
    assert "return true;" in barriers
    assert "cellsForClosedBoundary(" in barriers
    assert "centralJoin" in policy
    assert "excluded.size() < boundaryCount(count)" in policy
    assert "result.addAll(centralJoin)" in policy


def test_disposable_wave_seven_restores_trial_runtime_without_changing_event_phase():
    source = EVENT.read_text(encoding="utf-8")
    policy = (
        ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
        / "domain" / "RealitySplitRuntimePolicy.java"
    ).read_text(encoding="utf-8")
    compact = re.sub(r"\s+", "", source)

    assert "phase == EventPhase.WAVE_7" in policy
    assert "disposableTestWave && phase == EventPhase.COLLECTING" in policy
    assert "allowsRuntime(activeWave,phase,testWaveFrontVisualMode)" in compact
    assert "if (isRealitySplitTrialRuntimeActive())" in source
    assert "phase = EventPhase.WAVE_7" not in section(
        source, "private boolean isRealitySplitTrialRuntimeActive", "private boolean startRealitySplitTrials"
    )
    for method in (
        "private boolean startRealitySplitTrials",
        "private void finishRealitySplitTrial",
        "private void tickRealitySplitTrialRuntimes",
        "private boolean isCurrentRealitySplitTrialTarget",
        "private boolean isRealitySplitReflectionProjectile",
    ):
        start = source.index(method)
        body = source[start : source.index("\n    private ", start + len(method))]
        assert "isRealitySplitTrialRuntimeActive()" in body


def test_wave_seven_trial_objectives_search_the_chamber_not_two_blocks_from_core():
    source = EVENT.read_text(encoding="utf-8")
    objective = section(
        source,
        "private Location realitySplitTrialObjectiveLocation",
        "private Entity findRealitySplitTrialEntity",
    )

    assert "MIN_WAVE_CORE_DISTANCE_BLOCKS, chamber, null)" in objective
    assert "boundedCombatRadius(config.arenaRadius())" in objective
    assert "findSafeCombatLocation(anchor, preferred, 2.0D" not in objective


def test_wave7_objective_locations_use_playable_feet_anchor_not_core_top():
    source = EVENT.read_text(encoding="utf-8")
    room = section(
        source,
        "private boolean ensureRealitySplitTrialRoom",
        "private void logRealitySplitTrialComponentFailure",
    )
    reflection = section(
        source,
        "private Location realitySplitReflectionSealLocation",
        "private Location realitySplitTrialObjectiveLocation",
    )
    juggernaut = section(
        source,
        "private Location realitySplitTrialObjectiveLocation",
        "private Entity findRealitySplitTrialEntity",
    )

    assert "realitySplitReflectionSealLocation(state.chamber(),seal)" in re.sub(
        r"\s+", "", room
    )
    assert "realitySplitTrialObjectiveLocation(state.chamber(),anchor," in re.sub(
        r"\s+", "", room
    )
    assert "Location anchor = coreCombatAnchorLocation();" in reflection
    assert "Location anchor = coreCombatAnchorLocation();" in juggernaut


def test_disposable_local_wave_seven_uses_assigned_players_instead_of_stale_reward_roster():
    source = EVENT.read_text(encoding="utf-8")
    participant = section(
        source,
        "private boolean isRealitySplitTrialParticipant",
        "private void finishTrialAttack",
    )
    targets = section(
        source,
        "private List<Player> realitySplitTrialTargets",
        "private boolean isCurrentRealitySplitTrialTarget",
    )
    directional = section(
        source,
        "private void applyRealitySplitDirectionalDamage",
        "private Player closestRealitySplitTrialTarget",
    )
    closest = section(
        source,
        "private Player closestRealitySplitTrialTarget",
        "private boolean isCurrentRealitySplitTrialTarget",
    )

    assert "testWaveFrontVisualMode && activeWave == 7" in participant
    assert '"local".equalsIgnoreCase' in participant
    assert "allowsPlayerInChamber" in participant
    assert "Bukkit.getOnlinePlayers().stream()" in targets
    assert "isCurrentRealitySplitTrialTarget(source, player)" in targets
    assert "realitySplitTrialTargets(attacker)" in directional
    assert "realitySplitTrialTargets(source).stream()" in closest
    assert "activeBossParticipants()" not in directional + closest


def test_live_wave_seven_clients_can_fight_and_reflect_in_their_assigned_room():
    bot = (ROOT / "tests" / "LocalEndRiftMobCombatBot.js").read_text(encoding="utf-8")
    runner = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")

    assert "['spider', 'enderman', 'skeleton', 'warden', 'ravager', 'vex']" in bot
    assert "|| !sameWave7Chamber(entity)) return false" in bot
    assert "['skeleton', 'warden'].includes(entity.name)" in bot
    assert "function fireRangedTarget(target)" in bot
    assert "fireRangedTarget(rangedTarget)" in bot
    assert "function isGlowingWave7Seal(entity)" in bot
    assert "const activeSeal = activeWave7ReflectionSeal()" in bot
    assert "selectReflectionAimTarget({" in bot
    assert "lookAtServer(aimTarget.position)" in bot
    assert "activeSeal.position.offset(0, 1, 0)" in (ROOT / "tests" / "EndRiftReflectionTarget.js").read_text(encoding="utf-8")
    reflection = section(bot, "async function reflectFireball", "function scheduleFireballReflection")
    assert "setTimeout(resolve, 75)" not in reflection
    assert reflection.index("lookAtServer(aimTarget.position)") < reflection.index("bot._client.write('use_entity'")
    navigation = section(bot, "function navigateWave7", "function sampleMobs")
    assert "if (wave7Autopilot && wave7HoldPosition)" in navigation
    assert "if (navigationTarget) navigateWave7(navigationTarget)" in bot
    assert "$env:END_RIFT_REFLECT_ENABLED = '1'" in runner
    assert "$env:END_RIFT_REFLECT_DIAGNOSTICS = '1'" in runner
    assert "Remove-Item Env:END_RIFT_REFLECT_ENABLED" in runner
    assert "Remove-Item Env:END_RIFT_REFLECT_DIAGNOSTICS" in runner
    assert "$env:END_RIFT_BOT_WAVE7_HOLD_POSITION = '1'" in runner
    assert "Remove-Item Env:END_RIFT_BOT_WAVE7_HOLD_POSITION" in runner
