"""Source contracts for Wave 6 corrupted-zone effect routing.

The pure policy test covers the decision table.  These source assertions keep
the Bukkit adapter from bypassing that policy or turning the zone into a raw
damage/Poison effect, while leaving player-visible behavior to a live probe.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "RitualZoneEffectPolicy.java"
)
SOURCE = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "CopiMineEndEvent.java"
)


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    end = source.find("\n    private ", start + len(signature))
    if end < 0:
        end = source.find("\n    @EventHandler", start + len(signature))
    return source[start:end]


def test_policy_exposes_the_exact_zone_decision_table() -> None:
    source = POLICY.read_text(encoding="utf-8")

    assert "public static Result effect(boolean prisoner," in source
    assert "boolean insideActiveZone," in source
    assert "boolean controlSwapActive)" in source
    assert "record Result(boolean wither, boolean slowness, boolean reverseMovement)" in source


def test_zone_tick_routes_free_players_through_wither_slowness_policy() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    body = method_body(source, "private void tickRitualZones(long now)")

    assert "ritualFreeTargets(activeLivingPlayers())" in body
    assert "ritualZoneContains(center, player.getLocation())" in body
    assert "RitualZoneEffectPolicy.effect(" in body
    assert "PotionEffectType.WITHER" in body
    assert "PotionEffectType.SLOWNESS" in body
    assert "PotionEffectType.POISON" not in body
    assert "player.damage(" not in body


def test_zone_reverse_state_is_server_owned_and_cleared_on_reconciliation() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    tick_body = method_body(source, "private void tickRitualZones(long now)")
    reverse_body = method_body(source, "private boolean startRitualReverse(Player target, long now")

    assert "ritualControlPartners.containsKey" in tick_body
    assert "ritualZoneReverseRecipients" in tick_body
    assert "clearRitualZoneReverse" in tick_body
    assert "ritualControlInstances.put" in reverse_body
    assert "ritualReverseUntil.put" in reverse_body
    assert 'sendEndControlPacket(target, "START"' in reverse_body
    assert 'sendEndControlPacket(Bukkit.getPlayer(first), "STOP"' in source
    assert 'sendEndControlPacket(Bukkit.getPlayer(second), "STOP"' in source


def test_zone_cleanup_does_not_remove_general_potion_effects() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    tick_body = method_body(source, "private void tickRitualZones(long now)")

    assert "removePotionEffect" not in tick_body
    assert "clearRitualZoneReverse" in tick_body
