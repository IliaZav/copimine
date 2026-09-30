from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_transition_runes_hold_ten_seconds_and_wave_four_cannot_skip_them():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    objective = read(PLUGIN / "domain" / "EndRiftObjective.java")
    phases = read(PLUGIN / "domain" / "EventPhase.java")
    graph = read(PLUGIN / "domain" / "EndEventStateMachine.java")
    controller = read(PLUGIN / "runtime" / "EndRiftEncounterController.java")
    coordinator = read(PLUGIN / "runtime" / "EndRiftEncounterCoordinator.java")

    assert "TRANSITION_RUNE_HOLD_MILLIS = 10_000L" in event
    assert "new TransitionRuneController(TRANSITION_RUNE_HOLD_MILLIS)" in event
    assert "hold_ms=10000" in event
    assert "five seconds" not in event.lower()
    assert "INTERMISSION_4" in phases
    assert re.search(r"EventPhase\.WAVE_4,\s*EnumSet\.of\(EventPhase\.CORE_RESTORATION", graph)
    assert re.search(r"EventPhase\.CORE_RESTORATION,\s*EnumSet\.of\(EventPhase\.INTERMISSION_4", graph)
    assert re.search(r"EventPhase\.INTERMISSION_4,\s*EnumSet\.of\(EventPhase\.WAVE_5", graph)
    assert "case INTERMISSION_4 -> 5;" in controller
    assert "nextWaveNumberAfterIntermission(session.phase())" in coordinator
    assert "advanceIntermissionPhase(" in event
    assert "EventPhase.CORE_RESTORATION, EventPhase.WAVE_5" not in graph
    assert "hasTransitionRunesAfter" in objective
    assert "announceEventTitle(Integer.toString(countdown)" in event


def test_preboss_recovery_gateway_is_exactly_forty_seconds_and_single_fire():
    objective = read(PLUGIN / "domain" / "EndRiftObjective.java")
    controller_path = PLUGIN / "runtime" / "PreBossTransitionController.java"
    gateway_path = PLUGIN / "runtime" / "BossStartGateway.java"
    event = read(PLUGIN / "CopiMineEndEvent.java")

    assert "PRE_BOSS_SECONDS = 40" in objective
    assert controller_path.exists()
    assert gateway_path.exists()
    controller = read(controller_path)
    assert "DURATION_MILLIS = 40_000L" in controller
    assert "handoffStarted" in controller
    assert "callbackGeneration" in controller or "generation" in controller
    assert "preBossTransitionController.tick" in event
    assert "new PostWaveRecoveryService" in event
    assert "EndRiftEncounterController" in event
    assert "BOSS_CINEMATIC_DURATION_TICKS" not in event[event.index("private void tickPreBossCooldown"):event.index("private void renderPreBossCooldownVisual")]


def test_post_wave_recovery_repairs_thirty_percent_of_max_durability():
    service_path = PLUGIN / "runtime" / "PostWaveRecoveryService.java"
    policy_path = PLUGIN / "domain" / "PostWaveRecoveryPolicy.java"
    assert service_path.exists()
    assert policy_path.exists()
    service = read(service_path)
    policy = read(policy_path)
    assert "player.setHealth" in service
    assert "getStorageContents" in service
    assert "0.30D" in policy
    assert "Math.round" in policy
    assert "maxDurability" in policy


def test_post_wave_recovery_repairs_storage_armor_and_offhand_items():
    service = read(PLUGIN / "runtime" / "PostWaveRecoveryService.java")
    assert "getStorageContents()" in service
    assert "getArmorContents()" in service
    assert "getExtraContents()" in service
    assert service.count("repairItems(") >= 4


def test_lifecycle_authority_is_shared_by_live_facade_and_pure_session():
    controller = read(PLUGIN / "runtime" / "EndRiftEncounterController.java")
    session = read(PLUGIN / "runtime" / "EndRiftSession.java")
    event = read(PLUGIN / "CopiMineEndEvent.java")

    assert "EndEventStateMachine" in controller
    assert "private final EndRiftEncounterController encounterController" in session
    assert "new EndRiftEncounterController" in event
    assert "encounterController.transition" in event
    assert "encounterController.recoverTo" in event
    assert "new EndEventStateMachine" not in event


def test_live_official_wave_lifecycle_uses_the_shared_wave_coordinator():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    coordinator = PLUGIN / "runtime" / "EndRiftEncounterCoordinator.java"

    assert "EndRiftEncounterCoordinator encounterCoordinator" in event
    assert "new EndRiftEncounterCoordinator(new EndRiftSession(encounterController))" in event
    start = event[event.index("private boolean startCanonicalObjective"):
                  event.index("private void startWaveObjective")]
    tick = event[event.index("private boolean tickCurrentObjective"):
                 event.index("private boolean tickWaveObjective")]
    report = event[event.index("private boolean reportCurrentWaveResult"):
                   event.index("private boolean tickWaveObjective")]
    complete = event[event.index("private boolean completeWavePhase"):
                      event.index("private boolean completeCoreRestorationPhase")]

    assert "encounterCoordinator.startCurrentWave" in start
    assert "reportCurrentWaveResult(objectiveWave, objectiveComplete)" in tick
    assert "encounterCoordinator.tickCurrentWave" in report
    assert "encounterCoordinator.completeCurrentWaveObjective" in complete
    assert "EndRiftSession(EndRiftEncounterController encounterController)" in read(
        PLUGIN / "runtime" / "EndRiftSession.java")
    assert coordinator.exists()


def test_resumed_wave_six_restores_its_persisted_prisoner_from_the_live_tick():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    coordinator = read(PLUGIN / "runtime" / "EndRiftEncounterCoordinator.java")
    tick = event[event.index("private void tickCurrentRitualSphereObjective"):
                 event.index("private void renderRitualSphereChanneling")]
    capture = event[event.index("private void attemptRitualPrisonerCapture"):
                    event.index("private void synchronizePersistedRitualPrisonerCapture")]
    restore = event[event.index("private void synchronizePersistedRitualPrisonerCapture"):
                    event.index("private void ensureRitualPrisoner")]

    captured_gate = tick.index("if (!RitualSphereEncounterPolicy.hasCaptured(ritualSphereState)) {\n            return;")
    restore_call = tick.index("synchronizePersistedRitualPrisonerCapture();")
    prisoner_tick = tick.index("ensureRitualPrisoner(now)")
    assert captured_gate < restore_call < prisoner_tick
    assert "synchronizePersistedRitualPrisonerCapture()" not in capture
    assert "encounterCoordinator.restoreCurrentRitualPrisoner(context, prisonerId)" in restore
    assert "restoreCapturedPrisoner" in coordinator


def test_one_generation_roster_owns_participant_presence_and_objective_eligibility():
    lifecycle = read(PLUGIN / "runtime" / "AttemptLifecycleController.java")
    event = read(PLUGIN / "CopiMineEndEvent.java")
    arena_check = event[event.index("private boolean isActiveArenaParticipant"):
                        event.index("private boolean isCreativeTestTarget")]
    rune_tick = event[event.index("private void tickCurrentIntermission"):
                      event.index("private EventPhase wavePhase")]
    boss_context = event[event.index("private EncounterContext currentEncounterContext"):
                         event.index("private List<Player> activeWave7RecoveryPlayers")]

    assert "record ParticipantStatus(boolean registered, boolean active, boolean online," in lifecycle
    assert "boolean alive, boolean eligibleForObjective)" in lifecycle
    assert "Map<UUID, ParticipantStatus> participants" in lifecycle
    assert "private final Set<UUID> roster" not in lifecycle
    assert "private final Set<UUID> living" not in lifecycle
    assert "refreshObjectiveEligibility" in arena_check
    assert "attemptLifecycle.status(entry.getValue())" in rune_tick
    assert "attemptLifecycle.isObjectiveEligible(entry.getValue(), generation)" in rune_tick
    assert "attemptLifecycle.activeLivingOnlineRoster()" in rune_tick
    assert "Set<UUID> roster = attemptLifecycle.owns(generation)" in rune_tick
    assert "authoritativeAttemptRoster()" in boss_context
    roster_helper = event[event.index("private Set<UUID> authoritativeAttemptRoster"):
                           event.index("private boolean isOfficialCurrentAttempt")]
    assert "attemptLifecycle.roster()" in roster_helper
    assert "attemptLifecycle.markOffline(uuid, generation)" in event
    assert "attemptLifecycle.markOnline(playerUuid, generation)" in event


def test_phase_boundaries_close_the_previous_wave_scope_and_own_transient_entities():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    scope = read(PLUGIN / "runtime" / "EncounterResourceScope.java")
    publish = event[event.index("private boolean publishLifecycleTransition"):
                    event.index("private void forcePhase")]
    forced = event[event.index("private void forcePhase"):
                   event.index("private boolean cancelSessionTasks")]
    register = event[event.index("private void registerOwnedEntity"):
                     event.index("private boolean isPersistentLayoutEntity")]

    assert "rotateEncounterResourceScope(next)" in publish
    assert "rotateEncounterResourceScope(phase)" in forced
    assert "encounterResourceScope.owner().equals(owner)" in event
    assert "registerEntity(entity.getUniqueId()" in register
    assert "CleanupResult closeResources()" in scope
    assert "ownerKey" in scope


def test_official_wave_flow_does_not_start_a_wave_front_animation():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    live_start = event[event.index("private void spawnWaveForObjectiveInternal"):event.index("private int plannedWaveCountForDiagnostics")]
    assert "startWaveFrontAnimation" not in live_start
    assert "testWaveFrontVisualMode && activeWave >= 1" in event


def test_wave_objective_and_spell_names_are_not_sent_as_routine_system_titles():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    objective_start = event.index("private boolean startCanonicalObjective")
    objective_end = event.index("private void startWaveObjective", objective_start)
    fog_start = event.index("private void tickBlackFogObjective")
    fog_end = event.index("private void applyBlackFogEffects", fog_start)
    wave_intermission_start = event.index("private void tickCurrentIntermission")
    wave_intermission_end = event.index("private EventPhase wavePhase", wave_intermission_start)
    assert "announceEventTitle" not in event[objective_start:objective_end]
    assert "announceEventTitle" not in event[fog_start:fog_end]
    assert "announceEventTitle" not in event[wave_intermission_start:wave_intermission_end]
    assert 'announceEventTitle(Integer.toString(countdown), "", false)' in event


def test_wave6_unlock_and_prison_release_use_hud_and_world_state_without_titles():
    event = read(PLUGIN / "CopiMineEndEvent.java")
    caster_start = event.index("private void handleRitualCasterDeath")
    caster_end = event.index("private RitualCasterProgressionPolicy.MajorSpell majorSpell", caster_start)
    release_start = event.index("private void breakRitualPrison", caster_end)
    release_end = event.index("private void clearRitualSphereObjective", release_start)
    caster_progression = event[caster_start:caster_end]
    prison_release = event[release_start:release_end]
    assert ".sendTitle(" not in caster_progression
    assert "sendRitualPrisonerState(prisoner)" in caster_progression
    assert ".sendTitle(" not in prison_release


def test_live_boundary_probe_can_use_a_separate_local_server_copy():
    script = (ROOT / "tests" / "RunEndRiftWave6Wave7BoundariesLive.ps1").read_text(encoding="utf-8")
    assert "[string]$ServerDir" in script
    assert "[int]$ServerPort" in script
    assert "[int]$RconPort" in script
    assert "END_RIFT_BOT_PORT" in script
    assert "StartsWith($localPrefix" in script
    assert "-RconPort $RconPort" in script
