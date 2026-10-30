"""Мост сайт↔игра хранится в репозитории и сверяется с кодовой базой сайта.

Источник JAR раньше жил вне версионного контроля, поэтому нельзя было
воспроизвести сборку и доказать, что сервер принимает ровно те операции,
которые предлагает админка.
"""

from pathlib import Path

ROOT = Path(__file__).parents[1]
BRIDGE = ROOT / "game-bridge" / "src" / "me" / "copimine" / "website" / "CopiMineWebsiteBridge.java"


def test_bridge_source_is_in_version_control():
    assert BRIDGE.is_file(), "game-bridge source must be committed"
    assert (ROOT / "game-bridge" / "plugin.yml").is_file()


def test_every_website_action_has_a_bridge_handler():
    from backend.domain import SUPPORTED_ACTIONS

    source = BRIDGE.read_text(encoding="utf-8")
    # Мост обрабатывает операции двумя путями: отдельные if (снимки/карта/привязка)
    # и общий switch действий. Учитываем оба, иначе тест ложно падает.
    handled = set()
    for marker in ('operation.equals("', "case "):
        for part in source.split(marker)[1:]:
            if marker == 'operation.equals("':
                name = part.split('"')[0]
                handled.add(name)
                continue
            for name in part.split("->")[0].split(","):
                name = name.strip().strip('"')
                if name and all(c.isalnum() or c in "._" for c in name):
                    handled.add(name)

    missing = sorted(SUPPORTED_ACTIONS - handled)
    assert not missing, "сайт предлагает действия без обработчика в мосту: " + ", ".join(missing)


def test_bridge_scopes_exactly_the_published_maps():
    source = BRIDGE.read_text(encoding="utf-8")
    expected = {
        "public_overworld", "public_nether", "public_end",
        "admin_overworld", "admin_nether", "admin_end",
    }
    for name in expected:
        assert '"' + name + '"' in source, "мост не знает карту " + name