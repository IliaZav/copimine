"""Wave 6 prisoner input uses A/S/D/F without paired movement control."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
ABILITY = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/ritual/PrisonerAbilityController.java"
V4_SENDER = ROOT / "copimine-end-event/src/me/copimine/endevent/bridge/EndEventBridgeV4Sender.java"
V4_BOOTSTRAP = ROOT / "copimine-end-event/src/me/copimine/endevent/EndEventBridgeV4Bootstrap.java"
V4_CLIENT = ROOT / "tools/minecraft-26.3/client/src/main/java/me/copimine/client/ClientBridgeProtocolV4.java"


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
    event = SOURCE.read_text(encoding="utf-8")
    sender = V4_SENDER.read_text(encoding="utf-8")
    bootstrap = V4_BOOTSTRAP.read_text(encoding="utf-8")
    legacy_listener = event[event.index("public void onPluginMessageReceived"):
                            event.index("private void registerClientBridgeSession")]
    bridge_handler = event[event.index("void handleEndEventBridgeAbility"):
                           event.index("private void sendCurrentPresentationResume")]
    prisoner_handler = event[event.index("private void handleRitualPrisonerAbilityRequest"):
                             event.index("private void sendRitualPrisonerState")]

    # The V3 plugin-message listener remains telemetry only. Mutating Wave 6 requests
    # must pass through the current V4 session, stream, event, and generation checks.
    assert 'CHANNEL = "copimine:client_bridge_v4"' in sender
    assert "eventPlugin::handleEndEventBridgeAbility" in bootstrap
    assert "BridgeRequestCodec.decode(exactRequest)" in sender
    assert "matchesCurrentBinding(player, state, request.clientModSessionUuid(), request.clientNonce())" in sender
    assert "requestCounter != state.requestCounter + 1L" in sender
    assert "MessageDigest.isEqual(state.lastRequestBytes, exactRequest)" in sender
    assert "stream.readyConfirmed" in sender
    assert "context.eventUuid().equals(ability.eventUuid())" in sender
    assert "context.generation() != ability.generation()" in sender
    assert "V3 is telemetry only; mutating requests require the signed V4 receiver." in legacy_listener
    assert "handleRitualPrisonerAbilityRequest" not in legacy_listener

    # Event state is checked again at dispatch time, including the live prisoner and target.
    assert "request.generation() != generation" in bridge_handler
    assert "handleRitualPrisonerAbilityRequest(player, request.generation()" in bridge_handler
    assert "packetGeneration != generation" in prisoner_handler
    assert "!isCurrentRitualPrisoner(sender)" in prisoner_handler
    assert "isRitualAbilityTargetAllowed(sender, ability, target)" in prisoner_handler
    assert "prisonerAbilityController.request(" in prisoner_handler
    assert "decision.cooldownUntilMillis()" in prisoner_handler


def test_invalid_target_does_not_consume_a_cooldown() -> None:
    source = ABILITY.read_text(encoding="utf-8")
    request = source[source.index("public synchronized Decision request"):source.index("public synchronized HudState state")]
    assert "validate(" in request
    assert "if (rejection != Rejection.NONE)" in request
    assert request.index("if (rejection != Rejection.NONE)") < request.index("cooldowns.put(ability, until)")
    assert "return Rejection.NO_TARGET" in source


def test_client_does_not_request_or_apply_movement_control_swaps() -> None:
    source = V4_CLIENT.read_text(encoding="utf-8")
    assert "sendPrisonerAbility" in source
    assert "isReady(BridgeRequestCodec.Sender.END_EVENT)" in source
    assert "RitualControl" not in source
    assert "ReverseMovement" not in source
