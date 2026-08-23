from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
MAIN = (PLUGIN / "src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (PLUGIN / "config.yml").read_text(encoding="utf-8")
PHASES = (PLUGIN / "src/me/copimine/endevent/domain/EventPhase.java").read_text(encoding="utf-8")


def test_latest_event_phase_names_and_resource_balance_are_present() -> None:
    for phase in (
        "UNCONFIGURED", "COLLECTING", "READY_FOR_PLAYERS", "COUNTDOWN",
        "WAVE_1", "INTERMISSION_1", "WAVE_2", "INTERMISSION_2", "WAVE_3",
        "BOSS_ACTIVE", "FINAL_DRAIN", "FINAL_WAVE", "BOSS_FINISH",
        "VICTORY_PROCESSING", "UNLOCKED", "RECOVERY_REQUIRED",
    ):
        assert re.search(rf"\b{phase}\b", PHASES)
    assert "DIAMOND: 100" in CONFIG
    assert "ENDER_EYE: 64" in CONFIG
    assert "AMETHYST_SHARD: 128" in CONFIG
    assert "BLAZE_ROD: 64" in CONFIG
    assert "boss-xp: 3000" in CONFIG
    assert "elite-endermen: 6" in CONFIG
    assert "endermites: 8" in CONFIG
    assert "shulkers: 2" in CONFIG
    assert "health-bonus: 10.0" in CONFIG
    assert "attack-damage-bonus: 2.0" in CONFIG
    assert "visuals:" not in CONFIG
    assert "core-block:" not in CONFIG
    assert "pad-block:" not in CONFIG


def test_entity_ownership_uses_session_role_wave_and_generation_tags() -> None:
    for key in ("event_session_id", "event_role", "event_wave", "event_generation"):
        assert f'"{key}"' in MAIN
    assert "cleanupOwnedEntities(eventId, generation)" in MAIN
    assert "Bukkit.getWorlds()" in MAIN
    assert "entity.remove()" in MAIN


def test_core_command_preserves_real_target_block_and_uses_surface_overlays() -> None:
    assert "player.getTargetBlockExact(8)" in MAIN
    assert "coreBlockData = originalBlockData" in MAIN
    assert "block.setType(config.coreBlockMaterial()" not in MAIN
    assert "padBlock.setType(config.padBlockMaterial()" not in MAIN
    assert "spawnCoreOverlay" in MAIN
    assert "spawnRuneOverlay" in MAIN
    assert "ItemDisplay" in MAIN
    assert "setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED)" in MAIN
    assert "target block remains the real block selected by the admin" in MAIN
    assert "The pad coordinate is the air block above this floor" in MAIN
    assert "displays=CORE_OVERLAY_AND_RUNE_OVERLAYS" in MAIN
    assert "legacyVisualInArena" in MAIN
    assert "END_EVENT_VISUAL_CLEANUP" in MAIN


def test_endermite_wave_stats_are_applied_as_runtime_attribute_bonuses() -> None:
    assert "configureEventMobStats" in MAIN
    assert "config.endermiteHealthBonus()" in MAIN
    assert "config.endermiteAttackDamageBonus()" in MAIN
    assert "Attribute.GENERIC_MAX_HEALTH" in MAIN
    assert "Attribute.GENERIC_ATTACK_DAMAGE" in MAIN


def test_wave_scaling_is_bounded_for_small_and_large_official_rosters() -> None:
    assert "Math.max(0.8D, Math.min(2.0D, scalePlayers / 5.0D))" in MAIN


def test_final_ritual_is_direct_health_control_and_stops_normal_boss_loop() -> None:
    assert "FinalDrainMath.healthAfterDrain" in MAIN
    drain = MAIN[MAIN.index("private void applyFinalDrain"):MAIN.index("private void scheduleFinalRitualVisual")]
    assert "player.setHealth(Math.max" in drain
    assert "finalDrainTargets" in drain
    assert "boss.setInvulnerable(true)" in MAIN
    assert "boss.setHealth(Math.min(config.bossFinalHealth(), boss.getMaxHealth()))" in MAIN
    assert "if (!forced && phase != EventPhase.BOSS_ACTIVE)" in MAIN
    assert "PlayerDeathEvent" not in MAIN[MAIN.index("applyFinalDrain"):MAIN.index("scheduleFinalRitualVisual")]


def test_command_matrix_and_typed_boundaries_remain_fail_closed() -> None:
    for command in (
        '"arena"', '"gate"', '"portalroom"', '"resources"', '"ritual"',
        '"wave"', '"boss"', '"client"',
    ):
        assert command in MAIN
    assert "clientgate" not in MAIN.lower()
    assert "Bukkit.dispatchCommand" not in MAIN
    assert "CopiMineEconomyCore" not in MAIN
    assert "WorldAccessService" in MAIN
    assert "EventArtifactRewardService" in MAIN


def test_reward_count_is_frozen_roster_not_last_hit() -> None:
    assert "RewardRoster.commitExactly" in MAIN
    assert "officialRewardRoster" in MAIN
    assert ":participant:" in MAIN
    assert "rift-core-shard" in MAIN
    reward_method = MAIN[MAIN.index("private void issueVictoryRewards"):MAIN.index("private void checkVictoryRewardCompletion")]
    assert "getKiller" not in reward_method
    assert "bossKillerUuid" in MAIN
