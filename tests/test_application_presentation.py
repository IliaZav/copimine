import ast
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BOT = ROOT / "admin-web" / "backend" / "discord_bot.py"


def load_formatter():
    tree = ast.parse(BOT.read_text(encoding="utf-8"))
    wanted = []
    for node in tree.body:
        if isinstance(node, ast.Assign):
            targets = [target.id for target in node.targets if isinstance(target, ast.Name)]
            if "APPLICATION_ANSWER_LABELS" in targets:
                wanted.append(node)
        elif isinstance(node, ast.FunctionDef) and node.name == "format_application_answers":
            wanted.append(node)
    namespace: dict[str, Any] = {"Any": Any, "json": json}
    exec(compile(ast.Module(body=wanted, type_ignores=[]), str(BOT), "exec"), namespace)
    return namespace["format_application_answers"]


def test_candidate_answers_are_rendered_as_readable_sections():
    format_answers = load_formatter()
    rendered = format_answers(
        json.dumps(
            {
                "why_president": "Хочу представлять игроков.",
                "server_plan": "Добавлю понятные правила.",
                "short_program": "Проведу открытые дебаты.",
                "faction": "Картель",
            },
            ensure_ascii=False,
        )
    )

    assert "{\"why_president\"" not in rendered
    assert "**Почему вы хотите стать президентом?**" in rendered
    assert "Хочу представлять игроков." in rendered
    assert "**Какая у вас фракция?**\nКартель" in rendered


def test_empty_optional_faction_is_explicitly_readable():
    format_answers = load_formatter()
    rendered = format_answers({"why_president": "Ответ", "server_plan": "План", "short_program": "Программа", "faction": ""})
    assert "**Какая у вас фракция?**\nНе указана." in rendered
