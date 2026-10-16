from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for offset in range(opening, len(source)):
        if source[offset] == "{":
            depth += 1
        elif source[offset] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : offset]
    raise AssertionError(f"unterminated Java method: {signature}")


def test_event_music_sync_is_independent_of_signed_visual_bridge_readiness() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    body = _method_body(source, "private void refreshClientBindingsForPlayer(Player player)")

    music_sync = body.index("syncEventMusic(player);")
    bridge_gate = body.index("endEventBridgeV4 == null || !endEventBridgeV4.isReady(playerUuid)")

    assert music_sync < bridge_gate, (
        "native phase music must synchronize before V4 readiness can return early"
    )


def test_repeated_binding_refreshes_do_not_restart_a_player_track() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    body = _method_body(source, "private void syncEventMusic(Player player)")

    sync_gate = body.index("eventMusicSyncPolicy.shouldSync(")
    player_play = body.index("player.playSound(")
    assert sync_gate < player_play, "per-player track deduplication must guard playback"
    assert "eventMusicSyncPolicy.markSynchronized(player.getUniqueId(), track.soundId())" in source, (
        "players reached by a global track start must be marked as already synchronized"
    )
