"""Source contracts for Wave 6 ritual-prisoner health authority.

There is no Bukkit harness in this focused gate.  The parametrized cases
document the concrete damage paths that must enter the generic
``EntityDamageEvent`` listener, while the source assertions verify that the
listener cancels them without changing player health between ritual drains.
"""

from __future__ import annotations

from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
POLICY = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "domain"
    / "RitualPrisonerHealthPolicy.java"
)
SOURCE = (
    ROOT
    / "copimine-end-event"
    / "src"
    / "me"
    / "copimine"
    / "endevent"
    / "CopiMineEndEvent.java"
)

REQUIRED_DAMAGE_PATHS = (
    "melee",
    "projectile",
    "fall",
    "zone",
    "explosion",
    "fire",
    "poison",
    "wither",
    "generic entity damage",
)


def handler_body(source: str) -> str:
    start = source.index("public void onRitualPrisonerDamage")
    end = source.index("/** Guard-backed caster shields", start)
    return source[start:end]


def test_safe_external_damage_policy_is_constant_zero() -> None:
    source = POLICY.read_text(encoding="utf-8")
    start = source.index("public static double safeExternalDamage")
    end = source.index("public record DrainResult", start)
    body = source[start:end]

    assert body.count("return 0.0D;") == 1
    assert "Math.min" not in body
    assert "safeHealth - MIN_HEALTH" not in body


def test_required_damage_paths_use_generic_cancel_only_handler() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    body = handler_body(source)

    assert "onRitualPrisonerDamage(EntityDamageEvent event)" in body
    assert body.count("event.setCancelled(true);") == 1
    assert "event.getCause()" not in body
    assert "safeExternalDamage" not in body
    for forbidden in (
        "setHealth(",
        "setDamage(",
        "setFinalDamage(",
        "damage(",
        "getHealth() -",
    ):
        assert forbidden not in body


@pytest.mark.parametrize("damage_path", REQUIRED_DAMAGE_PATHS)
def test_each_required_damage_path_is_covered_by_generic_listener_contract(
    damage_path: str,
) -> None:
    source = SOURCE.read_text(encoding="utf-8")
    body = handler_body(source)

    assert "EntityDamageEvent event" in body, damage_path
    assert "event.setCancelled(true);" in body, damage_path
    assert "setHealth(" not in body, damage_path
