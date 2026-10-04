from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _source() -> str:
    return EVENT.read_text(encoding="utf-8")


def test_official_wave_transition_preserves_rewards_and_still_cleans_pressure():
    source = _source()
    initialize = _method(source, "private boolean initializeWaveGameplay(")
    assert "clearWaveEntities(sandbox)" in initialize
    cleanup = _method(source, "private void clearWaveEntities(boolean includeRewards)")
    assert cleanup.count("isWaveCleanupKind(") == 3
    assert cleanup.count("includeRewards") == 3
    predicate = _method(source, "private boolean isWaveCleanupKind(String kind, boolean includeRewards)")
    assert "EVENT_KIND_WAVE_REWARD.equals(kind)" in predicate
    assert "includeRewards" in predicate
    assert "isWaveCleanupKind(kind)" in predicate


def test_failed_wave6_capture_is_saved_as_cancelled():
    source = _source()
    body = _block(source, 'if (wave == 6 && !"combat".equals(testMode)')
    assert body.index("activeWave = 0") < body.index("saveStateAsync()") < body.index("return;")


def test_failed_sandbox_wave7_rehydration_does_not_change_official_phase():
    source = _source()
    body = _method(source, "private void restoreRealitySplitBarriersAfterBootstrap(")
    sandbox = _block(body, "if (testWaveFrontVisualMode && activeWave == 7)")
    assert "clearWaveEntities()" in sandbox
    assert "activeWave = 0" in sandbox
    assert "saveStateAsync()" in sandbox
    assert "forcePhase" not in sandbox
    assert "else if (!finalSealBarrierContext())" in body


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated method: {signature}")


def _block(source: str, marker: str) -> str:
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unterminated block: {marker}")


def test_playable_wave_adapters_do_not_schedule_the_removed_wave_front():
    source = _source()
    sandbox = _method(source, "private void spawnWave(int wave, boolean test)")
    official = _method(source, "private boolean spawnWaveForObjectiveInternal(")
    gameplay = _method(source, "private boolean initializeWaveGameplay(")

    assert "startWaveFrontAnimation(" not in sandbox
    assert "startWaveFrontAnimation(" not in official
    assert "startWaveFrontAnimation(" not in gameplay
    assert source.count("updateCorePulseObjective(") == 1


def test_sandbox_wave7_starts_real_trials_without_generic_replacement_mobs():
    source = _source()
    sandbox = _method(source, "private boolean initializeWaveGameplay(")

    assert "else if (wave == 7)" in sandbox
    start = sandbox.index("WaveObjectiveStartResult objectiveStart")
    wave7 = _block(sandbox[start:], "else if (wave == 7)")
    assert "startRealitySplitTrials(world, core)" in wave7
    assert "waveSpawnGroupIndex = waveSpawnSchedule.size()" in wave7
    assert "spawnWaveGroup(world, core, wave, wave == MAX_EVENT_WAVE" not in wave7


def test_sandbox_wave7_initializes_trials_before_starting_them():
    source = _source()
    sandbox = _method(source, "private boolean initializeWaveGameplay(")
    compact = " ".join(sandbox.split())

    initialize = "realitySplitTrialController.begin(generation, realitySplitChamberController.assignment());"
    assert initialize in compact
    assert compact.index(initialize) < compact.index("startRealitySplitTrials(world, core)")


def test_portal_capture_is_sequential_in_both_playable_modes():
    source = _source()
    objective = _method(source, "private void updatePortalObjective(long now)")

    assert "boolean sequential = activeWave == 3;" in objective
    assert "isOfficialAttempt()" not in objective


def test_objective_start_failure_is_propagated_before_wave_spawn_continues():
    source = _source()
    start = _method(source, "private WaveObjectiveStartResult startWaveObjective(")
    gameplay = _method(source, "private boolean initializeWaveGameplay(")

    assert "return startCanonicalObjective(wave, world, core);" in start
    for body in (gameplay,):
        assert "WaveObjectiveStartResult objectiveStart = startWaveObjective(" in body
        assert "if (!objectiveStart.started())" in body


def test_wave_targets_and_objectives_use_arena_eligible_wave_players():
    source = _source()
    ai = _method(source, "private void tickWaveMobAi()")
    portal = _method(source, "private void updatePortalObjective(long now)")
    fog = _method(source, "private void applyBlackFogEffects(long now, Location core)")
    capture = _method(source, "private void attemptRitualPrisonerCapture(long now)")

    assert "private List<Player> activeWaveParticipants()" in source
    assert "filter(this::isActiveArenaParticipant)" in _method(
        source, "private List<Player> activeWaveParticipants()"
    )
    for body in (ai, portal, fog, capture):
        assert "activeWaveParticipants()" in body
        assert "activeLivingPlayers()" not in body


def test_authoritative_accepted_mob_damage_emits_one_hurt_animation():
    source = _source()
    damage = _method(source, "public void onWaveMobPlayerDamageAuthoritative(")

    assert "if (!result.applied())" in damage
    assert "victim.playHurtAnimation(" in damage
    assert damage.index("if (!result.applied())") < damage.index("victim.playHurtAnimation(")
    assert damage.index("victim.playHurtAnimation(") < damage.index("victim.setHealth(")


def test_wave5_safe_zone_failure_does_not_advance_to_fog():
    source = _source()
    tick = _method(source, "private void tickBlackFogObjective(long now)")
    safe_zones = _method(source, "private boolean spawnCurrentSafeZoneVisuals(")

    assert "if (!spawnCurrentSafeZoneVisuals(core.getWorld(), core))" in tick
    assert "blackFogPhase = BlackFogPhase.FOG;" in tick
    assert "tag(display, EVENT_KIND_DISPLAY, 5, true);" in safe_zones
    assert '"wave5-safe-zone-"' in source


def test_wave5_exhausted_safe_zone_setup_retires_the_whole_sandbox():
    tick = _method(_source(), "private void tickBlackFogObjective(long now)")
    failure_start = tick.index('if (currentFogZoneSetupAttempts >= 3)')
    failure = tick[failure_start:tick.index('blackFogPhase = BlackFogPhase.SAFE;', failure_start)]
    assert 'clearWaveEntities();' in failure
    assert 'clearWaveObjectiveState();' in failure
    assert 'activeWave = 0;' in failure and 'saveStateAsync();' in failure
    reset = _method(_source(), 'private void clearWaveObjectiveState()')
    for flag in ('testWaveFrontVisualMode = false', 'sandboxWaveRoster = Set.of()', 'testRitualCombatMode = false'):
        assert flag in reset


def test_scope_owner_keeps_failed_cleanup_handle_for_a_retry():
    source = _source()
    close = _method(source, "private boolean closeEncounterResourceScope(String reason)")

    assert close.index("scope.closeResources()") < close.index("encounterResourceScope = null;")
    assert "if (result.success())" in close
    assert "encounterResourceScope == scope" in close


def test_new_attempt_generation_is_not_committed_before_scope_cleanup():
    source = _source()
    start = _method(source, "private void completeStartRitual(Set<UUID> candidateRoster)")
    recovery = _method(source, "private void recoverTransientSession()")

    generation_commit = "generation = Math.max(1L, generation + 1L);"
    assert "if (!cancelSessionTasks())" in start
    assert start.index("if (!cancelSessionTasks())") < start.index(generation_commit)
    assert start.index(generation_commit) < start.index("attemptLifecycle.begin(")
    assert "if (!cancelSessionTasks())" in recovery
    recovery_commit = "generation = Math.max(1L, staleGeneration + 1L);"
    assert recovery.index("if (!cancelSessionTasks())") < recovery.index(recovery_commit)


def test_playable_entry_adapters_share_the_entire_mechanic_initializer():
    source = _source()
    sandbox = _method(source, "private boolean spawnTestWave(")
    official = _method(source, "private boolean spawnWaveForObjectiveInternal(")
    for adapter in (sandbox, official):
        assert "initializeWaveGameplay(" in adapter
        assert "startWaveObjective(" not in adapter
        assert "spawnWaveGroup(" not in adapter
        assert "startRealitySplitTrials(" not in adapter


def test_test_wave_command_reports_refused_start_instead_of_false_success():
    command = _method(_source(), "private void handleTest(")
    assert "if (!spawnTestWave(" in command
    assert "[capture|combat]" in command


def test_sandbox_roster_is_frozen_and_does_not_read_old_official_eligibility():
    source = _source()
    eligibility = _method(source, "private boolean isActiveArenaParticipant(")
    sandbox = _method(source, "private boolean spawnTestWave(")
    assert "sandboxWaveRoster.contains(player.getUniqueId())" in eligibility
    assert eligibility.index("sandboxWaveRoster.contains") < eligibility.index("isOfficialAttemptActive()")
    assert "sandboxWaveRoster = Set.copyOf(" in sandbox
    assert "if (isOfficialAttemptActive())" in sandbox


def test_combat_sandbox_skips_capture_and_runs_the_shared_major_spell_scheduler():
    source = _source()
    tick = _method(source, "private void tickCurrentRitualSphereObjective(")
    capture = _method(source, "private void attemptRitualPrisonerCapture(")
    cast = _method(source, "private void castNextRitualAbility(")
    assert "if (isRitualCombatSandbox()) return;" in capture
    assert "ritualMajorSpellsReady()" in tick
    assert "ritualMajorSpellsReady()" in cast
    assert "RitualSphereEncounterPolicy.hasCaptured(ritualSphereState)" in tick
    assert "tickRitualSpellController(now)" in tick
    assert "castNextRitualAbility(now)" in tick


def test_caster_kill_probe_uses_real_health_death_and_slot_identity():
    source = _source()
    probe = _method(source, "private boolean killRitualCasterForTest(")
    assert "slot < 0 || slot >= RitualCasterProgressionPolicy.TOTAL_CASTERS" in probe
    assert "ritualCasterSlots.getOrDefault" in probe
    assert ".setHealth(0.0D)" in probe
    assert "ritualCasterDeathCount =" not in probe
    assert "if (!testWaveFrontVisualMode" in probe


def test_repeated_sandbox_start_cleans_before_allocating_a_new_packet_generation():
    sandbox = _method(_source(), "private boolean spawnTestWave(")
    cleanup = sandbox.index("if (!cancelSessionTasks())")
    allocate = sandbox.index("generation = Math.max(1L, generation + 1L);")
    assert cleanup < allocate < sandbox.index("initializeWaveGameplay(")
    assert "resetEncounterTaskRegistry(generation)" in sandbox


def test_sandbox_restart_restores_frozen_roster_and_capture_mode():
    source = _source()
    restore = _method(source, "private void applySnapshot(")
    encode = _method(source, "private Map<String, String> objectiveProgressSnapshot(")
    assert "waveObjectiveProgressSnapshot()" in encode
    assert "PreBossTickSnapshotPolicy.encode" in encode
    encode += _method(source, "private Map<String, String> waveObjectiveProgressSnapshot(")
    assert "SandboxWaveSessionSnapshot.decode" in restore
    assert "sandboxWaveRoster = sandboxSession.roster()" in restore
    assert "testRitualCombatMode = sandboxSession.combatMode()" in restore
    assert "SandboxWaveSessionSnapshot.encode" in encode


def test_fog_freeze_survives_the_regular_ai_heartbeat():
    source = _source()
    enable = _method(source, "private void ensureEventCombatAi(")
    combat = _method(source, "private boolean isWaveAiCombatEntity(")
    assert "isFogFrozenCombatEntity(entity)" in enable
    assert enable.index("isFogFrozenCombatEntity(entity)") < enable.index("mob.setAI(true)")
    assert "isFogFrozenCombatEntity(entity)" in combat


def test_carrier_visual_refresh_has_deadlines_instead_of_tick_phase_windows():
    source = _source()
    tick = _method(source, "private void tickCurrentCarrierObjective(")
    assert "now % 250L" not in tick
    assert "now % 500L" not in tick
    assert "carrierBeamRefresh.shouldRefresh(now)" in tick
    assert "carrierChargeRefresh.shouldRefresh(now)" in tick


def test_ritual_beam_membership_is_retained_between_packet_refreshes():
    render = _method(_source(), "private void renderCurrentRitualSphere(")
    assert render.index('desired.add("wave6-ritual-" + casterId + "-hand-" + handIndex)') < render.index(
        "if (now >= ritualNextBeamRefreshMillis)"
    )
    assert 'handIndex < 2' in render
    assert 'String beamKey = "wave6-ritual-" + casterId + "-hand-" + handIndex' in render
