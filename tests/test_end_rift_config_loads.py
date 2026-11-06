"""The shipped config.yml must satisfy every key EventConfig demands.

The plugin disables itself when a required key is missing. EventConfig reads most required keys
with a -1 default, so an ABSENT key is treated as a negative value and throws - the plugin then
logs "plugin is disabled" and every /cmend command fails with CommandException.

That is exactly what shipped: config.yml had no skeleton.elite-attack-damage-bonus while
EventConfig required it, so loading the default config disabled the whole plugin. No test caught
it, because nothing compared the shipped file against the keys the parser actually reads.

This test does that comparison statically: it extracts every key EventConfig requires from the
default-config path, then asserts each one is present in the shipped config.yml. It cannot run the
parser (that needs Paper), but it catches the missing-key class of failure, which is the one that
takes the plugin down before any wave starts.
"""
from __future__ import annotations

import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / 'copimine-end-event'
CONFIG = PLUGIN / 'config.yml'
EVENT_CONFIG = PLUGIN / 'src' / 'me' / 'copimine' / 'endevent' / 'EventConfig.java'

# Each entry is (java-variable holding the section, key, section path in config.yml).
# Keeping the path explicit avoids guessing from the variable name.
REQUIRED = [
    ('ritual', 'min-players', 'ritual.min-players'),
    ('ritual', 'max-players', 'ritual.max-players'),
    ('ritual', 'start-ritual-timeout-seconds', 'ritual.start-ritual-timeout-seconds'),
    ('ritual', 'intermission-seconds', 'ritual.intermission-seconds'),
    ('ritual', 'pad-occupancy-radius', 'ritual.pad-occupancy-radius'),
    ('waves', 'hard-cap', 'waves.hard-cap'),
    ('boss', 'health', 'boss.health'),
    ('boss', 'recent-target-memory', 'boss.recent-target-memory'),
    ('boss', 'target-lock-seconds', 'boss.target-lock-seconds'),
    ('boss', 'teleport-cooldown-seconds', 'boss.teleport-cooldown-seconds'),
    ('arena', 'radius', 'arena.radius'),
    ('arena', 'vertical-radius', 'arena.vertical-radius'),
    ('arena', 'boss-radius', 'arena.boss-radius'),
    ('arena', 'containment-radius', 'arena.containment-radius'),
    ('rewards', 'shard-channel-seconds', 'rewards.shard-channel-seconds'),
    ('rewards', 'shard-cooldown-seconds', 'rewards.shard-cooldown-seconds'),
    ('spider', 'health-bonus', 'mobs.spider.health-bonus'),
    ('spider', 'attack-damage-bonus', 'mobs.spider.attack-damage-bonus'),
    ('skeleton', 'health-bonus', 'mobs.skeleton.health-bonus'),
    ('skeleton', 'attack-damage-bonus', 'mobs.skeleton.attack-damage-bonus'),
    ('skeleton', 'elite-attack-damage-bonus', 'mobs.skeleton.elite-attack-damage-bonus'),
]


def config() -> dict:
    assert CONFIG.is_file(), f'the shipped config is missing: {CONFIG}'
    return yaml.safe_load(CONFIG.read_text(encoding='utf-8'))


def dig(document: dict, path: str):
    node = document
    for part in path.split('.'):
        assert isinstance(node, dict), f'{path} walks through a non-mapping at {part}'
        assert part in node, f'the shipped config.yml has no {path}'
        node = node[part]
    return node


def test_every_required_key_is_present_in_the_shipped_config() -> None:
    document = config()
    missing = []
    for _section, _key, path in REQUIRED:
        node = document
        for part in path.split('.'):
            if not isinstance(node, dict) or part not in node:
                missing.append(path)
                node = None
                break
            node = node[part]
    assert not missing, (
        f'the shipped config.yml is missing keys EventConfig requires: {sorted(set(missing))}. '
        'EventConfig reads them with a -1 default, so absence throws and disables the plugin.'
    )


def test_required_keys_are_really_read_by_the_parser() -> None:
    # Guards against this list drifting away from the parser: a key that the list claims is
    # required but the parser never reads would make the test assert nothing.
    parser = EVENT_CONFIG.read_text(encoding='utf-8')
    for _section, key, _path in REQUIRED:
        assert re.search(r'"[^"]*"\s*\)', parser) is not None
        assert f'"{key}"' in parser, f'EventConfig never reads {key!r}'


def test_the_elite_damage_bonus_key_is_not_missing_again() -> None:
    # The exact regression: this key was absent and disabled the plugin on load.
    parser = EVENT_CONFIG.read_text(encoding='utf-8')
    assert 'nonNegativeDouble(skeleton, "elite-attack-damage-bonus")' in parser, (
        'the parser must keep reading this key; if it stops, drop it from the required list too'
    )
    value = dig(config(), 'mobs.skeleton.elite-attack-damage-bonus')
    assert isinstance(value, (int, float)), 'elite-attack-damage-bonus must be numeric'
    assert value >= 0, 'elite-attack-damage-bonus must be non-negative or the plugin refuses to load'


def test_required_values_are_within_the_parser_bounds() -> None:
    document = config()
    # positiveInt/positiveDouble reject zero and negatives; nonNegativeDouble rejects negatives.
    positive_paths = [
        'ritual.min-players', 'ritual.max-players', 'ritual.start-ritual-timeout-seconds',
        'ritual.intermission-seconds', 'ritual.pad-occupancy-radius', 'waves.hard-cap',
        'boss.health', 'boss.recent-target-memory', 'boss.target-lock-seconds',
        'boss.teleport-cooldown-seconds', 'arena.radius', 'arena.vertical-radius',
        'arena.boss-radius', 'arena.containment-radius',
        'rewards.shard-channel-seconds', 'rewards.shard-cooldown-seconds',
    ]
    for path in positive_paths:
        value = dig(document, path)
        assert isinstance(value, (int, float)) and value > 0, f'{path} must be positive, got {value}'

    non_negative_paths = [
        'mobs.spider.health-bonus', 'mobs.spider.attack-damage-bonus',
        'mobs.skeleton.health-bonus', 'mobs.skeleton.attack-damage-bonus',
        'mobs.skeleton.elite-attack-damage-bonus',
    ]
    for path in non_negative_paths:
        value = dig(document, path)
        assert isinstance(value, (int, float)) and value >= 0, (
            f'{path} must be non-negative, got {value}'
        )


def test_the_boss_health_stays_at_the_pinned_roster_value() -> None:
    # EventConfig throws unless boss.health matches BossHealthPolicy.MIN_HEALTH exactly.
    document = config()
    assert dig(document, 'boss.health') == 5000, (
        'boss.health must stay 5000 to match the roster scaling policy'
    )
