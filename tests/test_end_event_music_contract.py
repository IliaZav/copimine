from __future__ import annotations

import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "src" / "assets" / "copimine"
PLUGIN = ROOT / "copimine-end-event"
MAIN = (PLUGIN / "src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (PLUGIN / "config.yml").read_text(encoding="utf-8")
RUNE_WAIT_LIVE = (ROOT / "tests/RunEndRiftRuneWaitMusicLive.ps1").read_text(encoding="utf-8")


TRACKS = {
    "end_rift/waves": "waves.ogg",
    "end_rift/boss": "boss.ogg",
    "end_rift/boss_half": "boss_half.ogg",
    "end_rift/boss_final": "boss_final.ogg",
    "end_rift/victory": "victory.ogg",
}

PHASE_TRACKS = {
    "end_rift/ritual_wait": "ritual_wait.ogg",
    "end_rift/wave_1": "wave_1.ogg",
    "end_rift/wave_2": "wave_2.ogg",
    "end_rift/wave_3": "wave_3.ogg",
    "end_rift/wave_4": "wave_4.ogg",
    "end_rift/wave_5": "wave_5.ogg",
    "end_rift/intermission_1": "intermission_1.ogg",
    "end_rift/intermission_2": "intermission_2.ogg",
    "end_rift/intermission_3": "intermission_3.ogg",
    "end_rift/intermission_4": "intermission_4.ogg",
    "end_rift/boss_cinematic": "boss_cinematic.ogg",
    "end_rift/final_drain": "final_drain.ogg",
    "end_rift/final_ritual": "final_ritual.ogg",
    "end_rift/final_wave": "final_wave.ogg",
    "end_rift/boss_finish": "boss_finish.ogg",
}


def _ogg_crc(page: bytes) -> int:
    value = 0
    for byte in page:
        value ^= byte << 24
        for _ in range(8):
            value = ((value << 1) ^ 0x04C11DB7) & 0xFFFFFFFF if value & 0x80000000 else (value << 1) & 0xFFFFFFFF
    return value


def test_resource_pack_declares_five_streamed_instrumental_event_tracks() -> None:
    sounds = json.loads((PACK / "sounds.json").read_text(encoding="utf-8"))
    assert set(TRACKS) <= set(sounds)
    for sound_id, filename in TRACKS.items():
        entries = sounds[sound_id]["sounds"]
        assert entries == [{"name": f"copimine:{sound_id}", "stream": True}]
        audio = PACK / "sounds" / "end_rift" / filename
        assert audio.is_file(), audio
        assert audio.stat().st_size > 1_000, audio
        assert audio.read_bytes()[:4] == b"OggS", audio


def test_resource_pack_declares_one_distinct_streamed_track_for_each_event_beat() -> None:
    sounds = json.loads((PACK / "sounds.json").read_text(encoding="utf-8"))
    assert set(PHASE_TRACKS) <= set(sounds)
    filenames = set()
    for sound_id, filename in PHASE_TRACKS.items():
        entries = sounds[sound_id]["sounds"]
        assert entries == [{"name": f"copimine:{sound_id}", "stream": True}]
        audio = PACK / "sounds" / "end_rift" / filename
        assert audio.is_file(), audio
        assert audio.stat().st_size > 100_000, audio
        assert audio.read_bytes()[:4] == b"OggS", audio
        filenames.add(filename)
    assert len(filenames) == len(PHASE_TRACKS)


def test_each_phase_track_is_a_valid_ogg_with_the_configured_duration() -> None:
    expected_seconds = {
        "ritual_wait.ogg": 22,
        "wave_1.ogg": 48, "wave_2.ogg": 46, "wave_3.ogg": 44,
        "wave_4.ogg": 50, "wave_5.ogg": 54,
        "intermission_1.ogg": 14, "intermission_2.ogg": 14,
        "intermission_3.ogg": 16, "intermission_4.ogg": 16,
        "boss_cinematic.ogg": 22, "final_drain.ogg": 24,
        "final_ritual.ogg": 20, "final_wave.ogg": 48,
        "boss_finish.ogg": 20,
    }
    for filename, expected in expected_seconds.items():
        duration = _ogg_duration_seconds(PACK / "sounds" / "end_rift" / filename)
        assert abs(duration - expected) <= 0.25, f"{filename}: {duration:.3f}s"


def test_music_sources_and_no_vocal_selection_are_recorded() -> None:
    licenses = (ROOT / "resourcepacks" / "END_RIFT_MUSIC_LICENSES.md").read_text(encoding="utf-8")
    assert "CC0" in licenses
    assert "без вокала" in licenses.lower()
    assert "производные аранжировки" in licenses.lower()
    for filename in PHASE_TRACKS.values():
        assert filename in licenses
    for url in (
        "https://opengameart.org/content/battle-theme-a",
        "https://opengameart.org/content/boss-battle-music",
        "https://opengameart.org/content/boss-battle-2-symphonic-metal",
        "https://opengameart.org/content/dramatic-boss-encounter",
        "https://opengameart.org/content/victory-theme-for-rpg",
    ):
        assert url in licenses


def _ogg_duration_seconds(path: Path) -> float:
    data = path.read_bytes()
    offset = 0
    last_granule = 0
    last_flags = 0
    first_packet = bytearray()
    packet = bytearray()
    while offset < len(data):
        assert data[offset:offset + 4] == b"OggS", f"invalid Ogg page at {offset}"
        header = data[offset:offset + 27]
        segment_count = header[26]
        lacing = data[offset + 27:offset + 27 + segment_count]
        payload_start = offset + 27 + segment_count
        payload_size = sum(lacing)
        payload = data[payload_start:payload_start + payload_size]
        last_granule = struct.unpack("<q", header[6:14])[0]
        page = bytearray(data[offset:payload_start + payload_size])
        stored_crc = struct.unpack("<I", page[22:26])[0]
        page[22:26] = b"\x00\x00\x00\x00"
        assert _ogg_crc(page) == stored_crc, f"invalid Ogg CRC at {offset}"
        last_flags = header[5]
        cursor = 0
        for segment_size in lacing:
            packet.extend(payload[cursor:cursor + segment_size])
            cursor += segment_size
            if segment_size < 255:
                if not first_packet:
                    first_packet = bytearray(packet)
                packet.clear()
        offset = payload_start + payload_size
    assert first_packet[:7] == b"\x01vorbis"
    sample_rate = struct.unpack("<I", first_packet[12:16])[0]
    assert sample_rate > 0
    assert last_granule > 0
    assert last_flags & 0x04, "Ogg stream must end with EOS"
    return last_granule / sample_rate


def test_victory_track_is_a_compact_twenty_second_instrumental() -> None:
    duration = _ogg_duration_seconds(PACK / "sounds" / "end_rift" / "victory.ogg")
    assert 19.5 <= duration <= 20.5, f"victory track must be about 20 seconds, got {duration:.3f}"


def test_music_configuration_and_phase_hooks_are_present() -> None:
    for key in ("waves:", "boss:", "boss-half:", "boss-final:", "victory:"):
        assert key in CONFIG
    for key in (
        "ritual-wait:",
        "wave-1:", "wave-2:", "wave-3:", "wave-4:", "wave-5:",
        "intermission-1:", "intermission-2:", "intermission-3:", "intermission-4:",
        "boss-cinematic:", "final-drain:", "final-ritual:",
        "final-wave:", "boss-finish:",
    ):
        assert key in CONFIG
    for hook in (
        "playEventMusic",
        "stopEventMusic",
        "END_EVENT_MUSIC",
        "END_EVENT_MUSIC_TEST",
        "Bukkit.getPlayerExact(args[3])",
        "SoundCategory.MUSIC",
        "config.wavesMusic()",
        "config.bossMusic()",
        "config.bossHalfMusic()",
        "config.bossFinalMusic()",
        "config.victoryMusic()",
        "config.phaseMusic(normalized)",
        "config.allMusicTracks()",
        "phaseMusicOrLegacy",
        'case WAVE_1 -> phaseMusicOrLegacy("wave-1"',
        'case INTERMISSION_4 -> phaseMusicOrLegacy("intermission-4"',
        'case FINAL_RITUAL -> phaseMusicOrLegacy("final-ritual"',
        'case READY_FOR_PLAYERS -> config.ritualWaitMusic()',
        'case COUNTDOWN -> config.ritualWaitMusic()',
        "isRitualMusicPhase",
    ):
        assert hook in MAIN


def test_rune_wait_music_reaches_players_who_join_or_respawn_during_wait() -> None:
    join = MAIN[MAIN.index("public void onPlayerJoin") : MAIN.index("public void onPlayerQuit")]
    respawn = MAIN[MAIN.index("public void onPlayerRespawn") : MAIN.index("public void onShardChannelDamage")]
    changed_world = MAIN[MAIN.index("public void onPlayerChangedWorld") : MAIN.index("public void onShardInteract")]
    for handler in (join, respawn, changed_world):
        assert "isEventMusicPhase()" in handler
        assert "refreshClientBindingsForPlayer" in handler


def test_live_rune_wait_probe_uses_real_pad_occupancy_and_automatic_music() -> None:
    for marker in (
        "environment:\\s*local",
        "server-port=25566",
        "rcon\\.port=25576",
        "READY_FOR_PLAYERS",
        "Get-PadCoordinates",
        "Teleport-ToPad",
        "state=COUNTDOWN",
        "pads=2/2",
        "SOUND_PACKET .*sound_effect.*ritual_wait",
        "LIVE_RUNE_WAIT_MUSIC_PASS",
        "LIVE_RUNE_WAIT_MUSIC_CLEANUP_PASS",
        "cmend ritual cancel",
        "cmend wave clear",
    ):
        assert marker in RUNE_WAIT_LIVE
