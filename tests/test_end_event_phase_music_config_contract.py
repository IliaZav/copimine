"""Keep the phase music config complete for every key required at plugin startup."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "copimine-end-event" / "config.yml"
EVENT_CONFIG = ROOT / "copimine-end-event" / "src/me/copimine/endevent/EventConfig.java"


def test_every_required_phase_music_entry_has_a_sound_and_loop_duration() -> None:
    config_text = CONFIG.read_text(encoding="utf-8")
    source_text = EVENT_CONFIG.read_text(encoding="utf-8")

    loader_start = source_text.index("private static Map<String, MusicTrack> readPhaseMusic")
    keys_start = source_text.index("new ArrayList<>(List.of(", loader_start)
    keys_end = source_text.index("));", keys_start)
    required_keys = re.findall(r'"([^"\\]+)"', source_text[keys_start:keys_end])

    phase_header = re.search(r"(?m)^  phase:\s*$", config_text)
    assert phase_header is not None, "music.phase section is missing"
    phase_text = config_text[phase_header.end() :]
    following_root_key = re.search(r"(?m)^[^\s#][^\r\n]*$", phase_text)
    if following_root_key is not None:
        phase_text = phase_text[: following_root_key.start()]

    entry_headers = list(re.finditer(r"(?m)^    ([a-z0-9-]+):\s*$", phase_text))
    entries = {
        entry.group(1): phase_text[entry.end() : entry_headers[index + 1].start() if index + 1 < len(entry_headers) else len(phase_text)]
        for index, entry in enumerate(entry_headers)
    }

    for key in required_keys:
        assert key in entries, f"music.phase.{key} section is missing"
        entry = entries[key]
        assert re.search(r"(?m)^      sound:\s*[a-z0-9_.-]+:[a-z0-9_./-]+\s*$", entry), (
            f"music.phase.{key}.sound must be a namespaced sound id"
        )
        assert re.search(r"(?m)^      loop-seconds:\s*\d+\s*$", entry), (
            f"music.phase.{key}.loop-seconds must be a non-negative integer"
        )
