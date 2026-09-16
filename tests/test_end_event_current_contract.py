"""Static/asset contract for the current End Rift encounter.

This file intentionally checks the source of truth rather than the generated
runtime directory.  It is a small CI guard against accidentally bringing a
removed encounter generation back into the official flow.
"""

from __future__ import annotations

import json
import hashlib
import re
import struct
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
PLUGIN_SRC = PLUGIN / "src" / "me" / "copimine" / "endevent"
DOMAIN = PLUGIN_SRC / "domain"
RUNTIME = PLUGIN_SRC / "runtime"
CLIENT = ROOT / "CopiMineClient"
CLIENT_JAVA = CLIENT / "src" / "main" / "java" / "me" / "copimine" / "client"
CLIENT_ASSETS = CLIENT / "src" / "main" / "resources" / "assets" / "copimineclient"
DISTRIBUTED_CLIENT_JAR = ROOT / "thirdparty" / "client-mods" / "CopiMineClient-0.1.1.jar"
PACK = ROOT / "resourcepacks"
PACK_ASSETS = PACK / "src" / "assets" / "copimine"
DOCS = ROOT / "docs"


CURRENT_WAVES = [
    "RIFT_CARRIERS",
    "RIFT_HUNT",
    "RIFT_GATES",
    "OBELISK_ASSAULT",
    "BLACK_FOG",
    "RITUAL_SPHERE",
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


def test_staged_client_artifact_matches_current_source_build() -> None:
    source_jar = CLIENT / "build" / "libs" / "CopiMineClient-0.1.1.jar"
    assert source_jar.is_file(), f"source-built client artifact is missing: {source_jar}"
    assert DISTRIBUTED_CLIENT_JAR.is_file(), (
        f"staged client artifact is missing: {DISTRIBUTED_CLIENT_JAR}"
    )

    source_bytes = source_jar.read_bytes()
    staged_bytes = DISTRIBUTED_CLIENT_JAR.read_bytes()
    source_sha256 = hashlib.sha256(source_bytes).hexdigest()
    staged_sha256 = hashlib.sha256(staged_bytes).hexdigest()
    assert staged_sha256 == source_sha256, (
        "staged CopiMineClient JAR is not the artifact produced from the current source: "
        f"source={source_sha256} staged={staged_sha256}"
    )

    manifest = json.loads(read(ROOT / "thirdparty" / "thirdparty_manifest.json"))
    client_rows = [
        row for row in manifest["artifacts"]["clientMods"]
        if row.get("path") == "thirdparty/client-mods/CopiMineClient-0.1.1.jar"
    ]
    assert len(client_rows) == 1
    assert client_rows[0]["sha256"] == source_sha256
    assert client_rows[0]["sha1"] == hashlib.sha1(source_bytes).hexdigest()

    checksums = read(ROOT / "thirdparty" / "checksums.txt")
    assert f"SHA256  thirdparty/client-mods/CopiMineClient-0.1.1.jar  {source_sha256}" in checksums


def test_modpack_manifest_matches_the_staged_archive() -> None:
    archive = ROOT / "thirdparty" / "CopiMineMods.zip"
    assert archive.is_file(), f"staged modpack archive is missing: {archive}"

    manifest = json.loads(read(ROOT / "thirdparty" / "thirdparty_manifest.json"))
    archive_bytes = archive.read_bytes()
    archive_metadata = manifest["clientArchive"]
    assert archive_metadata["path"] == "thirdparty/CopiMineMods.zip"
    assert archive_metadata["sha1"] == hashlib.sha1(archive_bytes).hexdigest()
    assert archive_metadata["sha256"] == hashlib.sha256(archive_bytes).hexdigest()


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


def test_disposable_boss_cleanup_clears_test_wave_marker() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    match = re.search(
        r"private void restoreSafePhaseAfterDisposableBossCleanup\(\)\s*\{"
        r"(?P<body>[\s\S]*?)\n    \}\n\n    private void clearBossOnly",
        root,
    )
    assert match, "disposable cleanup method must remain a small, inspectable boundary"
    body = match.group("body")
    assert "clearWaveObjectiveState();" in body
    assert "activeWave = 0;" in body
    assert "if (!isPersistedBossPhase(phase))" not in body, (
        "test cleanup must clear the diagnostic wave marker even when the official phase is COLLECTING"
    )


def test_manual_disposable_wave_clear_resets_transient_wave_marker() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    match = re.search(
        r"private void handleWave\(CommandSender sender, String\[\] args\)\s*\{"
        r"(?P<body>[\s\S]*?)\n    \}\n\n    private void handleBoss",
        root,
    )
    assert match, "wave command handler must remain inspectable"
    clear_match = re.search(
        r'if \("clear"\.equalsIgnoreCase\(args\[1\]\)[^\{]*\{'
        r"(?P<body>[\s\S]*?)\n        \}",
        match.group("body"),
    )
    assert clear_match, "manual wave clear branch must remain inspectable"
    clear_body = clear_match.group("body")
    assert "activeWave = 0;" in clear_body, (
        "clearing a disposable test wave must not leave a stale wave number in READY_FOR_PLAYERS"
    )
    assert "saveStateSync" in clear_body, (
        "disposable wave cleanup must persist its restored transient state"
    )


def test_disposable_wave_natural_completion_restores_phase_without_advancing_event() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    assert "boolean disposableWave = testWaveFrontVisualMode && !isOfficialAttempt();" in root
    assert "DISPOSABLE_WAVE_NATURAL_COMPLETE" in root
    assert re.search(
        r"if \(testWaveFrontVisualMode && activeWave >= 1 && activeWave <= 7\s*&&",
        root,
    )
    assert "activeWave = 0;" in root


def test_wave6_ritual_sphere_uses_server_owned_drain_and_exact_scaling() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    scaling = read(DOMAIN / "RitualSphereScalingPolicy.java")
    health = read(DOMAIN / "RitualPrisonerHealthPolicy.java")
    snapshot = read(DOMAIN / "RitualSphereEncounterSnapshot.java")
    assert "case RITUAL_SPHERE ->" in source
    assert "startRitualSphereObjective(world, core);" in source
    assert "tickCurrentRitualSphereObjective(now);" in source
    assert "WAVE6_RITUAL_SPHERE_READY" in source
    assert "authority=server" in source
    assert "DRAIN_INTERVAL_MILLIS = 20_000L" in health
    assert "DRAIN_HEALTH = 2.0D" in health
    assert "MIN_HEALTH = 1.0D" in health
    assert "RITUAL_SPHERE_ZONE_SIZE = 4" in source
    assert "RitualSphereEncounterSnapshot" in snapshot
    for marker in (
        "new Profile(count, 4, 12, 1, 1, count == 2 ? 0 : 1, 13)",
        "new Profile(count, 4, 12, 2, 1, 1, 12)",
        "new Profile(count, 5, 15, 3, 2, 2, 11)",
        "new Profile(count, 5, 15, 4, 2, 2, 10)",
        "new Profile(count, 6, 18, 5, 3, 3, 9)",
    ):
        assert marker in scaling


def test_wave6_ritual_sphere_visuals_keep_wave_ownership_and_rehydrate() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    start = source.index("private void spawnRitualSphereVisual")
    end = source.index("private void tagRitualPrisoner", start)
    body = source[start:end]
    assert "tag(display, EVENT_KIND_DISPLAY, 6, true)" in body
    assert "setPersistent(true)" in body
    assert "end_event_ritual_sphere" in body
    assert "restorePersistedRitualSphereObjective" in source
    assert "restorePersistedRitualSphereObjective();" in source
    assert "RITUAL_SPHERE_HEIGHT_OFFSET" in source


def test_wave6_legacy_rings_are_not_rendered_or_ticked_live() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    tick_start = source.index("private boolean tickCurrentObjective")
    tick_end = source.index("private boolean tickWaveObjective", tick_start)
    tick_body = source[tick_start:tick_end]
    assert "case RITUAL_SPHERE -> tickCurrentRitualSphereObjective(now);" in tick_body
    assert "case COLLAPSE_RINGS -> getLogger().fine(\"WAVE6_LEGACY_COLLAPSE_RING_NOT_TICKED" in tick_body

    render_start = source.index("private void renderWaveObjective")
    render_end = source.index("/** Render the same three radii", render_start)
    render_body = source[render_start:render_end]
    assert "renderCurrentRitualSphere(core, now);" in render_body
    assert "renderCurrentCollapseRings(core, now);" not in render_body

    containment_start = source.index("private CollapseRingEncounterPolicy.State activeCollapseRingForContainment")
    containment_end = source.index("private boolean collapseRingPlayerAssigned", containment_start)
    containment_body = source[containment_start:containment_end]
    assert "Objective.COLLAPSE_RINGS" in containment_body
    assert "activeWave == 6" in containment_body


def test_wave_three_portals_use_the_upright_gate_model() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    start = source.index("private List<UUID> spawnPortalModelVisual")
    end = source.index("private ItemDisplay spawnPortalModelLayer", start)
    body = source[start:end]

    assert "MODEL_RIFT_GATE" in body
    assert '"end_event_rift_gate"' in body
    assert "MODEL_PORTAL_OVERLAY" not in body


def test_spider_renderer_uses_an_adapted_custom_model() -> None:
    model = CLIENT_JAVA / "RiftSpiderModel.java"
    renderer = CLIENT_JAVA / "RiftSpiderModelRenderer.java"
    mixin = CLIENT_JAVA / "mixin" / "SpiderEntityRendererMixin.java"
    render_mixin = CLIENT_JAVA / "mixin" / "LivingEntityRendererMixin.java"
    assert model.is_file()
    assert renderer.is_file()
    assert mixin.is_file()
    assert_contains(model, "right_hind_leg", "left_front_leg", "rift_core", "rift_shell")
    assert_contains(renderer, "RiftSpiderModel")
    assert_contains(render_mixin, "render(Lnet/minecraft/entity/LivingEntity", "RiftSpiderModelRenderer", "copimine$activeModel")
    assert "end_rift_user_spider.png" in read(CLIENT_JAVA / "EndEventTextureCatalog.java")


def test_spider_renderer_model_selection_does_not_mutate_shared_model_state() -> None:
    mixin = read(CLIENT_JAVA / "mixin" / "LivingEntityRendererMixin.java")
    assert "@Redirect" in mixin
    assert "opcode = Opcodes.GETFIELD" in mixin
    assert "copimine$activeModel" in mixin
    assert "copimine$spiderModelSwap" not in mixin
    assert "copimine$spiderModelSwap.restore()" not in mixin
    assert "model = (M)" not in mixin


def test_end_rift_uses_the_vanilla_bossbar_until_custom_hud_is_reworked() -> None:
    client = read(CLIENT_JAVA / "CopiMineClient.java")
    mixins = read(CLIENT / "src" / "main" / "resources" / "copimineclient.mixins.json")
    server = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    assert "EndRiftBossBarHud.render" not in client
    assert "EndRiftBossBarHudMixin" not in mixins
    update_start = server.index("private void updateCurrentBossBar")
    update_end = server.index("private void finishBossCast", update_start)
    update_body = server[update_start:update_end]
    assert "sendCurrentBossBarVisualUpdate" not in update_body
    bind_start = server.index("private void bindBossClient")
    bind_end = server.index("private void sendBossPhaseVisualUpdate", bind_start)
    bind_body = server[bind_start:bind_end]
    assert "sendBossBarVisualUpdate" not in bind_body


def test_bound_guardian_does_not_render_the_vanilla_enderman_eyes_layer() -> None:
    mixin = CLIENT_JAVA / "mixin" / "EndermanEyesFeatureRendererMixin.java"
    mixins = read(CLIENT / "src" / "main" / "resources" / "copimineclient.mixins.json")
    assert mixin.is_file(), "the vanilla Enderman eyes layer must have a scoped suppression hook"
    assert "EndermanEyesFeatureRendererMixin" in mixins
    assert_contains(
        mixin,
        "EyesFeatureRenderer.class",
        "ClientBridgeProtocol.isBoundEndBoss",
        "callback.cancel()",
    )


def test_leaving_visual_audience_clears_player_scoped_client_state() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    start = source.index("private void refreshClientBindingsForOnlinePlayers")
    end = source.index("private void refreshClientBindingsForPlayer", start)
    body = source[start:end]
    branch = re.search(
        r"if \(!isEventVisualViewer\(player\)\)\s*\{([\s\S]*?)\n\s*\}",
        body,
    )
    assert branch, "viewer-loss branch must remain explicit"
    branch_body = branch.group(1)
    assert "clearClientEffects(player);" in branch_body
    assert branch_body.index("clearClientEffects(player);") < branch_body.index(
        "clientBindingReadyPlayers.remove(uuid);"
    )


def test_wave7_barriers_validate_or_repair_chambers_before_clearing_visuals() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    start = source.index("private void spawnRealitySplitBarriers")
    end = source.index("/** Remove one completed adjacent room boundary", start)
    body = source[start:end]

    assert "ensureRealitySplitChamberAssignment" in body
    assert body.index("if (!ensureRealitySplitChamberAssignment())") < body.index(
        'clearRealitySplitBarriers("wave7-rebuild")'
    )


def test_wave7_player_containment_covers_move_watchdog_join_and_respawn() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    tick_start = source.index("private void tick()")
    tick_end = source.index("private void updatePadOccupancy", tick_start)
    tick = source[tick_start:tick_end]
    assert "containRealitySplitPlayers();" in tick

    move_start = source.index("public void onRealitySplitPlayerMove")
    move_end = source.index("public void onRealitySplitPlayerTeleport", move_start)
    move = source[move_start:move_end]
    assert "PlayerMoveEvent" in move
    assert "RealitySplitPlayerTeleportPolicy.allows" in move
    assert "event.setTo(from)" in move

    join_start = source.index("public void onPlayerJoin")
    join_end = source.index("public void onPlayerQuit", join_start)
    join = source[join_start:join_end]
    # Reconnects are deliberately sent to the chamber center first. The
    # regular containment watchdog then enforces the same room boundary.
    assert "teleportRealitySplitPlayerToChamberCenter" in join

    respawn_start = source.index("public void onPlayerRespawn")
    respawn_end = source.index("private void tickOfflineRosterGrace", respawn_start)
    respawn = source[respawn_start:respawn_end]
    assert "containRealitySplitParticipant" in respawn


def test_creative_full_run_cleans_transient_wave_state() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    match = re.search(
        r"private void finishCreativeTest\(boolean success, String reason\)\s*\{"
        r"(?P<body>[\s\S]*?)\n    \}\n\n    private void handleWave",
        root,
    )
    assert match, "creative full-run cleanup method must remain inspectable"
    body = match.group("body")
    assert "clearWaveObjectiveState();" in body, (
        "creative full-run cleanup must remove objective visuals, barriers, rings and timers"
    )
    assert re.search(r"activeWave\s*=\s*0\s*;", body), (
        "creative full-run cleanup must not leave the disposable wave marker active"
    )
    assert "saveStateSync" in body, (
        "creative full-run cleanup must persist the restored transient state"
    )
    assert "creativeTestParticipantSnapshot" in body, (
        "creative full-run cleanup must remember the pre-test participant state"
    )
    assert "participantUuids.clear();" in body
    assert "participantUuids.addAll(creativeTestParticipantSnapshot);" in body


def test_creative_full_run_snapshots_participants_before_enabling_test_ai() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    start = root.index("private void startCreativeTest")
    end = root.index("private boolean officialCombatStateActive", start)
    body = root[start:end]
    assert "creativeTestParticipantSnapshot.clear();" in body
    assert "creativeTestParticipantSnapshot.addAll(participantUuids);" in body
    assert body.index("creativeTestParticipantSnapshot.addAll(participantUuids);") < body.index(
        "testCombatAiMode = true;"
    )


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
    assert_contains(store, "CURRENT_SCHEMA", "writeCurrent", "rewards.night-cloak-rolls",
                    "objective.progress.entries", "objectiveProgressEntries", "objectiveProgress")
    assert 'yaml.set("objective.progress", snapshot.objectiveProgress())' not in read(store)
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


def test_server_visual_diagnostics_report_the_actual_client_catalog() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    visual_match = re.search(
        r"private void handleTestVisuals\(CommandSender sender, String\[\] args\)\s*\{"
        r"(?P<body>[\s\S]*?)\n    \}\s*(?=/\*\*|private void startCreativeTest)",
        root,
    )
    assert visual_match, "visual diagnostic handler must remain inspectable"
    visual_body = visual_match.group("body")
    assert "clientVisualResourcePath(visual)" in visual_body
    assert "visual.toLowerCase(Locale.ROOT) + \".png\"" not in visual_body
    assert "clientBossResourcePath()" in visual_body
    assert "rift_guardian_\" + requestedPhase + \".png" not in visual_body

    mapping_match = re.search(
        r"private String clientVisualResourcePath\(String visualId\)\s*\{"
        r"(?P<body>[\s\S]*?)\n    \}\n\n    private String clientBossResourcePath",
        root,
    )
    assert mapping_match, "server visual-resource mapping must be explicit"
    mapping = mapping_match.group("body")
    for name in (
        "end_rift_user_enderman.png",
        "end_rift_elite.png",
        "end_rift_user_spider.png",
        "end_rift_skeleton.png",
        "end_rift_elite_skeleton.png",
    ):
        assert name in mapping, name
    assert "end_rift_user_boss.png" in root

    # ItemDisplay visuals are resolved by the server resource pack, not by
    # the optional Fabric client jar.  Keep this contract tied to the actual
    # runtime namespace so diagnostics cannot report a made-up client path.
    assert 'assets/copimine/textures/item/end_event_rift_obelisk_full_hd.png' in mapping
    assert 'assets/copimine/textures/item/end_event_rift_obelisk_damaged_hd.png' in mapping
    assert 'assets/copimine/textures/item/end_event_rift_obelisk_critical_hd.png' in mapping
    assert 'assets/copimine/textures/item/end_event_rift_fireball_hd.png' in mapping
    assert 'assets/copimine/textures/item/end_event_rift_tentacle_hd.png' in mapping
    assert 'assets/copimineclient/textures/entity/end_event_rift_obelisk_full_hd.png' not in mapping
    assert 'assets/copimineclient/textures/entity/end_event_rift_fireball_hd.png' not in mapping
    assert 'assets/copimineclient/textures/entity/end_rift_tentacle_hd.png' in mapping
    assert 'server=' in mapping
    assert ';client=' in mapping


def test_live_visual_probe_verifies_creative_cleanup_state() -> None:
    script = read(ROOT / "tests" / "RunEndRiftVisualFivePlayerLive.ps1")
    assert "CURRENT_CREATIVE_CLEANUP_PASS" in script
    assert "$baselineParticipantMatch" in script
    assert "$afterCreativeParticipantMatch" in script
    assert "baselineParticipants" in script
    assert re.search(
        r"\$creativeStatus\s*=\s*\(Invoke-LocalRcon 'cmend status'\)",
        script,
    )
    assert re.search(
        r"creativeStatus\s*-notmatch\s*'\(\?m\)wave=0\\s\+event-mobs=0\\s\+boss=none'",
        script,
    )
    assert "CURRENT_VISUAL_FINAL_CLEANUP_PASS" in script
    assert "$finalStatus" in script
    assert re.search(
        r"finalStatus\s*-notmatch\s*'\(\?m\)wave=0\\s\+event-mobs=0\\s\+boss=none'",
        script,
    )


def test_supplied_boss_geometry_and_animation_assets_are_runtime_bound() -> None:
    geometry_path = CLIENT_ASSETS / "models" / "entity" / "end_rift_guardian" / "geometry.json"
    geometry = json.loads(read(geometry_path))
    definitions = geometry["minecraft:geometry"]
    assert len(definitions) == 1
    definition = definitions[0]
    assert definition["description"]["texture_width"] == 16
    assert definition["description"]["texture_height"] == 16
    bones = definition["bones"]
    assert len(bones) == 16
    cubes = [cube for bone in bones for cube in bone.get("cubes", [])]
    assert len(cubes) >= 100
    assert all(set(cube["uv"]) == {"north", "south", "east", "west", "up", "down"}
               for cube in cubes)

    entity = CLIENT_ASSETS / "textures" / "entity"
    assert png_size(entity / "end_rift_user_boss.png") == (128, 128)
    assert png_size(entity / "end_rift_user_enderman.png") == (64, 32)
    assert png_size(entity / "end_rift_user_spider.png") == (64, 32)

    animation_dir = CLIENT_ASSETS / "models" / "entity" / "end_rift_guardian" / "animations"
    for name in ("idle.json", "running.json", "swipe.json", "hurt.json", "dying.json",
                 "udar_iz_grudi.json", "udar_po_zemle.animation.json"):
        animation = json.loads(read(animation_dir / name))
        assert len(animation["animations"]) == 1
        assert "animation_length" in next(iter(animation["animations"].values()))

    model = read(CLIENT_JAVA / "UserEndBossModelData.java")
    animator = read(CLIENT_JAVA / "UserEndBossAnimationPlayer.java")
    renderer = read(CLIENT_JAVA / "RiftGuardianModelRenderer.java")
    catalog = read(CLIENT_JAVA / "EndEventTextureCatalog.java")
    state = read(CLIENT_JAVA / "EndEventClientState.java")
    assert "applyExactFaceUv" in model
    assert "ModelPart.Quad" in model
    assert '"body".equals(sourceName)' in model
    assert "UserEndBossAnimationPlayer.apply" in read(CLIENT_JAVA / "RiftGuardianModel.java")
    assert "bossAnimationElapsedMillisForEntity" in state
    assert "startedAtMillis" in state
    assert "bossAnimationElapsedTicksForEntity" in read(CLIENT_JAVA / "ClientBridgeProtocol.java")
    assert "setAnimationElapsedTicks" in read(CLIENT_JAVA / "RiftGuardianModel.java")
    assert "System.currentTimeMillis()" in read(CLIENT_JAVA / "mixin" / "LivingEntityRendererMixin.java")
    assert "udar_iz_grudi.json" in animator
    assert "udar_po_zemle.animation.json" in animator
    assert "end_rift_user_boss.png" in renderer
    assert "end_rift_user_enderman.png" in catalog
    assert "end_rift_user_spider.png" in catalog


def test_distributed_client_jar_contains_the_current_boss_assets() -> None:
    assert DISTRIBUTED_CLIENT_JAR.is_file(), DISTRIBUTED_CLIENT_JAR
    with ZipFile(DISTRIBUTED_CLIENT_JAR) as archive:
        names = set(archive.namelist())
    required = {
        "me/copimine/client/UserEndBossModelData.class",
        "me/copimine/client/UserEndBossAnimationPlayer.class",
        "me/copimine/client/EndRiftBossBarHud.class",
        "me/copimine/client/RiftSpiderModel.class",
        "me/copimine/client/RiftSpiderModelRenderer.class",
        "me/copimine/client/mixin/LivingEntityRendererMixin.class",
        "assets/copimineclient/models/entity/end_rift_guardian/geometry.json",
        "assets/copimineclient/textures/entity/end_rift_user_boss.png",
        "assets/copimineclient/textures/gui/end_rift_bossbar_frame.png",
        "assets/copimineclient/textures/entity/end_rift_user_enderman.png",
        "assets/copimineclient/textures/entity/end_rift_user_spider.png",
        "assets/copimineclient/textures/entity/end_rift_elite.png",
        "assets/copimineclient/textures/entity/end_rift_skeleton.png",
        "assets/copimineclient/textures/entity/end_rift_elite_skeleton.png",
        "assets/copimineclient/models/entity/end_rift_guardian/animations/udar_iz_grudi.json",
        "assets/copimineclient/models/entity/end_rift_guardian/animations/udar_po_zemle.animation.json",
    }
    missing = required - names
    assert not missing, f"distributed client jar is stale, missing {sorted(missing)}"


def test_gate_has_a_runtime_model_and_lifecycle_binding() -> None:
    root = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    manifest = json.loads(read(PACK / "models_manifest.json"))
    gate = next((row for row in manifest["items"] if row.get("id") == "end_event_rift_gate"), None)
    assert gate is not None, "the End Rift gate must have a dedicated resource-pack model"
    assert gate["custom_model_data"] == 830018
    assert gate["base_material"] == "paper"
    model_path = PACK / "src" / "assets" / "copimine" / "models" / "item" / "end_event_rift_gate.json"
    assert model_path.is_file()
    model = json.loads(read(model_path))
    assert model.get("parent") == "minecraft:block/block"
    assert len(model.get("elements", [])) >= 5
    builder = read(PACK / "build-resourcepack.py")
    assert "assets/copimine/models/item/end_event_rift_gate.json" in builder
    assert "830018" in read(PACK / "models_manifest.json")
    assert '"custom_model_data": 830018' in read(PACK / "build" / "_stage" / "assets" / "minecraft" / "models" / "item" / "paper.json")
    assert "MODEL_RIFT_GATE = 830018" in root
    assert "ensureGateModelVisual" in root
    assert "clearGateModelVisual" in root
    assert root.index("ensureGateModelVisual") < root.index("finishGateOpening")
    assert root.index("clearGateModelVisual") < root.index("finishGateOpening")


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


def test_spell_matrix_probe_keeps_bot_alive_for_full_matrix() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftSpellMatrixLive.ps1")
    assert re.search(
        r"\[ValidateRange\(240,\s*600\)\][\s\S]*?\[int\]\$BotDurationSeconds\s*=\s*300",
        probe,
    ), "spell matrix bot must outlive music, spell, and final-strike probes"


def test_multiplayer_wrappers_allow_the_full_scaled_boss_run() -> None:
    for wrapper in (
        ROOT / "tests" / "RunEndRiftOfficialFivePlayerLive.ps1",
        ROOT / "tests" / "RunEndRiftOfficialTenPlayerLive.ps1",
    ):
        probe = read(wrapper)
        assert re.search(
            r"\[ValidateRange\(900,\s*3600\)\][\s\S]*?"
            r"\[int\]\$BotDurationSeconds\s*=\s*3600",
            probe,
        ), f"{wrapper.name} must keep clients alive through the scaled boss"
        assert re.search(
            r"\[ValidateRange\(600,\s*3500\)\][\s\S]*?"
            r"\[int\]\$TimeoutSeconds\s*=\s*3500",
            probe,
        ), f"{wrapper.name} must wait long enough for the scaled boss"


def test_multiplayer_wave1_delivery_moves_only_each_authoritative_holder() -> None:
    driver = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    pickup_branch = re.search(
        r"# Every charge can be picked up by a different participant\.[\s\S]*?"
        r"Teleport-Player \$pickedHolderMatch\.Groups\[1\]\.Value[\s\S]*?"
        r"\$activeCharge = \$null",
    driver,
    )
    assert pickup_branch, "Wave 1 delivery branch must teleport every charge holder"
    branch = pickup_branch.group(0)
    assert "Teleport-Player $pickedHolderMatch.Groups[1].Value" in branch, (
        "multi-player delivery must move only the authoritative charge holder so "
        "later participants are delivered without stacking the roster"
    )
    assert "$playersTeleportedToCore" not in driver, (
        "multi-player delivery must not stack every client on the Core"
    )


def test_multiplayer_wave1_delivery_moves_the_authoritative_picked_holder() -> None:
    driver = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wait_block = re.search(
        r"function\s+Wait-CarrierDelivery[\s\S]*?"
        r"throw\s+\"Timed out waiting for Wave 1",
        driver,
    )
    assert wait_block, "Wave 1 delivery wait block is missing"
    body = wait_block.group(0)
    assert "END_RIFT_CARRIER_PICKED_UP" in body
    assert "player_name=([A-Za-z0-9_]{1,16})" in body, (
        "delivery probe must read the player name from the authoritative pickup marker"
    )
    assert "Teleport-Player $pickedHolderMatch.Groups[1].Value" in body, (
        "delivery probe must teleport the player who actually picked up the charge, "
        "not whichever roster slot was scheduled for that delivery"
    )


def test_wave1_harness_resolves_named_players_without_unsupported_uuid_selectors() -> None:
    driver = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    source = read(
        ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
    )
    assert "player_name=" in source, (
        "the authoritative carrier pickup marker must expose the player name "
        "so the local Paper driver can target that player without UUID selectors"
    )
    assert re.search(
        r"END_RIFT_CARRIER_SELECTED[\s\S]*?location=",
        source,
    ), "carrier selection must expose a runtime location for the combat probe"
    assert "Teleport-PlayerUuid" not in driver
    assert "@a[uuid=" not in driver
    assert "@e[uuid=" not in driver
    assert "player_name=([A-Za-z0-9_]{1,16})" in driver
    assert "Teleport-Player $pickedHolderMatch.Groups[1].Value" in driver


def test_multiplayer_wave3_capture_does_not_stack_the_roster_on_each_portal() -> None:
    driver = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    capture_block = re.search(
        r"for \(\$portalIndex = 0; \$portalIndex -lt 3; \$portalIndex\+\+\)[\s\S]*?"
        r"\$waveThreeTransitionOffset = Wait-WaveComplete",
        driver,
    )
    assert capture_block, "Wave 3 portal capture loop is missing"
    body = capture_block.group(0)
    assert re.search(
        r"\$capturePlayer\s*=\s*\$playerNames\[\$portalIndex\s*%\s*\$playerNames\.Count\][\s\S]*?"
        r"Teleport-Player\s+\$capturePlayer",
        body,
    ), "Wave 3 must place one authoritative player on each active portal"
    assert "Teleport-PlayersToPoint $portalX" not in body
    assert "Teleport-PlayersToPoint $actionX" not in body


def test_wave1_charge_probe_uses_authoritative_logged_locations() -> None:
    driver = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(
        r"function\s+Parse-LoggedLocation[\s\S]*?"
        r"location=\[\^\\s\]\+",
        driver,
    ), "Wave 1 probe must parse runtime locations from authoritative server markers"
    assert "END_RIFT_CARRIER_SELECTED[^\\r\\n]*entity=" in driver
    assert "activeCarrierLocation = Parse-LoggedLocation $match.Value" in driver, (
        "Wave 1 probe must use the selected carrier's logged location"
    )


def test_combat_anchor_fallback_ignores_the_core_structure_when_runes_are_gone() -> None:
    source = read(
        ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
    )
    combat_level = re.search(
        r"private\s+int\s+combatLevelY\(\)\s*\{([\s\S]*?)\n\s*private\s+int\s+playableSurfaceScore",
        source,
    )
    assert combat_level, "combat level resolver is missing"
    body = combat_level.group(1)
    assert "playableSurfaceScore" in body, (
        "after ritual pads are removed, combat level must sample playable floor cells "
        "instead of treating the Core's vertical structure as the floor"
    )
    assert "return bestFloorY + 1" not in body, (
        "the old adjacent-structure heuristic can select the top of the Core as the combat floor"
    )


def test_wave_containment_watchdog_runs_every_server_tick() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    movement = read(DOMAIN / "CombatMovementPolicy.java")
    assert "waveContainmentTask" in source
    assert re.search(
        r"runTaskTimer\(\s*this,\s*this::tickWaveMobContainment,\s*1L,\s*1L\)",
        source,
    ), "wave containment must be checked every server tick"
    assert "tickWaveMobContainment" in source
    assert "CONTAINMENT_SAFETY_MARGIN_BLOCKS" in movement
    assert re.search(
        r"private void enforceWaveMobContainment\(\)[\s\S]*?double radius\s*=\s*waveMovementRadius\(\)",
        source,
    ), "wave leash must enter the boundary margin before native pathing can overshoot"


def test_single_boss_damage_probe_isolates_reach_from_real_health() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftBossDamageLive.ps1")
    assert re.search(
        r"boss spawn official confirm[\s\S]*?boss freeze[\s\S]*?boss damage 3000",
        probe,
    ), "single-boss survival probe must freeze the target before measuring HP"
    assert re.search(
        r"finally[\s\S]*?boss unfreeze[\s\S]*?boss kill cleanup",
        probe,
    ), "single-boss probe must release its local freeze during cleanup"
    assert "$checkpointHealth" in probe, (
        "single-boss probe must report the health checkpoint it actually measured"
    )
    assert "before=$checkpointHealth" in probe, (
        "single-boss live output must not contain a stale hard-coded HP value"
    )


def test_boss_bar_update_recreates_missing_bar_before_reading_audience() -> None:
    source = read(PLUGIN_SRC / "CopiMineEndEvent.java")
    method = re.search(
        r"private void updateCurrentBossBar\(LivingEntity boss\)\s*\{([\s\S]*?)\n\s*double max",
        source,
    )
    assert method and "ensureBossBar();" in method.group(1), (
        "every boss update path must restore the vanilla BossBar invariant"
    )


def test_obelisk_probe_primes_health_after_survival_protection() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftObeliskLive.ps1")
    assert re.search(
        r"gamemode spectator \$name[\s\S]*?attribute \$name minecraft:generic\.max_health base set 1024[\s\S]*?"
        r"attribute \$name minecraft:generic\.max_health base get[\s\S]*?gamemode survival \$name[\s\S]*?cmend test wave 4",
        probe,
    ), "obelisk reflection probe must verify max health before returning the bot to survival"
    assert re.search(
        r"effect clear \$name[\s\S]*?effect give \$name minecraft:resistance 120 4 true[\s\S]*?"
        r"effect give \$name minecraft:regeneration 120 4 true[\s\S]*?"
        r"attribute \$name minecraft:generic\.max_health base get",
        probe,
    ), "obelisk reflection probe must protect the bot before verifying its real max health"
    assert "minecraft:slow_falling" not in probe, (
        "the reflection probe must not combine slow falling with its jump loop and drift above the projectile"
    )
    assert "END_RIFT_OBELISK_PROBE_SUPPORT_NOT_AIR" in probe, (
        "the reflection probe must verify its temporary support cell before mutating the local arena"
    )
    assert "setblock 8 69 -46 minecraft:glass" in probe and "setblock 8 69 -46 air" in probe, (
        "the reflection probe's temporary support cell must be restored during cleanup"
    )


def test_obelisk_probe_uses_supported_player_max_health_commands() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftObeliskLive.ps1")
    assert "data merge entity $name {Health:1024f}" not in probe, (
        "Paper rejects direct NBT health writes for players; the live probe must not rely on that command"
    )
    assert re.search(
        r"attribute \$name minecraft:generic\.max_health base set 1024[\s\S]*?"
        r"attribute \$name minecraft:generic\.max_health base get[\s\S]*?"
        r"\$maxHealthMatch",
        probe,
    ), "the probe must verify the supported real max-health attribute instead of an impossible player NBT write"


def test_obelisk_probe_parses_attribute_value_after_command_prefix() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftObeliskLive.ps1")
    assert re.search(
        r"\$maxHealthMatch\s*=\s*\[Regex\]::Match\(\$maxHealthResult,\s*'is\\s\+",
        probe,
    ), "the probe must not parse digits from a player's name as the max-health value"


def test_obelisk_bot_retries_projectiles_until_paper_reach() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftObeliskBot.js")
    range_guard = re.search(
        r"const range\s*=\s*distance\(entity\.position,\s*bot\.entity\.position\)[\s\S]*?"
        r"if \(range > ([0-9.]+)\) \{([\s\S]*?)return\s*\n\s*\}",
        bot,
    )
    assert range_guard and float(range_guard.group(1)) <= 4.75, (
        "the obelisk bot must wait for a projectile to enter Paper's reliable interact reach"
    )
    assert bot.index("attempted.add(entity.id)") > range_guard.end(), (
        "a projectile must remain retryable while it is outside reliable interact reach"
    )
    assert "bot.setControlState('jump', true)" not in bot, (
        "the reflection probe must use a stable support position instead of climbing above the projectile"
    )


def test_official_wave7_probe_targets_every_supported_living_mob_type() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "@('enderman', 'skeleton', 'spider')" in probe, (
        "Wave 7 chamber positioning must enumerate every official living mob type"
    )
    assert "type=$entityType" in probe, (
        "Wave 7 helper must use the enumerated type when selecting targets"
    )


def test_official_obelisk_probe_covers_every_active_reflection_lane() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "$halfPi = [Math]::PI / 2.0D" in probe, (
        "obelisk lane angles must be computed as one scalar before array construction"
    )
    assert re.search(
        r"\$laneCount\s*=\s*Get-ObeliskCount\s+\$playerNames\.Count",
        probe,
    ), "multi-player obelisk probe must derive lanes from the active obelisk count"
    assert re.search(
        r"for \(\$lane = 0; \$lane -lt \$laneCount; \$lane\+\+\)[\s\S]*?"
        r"\$halfPi\s*\+\s*\(2\.0D \* \[Math\]::PI \* \$lane / \$laneCount\)",
        probe,
    ), (
        "multi-player obelisk probe must place reflection-capable clients on every active tower lane"
    )


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


def test_official_probe_does_not_teleport_players_every_combat_poll() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wait_block = re.search(
        r"function Wait-WaveComplete[\s\S]*?\n}\n\nfunction Wait-CoreRestoration",
        probe,
    )
    assert wait_block is not None, "Wave completion helper is missing"
    body = wait_block.group(0)
    assert "Teleport-PlayersIfOutsideCombatArea" in body, (
        "combat completion polling must only recover players that actually left the arena"
    )
    default_action = re.search(
        r"if \(\$null -eq \$completionAction\) \{([\s\S]*?)\n\s*}\n\s*Wait-Log",
        body,
    )
    assert default_action is not None, "Wave completion default action is missing"
    assert "Teleport-PlayersToCombatRing" not in default_action.group(1), (
        "repositioning every 500ms prevents the real bot from walking to a Wave 2 target"
    )


def test_official_probe_does_not_reset_obelisk_positions_every_poll() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wave4_block = re.search(
        r"\$w4Offset\s*=\s*\$waveFourStartOffset[\s\S]*?"
        r"Write-Evidence \"CURRENT_WAVE_PASS event=\$eventId wave=4",
        probe,
    )
    assert wave4_block is not None, "Wave 4 probe block is missing"
    body = wave4_block.group(0)
    assert "Teleport-PlayersToObeliskRing -Core $core" in body, (
        "Wave 4 must still place clients near the authored obelisk ring"
    )
    assert "Teleport-PlayersToObeliskRingIfDisplaced" in body, (
        "Wave 4 polling must have a conditional recovery path for knockback"
    )
    assert re.search(
        r"\$w4Offset[\s\S]*?Set-PlayerBotMode\s+-Mode PASSIVE[\s\S]*?"
        r"Teleport-PlayersToObeliskRing",
        body,
    ), "Wave 4 probe must keep combat navigation from leaving reflection lanes"
    mode_helper = re.search(
        r"function Set-PlayerBotMode[\s\S]*?\n}\n\nfunction Start-PlayerBot",
        probe,
    )
    assert mode_helper and "Set-Content" in mode_helper.group(0), (
        "the shared bot-mode helper must persist the passive mode consumed by real clients"
    )
    assert re.search(
        r"\$waveFourTransitionOffset[\s\S]*?Set-PlayerBotMode\s+-Mode ACTIVE",
        body,
    ), "Wave 4 probe must resume real mob combat after the obelisk objective"
    assert not re.search(
        r"-Action\s+\{\s*Teleport-PlayersToObeliskRing\s+-Core\s+\$core\s*\}",
        body,
    ), "repositioning every 500ms prevents real clients from reflecting fireballs"


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


def test_official_probe_staggers_multi_client_login_burst() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert re.search(
        r"Prepare-AuthMeAccounts[\s\S]*?"
        r"END_RIFT_BOT_SKIP_REGISTER[\s\S]*?"
        r"foreach\s*\(\$name\s+in\s+\$playerNames\)[\s\S]*?"
        r"\$authOffset\s*=\s*Get-LogLength[\s\S]*?"
        r"Start-PlayerBot\s+-Name\s+\$name\s+-Core\s+\$core[\s\S]*?"
        r"Wait-PlayerAuthenticated\s+-Name\s+\$name\s+-AfterOffset\s+\$authOffset",
        probe,
    ), "official multi-client probe must serialize AuthMe handshakes without a connection burst"


def test_official_probe_uses_disposable_pre_registered_accounts() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert re.search(
        r"function\s+Prepare-AuthMeAccounts[\s\S]*?authme unregister[\s\S]*?"
        r"authme register \$name endrift-local",
        probe,
    ), "official probe must prepare disposable AuthMe accounts before connecting"
    assert "END_RIFT_BOT_SKIP_REGISTER" in bot and "if (!skipRegister)" in bot


def test_official_probe_does_not_flood_rcon_while_waiting_for_wave_one_charge() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wait_block = re.search(r"function\s+Wait-CarrierDelivery[\s\S]*?throw\s+\"Timed out waiting for Wave 1", probe)
    assert wait_block is not None, "Wave 1 delivery wait block is missing"
    assert "-Action" not in wait_block.group(0), (
        "10-player Wave 1 probe must not issue an unbounded RCON action callback"
    )
    assert all(
        re.search(
            rf"Wait-CarrierDelivery\s+-AfterOffset\s+\$carrierOffset[\s\S]*?"
            rf"-DeliveryNumber\s+{delivery_number}",
            probe,
        )
        for delivery_number in (1, 2, 3)
    ), "Wave 1 probe must wait on authoritative delivery progress rather than a stale charge UUID"
    assert "Parse-LoggedLocation" in probe
    assert "Teleport-PlayersToNearestWaveMobIfFar" in wait_block.group(0), (
        "Wave 1 delivery polling must keep one real client near the current mob "
        "until the carrier marker exists"
    )
    assert "@a[uuid=" not in probe and "@e[uuid=" not in probe, (
        "the local Paper driver must not rely on unsupported UUID selectors"
    )


def test_official_probe_does_not_revisit_historical_carrier_each_poll() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wait_block = re.search(
        r"function Wait-CarrierDelivery[\s\S]*?"
        r"throw \"Timed out waiting for Wave 1",
        probe,
    )
    assert wait_block is not None, "Wave 1 delivery wait block is missing"
    body = wait_block.group(0)
    carrier_block = re.search(
        r"\$carrierMatches\s*=\s*\[Regex\]::Matches[\s\S]*?"
        r"\$chargeMatches\s*=",
        body,
    )
    assert carrier_block is not None, "carrier selection scan is missing"
    carrier_body = carrier_block.group(0)
    assert re.search(
        r"if\s*\(\$carrierMatches\.Count\s*-gt\s*0\)[\s\S]*?"
        r"\$match\s*=\s*\$carrierMatches\[\$carrierMatches\.Count\s*-\s*1\]",
        carrier_body,
    ), "each poll must retain only the newest selected carrier marker"
    assert "foreach ($match in $carrierMatches)" not in carrier_body, (
        "replaying historical carrier markers causes a teleport reset-loop"
    )


def test_official_probe_recovers_to_the_current_carrier_without_replaying_history() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wait_block = re.search(
        r"function Wait-CarrierDelivery[\s\S]*?"
        r"throw \"Timed out waiting for Wave 1",
        probe,
    )
    assert wait_block is not None, "Wave 1 delivery wait block is missing"
    carrier_body = re.search(
        r"elseif \(\$null -ne \$activeCarrier[\s\S]*?"
        r"elseif \(\$null -eq \$activeCarrier",
        wait_block.group(0),
    )
    assert carrier_body is not None, "active carrier recovery branch is missing"
    assert "Teleport-PlayersToNearestWaveMobIfFar -Name $PickupPlayer" in carrier_body.group(0), (
        "the probe must conditionally follow the live carrier after its logged spawn position "
        "becomes stale"
    )


def test_official_probe_uses_a_windows_powershell_compatible_tick_counter() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "[Environment]::TickCount64" not in probe, (
        "Windows PowerShell on the local probe host does not expose Environment.TickCount64"
    )
    assert probe.count("[Environment]::TickCount") >= 3, (
        "combat, Wave 1 and Wave 4 recovery paths must use the supported tick counter"
    )


def test_official_probe_rotates_small_wave4_rosters_across_all_obelisk_lanes() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "function Teleport-PlayersToWave4ReflectionLaneIfDue" in probe, (
        "small official Wave 4 rosters need a bounded lane-rotation helper"
    )
    assert re.search(
        r"function Teleport-PlayersToWave4ReflectionLaneIfDue[\s\S]*?"
        r"\[Environment\]::TickCount[\s\S]*?"
        r"lastWave4LaneChangeAt[\s\S]*?Get-ObeliskCount",
        probe,
    ), "Wave 4 lane rotation must be throttled and use the configured obelisk count"
    wave4_block = re.search(
        r"\$w4Offset\s*=\s*\$waveFourStartOffset[\s\S]*?"
        r"Write-Evidence \"CURRENT_WAVE_PASS event=\$eventId wave=4",
        probe,
    )
    assert wave4_block is not None, "Wave 4 probe block is missing"
    assert "Teleport-PlayersToWave4ReflectionLaneIfDue -Core $core" in wave4_block.group(0), (
        "Wave 4 completion polling must move a 2-3 player roster between authored lanes"
    )


def test_official_probe_pauses_mob_navigation_during_wave4_reflection() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    assert "function Set-PlayerBotMode" in probe, (
        "the official probe needs an explicit control-file mode switch"
    )
    wave4_block = re.search(
        r"\$w4Offset\s*=\s*\$waveFourStartOffset[\s\S]*?"
        r"Write-Evidence \"CURRENT_WAVE_PASS event=\$eventId wave=4",
        probe,
    )
    assert wave4_block is not None, "Wave 4 probe block is missing"
    wave4_text = wave4_block.group(0)
    assert "Set-PlayerBotMode -Mode PASSIVE" in wave4_text, (
        "Wave 4 must stop mob navigation so players stay on the reflection lane"
    )
    assert re.search(
        r"Wait-WaveComplete -Wave 4[\s\S]*?\n\s*Set-PlayerBotMode -Mode ACTIVE",
        wave4_text,
    ), "the probe must resume real mob attacks after Wave 4 completes"


def test_combat_bot_passive_mode_cancels_inflight_navigation() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    passive_block = re.search(
        r"function enterPassiveMode \(\) \{[\s\S]*?\n\}\n\nfunction enterActiveMode",
        bot,
    )
    assert passive_block is not None, "the combat bot passive-mode handler is missing"
    assert "stopNavigation()" in passive_block.group(0), (
        "passive mode must cancel an already-running navigation interval"
    )


def test_wave4_lane_helper_recovers_displacement_during_lane_dwell() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    helper = re.search(
        r"function Teleport-PlayersToWave4ReflectionLaneIfDue[\s\S]*?\n}\n\nfunction Get-OfflinePlayerUuid",
        probe,
    )
    assert helper is not None, "Wave 4 lane helper is missing"
    helper_text = helper.group(0)
    assert "$shouldRotate" in helper_text, (
        "lane rotation and position recovery need separate decisions"
    )
    assert re.search(
        r"\$shouldRotate[\s\S]*?\$targetX[\s\S]*?\$dx \* \$dx",
        helper_text,
    ), "the helper must still check the current player's distance on every poll"
    assert not re.search(
        r"-lt 20000L\)\s*\{\s*return\s*\}",
        helper_text,
    ), "a lane dwell must not skip recovery after mob knockback"


def test_wave4_lane_recovery_keeps_real_clients_inside_projectile_window() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    helper = re.search(
        r"function Teleport-PlayersToWave4ReflectionLaneIfDue[\s\S]*?\n}\n\nfunction Get-OfflinePlayerUuid",
        probe,
    )
    assert helper is not None, "Wave 4 lane helper is missing"
    helper_text = helper.group(0)
    assert "$laneTolerance = 0.75D" in helper_text, (
        "lane recovery must use a sub-block tolerance so a one-block mob nudge is corrected"
    )
    assert "$reflectionRadius = 7.0D" in helper_text, (
        "reflection lanes must stay at the clear inner radius instead of the east-wall collision edge"
    )
    assert re.search(
        r"\$targetX[\s\S]*\$reflectionRadius|\$targetZ[\s\S]*\$reflectionRadius",
        helper_text,
    ), "both coordinates must use the clear inner reflection radius"
    assert re.search(
        r"\$dx \* \$dx \+ \$dz \* \$dz -gt \$laneTolerance \* \$laneTolerance",
        helper_text,
    ), "lane recovery must apply the tight tolerance to every poll"


def test_official_probe_shortens_small_roster_wave4_lane_dwell() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    helper = re.search(
        r"function Teleport-PlayersToWave4ReflectionLaneIfDue[\s\S]*?\n}\n\nfunction Get-OfflinePlayerUuid",
        probe,
    )
    assert helper is not None, "Wave 4 lane helper is missing"
    helper_text = helper.group(0)
    assert "$wave4LaneDwellMs = 6000L" in helper_text, (
        "small official rosters need a bounded short dwell so every active source gets repeated real reflection attempts"
    )
    assert re.search(
        r"\$now\s*-\s*\$script:lastWave4LaneChangeAt\s*-ge\s*\$wave4LaneDwellMs",
        helper_text,
    ), "Wave 4 rotation must use the explicit short dwell constant"


def test_official_probe_removes_mob_knockback_from_reflection_clients() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    configure = re.search(
        r"function Configure-Players[\s\S]*?\n}\n\nfunction Wait-Transition",
        probe,
    )
    assert configure is not None, "official player configuration block is missing"
    assert "minecraft:generic.knockback_resistance base set 1" in configure.group(0), (
        "reflection clients must not be displaced by event mobs while the probe measures real use_entity hits"
    )


def test_official_probe_does_not_reset_wave5_or_wave6_combat_clients() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    wave5 = re.search(
        r"\$fogOffset\s*=\s*\$waveFiveStartOffset[\s\S]*?"
        r"\$waveFiveTransitionOffset",
        probe,
    )
    assert wave5 is not None, "Wave 5 probe block is missing"
    wave6 = re.search(
        r"\$ritualOffset\s*=\s*\$waveSixStartOffset[\s\S]*?"
        r"\$waveSixTransitionOffset",
        probe,
    )
    assert wave6 is not None, "Wave 6 probe block is missing"
    for block_name, block in (("Wave 5", wave5.group(0)), ("Wave 6", wave6.group(0))):
        assert "Teleport-PlayersIfOutsideCombatArea -Core $core" in block, (
            f"{block_name} must use conditional arena recovery"
        )
        assert "-Action { Teleport-PlayersToCombatRing -Core $core }" not in block, (
            f"{block_name} must not reset a real client every 500ms while it is fighting"
        )


def test_official_probe_recovers_wave5_and_wave6_melee_range_without_fake_damage() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1")
    helper = re.search(
        r"function Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange[\s\S]*?\n}\n\nfunction Teleport-PlayersToNearestChamberMob",
        probe,
    )
    assert helper is not None, "wave combat range recovery helper is missing"
    helper_text = helper.group(0)
    assert "tag=copimine_end_event" in helper_text
    assert "DistanceSquared" in helper_text
    assert "2.75D * 2.75D" in helper_text
    assert "Teleport-Player" in helper_text
    assert "/damage" not in helper_text and "/kill" not in helper_text
    assert re.search(
        r"\$fogOffset[\s\S]*?Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange[\s\S]*?"
        r"\$waveFiveTransitionOffset",
        probe,
    ), "Wave 5 polling must keep a real client in melee range of the current mob"
    assert re.search(
        r"\$ritualOffset[\s\S]*?Teleport-PlayersToNearestWaveMobIfOutOfMeleeRange[\s\S]*?"
        r"\$waveSixTransitionOffset",
        probe,
    ), "Wave 6 polling must keep a real client in melee range of the current mob"


def test_end_rift_gate_keeps_pinned_paper_api_on_persistence_classpath() -> None:
    gate = read(ROOT / "tests" / "RunEndRiftEventChecks.ps1")
    assert re.search(
        r"\$paperApiJar\s*=\s*\$env:PAPER_API_JAR[\s\S]*?"
        r"\$persistenceClasspath\s*=\s*@\(\$testBuild,\s*\$pluginClasses,\s*\$paperApiJar,\s*\$guavaJar\)",
        gate,
    ), "clean CI runners must include the pinned Paper API when compiling persistence tests"


def test_end_rift_persistence_classpath_excludes_ambiguous_guava_versions() -> None:
    gate = read(ROOT / "tests" / "RunEndRiftEventChecks.ps1")
    assert re.search(
        r"\$guavaJar\s*=\s*Get-ChildItem[\s\S]*?guava-32\.1\.2-jre\.jar[\s\S]*?"
        r"\$mavenJars\s*=\s*@\(Get-ChildItem[\s\S]*?"
        r"Where-Object\s+\{\s*\$_.Name\s+-notlike\s+['\"]guava-\*\.jar",
        gate,
    ), "persistence tests must not let an arbitrary Maven Guava jar win classpath order"


def test_ci_java_job_installs_pytest_before_end_rift_gate() -> None:
    workflow = read(ROOT / ".github" / "workflows" / "ci.yml")
    assert re.search(
        r"Install resource-pack build dependency[\s\S]*?python -m pip install[^\n]*pytest",
        workflow,
    ), "the Java CI job must install pytest before invoking the End Rift gate"


def test_ci_persistence_classpath_materializes_pinned_snake_yaml() -> None:
    workflow = read(ROOT / ".github" / "workflows" / "ci.yml")
    assert re.search(
        r"\$snakeYamlJar\s*=\s*Join-Path\s+\$env:USERPROFILE\s+"
        r"'.m2\\repository\\org\\yaml\\snakeyaml\\2\.2\\snakeyaml-2\.2\.jar'",
        workflow,
    ), "the CI persistence classpath must include SnakeYAML used by Bukkit YamlConfiguration"
    assert "https://repo.papermc.io/repository/maven-public/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar" in workflow
    assert "1467931448a0817696ae2805b7b8b20bfb082652bf9c4efaed528930dc49389b" in workflow


def test_ci_persistence_classpath_materializes_compatible_guava() -> None:
    workflow = read(ROOT / ".github" / "workflows" / "ci.yml")
    assert re.search(
        r"\$guavaJar\s*=\s*Join-Path\s+\$env:USERPROFILE\s+"
        r"'.m2\\repository\\com\\google\\guava\\guava\\32\.1\.2-jre\\guava-32\.1\.2-jre\.jar'",
        workflow,
    ), "the CI persistence classpath must pin the Guava version expected by Paper API"
    assert "https://repo.papermc.io/repository/maven-public/com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.jar" in workflow
    assert "bc65dea7cf d9e4dacf8419d8af0e741655857d27885bb35d943d7187fc3a8fce".replace(" ", "") in workflow.lower()


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
        r"Wait-WaveComplete\s+-Wave 6[\s\S]*?-AfterOffset \$ritualOffset",
        probe,
    ), "Wave 6 must reuse the Ritual Sphere cursor because completion markers share a tick"


def test_official_wave_bot_aims_at_the_projectile_for_reflection() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "nearestWave4ObeliskDisplay" in bot
    assert "target=projectile" in bot
    assert "origin=${sourceAnchor ? 'known' : 'nearest'}" in bot
    # Mineflayer's forced look path updates both the public entity rotation and
    # its private last-sent rotation before the attack packet is emitted.
    assert "bot.look(yaw, pitch, true)" in bot
    assert "await lookAtServer(current.position)" in bot
    assert "await lookAtServer(refreshed.position)" in bot
    assert "bot._client.write('use_entity'" in bot


def test_official_wave_bot_uses_stable_projectile_identity() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "function projectileIdentity(entity)" in bot
    assert "entity.uuid" in bot
    assert "reflectedProjectiles.has(projectileKey)" in bot
    assert "reflectedProjectiles.add(projectileKey)" in bot
    assert "reflectionTimers.has(projectileKey)" in bot
    assert "projectileOrigins.get(projectileKey)" in bot
    assert "current && projectileIdentity(current) === projectileKey" in bot
    assert "reflectedEntityIds" not in bot


def test_official_wave_bot_serializes_reflection_attempts_per_projectile() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "const reflectionInFlight = new Set()" in bot, (
        "the official wave bot must track a projectile while its asynchronous reflection loop is running"
    )
    assert re.search(
        r"if \(reflectedProjectiles\.has\(projectileKey\)\s*\|\|\s*"
        r"reflectionInFlight\.has\(projectileKey\)\) return",
        bot,
    ), "a rescan must not schedule a second reflection loop for the same projectile"
    assert re.search(
        r"const timer = setTimeout\(async \(\) => \{[\s\S]*?"
        r"reflectionTimers\.delete\(projectileKey\)[\s\S]*?"
        r"reflectionInFlight\.add\(projectileKey\)[\s\S]*?"
        r"try \{[\s\S]*?finally \{[\s\S]*?"
        r"reflectionInFlight\.delete\(projectileKey\)",
        bot,
    ), "the in-flight marker must survive until the async attempt loop exits"


def test_official_wave_bot_enters_survival_melee_range_before_attacking() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "const meleeAttackDistance = 2.75" in bot, (
        "the official wave bot must use a conservative survival interaction range"
    )
    assert re.search(
        r"function attackNearest\(\)[\s\S]*?"
        r"\.filter\(entity => distance\(entity\.position, bot\.entity\.position\)"
        r" <= meleeAttackDistance\)",
        bot,
    ), "the bot must only emit a melee attack once the target is inside the safe range"
    assert re.search(
        r"function tickWave7Navigation\(\)[\s\S]*?"
        r"if \(targetDistance <= meleeAttackDistance\)",
        bot,
    ), "the bot must keep navigating until a target is inside the same safe range"
    assert re.search(
        r"if \(!target\) \{[\s\S]*?navigateWave7\(navigationTarget\)",
        bot,
    ), "an out-of-range target must select bounded navigation instead of being skipped"


def test_official_wave_bot_waits_for_projectile_uuid_before_scheduling() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert re.search(
        r"function scheduleFireballReflection\(entity\)[\s\S]*?"
        r"const uuid = String\(entity\?\.uuid \|\| ''\)\.trim\(\)[\s\S]*?"
        r"if \(!uuid\) return",
        bot,
    ), "the probe must not create a fallback-id reflection timer before UUID metadata arrives"
    assert re.search(
        r"async function reflectFireball\(entity\)[\s\S]*?"
        r"const uuid = String\(entity\?\.uuid \|\| ''\)\.trim\(\)[\s\S]*?"
        r"if \(!uuid\) return false",
        bot,
    ), "direct reflection must reject an entity whose UUID is not known yet"


def test_official_wave_bot_rescans_entities_after_large_fireball_registry_resolution() -> None:
    bot = read(ROOT / "tests" / "LocalEndRiftMobCombatBot.js")
    assert "function scanRiftFireballs()" in bot, (
        "the official wave bot must recover when LargeFireball is initially exposed as an unknown entity"
    )
    assert re.search(
        r"function scanRiftFireballs\(\)[\s\S]*?Object\.values\(bot\.entities\)[\s\S]*?"
        r"isRiftFireball\(entity\)[\s\S]*?scheduleFireballReflection\(entity\)",
        bot,
    ), "the rescan must schedule every now-resolved fireball through the normal UUID path"
    assert re.search(
        r"reflectionScanTimer\s*=\s*setInterval\(scanRiftFireballs,\s*100\)",
        bot,
    ), "the rescan must run on a bounded 100 ms cadence during a live client session"
    assert "clearInterval(reflectionScanTimer)" in bot, (
        "the fireball rescan timer must be cleared when the client ends"
    )


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
        r"function\s+Parse-LoggedLocation[\s\S]*?"
        r"return\s+,\(\[double\[\]\]@\(",
        probe,
    ), "the Wave 1 probe must parse a stable absolute location from the marker"
    assert re.search(
        r"function\s+Wait-CarrierDelivery[\s\S]*?"
        r"END_RIFT_CARRIER_PICKED_UP[\s\S]*?"
        r"pickedHolderMatch[\s\S]*?"
        r"Teleport-Player\s+\$pickedHolderMatch\.Groups\[1\]\.Value",
        probe,
    ), "delivery confirmation must parse and move the authoritative holder after any pickup"
    wait_block = re.search(
        r"function\s+Wait-CarrierDelivery[\s\S]*?"
        r"throw\s+\"Timed out waiting for Wave 1",
        probe,
    )
    assert wait_block is not None
    assert "END_RIFT_CARRIER_SELECTED[^\\r\\n]*entity=([0-9a-fA-F-]{36})" in wait_block.group(0)
    assert "Teleport-Player $PickupPlayer $activeCarrierLocation[0]" in wait_block.group(0), (
        "Wave 1 probe must move a client to a selected live carrier so its combat bot can finish the objective"
    )


def test_official_probe_does_not_assign_a_hardcoded_bot_password() -> None:
    probe = (ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1").read_text(encoding="utf-8")
    assert re.search(
        r"(?im)^\s*\$env:END_RIFT_BOT_PASSWORD\s*=\s*['\"][^'\"]+['\"]",
        probe,
    ) is None, "the live probe must not assign a password literal that trips secret validation"


def test_boss_shield_live_probe_covers_blocked_vulnerable_and_restored_states() -> None:
    probe = read(ROOT / "tests" / "RunEndRiftBossShieldLive.ps1")
    default_name = re.search(r"\[string\]\$BotName\s*=\s*'([^']+)'", probe)
    assert default_name is not None
    assert len(default_name.group(1)) <= 16, "the default shield bot name must fit Minecraft's 16-character username limit"
    assert "cmend boss spawn official confirm" in probe
    assert "cmend boss phase last_seal" in probe
    assert "cmend boss phase hunt" in probe
    assert "reason=permanent-guardian-shield" in probe
    assert re.search(
        r"shieldBefore[\s\S]*?shieldAfter[\s\S]*?Abs\(\$shieldAfter\s*-\s*\$shieldBefore\)",
        probe,
    ), "shield-on probe must compare two measured real-health checkpoints"
    assert re.search(
        r"vulnerableBefore[\s\S]*?vulnerableAfter[\s\S]*?vulnerableAfter\s*-ge\s*\$vulnerableBefore",
        probe,
    ), "vulnerable phase probe must require a real HP decrease"
    assert re.search(
        r"restoredBefore[\s\S]*?restoredAfter[\s\S]*?Abs\(\$restoredAfter\s*-\s*\$restoredBefore\)",
        probe,
    ), "restored shield probe must compare another real-health checkpoint"
    assert "BOSS_DAMAGE_ACCEPTED" in probe
    assert "cmend boss kill cleanup" in probe
