from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = (ROOT / "copimine-artifacts" / "items.yml").read_text(encoding="utf-8")
LOCAL_ITEMS = (ROOT / "minecraft/server/plugins/CopiMineArtifacts/items.yml").read_text(encoding="utf-8")


def test_rift_core_shard_has_the_world_echo_lore_in_source_and_local_copy() -> None:
    phrase = "&8Из него всё ещё доносится отголосок мира, которого больше нет"
    response = "&7Он откликается лишь тем, кто стоял у Разлома до конца"
    for catalog in (ITEMS, LOCAL_ITEMS):
        shard = catalog[catalog.index("- id: rift_core_shard"):]
        shard = shard[:shard.index("\n  - id:", 1)] if "\n  - id:" in shard else shard
        assert phrase in shard
        assert response in shard
