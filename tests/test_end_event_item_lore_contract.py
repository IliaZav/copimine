from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = (ROOT / "copimine-artifacts" / "items.yml").read_text(encoding="utf-8")
LOCAL_ITEMS = (ROOT / "minecraft/server/plugins/CopiMineArtifacts/items.yml").read_text(encoding="utf-8")


def test_rift_core_shard_uses_the_v2_name_and_single_atmospheric_lore_line() -> None:
    name = 'name: "&eОсколок Ядра Разлома"'
    phrase = '&eИз него всё ещё доносятся отголоски хаоса Разлома.'
    for catalog in (ITEMS, LOCAL_ITEMS):
        shard = catalog[catalog.index("- id: rift_core_shard"):]
        shard = shard[:shard.index("\n  - id:", 1)] if "\n  - id:" in shard else shard
        assert name in shard
        assert "cooldown_seconds: 600" in shard
        assert phrase in shard
        assert '      - "' in shard
        assert shard.count('      - "') == 1
