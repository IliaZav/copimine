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

EXPECTED_ELITE_MOB_ATLAS_HASHES = {
    "enderman_elite.png": "5923111b4ac459daee04aeeea4930dcac4b1156ed3d94bbaaa4b89b987fc6bdc",
    "skeleton_elite.png": "df29a577e2cc5896507044db37349216c2401311e458576ecbb2e0fe8ce65514",
    "spider_elite.png": "40ab699e7dc46d50a26728679539b30ced556269b795b06ed7bf498dd1b4d052",
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


def test_generator_copies_supplied_elite_mob_atlases_byte_for_byte(
    tmp_path: Path, monkeypatch,
) -> None:
    expected_source = (
        CLIENT_TOOLS.parent / "src" / "main" / "asset-source" / "elite-end-event-mobs"
    )
    assert atlas_generator.SUPPLIED_ELITE_MOB_ATLASES == expected_source

    monkeypatch.setattr(atlas_generator, "OUT", tmp_path)
    atlas_generator.copy_supplied_elite_mob_atlases()

    expected_targets = {
        "enderman_elite.png": "end_rift_elite.png",
        "skeleton_elite.png": "end_rift_elite_skeleton.png",
        "spider_elite.png": "end_rift_elite_spider.png",
    }
    for source_name, expected_hash in EXPECTED_ELITE_MOB_ATLAS_HASHES.items():
        source = expected_source / source_name
        target = tmp_path / expected_targets[source_name]
        assert source.is_file(), source
        assert target.is_file(), target
        assert source.read_bytes() == target.read_bytes()
        assert hashlib.sha256(target.read_bytes()).hexdigest() == expected_hash
