from __future__ import annotations

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MAIN_PATH = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
MAIN = MAIN_PATH.read_text(encoding="utf-8")
LIVE = (ROOT / "tests/RunEndRiftObeliskLive.ps1").read_text(encoding="utf-8")
BOT = (ROOT / "tests/LocalEndRiftObeliskBot.js").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
EVENT_CONFIG = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")
CLIENT_CATALOG = (ROOT / "CopiMineClient/src/main/java/me/copimine/client/EndEventTextureCatalog.java").read_text(encoding="utf-8")
PACK_BUILDER = (ROOT / "resourcepacks/build-resourcepack.py").read_text(encoding="utf-8")
MANIFEST = json.loads((ROOT / "resourcepacks/models_manifest.json").read_text(encoding="utf-8"))


def _body(start_marker: str, end_marker: str) -> str:
    start = MAIN.index(start_marker)
    end = MAIN.index(end_marker, start)
    return MAIN[start:end]


def test_spell_is_registered_only_in_distortion() -> None:
    stage_policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossStagePolicy.java").read_text(encoding="utf-8")
    assert 'RIFT_OBELISKS("rift_obelisks", "Обелиски Разлома")' in (
        ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EndRiftAiPolicy.java"
    ).read_text(encoding="utf-8")
    distortion = stage_policy[stage_policy.index("case DISTORTION"):stage_policy.index("case ABSORPTION")]
    assert "RIFT_OBELISKS" in distortion
    assert "RIFT_OBELISKS" not in stage_policy[stage_policy.index("case ABSORPTION"):]
    assert "case RIFT_OBELISKS -> startRiftObelisks" in MAIN


def test_config_has_exact_safe_defaults_and_parser_rejects_other_stages() -> None:
    for line in (
        "enabled: true",
        "stages: [DISTORTION]",
        "cooldown-seconds: [18, 24]",
        "health: 3",
        "max-active: 4",
        "pulse-radius: 5.0",
        "pulse-interval-ticks: 40",
        "fire-interval-ticks: 80",
        "max-active-fireballs: 8",
        "fireball-damage: 6.0",
        "blindness-ticks: 40",
        "debuff-ticks: 60",
    ):
        assert line in CONFIG
    assert "stages must contain only DISTORTION" in EVENT_CONFIG
    assert "maxActive > 4" in EVENT_CONFIG
    assert "maxActiveFireballs > 8" in EVENT_CONFIG


def test_spawn_placement_and_scaling_are_bounded_and_display_only() -> None:
    spawn = _body("private void startRiftObelisks", "private Location resolveRiftObeliskLocation")
    placement = _body("private Location resolveRiftObeliskLocation", "private void tickRiftObelisks")
    assert "RiftObeliskScalingPolicy.countForPlayers(participants.size())" in spawn
    assert "tuning.maxActive()" in spawn
    assert "RiftObeliskPlacementPolicy.candidates(requested)" in spawn
    assert "isArenaLocation(candidate)" in placement
    assert "isSafeCombatStep(anchor, candidate" in placement
    assert "activeRiftObelisks.values().stream()" in placement
    assert "BlockDisplay" not in spawn
    assert "temporary blocks" not in spawn.lower()


def test_fireball_is_event_owned_reflectable_non_incendiary_and_capped() -> None:
    launch = _body("private void launchRiftFireball", "public void onRiftFireballReflect")
    reflect = _body("public void onRiftFireballReflect", "public void onRiftObeliskDamage")
    impact = _body("private void handleRiftFireballImpact", "private RiftObeliskRuntimeState findRiftObeliskAt")
    assert "LargeFireball" in launch
    assert "setDisplayItem(overlayItem(MODEL_RIFT_FIREBALL" in launch
    assert "setYield(0.0F)" in launch
    assert "setIsIncendiary(false)" in launch
    assert "setVisualFire(false)" in launch
    assert "activeRiftFireballs.size() >= config.riftObeliskTuning().maxActiveFireballs()" in MAIN
    assert "state.reflect(player.getUniqueId())" in reflect
    assert "setDirection(direction)" in reflect
    assert "teleportCombatEntity(fireball, reflectedOrigin)" in reflect
    assert "player.damage(effects.damage(), fireball)" in impact
    assert "RIFT_FIREBALL_EFFECTS_APPLIED" in impact
    assert "blocks=false fire=false" in impact


def test_exact_pulse_and_hit_effects_are_policy_driven() -> None:
    policy = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RiftFireballPolicy.java").read_text(encoding="utf-8")
    assert "PULSE_RADIUS = 5.0D" in policy
    assert "PULSE_INTERVAL_TICKS = 40" in policy
    assert "FIRE_INTERVAL_TICKS = 80" in policy
    assert "MAX_ACTIVE_FIREBALLS = 8" in policy
    assert "new EffectProfile(0.0D, 0, 60, 0, 60, 1, 60, 0)" in policy
    assert "new EffectProfile(damage, blindnessTicks, debuffTicks" in policy
    assert "effects.blindnessTicks()" in MAIN
    assert "effects.nauseaAmplifier()" in MAIN
    assert "effects.slownessAmplifier()" in MAIN


def test_boss_immunity_filters_only_rift_fireball_and_keeps_normal_player_damage() -> None:
    damage = _body("public void onBossDamage", "private void applyBossDamage")
    explosion = _body("public void onArenaEntityExplode", "public void onArenaPistonExtend")
    assert "RiftFireballPolicy.blocksBossDamage" in damage
    assert "RIFT_FIREBALL_BOSS_DAMAGE_BLOCKED" in damage
    assert "RIFT_FIREBALL_EXPLOSION_BOSS_DAMAGE_BLOCKED" in MAIN
    assert "event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION" in damage
    assert "handleRiftFireballImpact(fireball, null)" in explosion
    assert "applyBossDamage(boss" in damage


def test_cleanup_covers_stage_exit_victory_reset_disable_and_owned_entity_recovery() -> None:
    for marker in (
        'clearRiftObelisks("session cancellation")',
        'clearRiftObelisks("combat AI reset")',
        'clearRiftObelisks("boss cleanup")',
        'clearRiftObelisks("official boss defeat")',
        'clearRiftObelisks("final drain")',
        'clearRiftObelisks("victory")',
        'clearRiftObelisks("stage exit from DISTORTION")',
        'clearRiftObelisks("owned event cleanup")',
        'clearRiftObelisks("owned generation cleanup")',
    ):
        assert marker in MAIN
    assert "RIFT_OBELISKS_CLEANUP" in MAIN
    assert "EVENT_KIND_OBELISK.equals(kind)" in MAIN
    assert "EVENT_KIND_RIFT_FIREBALL.equals(kind)" in MAIN


def test_manual_wave_reset_reconciles_world_entities_after_reload() -> None:
    reset = _body("private boolean isWaveCleanupKind", "private boolean isLiveOwnedEntity")
    assert "Set<UUID> waveEntityIds" in reset
    assert "for (World world : Bukkit.getWorlds())" in reset
    assert "ownedByEvent(entity, eventId)" in reset
    assert "Bukkit.getEntity(entityId)" in reset
    assert "END_EVENT_WAVE_RESET_CLEANUP" in reset


def test_resource_pack_models_and_textures_are_real_32px_assets_without_vanilla_override() -> None:
    expected = {
        "end_event_rift_obelisk_full": 830010,
        "end_event_rift_obelisk_damaged": 830011,
        "end_event_rift_obelisk_critical": 830012,
        "end_event_rift_fireball": 830013,
    }
    entries = {entry["id"]: entry for entry in MANIFEST["items"]}
    for name, custom_model_data in expected.items():
        entry = entries[name]
        assert entry["custom_model_data"] == custom_model_data
        namespace, model_path = entry["model"].split(":", 1)
        model = ROOT / "resourcepacks/src/assets" / namespace / "models" / Path(model_path)
        model = model.with_suffix(".json")
        assert model.is_file(), model
        payload = json.loads(model.read_text(encoding="utf-8"))
        assert payload["elements"]
        texture_namespace, texture_path = entry["texture"].split(":", 1)
        texture = ROOT / "resourcepacks/src/assets" / texture_namespace / "textures" / Path(texture_path)
        texture = texture.with_suffix(".png")
        assert texture.is_file(), texture
        with Image.open(texture) as image:
            assert image.size == (32, 32)
        assert str(texture.relative_to(ROOT / "resourcepacks/src")).replace("\\", "/") in PACK_BUILDER
        assert str(model.relative_to(ROOT / "resourcepacks/src")).replace("\\", "/") in PACK_BUILDER
    assert "assets/minecraft/textures/entity/fireball" not in PACK_BUILDER
    assert "assets/minecraft/models/entity/fireball" not in PACK_BUILDER


def test_client_catalog_has_fallback_assets_for_each_event_visual() -> None:
    for visual_id, filename in (
        ("END_RIFT_OBELISK_FULL_V1", "end_event_rift_obelisk_full.png"),
        ("END_RIFT_OBELISK_DAMAGED_V1", "end_event_rift_obelisk_damaged.png"),
        ("END_RIFT_OBELISK_CRITICAL_V1", "end_event_rift_obelisk_critical.png"),
        ("END_RIFT_FIREBALL_V1", "end_event_rift_fireball.png"),
    ):
        assert visual_id in CLIENT_CATALOG
        assert filename in CLIENT_CATALOG
        path = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity" / filename
        assert path.is_file(), path
        with Image.open(path) as image:
            assert image.size == (32, 32)


def test_obelisk_tiles_are_symmetric_and_states_are_visibly_distinct() -> None:
    images = {}
    for variant in ("full", "damaged", "critical"):
        path = ROOT / "resourcepacks/src/assets/copimine/textures/item" / f"end_event_rift_obelisk_{variant}.png"
        with Image.open(path) as source:
            images[variant] = source.convert("RGBA").copy()
        image = images[variant]
        for y in range(32):
            for x in range(32):
                assert image.getpixel((x, y)) == image.getpixel((31 - x, y))
                assert image.getpixel((x, y)) == image.getpixel((x, 31 - y))
        assert len(image.getcolors(maxcolors=4096) or []) >= 8
    assert images["full"].tobytes() != images["damaged"].tobytes()
    assert images["damaged"].tobytes() != images["critical"].tobytes()


def test_live_probe_uses_real_local_clients_and_checks_authoritative_outcomes() -> None:
    for marker in (
        "LocalEndRiftObeliskBot.js",
        "cmend boss spell rift_obelisks",
        "RIFT_FIREBALL_EFFECTS_APPLIED",
        "RIFT_OBELISK_REFLECTED_HIT .*remaining_health=2",
        "RIFT_OBELISK_REFLECTED_HIT .*remaining_health=1",
        "RIFT_OBELISK_REFLECTED_HIT .*remaining_health=0",
        "BukkitValues.\"copimineendevent:end_event_boss_virtual_health\"",
        "END_RIFT_REFLECT_ENABLED",
        "LIVE_RIFT_OBELISK_NORMAL_DAMAGE_PASS",
        "LIVE_RIFT_OBELISK_CLEANUP_PASS",
    ):
        assert marker in LIVE
    assert "SetupEndRiftLocalScene.ps1" not in LIVE
    assert "127.0.0.1" in LIVE
    assert "bot._client.write('use_entity'" in BOT
    assert "bot._client.write('look'" in BOT
    assert "Math.fround((Math.PI - yaw) * 180 / Math.PI)" in BOT
    assert "bot.lookAt(" not in BOT
