"""Поиск админки: у каждого раздела, подраздела и действия должны быть алиасы.

Раньше часть действий («Сохранить мир», «Время суток», «Белый список») не находилась
поиском: в таблице алиасов для них не было строк. Тест не даёт этому вернуться.
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEARCH = ROOT / "frontend" / "assets" / "js" / "search-v73.js"
ACTIONS = ROOT / "frontend" / "assets" / "js" / "actions-v6.js"
REVIEW = ROOT / "frontend" / "assets" / "js" / "review-v7.js"


def _search_source():
    return SEARCH.read_text(encoding="utf-8")


def _section_aliases():
    src = _search_source()
    block = re.search(r"const aliases=\{(.*?)\n\};", src, re.S)
    assert block, "таблица aliases не найдена"
    return dict(re.findall(r"^\s*(\w+):'([^']*)'", block.group(1), re.M))


def _subtab_aliases():
    src = _search_source()
    return re.findall(r"^\s*\['(\w+)','(\w+)','([^']+)','([^']*)'", src, re.M)


def _action_aliases():
    src = _search_source()
    block = re.search(r"const actionAliases=\{(.*?)\n\};", src, re.S)
    assert block, "таблица actionAliases не найдена"
    return dict(re.findall(r"^\s*'([\w.-]+)':'([^']*)'", block.group(1), re.M))


def _actions():
    src = ACTIONS.read_text(encoding="utf-8")
    block = re.search(r"window\.CM6_ADMIN_ACTIONS=(\{.*?)\s*;?\s*$", src, re.S)
    assert block, "журнал действий не найден"
    return json.loads(block.group(1))["actions"]


def _nav_tabs():
    src = REVIEW.read_text(encoding="utf-8")
    block = re.search(r"const adminGroups=\[(.*?)\n\];", src, re.S)
    assert block, "навигация админки не найдена"
    return re.findall(r"\['([a-z]+)','[^']+','[a-z-]+'\]", block.group(1))


def test_every_admin_tab_has_aliases():
    aliases = _section_aliases()
    missing = [tab for tab in _nav_tabs() if tab not in aliases]
    assert not missing, f"у разделов нет алиасов: {missing}"


def test_every_action_has_aliases():
    aliases = _action_aliases()
    missing = [a["id"] for a in _actions() if a["id"] not in aliases]
    assert not missing, f"у действий нет алиасов: {missing}"


def test_every_subtab_has_aliases():
    empty = [f"{area}/{sub}" for area, sub, _title, alias in _subtab_aliases() if not alias.strip()]
    assert not empty, f"у подразделов нет алиасов: {empty}"


def test_action_aliases_cover_russian_names():
    """Алиасы должны включать само название действия: иначе поиск по нему промахнётся."""
    aliases = _action_aliases()
    weak = []
    for action in _actions():
        words = [w.lower() for w in aliases.get(action["id"], "").split(";") if w.strip()]
        name = action["name"].lower()
        if name not in words and not any(w in name for w in words if len(w) > 3):
            weak.append(action["id"])
    assert not weak, f"алиасы не покрывают название: {weak}"


def test_hidden_actions_are_marked_unavailable():
    """Действие без поддержки моста помечается available=False и не попадает в интерфейс."""
    bridge = {
        "kick", "ban", "unban", "heal", "feed", "kill", "gamemode", "teleport",
        "freeze", "unfreeze", "inventory.snapshot", "inventory.remove",
        "whitelist.add", "whitelist.remove", "time.day", "time.night",
        "weather.clear", "weather.rain", "save",
    }
    wrong = [a["id"] for a in _actions() if a.get("available") is not (a["id"] in bridge)]
    assert not wrong, f"неверная пометка доступности: {wrong}"
