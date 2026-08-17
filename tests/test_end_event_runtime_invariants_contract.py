from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")


def test_roster_and_combat_membership_exclude_non_survival_players() -> None:
    participant = MAIN[MAIN.index("private boolean isActiveArenaParticipant"):
                       MAIN.index("private void tickFinalRitual")]
    occupancy = MAIN[MAIN.index("private void updatePadOccupancy"):
                     MAIN.index("private String padKey")]
    for source in (participant, occupancy):
        assert "GameMode.SPECTATOR" in source
        assert "GameMode.CREATIVE" in source
        assert "getHealth()" in source


def test_final_ritual_waits_for_eligible_players_and_has_bounded_telegraph() -> None:
    assert "case FINAL_RITUAL -> tickFinalRitual()" in MAIN
    drain = MAIN[MAIN.index("private void applyFinalDrain"):
                 MAIN.index("private void scheduleFinalRitualVisual")]
    assert "eligible.isEmpty()" in drain
    assert "finalDrainApplied = true" in drain
    assert "final-ritual-telegraph-ticks: 80" in CONFIG
    assert "config.finalRitualTelegraphTicks()" in MAIN


def test_shard_is_end_gated_and_cancels_on_damage_without_early_cooldown() -> None:
    channel = MAIN[MAIN.index("private void startShardChannel"):
                   MAIN.index("private Location safePortalDestination")]
    assert "!endUnlocked" in channel
    assert "worldAccessService.isEndEnabled()" in channel
    assert "onShardChannelDamage" in MAIN
    finish = MAIN[MAIN.index("private void finishShardChannel"):
                  MAIN.index("private Location safePortalDestination")]
    assert finish.index("player.teleport(destination)") < finish.index("shardCooldowns.put")
    assert "shardCooldowns.remove" in finish


def test_boss_reward_waits_for_official_recipient_and_inventory_capacity() -> None:
    loot = MAIN[MAIN.index("private void applyBossLootOnce"):
                MAIN.index("private void checkVictoryRewardCompletion")]
    assert "BOSS_LOOT_WAITING" in loot
    assert "officialRewardRoster" in loot
    assert "canFitBossBundle" in loot
    assert "bossLootCommitted = true" in loot


def test_official_boss_death_clears_the_live_boss_reference() -> None:
    death_body = MAIN[MAIN.index("public void onOwnedEntityDeath"):
                      MAIN.index("private void addConfiguredDrops")]
    assert "ownedEntities.remove(entity.getUniqueId())" in death_body
    assert "bossUuid = null" in death_body


def test_victory_recovery_does_not_rewrite_a_completed_saga_to_pending() -> None:
    issue = MAIN[MAIN.index("private void issueVictoryRewards"):
                 MAIN.index("private void checkVictoryRewardCompletion")]
    check = MAIN[MAIN.index("private void checkVictoryRewardCompletion"):
                 MAIN.index("private void resumeVictorySaga")]
    assert "!VICTORY_REWARDS_PENDING.equals(victoryStep)" in issue
    assert "!VICTORY_REWARDS_DELIVERED.equals(victoryStep)" in issue
    assert "phase == EventPhase.UNLOCKED" not in check.split("if (!endUnlocked", 1)[0]


def test_arena_mutation_protection_covers_non_player_world_mutations() -> None:
    for method in (
        "onArenaBlockExplode", "onArenaEntityExplode", "onArenaPistonExtend",
        "onArenaPistonRetract", "onArenaFluidFlow", "onArenaFire", "onArenaFade",
        "onArenaEntityChangeBlock", "onArenaInventoryMove", "onArenaInventoryPickup",
        "onOwnedDisplayDamage",
    ):
        assert method in MAIN
    assert "isProtectedEventLocation" in MAIN
    assert "isProtectedInventory" in MAIN


def test_permissions_keep_admin_and_test_surfaces_separate() -> None:
    plugin = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")
    assert "copimine.endevent.admin" in plugin
    assert "copimine.endevent.test" in plugin
    assert "validTest(sender)" in MAIN
    assert "client status|bindboss|clear [player]" in MAIN
    assert "Bukkit.getPlayerExact(args[3])" in MAIN
