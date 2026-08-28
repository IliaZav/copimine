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
    pickup = _body("public void onWaveRewardPickup", "private void")
    assert "event.setCancelled(true)" in pickup
    assert "System.currentTimeMillis()" in pickup


def test_wave_reward_does_not_reissue_after_the_durable_wave_marker() -> None:
    reward = _body("private boolean spawnWaveCompletionLoot", "private void spawnWave")
    assert "if (waveRewardsIssued.contains(wave))" in reward
    assert "EVENT_KIND_WAVE_REWARD" in reward
    assert "Test waves" not in reward
    assert "wave-rewards:" in CONFIG
