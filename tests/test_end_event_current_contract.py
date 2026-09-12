"""Static/asset contract for the current End Rift encounter.

This file intentionally checks the source of truth rather than the generated
runtime directory.  It is a small CI guard against accidentally bringing a
removed encounter generation back into the official flow.
"""

from __future__ import annotations

import json
import re
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
PLUGIN_SRC = PLUGIN / "src" / "me" / "copimine" / "endevent"
DOMAIN = PLUGIN_SRC / "domain"
RUNTIME = PLUGIN_SRC / "runtime"
CLIENT = ROOT / "CopiMineClient"
CLIENT_JAVA = CLIENT / "src" / "main" / "java" / "me" / "copimine" / "client"
CLIENT_ASSETS = CLIENT / "src" / "main" / "resources" / "assets" / "copimineclient"
PACK = ROOT / "resourcepacks"
PACK_ASSETS = PACK / "src" / "assets" / "copimine"
DOCS = ROOT / "docs"


CURRENT_WAVES = [
    "RIFT_CARRIERS",
    "RIFT_HUNT",
    "RIFT_GATES",
    "OBELISK_ASSAULT",
    "BLACK_FOG",
    "COLLAPSE_RINGS",
    "REALITY_SPLIT",
]
CURRENT_BOSS_PHASES = ["AWAKENING", "HUNT", "RIFT", "OVERLOAD", "RAGE", "LAST_SEAL"]
CURRENT_AUDIO = [
    "ritual_wait",
    "wave_1",
    "wave_2",
    "wave_3",
    "wave_4",
    "wave_5",
    "wave_6",
    "wave_7",
    "intermission_1",
    "intermission_2",
    "intermission_3",
    "intermission_5",
    "intermission_6",
    "core_restoration",
    "pre_boss_cooldown",
    "boss_cinematic",
    "boss_awakening",
    "boss_hunt",
    "boss_rift",
    "boss_overload",
    "boss_rage",
    "boss_last_seal",
    "boss_finish",
    "victory",
]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def assert_contains(path: Path, *needles: str) -> None:
    text = read(path)
    missing = [needle for needle in needles if needle not in text]
    assert not missing, f"{path}: missing {missing}"


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
    width, height = struct.unpack(">II", data[16:24])
    return width, height


def test_local_schema_and_current_config() -> None:
    config = read(PLUGIN / "config.yml")
    assert re.search(r"(?m)^environment:\s*local\s*$", config)
    assert re.search(r"(?m)^\s*schema-version:\s*4\s*$", config)
    assert re.search(r"(?m)^\s*start-ritual-timeout-seconds:\s*60\s*$", config)
    assert re.search(r"(?m)^\s*intermission-seconds:\s*20\s*$", config)
    assert re.search(r"(?m)^\s*min-players:\s*2\s*$", config)
    assert re.search(r"(?m)^\s*max-players:\s*20\s*$", config)
    assert re.search(r"(?m)^\s*hard-cap:\s*56\s*$", config)
    waves = config.split("\nwaves:\n", 1)[1]
    for index, objective in enumerate(CURRENT_WAVES, 1):
        assert re.search(rf"(?m)^\s*wave-{index}:\s*$", config)
        match = re.search(rf"(?ms)^  wave-{index}:\s*\n(.*?)(?=^  wave-|\Z)", waves)
        assert match, f"missing wave block {index}"
        assert f"type: {objective}" in match.group(1)
    assert "health: 5000" in config
    assert re.search(r"(?ms)stages:\s*\[WAVE_4\]", config)
    assert "max-active: 6" in config
    assert "max-active-fireballs: 8" in config
    assert "blindness-ticks: 40" in config
    assert "debuff-ticks: 60" in config


def test_current_domain_vocabulary_and_graph() -> None:
    objective = DOMAIN / "EndRiftObjective.java"
    phase = DOMAIN / "BossPhase.java"
    event_phase = DOMAIN / "EventPhase.java"
    machine = DOMAIN / "EndEventStateMachine.java"
    assert_contains(objective, "MAX_WAVE = 7", *CURRENT_WAVES)
    assert_contains(phase, *CURRENT_BOSS_PHASES, "forHealth")
    assert_contains(event_phase, *[
        "WAVE_1", "INTERMISSION_1", "WAVE_2", "INTERMISSION_2", "WAVE_3",
        "INTERMISSION_3", "WAVE_4", "CORE_RESTORATION", "WAVE_5",
        "INTERMISSION_5", "WAVE_6", "INTERMISSION_6", "WAVE_7",
        "PRE_BOSS_COOLDOWN", "BOSS_CINEMATIC", "BOSS_ACTIVE", "BOSS_FINISH",
        "VICTORY_PROCESSING", "UNLOCKED", "RECOVERY_REQUIRED",
    ])
    graph = read(machine)
    assert "EventPhase.WAVE_6, EventPhase.PRE_BOSS_COOLDOWN" not in graph
    assert re.search(r"EventPhase.INTERMISSION_6,\s*EnumSet\.of\(EventPhase.WAVE_7", graph)
    assert re.search(r"EventPhase.WAVE_7,\s*EnumSet\.of\(EventPhase.PRE_BOSS_COOLDOWN", graph)
    assert "recoveryPhase" in graph


def test_current_boss_is_real_health_and_damage_is_explicit() -> None:
    root = PLUGIN_SRC / "CopiMineEndEvent.java"
    damage = DOMAIN / "BossDamagePolicy.java"
    real = DOMAIN / "BossRealHealthDamagePolicy.java"
    health = DOMAIN / "BossHealthPolicy.java"
    phase = DOMAIN / "BossPhasePolicy.java"
    root_text = read(root)
    assert_contains(health, "MIN_HEALTH = 5_000.0D", "MAX_HEALTH = 20_000.0D", "maxHealthFor")
    assert_contains(real, "applyHits", "remainingHealth", "finalDamage")
    assert_contains(damage, "DamageImmunityReason", "Normal current-phase damage")
    assert_contains(phase, "damageAllowed", "BOSS_CINEMATIC", "FINAL_STRIKE_COMMIT")
    assert "BossVirtualHealthPolicy" not in root_text
    assert "setNoDamageTicks(0" not in root_text
    assert "setLastDamage(0" not in root_text
    assert "virtualHealth" not in root_text
    assert "handleCurrentBossDamage" in root_text
    assert "getFinalDamage()" in root_text


def test_core_restoration_logs_normal_entry_before_the_deadline() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    assert re.search(
        r"completedWave == 4[\s\S]*?CORE_RESTORATION[\s\S]*?"
        r"phaseDeadlineMillis = System\.currentTimeMillis\(\) \+ 6_000L;[\s\S]*?"
        r"END_RIFT_CORE_RESTORATION_STARTED",
        root,
    ), "normal Wave 4 entry must emit the restoration-start marker"


def test_waves_have_current_runtime_policies() -> None:
    assert_contains(DOMAIN / "WaveMechanicsPolicy.java", "clampToPressure", "gateCount", "WaveCounts")
    assert_contains(DOMAIN / "WaveVisualPolicy.java", "EndRiftObjective.Objective", "MAX_RADIUS_BLOCKS")
    assert_contains(DOMAIN / "CombatTacticsPolicy.java", "objective", "RIFT_CARRIERS", "REALITY_SPLIT")
    assert_contains(DOMAIN / "SkeletonCombatPolicy.java", "BLACK_FOG", "COLLAPSE_RINGS", "REALITY_SPLIT")
    assert_contains(DOMAIN / "BlackFogTimingPolicy.java", "CYCLE_COUNT = 3", "SAFE_ZONE_SECONDS", "FOG_SECONDS")
    assert_contains(DOMAIN / "CollapseRingEncounterPolicy.java", "BOTH_ALIVE", "FIRST_DOWN", "PAIR_DEFEATED")
    assert_contains(RUNTIME / "RealitySplitChamberController.java", "openBoundary", "allowsMobTarget", "allowsInteraction")
    assert_contains(DOMAIN / "ChamberIsolationPolicy.java", "chamberByPlayer")
    assert_contains(DOMAIN / "PressureBudgetController.java", "HARD_CAP = 56")
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    assert "spawnWaveForObjective" in root
    for marker in ("startWave4ObeliskAssault", "tickWave4ObeliskAssault",
                   "clearWave4Obelisks", "startCanonicalObjective",
                   "tickBlackFogObjective", "tickCurrentChamberObjective"):
        assert marker in root, marker


def test_w4_obelisk_contract_and_scaling() -> None:
    scaling = DOMAIN / "ObeliskScalingPolicy.java"
    integrity = DOMAIN / "ObeliskIntegrityPolicy.java"
    geometry = DOMAIN / "ObeliskGeometryPolicy.java"
    fire = DOMAIN / "ObeliskFireDirectorPolicy.java"
    projectile = DOMAIN / "ObeliskProjectilePolicy.java"
    assert_contains(scaling, "MAX_OBELISKS = 6", "profileForPlayers", "new Profile(6, 3, 18")
    assert_contains(integrity, "NOT_REFLECTED", "STALE_GENERATION", "OBELISK_RIFT_FIREBALL", "tryReflectedHit")
    assert_contains(geometry, "FOOTPRINT_RADIUS = 1", "HEIGHT = 5", "EMERGENCE_TICKS = 70")
    assert_contains(fire, "fireInterval", "chooseTarget", "staggerTicks")
    assert_contains(projectile, "REFLECTED", "OUTBOUND", "current", "expired")
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    assert "activeWave4Obelisks.size()" in root
    assert "RiftObeliskTimingPolicy.firstFireTick" in root
    assert "max-active-fireballs" not in root  # config, not a runtime literal


def test_cleanup_and_generation_ownership() -> None:
    scope = RUNTIME / "EncounterResourceScope.java"
    lifecycle = RUNTIME / "AttemptLifecycleController.java"
    tasks = PLUGIN_SRC / "EventTaskRegistry.java"
    journal = PLUGIN_SRC / "HazardMutationJournal.java"
    deposit = PLUGIN_SRC / "DepositJournal.java"
    assert_contains(scope, "registerTask", "registerEntity", "registerCloser", "close()")
    assert_contains(lifecycle, "pendingNextGeneration", "commitWipe", "abortWipe", "generation must be positive")
    assert_contains(tasks, "pruneCompleted", "cancelAll", "activeCount")
    assert_contains(journal, "unresolved hazard journal must be restored", "floor-original", "markRestored")
    assert_contains(deposit, "JournalCorruptionException", "torn final", "fields.length != 6")
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    for marker in ("clearActiveEventArrows", "clearActiveRiftProjectiles",
                   "clearRiftObelisks", "clearTentacles", "clearCurrentRiftFractures"):
        assert marker in root
    assert "generation <= 0L" in read(RUNTIME / "RealitySplitChamberController.java")


def test_transition_runes_are_a_bijection() -> None:
    policy = DOMAIN / "TransitionRunePolicy.java"
    controller = RUNTIME / "TransitionRuneController.java"
    assert_contains(policy, "DUPLICATE_RUNE", "DUPLICATE_PLAYER", "eligible()", "advance")
    assert_contains(controller, "reset", "TransitionRunePolicy", "holdMillis")
    assert "DUPLICATE_PLAYER" in read(ROOT / "tests" / "TransitionRunePolicyTest.java")


def test_rewards_are_durable_and_per_player() -> None:
    reward = DOMAIN / "NightCloakRollPolicy.java"
    snapshot = PLUGIN_SRC / "EventSnapshot.java"
    store = PLUGIN_SRC / "EventStateStore.java"
    artifacts = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    assert_contains(reward, "resolve", "roll", "SplittableRandom")
    assert "night-cloak-chance: 0.30" in read(PLUGIN / "config.yml")
    assert_contains(snapshot, "nightCloakRolls", "officialRewardRoster", "waveRewardsIssued")
    assert_contains(store, "CURRENT_SCHEMA", "writeCurrent", "rewards.night-cloak-rolls")
    assert "EventArtifactRewardService" in artifacts
    assert "issueVictoryRewards" in artifacts


def test_client_uses_one_current_phase_and_animation_contract() -> None:
    state = CLIENT_JAVA / "EndEventClientState.java"
    renderer = CLIENT_JAVA / "RiftGuardianModelRenderer.java"
    model = CLIENT_JAVA / "RiftGuardianModel.java"
    animation_catalog = CLIENT_JAVA / "BossAnimationId.java"
    animator = CLIENT_JAVA / "EndRiftTentacleAnimator.java"
    state_text = read(state)
    renderer_text = read(renderer)
    assert_contains(state, *CURRENT_BOSS_PHASES, "UNKNOWN", "Unknown End Rift animation")
    assert_contains(renderer, *CURRENT_BOSS_PHASES, "keeping an explicit UNKNOWN pose")
    assert_contains(animation_catalog, "Running2", "Swipe2", "Hurt2", "Dying2",
                    "udar_iz_grudi", "udar_po_zemle2", "PHASE_TRANSITION")
    assert_contains(model, "FINAL_STRIKE", "LAST_SEAL", "PHASE_SHIFT", "MELEE_SWIPE",
                    "SPELL_RIFT_OBELISKS", "GROUND_SLAM")
    assert_contains(animator, "SHIELD_CHANNEL", "GRAB_SUCCESS", "HOLD", "THROW", "SPAWN_UNDER_PLAYER")
    assert '"HUNTER"' not in state_text
    assert '"DISTORTION"' not in state_text
    assert '"ABSORPTION"' not in state_text
    assert '"CATASTROPHE"' not in state_text
    for name in ("rift_guardian_awakening.png", "rift_guardian_hunt.png",
                 "rift_guardian_rift.png",
                 "rift_guardian_overload.png", "rift_guardian_rage.png",
                 "rift_guardian_last_seal.png", "rift_guardian_final_strike.png"):
        path = CLIENT_ASSETS / "textures" / "entity" / name
        assert path.is_file(), name
        width, height = png_size(path)
        assert (width, height) == (512, 512), f"{name}: {width}x{height}"
    assert not list((CLIENT_ASSETS / "textures" / "entity").glob("rift_guardian_*hunter*.png"))


def test_client_asset_dimensions_and_event_visuals() -> None:
    entity = CLIENT_ASSETS / "textures" / "entity"
    required = [
        "end_event_rift_fireball_hd.png",
        "end_event_rift_obelisk_full_hd.png",
        "end_event_rift_obelisk_damaged_hd.png",
        "end_event_rift_obelisk_critical_hd.png",
        "end_rift_tentacle_hd.png",
    ]
    for name in required:
        path = entity / name
        assert path.is_file(), name
        width, height = png_size(path)
        assert width >= 128 and height >= 128, f"{name}: {width}x{height}"
    tentacle_width, tentacle_height = png_size(entity / "end_rift_tentacle_hd.png")
    assert (tentacle_width, tentacle_height) == (512, 512)
    for name in ("end_event_rift_fireball.png", "end_event_rift_obelisk_full.png",
                 "end_event_rift_obelisk_damaged.png", "end_event_rift_obelisk_critical.png"):
        assert (entity / name).is_file(), name
    protocol = read(CLIENT / "PROTOCOL.md")
    packet = read(CLIENT_JAVA / "EndEventPacket.java")
    bridge = read(CLIENT_JAVA / "ClientBridgeProtocol.java")
    state_text = read(CLIENT_JAVA / "EndEventClientState.java")
    assert "END_EVENT:END_BOSS_BAR" in protocol
    assert "RIFT|EXECUTING" in protocol
    assert "fromBridgePayload" in packet
    assert "END_EVENT_MAGIC" not in packet + bridge
    assert "bossId()" not in packet + bridge
    assert "applyEndEventPayload" in bridge
    assert "tentacleTargetForEntity" in state_text
    assert "targetYaw" in read(CLIENT_JAVA / "EndRiftTentacleRenderer.java")
    assert "targetSuffix" in read(PLUGIN_SRC / "CopiMineEndEvent.java")


def test_tentacle_rig_asset_contract() -> None:
    model_path = PACK_ASSETS / "models" / "item" / "end_event_rift_tentacle.json"
    model = json.loads(read(model_path))
    rig = model["copimine_rig"]
    assert rig["texture_size"] == [512, 512]
    assert rig["forward_axis"] == "+Z"
    assert rig["grab_socket"]["parent"] == "tip"
    assert rig["grab_socket"]["geometry"] is False
    assert len(rig["bones"]) >= 12
    master = PACK / "art" / "end_rift_tentacle_atlas_master.png"
    assert master.is_file()
    assert png_size(master) == (1254, 1254)
    generator = read(PACK / "generate_end_rift_tentacle_assets.py")
    assert "MASTER_TEXTURE" in generator
    assert "Image.Resampling.LANCZOS" in generator


def test_resource_pack_sound_catalog_is_current_and_complete() -> None:
    sound_json = json.loads(read(PACK_ASSETS / "sounds.json"))
    for name in CURRENT_AUDIO:
        key = f"end_rift/{name}"
        assert key in sound_json, key
        assert (PACK_ASSETS / "sounds" / "end_rift" / f"{name}.ogg").is_file(), name
    builder = read(PACK / "build-resourcepack.py")
    for name in CURRENT_AUDIO:
        assert name in builder, name
    for old_name in ("waves", "boss", "boss_half", "boss_final", "intermission_4",
                     "final_drain", "final_ritual", "final_wave"):
        assert not (PACK_ASSETS / "sounds" / "end_rift" / f"{old_name}.ogg").exists(), old_name


def test_current_docs_are_the_only_active_end_rift_guidance() -> None:
    spec = DOCS / "superpowers" / "specs" / "2026-09-10-end-rift-event-v3-final.md"
    guide = DOCS / "END_RIFT_EVENT_GUIDE_RU.md"
    assert spec.is_file()
    guide_text = read(guide)
    spec_text = read(spec)
    for text in (guide_text, spec_text):
        assert all(wave in text for wave in CURRENT_WAVES)
        assert all(phase in text for phase in CURRENT_BOSS_PHASES)
        assert "RIFT_CARRIERS" in text
        assert "REALITY_SPLIT" in text
    assert "FINAL_DRAIN" not in guide_text
    assert "FINAL_RITUAL" not in guide_text
    assert "FINAL_WAVE" not in guide_text
    for name in (
        "end-rift-boss-ai.md",
        "end-rift-skeleton-ai.md",
        "2026-08-17-end-rift-event-design.md",
        "2026-08-24-end-rift-completion-design.md",
        "2026-08-31-end-rift-boss-visuals-design.md",
        "2026-09-02-end-rift-combat-visual-fixes.md",
        "2026-09-07-end-rift-event-v2-codex-master-prompt.md",
        "2026-09-07-end-rift-event-v2-design.md",
        "2026-09-07-end-rift-event-v2.md",
    ):
        path = DOCS / "superpowers" / "specs" / name
        if name in ("end-rift-boss-ai.md", "end-rift-skeleton-ai.md"):
            path = DOCS / name
        assert not path.exists(), path


def test_active_source_has_no_removed_execution_paths() -> None:
    roots = [PLUGIN_SRC, RUNTIME, DOMAIN, CLIENT_JAVA, CLIENT / "PROTOCOL.md", PACK / "build-resourcepack.py"]
    # Keep raw migration spellings isolated in the decoder/test that consumes
    # old files.  The current runtime and active contract tests are scanned.
    forbidden = [
        "V2WaveObjectivePolicy", "V2BossStage", "spawnV2Wave", "tickV2WaveObjective",
        "castV2BossSpell", "tickV2Boss", "WaveSixChamberController", "TOWER_DEFENSE",
        "RIFT_STORM", "tower_artillery", "storm_kite", "final_volley",
        "BossVirtualHealthPolicy", "BossThresholdPolicy", "FINAL_DRAIN", "FINAL_RITUAL",
        "FINAL_WAVE", "finalDrainTriggered", "finalDrainApplied", "absorptionTriggered",
        "absorptionCompleted", "absorptionAttackEmpowered", "judgmentTriggered",
        "judgmentCompleted", "COUNTDOWN", "BOSS_V2_", "V2_FOG_",
    ]
    for root in roots:
        paths = [root] if root.is_file() else list(root.rglob("*.java"))
        for path in paths:
            text = read(path)
            for token in forbidden:
                assert token not in text, f"{path}: stale token {token}"


def test_current_test_suite_does_not_reference_deleted_models() -> None:
    for path in (ROOT / "tests").glob("*.java"):
        text = read(path)
        for token in ("V2WaveObjectivePolicy", "WaveSixChamberController",
                      "BossVirtualHealthPolicy", "BossStagePolicy", "BossStage"):
            assert token not in text, f"{path}: deleted model {token}"


def test_live_probes_use_only_current_encounter_vocabulary() -> None:
    required = [
        ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1",
        ROOT / "tests" / "RunEndRiftVisualFivePlayerLive.ps1",
        ROOT / "tests" / "RunEndRiftBossVisualLive.ps1",
        ROOT / "tests" / "RunEndRiftSpellMatrixLive.ps1",
    ]
    for path in required:
        assert path.is_file(), path
    forbidden = (
        "V2_", "V3_", "FINAL_WAVE", "FINAL_DRAIN", "FINAL_RITUAL",
        "intermission-4", "control_reverse", "will_distortion",
        "tower_artillery", "storm_kite", "final_volley",
        "ABSORPTION_CHANNEL", "JUDGMENT_CAST", "CATASTROPHE",
        "BossVirtualHealthPolicy", "WaveSixChamberController", "BossStage",
    )
    for path in (ROOT / "tests").glob("RunEndRift*Live.ps1"):
        text = read(path)
        for token in forbidden:
            assert token not in text, f"{path}: stale live-probe token {token}"


def test_official_probe_keeps_the_pre_ritual_cursor_for_w1() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(r"\$runLogOffset\s*=\s*Get-LogLength", probe)
    assert re.search(r"\$ritualOffset\s*=\s*\$runLogOffset", probe)
    assert re.search(
        r"\$waveOffset\s*=\s*\$ritualOffset",
        probe,
    ), "W1 cursor must include the same-tick ritual-to-wave transition"
    assert not re.search(
        r"RITUAL_COMPLETED[\s\S]*?\$waveOffset\s*=\s*Get-LogLength",
        probe,
    ), "W1 wait must not reset its cursor after RITUAL_COMPLETED"


def test_official_probe_carries_wave_completion_cursor_into_transition() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(r"function Wait-Transition[\s\S]*?\[int64\]\$AfterOffset", probe)
    assert re.search(r"function Wait-WaveComplete[\s\S]*?return \$offset", probe)
    for wave in (1, 2, 3, 5, 6):
        assert re.search(
            rf"Wait-Transition\s+-CompletedWave\s+{wave}\s+-Pads\s+\$pads\s+-AfterOffset",
            probe,
        ), f"wave {wave} transition must reuse its completion cursor"
    assert re.search(
        r"function Wait-CoreRestoration[\s\S]*?END_RIFT_CORE_RESTORATION_STARTED"
        r"[\s\S]*?END_RIFT_CORE_RESTORATION_COMPLETED"
        r"[\s\S]*?END_RIFT_WAVE_STARTED.*wave=5",
        probe,
    ), "wave 4 must pass through the explicit core-restoration stage"
    assert not re.search(
        r"Wait-Transition\s+-CompletedWave\s+4\s+-Pads",
        probe,
    ), "wave 4 must not use an intermission-rune transition"
    assert re.search(r"Write-Evidence .*CURRENT_TRANSITION_PASS.*\| Out-Null[\s\S]*?return \[int64\]\$offset", probe)


def test_official_probe_can_override_completion_repositioning_for_wave4() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(
        r"function Wait-WaveComplete[\s\S]*?\[scriptblock\]\$Action",
        probe,
    ), "Wave completion helper must accept a caller-owned positioning action"
    assert re.search(
        r"function Wait-WaveComplete[\s\S]*?\$completionAction[\s\S]*?"
        r"Wait-Log\s+-AfterOffset \$offset[\s\S]*?-Action \$completionAction",
        probe,
    ), "Wave 4 must not be silently repositioned to the generic combat ring"


def test_official_probe_carries_wave7_completion_cursor_into_boss_transition() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(
        r"\$waveSevenTransitionOffset\s*=\s*Wait-WaveComplete\s+-Wave 7",
        probe,
    ), "Wave 7 completion must expose its pre-completion cursor"
    assert re.search(
        r"\$bossOffset\s*=\s*\$waveSevenTransitionOffset",
        probe,
    ), "boss waits must reuse the Wave 7 cursor because cinematic markers may share its completion tick"
    assert not re.search(
        r"Wait-WaveComplete\s+-Wave 7[\s\S]*?\$bossOffset\s*=\s*Get-LogLength",
        probe,
    ), "the boss wait must not reset its cursor after Wave 7 completion"


def test_end_rift_gate_keeps_pinned_paper_api_on_persistence_classpath() -> None:
    gate = read(ROOT / "tests" / "RunEndRiftEventChecks.ps1")
    assert re.search(
        r"\$paperApiJar\s*=\s*\$env:PAPER_API_JAR[\s\S]*?"
        r"\$persistenceClasspath\s*=\s*@\(\$testBuild,\s*\$pluginClasses,\s*\$paperApiJar\)",
        gate,
    ), "clean CI runners must include the pinned Paper API when compiling persistence tests"


def test_official_probe_keeps_same_tick_objective_completion_markers() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(
        r"Wait-WaveComplete\s+-Wave 5[\s\S]*?-AfterOffset \$fogOffset",
        probe,
    ), "Wave 5 must reuse the fog cursor because completion markers share a tick"
    assert re.search(
        r"Wait-WaveComplete\s+-Wave 7[\s\S]*?-AfterOffset \$chamberOffset",
        probe,
    ), "Wave 7 must reuse the chamber cursor because completion markers share a tick"
    assert re.search(
        r"Wait-WaveComplete\s+-Wave 6[\s\S]*?-AfterOffset \$ringsOffset",
        probe,
    ), "Wave 6 must reuse the ring cursor because completion markers share a tick"


def test_official_wave_bot_aims_at_the_projectile_for_reflection() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "nearestWave4ObeliskDisplay" in bot
    assert "target=projectile" in bot
    assert "origin=${sourceAnchor ? 'known' : 'nearest'}" in bot
    assert "flags: { onGround, hasHorizontalCollision: undefined }" in bot


def test_official_wave7_bot_has_room_local_autopilot() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "ACTIVE_WAVE7" in bot
    assert "wave7Autopilot" in bot
    assert "sameWave7Chamber" in bot
    assert re.search(
        r"CHAMBERS_ASSIGNED[\s\S]*?ACTIVE_WAVE7",
        probe,
    ), "the official probe must enable room-local navigation after chamber assignment"
    assert "Get-OfflinePlayerUuid" in probe
    assert re.search(
        r"CHAMBERS_ASSIGNED[\s\S]*?Teleport-PlayersToChambers",
        probe,
    ), "the probe must restore the server's UUID-sorted room assignment after transition-pad teleports"
    assert "Teleport-PlayersToNearestChamberMob" in probe
    assert re.search(
        r"END_RIFT_CHAMBERS_COMPLETE.*?WaitSeconds 900\s+-Action \{ Teleport-PlayersToNearestChamberMob \}",
        probe,
    ), "Wave 7 probe must keep its test-only repositioning inside the chamber wait"
    assert re.search(
        r"Wait-WaveComplete\s+-Wave 7[\s\S]*?Set-Content[\s\S]*?ACTIVE",
        probe,
    ), "the official probe must return bots to normal boss combat after Wave 7"


def test_official_boss_probe_handles_last_seal_guardians() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "END_RIFT_GUARDIAN_PROBE_NAMES" in bot
    assert "guardianProbeEnabled" in bot
    assert "!wave7Autopilot && !guardianProbeEnabled" in bot
    assert re.search(
        r"END_RIFT_GUARDIAN_PROBE_NAMES\s*=\s*\$playerNames\s*-join\s*','",
        probe,
    ), "the official boss probe must exercise the real Last Seal guardian path with every client"
    assert "Teleport-GuardianProbeToNearest" in probe
    assert re.search(
        r"BOSS_DEFEAT_COMMITTED[\s\S]*?Teleport-GuardianProbeToNearest",
        probe,
    ), "the Last Seal probe must keep the guardian client in range after tentacle throws"


def test_official_probe_handles_a_carrier_picked_up_before_position_read() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(
        r"chargePosition[\s\S]*?CARRIER_PICKED_UP[\s\S]*?chargeId",
        probe,
    ), "the Wave 1 probe must handle the display disappearing after an immediate pickup"
    assert re.search(
        r"Wait-Log\s+-AfterOffset \$carrierOffset\s+-Pattern \$pickupPattern",
    probe,
    ), "pickup confirmation must remain scoped to the current carrier cursor"
