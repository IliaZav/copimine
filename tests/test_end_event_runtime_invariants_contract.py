from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
STATE_STORE = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventStateStore.java").read_text(encoding="utf-8")
EVENT_CONFIG = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")


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
    assert "case FINAL_DRAIN, FINAL_RITUAL -> tickFinalRitual()" in MAIN
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
    assert "BOSS_REWARDS_RESERVED" in loot
    assert "BOSS_REWARDS_DELIVERED" in loot
    assert "BOSS_REWARDS_REVIEW_REQUIRED" in loot


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


def test_event_mob_visual_bindings_are_cleaned_with_owned_entities() -> None:
    assert "entityBindingInstances" in MAIN
    assert "END_ENTITY_UNBIND" in MAIN
    assert "unbindEventEntityClient" in MAIN
    assert "clientVisualId" in MAIN


def test_core_and_rune_visuals_never_replace_vanilla_block_materials() -> None:
    assert "coreBlockData = originalBlockData" in MAIN
    assert "block.setType(config.coreBlockMaterial()" not in MAIN
    assert "padBlock.setType(config.padBlockMaterial()" not in MAIN
    assert "spawnCoreOverlay" in MAIN
    assert "spawnRuneOverlay" in MAIN
    assert "ItemDisplay.ItemDisplayTransform.FIXED" in MAIN
    assert "coreOverlayLocation(core)" in MAIN
    assert "runeOverlayLocation(floor)" in MAIN
    assert "return core.getLocation().add(0.5D, 0.5D, 0.5D);" in MAIN
    assert "return floor.getLocation().add(0.5D, 1.0D, 0.5D);" in MAIN
    assert "floor.getLocation().add(0.5D, 1.5D, 0.5D)" not in MAIN
    assert "new Vector3f(1.06F, 1.06F, 1.06F)" in MAIN
    assert "entity.setViewRange(64.0F)" in MAIN
    assert "entity.setDisplayWidth(1.0F)" in MAIN
    assert "entity.setDisplayHeight(1.0F)" in MAIN
    assert "entity.setDisplayHeight(0.25F)" in MAIN


def test_core_layout_adapts_runes_to_nearest_safe_vanilla_floor() -> None:
    for marker in (
        "findSafePadBlock",
        "two-air-blocks over solid floor",
        "coordinates",
        "No block material is replaced here",
    ):
        assert marker in MAIN


def test_bootstrap_preserves_an_active_core_even_when_worldcore_is_already_unlocked() -> None:
    for marker in (
        "configuredEventInProgress",
        "WorldCore already reports End unlocked; preserving active event",
        "phase != EventPhase.UNLOCKED",
        "!VICTORY_COMPLETE.equals(victoryStep)",
    ):
        assert marker in MAIN


def test_recovery_preserves_a_committed_official_roster() -> None:
    recovery = MAIN[MAIN.index("private void recoverTransientSession"):
                    MAIN.index("private void cancelSessionTasks")]
    assert "officialRewardRoster.clear()" not in recovery
    assert "rewardStatuses.clear()" not in recovery


def test_final_ritual_owns_one_bounded_visual_task() -> None:
    assert "finalRitualVisualTask" in MAIN
    visual = MAIN[MAIN.index("private void scheduleFinalRitualVisual"):
                  MAIN.index("private void spawnParticleLine")]
    assert "finalRitualVisualTask != null" in visual
    assert "finalRitualVisualTask = holder[0]" in visual


def test_all_participants_are_durable_and_announced_separately_from_rewards() -> None:
    assert "participantUuids" in MAIN
    assert "snapshot.participants()" in MAIN
    assert "yaml.set(\"participants.all\"" in STATE_STORE
    assert "yaml.getStringList(\"participants.all\")" in STATE_STORE
    victory = MAIN[MAIN.index("private void announceVictory"):
                   MAIN.index("public void onPlayerJoin")]
    assert "participantUuids" in victory
    assert "String names = participantUuids" in victory
    assert "String rewardNames = officialRewardRoster" in victory


def test_countdown_commits_phase_before_spawning_the_first_wave() -> None:
    countdown = MAIN[MAIN.index("RewardRoster roster = RewardRoster.commitExactly"):
                     MAIN.index("private void cancelRitual")]
    assert countdown.index("transition(EventPhase.WAVE_1") < countdown.index("saveStateSync()")
    assert "if (!saveStateSync())" in countdown
    assert countdown.index("saveStateSync()") < countdown.index("spawnWave(1, false)")


def test_recovered_committed_roster_cannot_be_replaced_by_a_late_pad_occupant() -> None:
    countdown = MAIN[MAIN.index("RewardRoster roster = RewardRoster.commitExactly"):
                     MAIN.index("private void cancelRitual")]
    assert "!officialRewardRoster.isEmpty()" in countdown
    assert "committed roster mismatch after recovery" in countdown
    assert "officialRewardRoster.addAll(roster.players())" in countdown


def test_boss_reward_state_and_recipient_are_durable() -> None:
    assert "bossRewardStatus" in MAIN
    assert "bossRewardRecipientUuid" in MAIN
    assert 'yaml.set("event.boss-reward-status"' in STATE_STORE
    assert 'yaml.set("event.boss-reward-recipient"' in STATE_STORE


def test_final_drain_has_a_durable_per_player_plan_and_commit() -> None:
    drain = MAIN[MAIN.index("private void applyFinalDrain"):
                 MAIN.index("private void scheduleFinalRitualVisual")]
    assert "finalDrainTargets" in drain
    assert "finalDrainAppliedPlayers" in drain
    assert "finalDrainTargets.put" in drain
    assert "finalDrainAppliedPlayers.add" in drain
    assert drain.index("saveStateSync()") < drain.index("player.setHealth")
    assert "final-drain.targets" in STATE_STORE
    assert "final-drain.applied-players" in STATE_STORE


def test_core_clicks_are_guarded_per_player_and_server_tick() -> None:
    guard = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/CoreInteractionGuard.java").read_text(encoding="utf-8")
    assert "serverTick" in guard
    assert "accepted.add" in guard
    assert "Bukkit.getCurrentTick()" in MAIN
    assert "coreInteractionGuard.accept" in MAIN


def test_victory_saga_issues_rewards_before_worldcore_unlock() -> None:
    begin = MAIN[MAIN.index("private void beginVictory"):
                 MAIN.index("private void unlockEnd")]
    issue = MAIN[MAIN.index("private void issueVictoryRewards"):
                 MAIN.index("private void applyBossLootOnce")]
    completion = MAIN[MAIN.index("private void checkVictoryRewardCompletion"):
                      MAIN.index("private void resumeVictorySaga")]
    assert "issueVictoryRewards()" in begin
    assert "unlockEnd(" not in begin
    assert "!endUnlocked || rewardService" not in issue
    assert "unlockEnd(null, \"official-victory\")" in completion


def test_wave_loot_uses_configurable_deterministic_one_roll_profiles() -> None:
    for marker in ("LootEntry", "SplittableRandom", "lootProfiles", "lootProfile"):
        assert marker in EVENT_CONFIG or marker in MAIN
    assert "event-loot-rolls:" in CONFIG
    assert "lootIssuedEntityUuids.add" in MAIN
    death = MAIN[MAIN.index("public void onOwnedEntityDeath"):
                 MAIN.index("private void addConfiguredDrops")]
    assert "addConfiguredDrops(event" in death
