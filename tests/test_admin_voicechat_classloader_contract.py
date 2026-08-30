from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN_YML = ROOT / "copimine-admin-plugin" / "plugin.yml"


def _dependency_block(text: str, key: str) -> str:
    match = re.search(
        rf"(?ms)^{re.escape(key)}:\s*\n(?P<body>(?:^[ \t]+-.*\n?)+)",
        text,
    )
    return match.group("body") if match else ""


def test_voicechat_api_plugin_is_hard_dependency_for_event_registration() -> None:
    text = PLUGIN_YML.read_text(encoding="utf-8")
    hard_dependencies = _dependency_block(text, "depend")
    soft_dependencies = _dependency_block(text, "softdepend")

    assert re.search(r"(?m)^\s+-\s+voicechat\s*$", hard_dependencies)
    assert not re.search(r"(?m)^\s+-\s+voicechat\s*$", soft_dependencies)
