from __future__ import annotations

import hashlib
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLIENT_TOOLS = ROOT / "CopiMineClient" / "tools"
sys.path.insert(0, str(CLIENT_TOOLS))

import generate_end_rift_texture_atlases as atlas_generator  # noqa: E402


EXPECTED_SKIN_HASHES = {
    "enderman-1.png": "a9a154f232919627451431e3f3874c9e850f23e531eae2cfe2a4a9cc16edf447",
    "spider.png": "19c46ff4aa829e7101b25a50a55090cd1d8145c2f83b95d64c13a20f6b5c9abf",
}


def test_generator_restores_supplied_skins_from_tracked_asset_source(
    tmp_path: Path, monkeypatch,
) -> None:
    expected_source = CLIENT_TOOLS.parent / "src" / "main" / "asset-source" / "end-event-mobs"
    assert atlas_generator.SUPPLIED_SKINS == expected_source

    monkeypatch.setattr(atlas_generator, "OUT", tmp_path)
    atlas_generator.copy_supplied_user_skins()

    for source_name, expected_hash in EXPECTED_SKIN_HASHES.items():
        target_name = {
            "enderman-1.png": "end_rift_user_enderman.png",
            "spider.png": "end_rift_user_spider.png",
        }[source_name]
        target = tmp_path / target_name
        assert target.is_file()
        assert hashlib.sha256(target.read_bytes()).hexdigest() == expected_hash
