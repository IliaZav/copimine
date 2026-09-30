"""Contracts for the fixed, escapable server-owned Gravity Well."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualZoneEffectPolicy.java"
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    end = source.find("\n    private ", start + len(signature))
    if end < 0:
        end = source.find("\n    @EventHandler", start + len(signature))
    return source[start:end]


def test_gravity_well_policy_uses_a_fixed_four_block_radius_and_bounded_pull() -> None:
    source = POLICY.read_text(encoding="utf-8")
    assert "RADIUS_BLOCKS = 4.0D" in source
    assert "PULL_PER_UPDATE = 0.12D" in source
    assert "public static Result effect(boolean insideActiveZone, boolean damagePulseDue)" in source
    assert "record Result(boolean slowness, boolean pull, boolean periodicDamage)" in source
    assert "return new Pull(0.0D, 0.0D)" in source


def test_zone_is_server_timed_telegraph_active_effect_and_periodic_damage() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    start = method_body(
        source,
        "private void startRitualZone(Player target, Location selectedCenter,",
    )
    tick = method_body(source, "private void tickRitualZones(long now)")
    assert "RITUAL_ZONE_TELEGRAPH_MILLIS = 1_200L" in source
    assert "RITUAL_ZONE_DURATION_MILLIS = 3_500L" in source
    assert "ritualZoneTelegraphUntil.put(zoneId, now + warning)" in start
    assert "ritualZoneExpiresAt.put(zoneId, now + warning + durationMillis)" in start
    assert "RitualZoneEffectPolicy.effect(" in tick
    assert "RitualZoneEffectPolicy.pull(" in tick
    assert "player.damage(1.0D)" in tick
    assert "ritualZoneNextDamageAtMillis.put(playerId, now + 1_000L)" in tick


def test_zone_effect_is_limited_to_active_players_inside_radius_and_cleans_up() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    tick = method_body(source, "private void tickRitualZones(long now)")
    expire = method_body(source, "private void expireRitualZones(long now)")
    assert "ritualFreeTargets(activeLivingPlayers())" in tick
    assert "if (!effects.slowness())" in tick
    assert "ritualZoneNextDamageAtMillis.keySet().retainAll(affectedPlayers)" in tick
    assert "RitualZoneEffectPolicy.contains(" in source
    assert "if (now >= ritualZoneExpiresAt.getOrDefault(zoneId, 0L))" in expire
    assert "ritualZoneCenters.remove(zoneId)" in expire


def test_gravity_well_has_no_reverse_movement_or_wither_damage_route() -> None:
    policy = POLICY.read_text(encoding="utf-8")
    source = SOURCE.read_text(encoding="utf-8")
    assert "controlSwap" not in policy
    assert "reverseMovement" not in policy
    zone_tick = method_body(source, "private void tickRitualZones(long now)")
    assert "PotionEffectType.WITHER" not in zone_tick
    assert "RitualControlPair" not in source
