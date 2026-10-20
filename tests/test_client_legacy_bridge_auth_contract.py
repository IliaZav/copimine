from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT_PROTOCOL = ROOT / "CopiMineClient/src/main/java/me/copimine/client/ClientBridgeProtocol.java"


def test_unsigned_v3_mutations_are_rejected_before_the_legacy_receiver_dispatches():
    source = CLIENT_PROTOCOL.read_text(encoding="utf-8")
    receiver_start = source.index("ClientPlayNetworking.registerGlobalReceiver(BridgePayload.ID,")
    receiver_end = source.index("        });", receiver_start)
    receiver = source[receiver_start:receiver_end]

    guard = "if (isLegacyProtectedMutationRejected(payload.type()))"
    assert guard in receiver
    assert receiver.index(guard) < receiver.index("switch (payload.type())")
    assert "return;" in receiver[receiver.index(guard):receiver.index("switch (payload.type())")]

    for mutation in ("TYPE_VISUAL_START", "TYPE_VISUAL_STOP", "TYPE_VISUAL_CLEAR_ALL"):
        assert f"case {mutation}" not in receiver
    for effect in ("manager.start(", "manager.stop(", "manager.clearAll(", "END_EVENT_PLAYER_VISUALS.clear("):
        assert effect not in receiver

    predicate_start = source.index("static boolean isLegacyProtectedMutationRejected(")
    predicate_end = source.index("\n    }", predicate_start)
    predicate = source[predicate_start:predicate_end]
    for rejected in (
        "payloadType.startsWith(TYPE_END_EVENT_PREFIX)",
        "TYPE_VISUAL_START.equals(payloadType)",
        "TYPE_VISUAL_STOP.equals(payloadType)",
        "TYPE_VISUAL_CLEAR_ALL.equals(payloadType)",
        "TYPE_PRISONER_ABILITY_REQUEST.equals(payloadType)",
    ):
        assert rejected in predicate
