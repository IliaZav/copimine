"""Source contracts for Wave 6 control-swap pairing and lifecycle cleanup.

There is no Bukkit harness in this focused gate.  These assertions therefore
check the pure policy boundary and the server methods that own paired control
state, while leaving player-visible runtime acceptance to a separate live
probe.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualControlPairPolicy.java"
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    end = source.find("\n    private ", start + len(signature))
    if end < 0:
        end = source.find("\n    @EventHandler", start + len(signature))
    return source[start:end]


def test_policy_exposes_explicit_exclusion_and_keeps_compatibility() -> None:
    source = POLICY.read_text(encoding="utf-8")

    assert "pair(List<String> participantIds, String excludedId, int requestedPairs)" in source
    assert "if (excludedId != null && excludedId.equals(normalized))" in source
    assert "return pair(participantIds, null, requestedPairs);" in source
    assert "Math.min(MAX_PAIRS, requestedPairs)" in source
    assert "new LinkedHashSet<>()" in source


def test_start_path_passes_the_current_prisoner_to_pairing() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    body = method_body(
        source,
        "private void startRitualControlSwap(long now, int liveAmplifiers)",
    )

    assert "ritualFreeTargets(activeLivingPlayers())" in body
    assert "UUID prisonerId = ritualPrisonerId();" in body
    assert "String excludedId = prisonerId == null ? null : prisonerId.toString();" in body
    assert "RitualControlPairPolicy.pair(\n                ids, excludedId," in body


def test_pair_creation_and_teardown_keep_both_maps_in_sync() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    start_body = method_body(
        source,
        "private void startRitualControlSwap(long now, int liveAmplifiers)",
    )
    teardown_body = method_body(source, "private void clearRitualControlPair(UUID first, UUID second, String reason)")

    for map_name, first, second in (
        ("ritualControlInstances", "first", "second"),
        ("ritualControlPartners", "first", "second"),
        ("ritualControlExpiresAt", "first", "second"),
    ):
        assert f"{map_name}.put({first}," in start_body
        assert f"{map_name}.put({second}," in start_body

    for map_name in (
        "ritualControlInstances",
        "ritualControlPartners",
        "ritualControlExpiresAt",
    ):
        assert f"{map_name}.remove(first)" in teardown_body
        assert f"{map_name}.remove(second)" in teardown_body
    assert 'sendEndControlPacket(Bukkit.getPlayer(first), "STOP"' in teardown_body
    assert 'sendEndControlPacket(Bukkit.getPlayer(second), "STOP"' in teardown_body
    assert "ritualReverseUntil.remove" not in teardown_body


def test_all_pair_lifecycle_triggers_use_atomic_cleanup() -> None:
    source = SOURCE.read_text(encoding="utf-8")

    for handler in (
        "public void onPlayerQuit(PlayerQuitEvent event)",
        "public void onPlayerDeath(PlayerDeathEvent event)",
        "public void onPlayerChangedWorld(PlayerChangedWorldEvent event)",
    ):
        assert "clearRitualControlForPlayerLifecycle" in method_body(source, handler)

    tick_body = method_body(source, "private void tickRitualControls(long now)")
    assert "ritualControlPartners.keySet()" in tick_body
    assert "ritualControlExpiresAt.keySet()" in tick_body
    assert "!Objects.equals(ritualControlPartners.get(partner), id)" in tick_body
    assert "!Objects.equals(ritualControlPairId(instance), ritualControlPairId(partnerInstance))" in tick_body
    assert "!Objects.equals(expiresAt, partnerExpiresAt)" in tick_body
    assert "!ritualControlPlayersShareWorld(id, partner)" in tick_body
    assert "clearRitualControlPair(id, partner, \"invalid-participant\")" in tick_body
    assert "now >= expiresAt" in tick_body
    assert 'clearRitualControlPair(id, partner, "expired")' in tick_body

    input_body = method_body(
        source,
        "private void applyRitualControlInput(Player source, String instanceId, String pairId",
    )
    assert "String sourceInstance = ritualControlInstances.get(sourceId);" in input_body
    assert "String targetInstance = targetId == null ? null : ritualControlInstances.get(targetId);" in input_body
    assert "Long expiresAt = ritualControlExpiresAt.get(sourceId);" in input_body
    assert "Long targetExpiresAt = targetId == null ? null : ritualControlExpiresAt.get(targetId);" in input_body
    assert "!Objects.equals(ritualControlPartners.get(targetId), sourceId)" in input_body
    assert "!Objects.equals(expiresAt, targetExpiresAt)" in input_body
    assert "!source.isOnline()" in input_body
    assert "!target.isOnline()" in input_body
    assert "!isFreeRitualTarget(source)" in input_body
    assert "!isFreeRitualTarget(target)" in input_body
    assert "!Objects.equals(sourceInstance, instanceId)" in input_body
    assert "!Objects.equals(ritualControlPairId(sourceInstance), ritualControlPairId(targetInstance))" in input_body
    assert 'clearRitualControlPair(sourceId, targetId, "invalid-participant")' in input_body
    assert 'clearRitualControlPair(sourceId, targetId, "input-expired")' in input_body


def test_pair_cleanup_accepts_malformed_member_ids_without_touching_reverse_state() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    teardown_body = method_body(source, "private void clearRitualControlPair(UUID first, UUID second, String reason)")

    assert "if (first == null || second == null || first.equals(second))" not in teardown_body
    for map_name in (
        "ritualControlInstances",
        "ritualControlPartners",
        "ritualControlExpiresAt",
    ):
        assert f"{map_name}.remove(first)" in teardown_body
        assert f"{map_name}.remove(second)" in teardown_body
    assert 'sendEndControlPacket(Bukkit.getPlayer(first), "STOP"' in teardown_body
    assert 'sendEndControlPacket(Bukkit.getPlayer(second), "STOP"' in teardown_body
    assert "ritualReverseUntil.remove" not in teardown_body
