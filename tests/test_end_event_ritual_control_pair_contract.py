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
    body = method_body(source, "private void startRitualControlSwap(long now)")

    assert "ritualFreeTargets(activeLivingPlayers())" in body
    assert "ritualPrisonerUuid == null ? null : ritualPrisonerUuid.toString()" in body
    assert "RitualControlPairPolicy.pair(\n                ids, excludedId," in body


def test_pair_creation_and_teardown_keep_both_maps_in_sync() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    start_body = method_body(source, "private void startRitualControlSwap(long now)")
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
    assert "!isValidRitualControlParticipant" in tick_body
    assert 'clearRitualControl(id, "invalid-participant")' in tick_body
    assert "now >= ritualControlExpiresAt.getOrDefault(id, 0L)" in tick_body
    assert 'clearRitualControl(id, "expired")' in tick_body

    input_body = method_body(
        source,
        "private void applyRitualControlInput(Player source, String instanceId, String pairId",
    )
    assert "!isFreeRitualTarget(source)" in input_body
    assert "!isFreeRitualTarget(target)" in input_body
    assert 'clearRitualControl(sourceId, "invalid-participant")' in input_body
