"""Проверяет, что id карт BlueMap сопоставляются явно и что срок кода привязки
одинаков в плагине, бэкенде и тексте для игрока."""

from pathlib import Path
import re

ROOT = Path(__file__).parents[1]
BRIDGE = ROOT / "game-bridge" / "src" / "me" / "copimine" / "website" / "CopiMineWebsiteBridge.java"


def test_map_ids_accept_both_underscore_and_hyphen_names():
    from backend.maps import MAP_IDS, DIMENSIONS

    assert set(MAP_IDS) == {"public", "admin"}
    for scope in ("public", "admin"):
        assert set(MAP_IDS[scope]) == {d for d, _ in DIMENSIONS}
        for pair in MAP_IDS[scope].values():
            assert pair == (scope + "-" + pair[0].split("-", 1)[1], scope + "_" + pair[0].split("-", 1)[1])
    assert MAP_IDS["public"]["overworld"] == ("public-overworld", "public_overworld")
    assert MAP_IDS["admin"]["end"] == ("admin-end", "admin_end")


def test_publication_reads_every_dimension_through_the_map_id_table(tmp_path):
    import json
    from backend.maps import MapStorage, DIMENSIONS

    source = tmp_path / "source"
    source.mkdir()
    (source / "index.html").write_text("<html>BlueMap</html>")
    # Underscore naming, as the live server writes it.
    ids = ["public_" + d for d, _ in DIMENSIONS] + ["admin_" + d for d, _ in DIMENSIONS]
    (source / "settings.json").write_text(json.dumps({"maps": ids}))
    for map_id in ids:
        folder = source / "maps" / map_id
        folder.mkdir(parents=True)
        (folder / "settings.json").write_text(json.dumps({"name": map_id}))
        (folder / "textures.json").write_text("{}")

    store = MapStorage(tmp_path / "published", minimum_free=0)
    stats = store.publish(source, "d" * 32)
    assert stats["dimensions"]["public"] == ["public_" + d for d, _ in DIMENSIONS]
    assert stats["dimensions"]["private"] == ["admin_" + d for d, _ in DIMENSIONS]


def test_link_code_expiry_is_one_tested_value():
    from backend.game import LINK_TTL_MS

    source = BRIDGE.read_text(encoding="utf-8")
    minutes = re.search(r"[Сс]рок — (\d+) минут", source)
    assert minutes, "в сообщении плагина должен быть указан срок кода"
    assert int(minutes.group(1)) * 60 * 1000 == LINK_TTL_MS

    page = (ROOT / "frontend" / "assets" / "js" / "copimine-react.v1.js").read_text(encoding="utf-8")
    fallback = re.search(r'"через (\d+) минут"', page)
    assert fallback and int(fallback.group(1)) * 60 * 1000 == LINK_TTL_MS

    account = (ROOT / "frontend" / "assets" / "js" / "account-v9.js").read_text(encoding="utf-8")
    assert str(LINK_TTL_MS) in account, "запасной срок в account-v9.js должен совпадать"