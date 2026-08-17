from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks" / "src" / "assets" / "copimine"
PLUGIN = ROOT / "copimine-end-event"
MAIN = (PLUGIN / "src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (PLUGIN / "config.yml").read_text(encoding="utf-8")


TRACKS = {
    "end_rift/waves": "waves.ogg",
    "end_rift/boss": "boss.ogg",
    "end_rift/boss_half": "boss_half.ogg",
    "end_rift/boss_final": "boss_final.ogg",
    "end_rift/victory": "victory.ogg",
}


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


def test_music_sources_and_no_vocal_selection_are_recorded() -> None:
    licenses = (ROOT / "resourcepacks" / "END_RIFT_MUSIC_LICENSES.md").read_text(encoding="utf-8")
    assert "CC0" in licenses
    assert "без вокала" in licenses.lower()
    for url in (
        "https://opengameart.org/content/battle-theme-a",
        "https://opengameart.org/content/boss-battle-music",
        "https://opengameart.org/content/boss-battle-2-symphonic-metal",
        "https://opengameart.org/content/dramatic-boss-encounter",
        "https://opengameart.org/content/victory-theme-for-rpg",
    ):
        assert url in licenses


def test_music_configuration_and_phase_hooks_are_present() -> None:
    for key in ("waves:", "boss:", "boss-half:", "boss-final:", "victory:"):
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
    ):
        assert hook in MAIN
