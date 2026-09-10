from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
EVENT_CONFIG = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")
STATE_MACHINE = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EndEventStateMachine.java").read_text(encoding="utf-8")
V3_FLOW = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/V3WaveObjectivePolicy.java").read_text(encoding="utf-8")
V3_SCALING = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/V3ObeliskScalingPolicy.java").read_text(encoding="utf-8")
JOURNAL = (ROOT / "copimine-end-event/src/me/copimine/endevent/HazardMutationJournal.java").read_text(encoding="utf-8")


def _body(start: str, end: str) -> str:
    first = MAIN.index(start)
    return MAIN[first:MAIN.index(end, first)]


def test_v3_config_declares_seven_wave_flow_and_separate_six_obelisk_cap():
    assert "schema-version: 3" in CONFIG
    for line in (
        "wave-4:",
        "type: obelisk_assault",
        "wave-5:",
        "type: black_fog",
        "wave-6:",
        "type: collapse_rings",
        "wave-7:",
        "type: chambers",
        "v3-max-active: 6",
    ):
        assert line in CONFIG
    assert "int v3MaxActive" in EVENT_CONFIG
    assert "v3MaxActive > 6" in EVENT_CONFIG
    assert "v3-max-active" in EVENT_CONFIG


def test_v3_wave_objectives_are_explicit_and_do_not_use_the_legacy_wave_four_slot():
    for token in (
        "MAX_WAVE = 7",
        "case 4 -> Objective.OBELISK_ASSAULT",
        "case 5 -> Objective.BLACK_FOG",
        "case 6 -> Objective.COLLAPSE_RINGS",
        "case 7 -> Objective.REALITY_SPLIT",
        "case 4 -> -1",
        "case 7 -> 6",
        "completedWave <= 6",
    ):
        assert token in V3_FLOW
    for token in (
        "spawnV3Wave",
        "startV3Wave4ObeliskAssault",
        "tickV3WaveObjective",
        "tickV3WaveCompletion",
        "tickCoreRestoration",
        "chamberWaveNumber",
    ):
        assert token in MAIN
    assert "clearV3Wave4Obelisks(\"" in MAIN
    assert "public void onDisable" in MAIN
    assert "map.put(EventPhase.WAVE_4, EnumSet.of(EventPhase.INTERMISSION_4))" in STATE_MACHINE


def test_v3_scaling_is_exact_and_bounded():
    assert "MAX_OBELISKS = 6" in V3_SCALING
    expected_profiles = (
        "new Profile(4, 3, 12, 3, 1)",
        "new Profile(4, 4, 16, 4, 2)",
        "new Profile(5, 4, 20, 5, 2)",
        "new Profile(5, 5, 25, 6, 3)",
        "new Profile(6, 5, 30, 8, 3)",
    )
    for profile in expected_profiles:
        assert profile in V3_SCALING
    assert "Math.min(4, livingPlayers)" in V3_FLOW
    assert "Math.min(V3ObeliskScalingPolicy.MAX_OBELISKS" in (
        (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/V3ObeliskPlacementPolicy.java").read_text(encoding="utf-8")
    )


def test_v3_obelisks_are_real_journaled_blocks_with_staged_emergence():
    start = _body("private void startV3Wave4ObeliskAssault", "private boolean tickV3Wave4ObeliskAssault")
    tick = _body("private boolean tickV3Wave4ObeliskAssault", "private void advanceV3ObeliskEmergence")
    emergence = _body("private void advanceV3ObeliskEmergence", "private void applyV3ObeliskStageBlocks")
    for token in (
        "v3ObeliskCells",
        "hazardJournal.prepare",
        '"OBELISK"',
        "profile.obeliskCount()",
        "activationTick",
    ):
        assert token in start
    for token in (
        "V3ObeliskStage.ACTIVE",
        "profile.fireballCap",
        "tickRiftFireballs(null)",
        "countLiveWaveEntitiesForWave(4)",
    ):
        assert token in tick
    for token in (
        "EMERGE_BASE",
        "EMERGE_BODY",
        "EMERGE_CROWN",
        "applyV3ObeliskStageBlocks",
    ):
        assert token in emergence
    assert "sameBlockData" in MAIN
    assert "block.setType" in MAIN
    assert 'EVENT_KIND_OBELISK.equals(kind)' in MAIN
    assert 'EVENT_KIND_RIFT_FIREBALL.equals(kind)' in MAIN
    assert "isObeliskMutation" in JOURNAL


def test_v3_reflected_fireball_is_generation_scoped_and_exactly_once():
    hit = _body("private void applyV3ReflectedObeliskHit", "private void removeV3Wave4Obelisk")
    for token in (
        "v3Wave4ConsumedFireballs.contains",
        "fireball.reflected()",
        "fireball.generation() == generation",
        "fireball.reflectorUuid() != null",
        "v3Wave4ConsumedFireballs.add",
        "obelisk.health(result.remainingHealth())",
    ):
        assert token in hit
    reflect = _body("public void onRiftFireballReflect", "public void onRiftObeliskDamage")
    assert "isActiveBossParticipant(player)" in reflect
    assert "state.reflected()" in reflect
    assert '"already-reflected"' in reflect
    assert "setDirection(direction)" in reflect


def test_v3_fireball_launch_starts_above_the_real_obelisk_crown():
    launch = _body("private void launchV3Wave4Fireball", "private V3Wave4ObeliskRuntimeState findV3Wave4ObeliskAt")
    assert "obelisk.base().clone().add(0.0D, 3.25D, 0.0D)" in launch
    assert "obelisk.base().clone().add(0.0D, 2.65D, 0.0D)" not in launch


def test_v3_reflected_fireball_uses_swept_collision_between_runtime_samples():
    tick = _body("private void tickRiftFireballs", "private void launchRiftFireball")
    assert "findV3Wave4ObeliskAlongPath" in tick
    assert "state.lastLocation()" in tick
    collision = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RiftFireballCollisionPolicy.java").read_text(
        encoding="utf-8"
    )
    assert "segmentIntersectsObelisk" in collision
    assert "five-tick sampler" in collision


def test_v3_official_boss_rift_uses_fractures_and_never_legacy_obelisks():
    """The official V3 boss RIFT phase must not resurrect the old V2 spell."""
    sync = _body("private void synchronizeV2BossStage", "private void startV2LastSealVisuals")
    tick = _body("private void tickV2Boss", "private BossStagePolicy.CombatProfile currentBossCombatProfile")
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/V2BossStagePolicy.java").read_text(
        encoding="utf-8"
    )
    assert "isOfficialV3Attempt()" in sync
    assert "startV3RiftFractures" in sync
    assert "startRiftObelisks(boss, true)" in sync
    assert "isOfficialV3Attempt()" in tick
    assert "tickV3RiftFractures" in tick
    fracture_tick = _body("private void tickV3RiftFractures", "private void renderV3RiftFracture")
    assert "clearV3RiftFractures" in fracture_tick
    rift_pool = policy[policy.index("case RIFT ->"):policy.index("case OVERLOAD ->")]
    assert "RIFT_OBELISKS" not in rift_pool
    assert "RIFT_FRACTURES" in MAIN


def test_v3_boss_phase_contract_is_exact_and_tentacles_are_last_seal_only():
    stage = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/V2BossStage.java").read_text(
        encoding="utf-8"
    )
    for marker in (
        "RIFT(0.60D, 0.45D",
        "OVERLOAD(0.45D, 0.30D",
        "RAGE(0.30D, 0.20D",
        "LAST_SEAL(0.20D, 0.00D",
    ):
        assert marker in stage
    tentacles = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/TentacleScalingPolicy.java").read_text(
        encoding="utf-8"
    )
    assert "stage == V2BossStage.LAST_SEAL" in tentacles
    assert "permanentFor(stage != LAST_SEAL) == 0" not in tentacles


def test_v3_official_live_driver_has_a_complete_seven_wave_branch():
    live = (ROOT / "tests/RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    v3_start = live.index("if ($isV3Flow) {\n    $expectedV3Obelisks")
    legacy_fallback = live.index(
        "Wait-LogRegex -Pattern 'V2_WAVE_OBJECTIVE_STARTED.*wave=4.*BLACK_FOG'",
        v3_start,
    )
    v3_branch = live[v3_start:legacy_fallback]
    for token in (
        "Wait-EventWaveCompleted -Wave 4",
        "Wait-V2TransitionToWave -CompletedWave 4 -NextWave 5",
        "V3_WAVE_STARTED.*wave=5",
        "V3_WAVE_STARTED.*wave=6",
        "V3_WAVE_STARTED.*wave=7",
        "CORE_RESTORATION",
        "INTERMISSION_6",
        "PRE_BOSS_COOLDOWN",
    ):
        assert token in v3_branch
    assert "V2_WAVE_OBJECTIVE_STARTED.*wave=4.*BLACK_FOG" not in v3_branch


def test_v3_blocks_and_projectiles_have_event_cleanup_and_protection_hooks():
    for token in (
        "onArenaBlockBreak",
        "onArenaBlockExplode",
        "onArenaEntityExplode",
        "isV3Wave4ObeliskCell",
        "clearV3Wave4Obelisks",
        'clearV3Wave4Obelisks("wave-4-boundary")',
        'clearV3Wave4Obelisks("startup-failed")',
        "v3Wave4ConsumedFireballs.clear()",
        "hazardJournal.markRestored()",
    ):
        assert token in MAIN


def test_v3_fracture_activation_log_is_phase_scoped_for_live_evidence():
    assert 'V3_RIFT_FRACTURE_ACTIVE event=' in MAIN
    assert 'fracture=" + state.id() + " phase=RIFT radius=' in MAIN
