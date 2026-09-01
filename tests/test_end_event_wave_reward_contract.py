from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")


def _body(start: str, end: str) -> str:
    first = MAIN.index(start)
    return MAIN[first:MAIN.index(end, first)]


def test_wave_reward_is_personal_for_every_frozen_participant_and_has_shared_rare_marker() -> None:
    reward = _body("private boolean spawnWaveCompletionLoot", "private void spawnWave")
    assert "officialRewardRoster" in reward
    assert "participant" in reward
    assert "shared-rare" in reward
    assert "waveRewardsIssued.add(wave)" in reward
    assert "saveStateSync()" in reward


def test_wave_reward_owner_window_is_enforced_by_the_player_pickup_event() -> None:
    assert "EntityPickupItemEvent" in MAIN
    assert "keyRewardOwner" in MAIN
    assert "keyRewardExpiresAt" in MAIN
    assert "WAVE_REWARD_OWNER_WINDOW_MILLIS = 30_000L" in MAIN
    pickup = _body("private boolean allowWaveRewardPickup", "private void")
    assert "return false" in pickup
    assert "event.setCancelled(!allowWaveRewardPickup" in MAIN
    assert "System.currentTimeMillis()" in pickup


def test_personal_wave_reward_stacks_cannot_merge_across_participants() -> None:
    reward = _body("private boolean spawnWaveCompletionLoot", "private void spawnWave")
    assert "keyRewardStackOwner" in reward
    assert "rewardItemStack(material, amount, recipient)" in reward
    assert "end_event_reward_stack_owner" in MAIN
    assert "ItemMeta" in reward


def test_wave_reward_items_explicitly_allow_players_to_pick_them_up() -> None:
    reward = _body("private boolean spawnWaveCompletionLoot", "private void spawnWave")
    assert reward.count("setCanPlayerPickup(true)") >= 2
    assert reward.count("setCanMobPickup(false)") >= 2
    assert reward.count("setPickupDelay(20)") >= 2
    assert reward.index("setCanPlayerPickup(true)") < reward.index("setPickupDelay(20)")


def test_wave_reward_authorization_covers_both_paper_player_pickup_events() -> None:
    assert "PlayerAttemptPickupItemEvent" in MAIN
    assert "PlayerPickupItemEvent" in MAIN
    assert "onWaveRewardAttemptPickup" in MAIN
    assert "onWaveRewardLegacyPickup" in MAIN
    assert "onWaveRewardAttemptPickupFinal" in MAIN
    assert "EventPriority.MONITOR" in MAIN
    assert "allowWaveRewardPickup" in MAIN


def test_wave_reward_pickup_uses_the_stack_owner_and_releases_only_authorized_rewards() -> None:
    pickup = _body("private boolean allowWaveRewardPickup", "private void")
    assert "keyRewardStackOwner" in pickup
    assert "stackOwnerText" in pickup
    assert "event.setCancelled(!allowWaveRewardPickup" in MAIN
    assert "WAVE_REWARD_PICKUP_ALLOWED" in pickup


def test_wave_reward_does_not_reissue_after_the_durable_wave_marker() -> None:
    reward = _body("private boolean spawnWaveCompletionLoot", "private void spawnWave")
    assert "if (waveRewardsIssued.contains(wave))" in reward
    assert "EVENT_KIND_WAVE_REWARD" in reward
    assert "Test waves" not in reward
    assert "wave-rewards:" in CONFIG
