"""Wave 6 prisoner input uses A/S/D/F without paired movement control."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
ABILITY = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/ritual/PrisonerAbilityController.java"
PROTOCOL = ROOT / "CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java"


def test_wave6_has_no_reverse_or_pair_state_in_server() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    for obsolete in (
        "RitualControlPairPolicy",
        "startRitualReverse",
        "startRitualControlSwap",
        "ritualControlPartners",
        "ritualReverseUntil",
        "ritualZoneReverseRecipients",
        "sendEndControlPacket",
    ):
        assert obsolete not in source


def test_prisoner_requests_are_bound_to_current_generation_and_server_targets() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    assert '"END_PRISONER_ABILITY_REQUEST"' in source
    assert "packetGeneration != generation" in source
    assert "!isCurrentRitualPrisoner(sender)" in source
    assert "isRitualAbilityTargetAllowed(sender, ability, target)" in source
    assert "prisonerAbilityController.request(" in source
    assert "decision.cooldownUntilMillis()" in source


def test_invalid_target_does_not_consume_a_cooldown() -> None:
    source = ABILITY.read_text(encoding="utf-8")
    request = source[source.index("public synchronized Decision request"):source.index("public synchronized HudState state")]
    assert "validate(" in request
    assert "if (rejection != Rejection.NONE)" in request
    assert request.index("if (rejection != Rejection.NONE)") < request.index("cooldowns.put(ability, until)")
    assert "return Rejection.NO_TARGET" in source


def test_client_does_not_request_or_apply_movement_control_swaps() -> None:
    source = PROTOCOL.read_text(encoding="utf-8")
    assert "END_PRISONER_ABILITY_REQUEST" in source
    assert "RitualControl" not in source
    assert "ReverseMovement" not in source
