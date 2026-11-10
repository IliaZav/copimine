"""The server must support the current scaled Guardian's entire real-HP range.

Paper/Spigot clamp an attribute base value to the configured maximum in
`spigot.yml` (`attribute.maxHealth.max`).  With the historical 1024/2048 cap the
boss creation path fails with BOSS_CURRENT_HEALTH_UNSUPPORTED and removes the
entity, so this is a release-blocking platform setting, not a cosmetic one.
"""

from __future__ import annotations

import pathlib
import re

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
SPIGOT = REPO_ROOT / "minecraft" / "server" / "spigot.yml"
BOSS_HEALTH_POLICY = (
    REPO_ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "BossScalingPolicy.java"
)

MAX_HEALTH_PATTERN = re.compile(
    r"^  attribute:\s*$.*?^    maxHealth:\s*$\n^      max:\s*(?P<max>[0-9.]+)\s*$",
    re.MULTILINE | re.DOTALL,
)
POLICY_MAX_PATTERN = re.compile(r"HP_CAP\s*=\s*([0-9_.]+)D")


def _configured_max_health() -> float:
    text = SPIGOT.read_text(encoding="utf-8")
    match = MAX_HEALTH_PATTERN.search(text)
    assert match, "minecraft/server/spigot.yml must keep an attribute.maxHealth.max entry"
    return float(match.group("max"))


def test_server_attribute_cap_allows_the_declared_boss_health() -> None:
    match = POLICY_MAX_PATTERN.search(BOSS_HEALTH_POLICY.read_text(encoding="utf-8"))
    assert match, "BossScalingPolicy.HP_CAP must stay parseable"
    declared = float(match.group(1).replace("_", ""))
    configured = _configured_max_health()
    assert configured >= declared, (
        "spigot.yml attribute.maxHealth.max must allow BossScalingPolicy.HP_CAP; "
        f"configured={configured} declared={declared}"
    )


def test_server_attribute_cap_allows_the_minimum_boss_health() -> None:
    text = BOSS_HEALTH_POLICY.read_text(encoding="utf-8")
    match = re.search(r"HP_FLOOR\s*=\s*([0-9_.]+)D", text)
    assert match, "BossScalingPolicy.HP_FLOOR must stay parseable"
    assert _configured_max_health() >= float(match.group(1).replace("_", ""))
