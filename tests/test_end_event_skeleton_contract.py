from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CONFIG = ROOT / "copimine-end-event/config.yml"
AI = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EndRiftAiPolicy.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SkeletonCombatPolicy.java"
CLIENT = ROOT / "CopiMineClient"
CLIENT_MIXINS = ROOT / "CopiMineClient/src/main/resources/copimineclient.mixins.json"
CLIENT_TEXTURES = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity"
CLIENT_CATALOG = ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java"


def _wave_block(name: str, next_name: str | None = None) -> str:
    text = CONFIG.read_text(encoding="utf-8")
    waves_start = text.index("\nwaves:\n") + 1
    start = text.index(f"  {name}:\n", waves_start)
    end = text.index(f"  {next_name}:\n", start) if next_name else text.index("\n# Event-owned mobs", start)
    return text[start:end]


def test_waves_use_skeletons_and_never_spawn_shulkers() -> None:
    server = SERVER.read_text(encoding="utf-8")
    config = CONFIG.read_text(encoding="utf-8")
    assert "EntityType.SKELETON" in server
    assert "EntityType.SHULKER" not in server
    assert "Shulker" not in server
    assert "shulkers:" not in config
    assert "shulker:" not in config
    for name, next_name in (
        ("wave-1", "wave-2"),
        ("wave-2", "wave-3"),
        ("wave-3", "wave-4"),
        ("wave-4", "wave-5"),
        ("wave-5", "final"),
        ("final", None),
    ):
        block = _wave_block(name, next_name)
        assert re.search(r"(?m)^\s+skeletons:\s*\d+\s*$", block), name
        assert re.search(r"(?m)^\s+elite-skeletons:\s*\d+\s*$", block), name


def test_skeleton_ai_accepts_only_eligible_players_and_filters_arrows() -> None:
    server = SERVER.read_text(encoding="utf-8")
    for marker in (
        "EntityTargetLivingEntityEvent",
        "EntityShootBowEvent",
        "Arrow",
        "onEventSkeletonTarget",
        "onEventSkeletonShootBow",
        "onEventSkeletonArrowDamage",
        "canTargetPlayersOnly",
        "SKELETON_TARGET_BLOCKED",
        "SKELETON_ARROW_PLAYER_HIT",
        "setTarget(null)",
        "SkeletonArrowPolicy",
        "POISON_NAUSEA",
        "EXPLOSIVE",
        "createExplosion",
        "breakBlocks=false",
        "PotionEffectType.POISON",
        "PotionEffectType.NAUSEA",
    ):
        assert marker in server
    assert "event.setCancelled(true)" in server
    assert "instanceof Player" in server
    non_player_branch = server[
        server.index("public void onEventSkeletonArrowDamage"):
        server.index("public void onCustomEventArrowDamage")
    ]
    assert "SKELETON_ARROW_NON_PLAYER_BLOCKED" in non_player_branch
    assert "cleanupEventArrow(arrow.getUniqueId())" in non_player_branch


def test_skeleton_arrow_payloads_are_bounded_and_non_destructive() -> None:
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/SkeletonArrowPolicy.java").read_text(
        encoding="utf-8"
    )
    server = SERVER.read_text(encoding="utf-8")
    for marker in (
        "STATUS_CHANCE_PERCENT = 20",
        "STATUS_DURATION_TICKS = 7 * 20",
        "STATUS_AMPLIFIER = 2",
        "ArrowKind",
        "isStatusRoll",
        "breaksBlocks()",
    ):
        assert marker in policy
    assert "world.createExplosion" in server
    explosive = server[server.index("private void detonateExplosiveArrow"):server.index("private void clearActiveEventArrows")]
    assert "false, false" in explosive
    assert "PotionEffectType.POISON" in server
    assert "PotionEffectType.NAUSEA" in server
    assert "SkeletonArrowPolicy.STATUS_DURATION_TICKS" in server


def test_wave_commander_is_one_per_difficult_wave_and_aura_is_removed() -> None:
    server = SERVER.read_text(encoding="utf-8")
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WaveCommanderPolicy.java").read_text(
        encoding="utf-8"
    )
    for marker in (
        "WaveCommanderPolicy",
        "WAVE_COMMANDER",
        "tickWaveCommanderAura",
        "keyWaveCommander",
        "keyCommanderAura",
        "PotionEffectType.STRENGTH",
        "removePotionEffect(PotionEffectType.STRENGTH)",
        "WaveCommanderPolicy.MAX_LIVE_MOBS",
    ):
        assert marker in server
    for marker in (
        "FIRST_DIFFICULT_WAVE = 3",
        "LAST_DIFFICULT_WAVE = 6",
        "shouldAssign",
        "AURA_RADIUS_BLOCKS = 10.0D",
        "MAX_LIVE_MOBS = 56",
    ):
        assert marker in policy


def test_both_skeleton_variants_have_stable_roles_and_arrow_spell() -> None:
    server = SERVER.read_text(encoding="utf-8")
    ai = AI.read_text(encoding="utf-8")
    assert "spawnSkeleton" in server
    assert "miniBoss" in server
    assert "SkeletonCombatPolicy.arrowProfile" in server
    assert "ARROW_SALVO" in ai
    assert '"arrow_salvo"' in ai
    assert "miniBossArrowSalvo" in server
    assert "RIFT_ARROWS" in ai
    assert '"rift_arrows"' in ai
    assert "riftArrowVolley" in server
    assert "spawnSpellFlightPattern" in server
    assert 'case "arrow_salvo"' in server
    assert 'case "rift_arrows"' in server


def test_skeleton_arrow_rate_limit_uses_the_profile_and_cleans_state() -> None:
    server = SERVER.read_text(encoding="utf-8")
    shoot = server[
        server.index("public void onEventSkeletonShootBow"):
        server.index("public void onEventSkeletonArrowDamage")
    ]
    wave_cleanup = server[
        server.index("private void clearWaveEntities"):
        server.index("private boolean isLiveOwnedEntity")
    ]
    combat_cleanup = server[
        server.index("private int clearWaveCombatEntities"):
        server.index("private void clearBossOnly")
    ]
    assert "nextSkeletonArrowMillis" in server
    assert "profile.cooldownTicks()" in shoot
    assert "SKELETON_ARROW_COOLDOWN_BLOCKED" in shoot
    assert "nextSkeletonArrowMillis.remove" in wave_cleanup
    assert "nextSkeletonArrowMillis.clear" in wave_cleanup
    assert "nextSkeletonArrowMillis.remove" in combat_cleanup
    assert "nextSkeletonArrowMillis.clear" in combat_cleanup


def test_skeleton_policy_is_pure_and_bounded() -> None:
    assert POLICY.is_file()
    policy = POLICY.read_text(encoding="utf-8")
    for marker in (
        "canTargetPlayersOnly",
        "arrowProfile",
        "hasArrowSpell",
        "record ArrowProfile",
        "bone_tracer",
        "rift_salvo",
    ):
        assert marker in policy
    assert "arrowCount = Math.max" in policy
    assert "damage = finite" in policy


def test_client_has_uuid_scoped_skeleton_textures_and_renderer() -> None:
    mixins = CLIENT_MIXINS.read_text(encoding="utf-8")
    catalog = CLIENT_CATALOG.read_text(encoding="utf-8")
    assert "SkeletonEntityRendererMixin" in mixins
    for name in ("end_rift_skeleton.png", "end_rift_elite_skeleton.png"):
        path = CLIENT_TEXTURES / name
        assert path.is_file()
        assert path.stat().st_size > 100
    for visual in ("END_RIFT_SKELETON_V1", "END_RIFT_ELITE_SKELETON_V1"):
        assert visual in catalog
        assert visual in SERVER.read_text(encoding="utf-8")


def test_skeleton_texture_diagnostics_do_not_grow_with_transient_entity_uuids() -> None:
    mixin = (CLIENT / "src/main/java/me/copimine/client/mixin/SkeletonEntityRendererMixin.java").read_text(
        encoding="utf-8"
    )
    assert 'String bindingKey = visual + "|" + resourcePresent;' in mixin
    assert 'entity.getUuid() + "|"' not in mixin


def test_wave_ai_logs_skeleton_behavior_per_wave_without_mob_aggro() -> None:
    server = SERVER.read_text(encoding="utf-8")
    assert "WAVE_SKELETON_BEHAVIOR" in server
    assert "target=PLAYER_ONLY" in server
    assert "waveTacticalDestination" in server
    assert "ARTILLERY" in server
    assert "isCombatTarget(target)" in server
    assert "SkeletonCombatPolicy.behaviorForWave" in server
    assert "hasLiveTestWaveEntities()" in server


def test_wave_two_marked_target_diagnostic_is_visible_at_default_log_level() -> None:
    server = SERVER.read_text(encoding="utf-8")
    assert re.search(r'getLogger\(\)\.info\("WAVE_SKELETON_MARKED_TARGET', server)


def test_tower_skeleton_keeps_its_artillery_core_job_until_player_aggro() -> None:
    server = SERVER.read_text(encoding="utf-8")
    tick = server[server.index("private void tickWaveMobAi()"):server.index("private void enforceWaveMobContainment")]
    assert "boolean towerCoreJob = towerMob && !playerAggro" in tick
    assert "maintainSkeletonTowerPosture(skeleton, kind, now)" in tick
    assert "waveTacticalDestination(skeleton, null, kind, now)" in server
