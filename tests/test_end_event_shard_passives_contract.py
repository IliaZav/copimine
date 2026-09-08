from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
SNAPSHOT = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventSnapshot.java").read_text(
    encoding="utf-8"
)
STORE = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventStateStore.java").read_text(
    encoding="utf-8"
)


def test_anchor_cooldown_is_configured_and_durable() -> None:
    assert "abyss-anchor-cooldown-seconds: 1800" in CONFIG
    assert "abyssAnchorCooldowns" in SNAPSHOT
    assert "rewards.abyss-anchor-cooldowns" in STORE
    assert "abyssAnchorCooldowns" in MAIN


def test_shard_passives_have_server_authoritative_handlers() -> None:
    for marker in (
        "AbyssAnchorPolicy",
        "ShardPassivePolicy",
        "tickShardPassives",
        "onRiftShardVoidRescue",
        "onRiftShardEndermanDamage",
        "onRiftShardEnderPearlDamage",
        "isAuthenticRiftCoreShard",
    ):
        assert marker in MAIN


def test_anchor_records_cooldown_before_teleport_and_restores_two_health() -> None:
    start = MAIN.index("onRiftShardVoidRescue")
    end = MAIN.index("onRiftShardEndermanDamage", start)
    body = MAIN[start:end]
    assert "event.setCancelled(true)" in body
    assert "abyssAnchorCooldowns.put" in body
    assert "saveStateSync()" in body
    assert "player.setHealth(AbyssAnchorPolicy.RESCUE_HEALTH)" in body
    assert body.index("saveStateSync()") < body.index("player.teleport")


def test_owned_end_effects_are_hidden_and_removed_without_touching_other_effects() -> None:
    assert "PotionEffectType.STRENGTH" in MAIN
    assert "PotionEffectType.SPEED" in MAIN
    assert "END_EFFECT_REFRESH_TICKS" in MAIN
    assert "false, false, false), true" in MAIN
    assert "removeShardPassiveEffects" in MAIN
