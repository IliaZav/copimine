"""Additional completion gates for the local End Rift event.

The focused event contracts cover individual mechanics.  These checks keep
the final local bundle honest as a whole: the source plugin roster is the
authoritative input, the disposable driver covers every stage, and the
client-facing visual set is complete without replacing vanilla textures.
"""

from __future__ import annotations

import ast
import json
import re
import zipfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(
    encoding="utf-8"
)
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
PLUGIN_VERSIONS = json.loads((ROOT / "deploy/plugin_versions.json").read_text(encoding="utf-8"))
PLUGIN_DIR = ROOT / "minecraft/server/plugins"
PACK_SOURCE = ROOT / "resourcepacks/src"
PACK_ZIP = ROOT / "resourcepacks/build/CopiMineResourcePack.zip"
BUG_LOG = ROOT / "docs/superpowers/evidence/2026-08-26-end-rift-bug-log.md"


def _block(text: str, marker: str, next_marker: str) -> str:
    start = text.index(marker)
    end = text.index(next_marker, start)
    return text[start:end]


def test_authoritative_project_plugin_roster_is_complete_and_has_no_legacy_login_plugin() -> None:
    expected = sorted(PLUGIN_VERSIONS["plugins"])
    actual = sorted(path.name for path in PLUGIN_DIR.glob("*.jar"))

    assert len(expected) == 30
    assert actual == expected
    assert not any(path.name.lower() == "nlogin.jar" for path in PLUGIN_DIR.glob("*.jar"))


def test_creative_driver_covers_every_disposable_stage_in_order() -> None:
    markers = (
        "CREATIVE_TEST_START",
        "CREATIVE_TEST_CORE",
        "CREATIVE_TEST_RESOURCES",
        "CREATIVE_TEST_RUNES",
        "CREATIVE_TEST_WAVE_1",
        "CREATIVE_TEST_INTERMISSION_1",
        "CREATIVE_TEST_WAVE_2",
        "CREATIVE_TEST_INTERMISSION_2",
        "CREATIVE_TEST_WAVE_3",
        "CREATIVE_TEST_BOSS_ACTIVE",
        "CREATIVE_TEST_BOSS_SPELL",
        "CREATIVE_TEST_HALF",
        "CREATIVE_TEST_CONTROL",
        "CREATIVE_TEST_FINAL_DRAIN",
        "CREATIVE_TEST_FINAL_WAVE",
        "CREATIVE_TEST_BOSS_FINISH",
        "CREATIVE_TEST_CLEANUP",
        "CREATIVE_TEST_COMPLETE",
    )
    positions = [MAIN.index(marker) for marker in markers]
    assert positions == sorted(positions)
    assert "official_phase_unchanged=true" in MAIN
    assert '" official_roster=" + officialRewardRoster.size()' in MAIN


def test_configured_waves_use_spiders_and_keep_the_requested_combat_numbers() -> None:
    assert "endermite" not in CONFIG.lower()
    assert "health-bonus: 10.0" in CONFIG
    assert "attack-damage-bonus: 2.0" in CONFIG
    assert "health: 2500.0" in CONFIG
    assert "attack-damage-bonus: 8.0" in CONFIG
    assert "half-health: 1250.0" in CONFIG
    assert "final-threshold: 250.0" in CONFIG
    assert "wave-reward-shared-rare:" in CONFIG

    wave_blocks = [
        _block(CONFIG, "  wave-1:\n", "  wave-2:\n"),
        _block(CONFIG, "  wave-2:\n", "  wave-3:\n"),
        _block(CONFIG, "  wave-3:\n", "  wave-4:\n"),
        _block(CONFIG, "  wave-4:\n", "  wave-5:\n"),
        _block(CONFIG, "  wave-5:\n", "  final:\n"),
        _block(CONFIG, "  final:\n", "\n# Event-owned mobs"),
    ]
    assert all(re.search(r"(?m)^    spiders:\s*\d+\s*$", block) for block in wave_blocks)
    assert all("endermite" not in block.lower() for block in wave_blocks)


def test_event_announces_every_wave_on_screen_and_drops_rewards_at_the_core() -> None:
    for marker in (
        "WAVE_4", "WAVE_5", "INTERMISSION_3", "INTERMISSION_4",
        "sendTitle", "spawnWaveCompletionLoot", "wave-rewards",
        "coreCombatAnchorLocation", "ItemStack",
    ):
        assert marker in MAIN or marker in CONFIG


def test_final_boss_entry_has_a_delayed_epic_announcement() -> None:
    assert "BOSS_SPAWN_DELAY" in MAIN
    assert "хранитель разлома пробуждается" in MAIN.lower()
    assert "sendTitle" in MAIN


def test_every_flying_spell_has_a_named_particle_pattern_and_bounded_flight() -> None:
    flight = MAIN[MAIN.index("private void launchSpellFlight"):MAIN.index("private void spawnSpellFlightPattern")]
    pattern = MAIN[MAIN.index("private void spawnSpellFlightPattern"):MAIN.index("private void spawnRiftProjectileTrail")]

    for spell_id in (
        "void_blast",
        "rift_projectile",
        "void_mark",
        "will_distortion",
        "rift_step",
        "void_snare",
        "echo_pulse",
    ):
        assert f'case "{spell_id}"' in pattern
    assert 'case "summon", "summon_servants"' in pattern
    assert "SPELL_FLIGHT_TICKS" in flight
    assert "BOSS_SPELL_FLIGHT" in MAIN
    assert "MINIBOSS_SPELL_FLIGHT" in MAIN
    assert "taskRegistry.owns(callbackGeneration)" in flight


def test_resource_reset_rebuilds_the_core_visual_after_clearing_charge() -> None:
    reset = MAIN.split('if ("reset".equalsIgnoreCase(args[1]))', 1)[1].split(
        'if ("add".equalsIgnoreCase(args[1])', 1
    )[0]
    assert "coreCharged = false;" in reset
    assert "rebuildPersistedVisuals();" in reset


def test_visual_regression_builder_calls_preserve_the_tracked_production_properties() -> None:
    visual_tests_path = ROOT / "tests/test_end_event_visual_regressions.py"
    visual_tests = visual_tests_path.read_text(encoding="utf-8")
    tree = ast.parse(visual_tests, filename=str(visual_tests_path))
    builder_calls = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        if not isinstance(node.func, ast.Attribute) or node.func.attr != "run":
            continue
        source = ast.get_source_segment(visual_tests, node) or ""
        if "str(BUILDER)" in source:
            builder_calls.append(source)

    assert builder_calls
    assert all("--skip-server-properties" in call for call in builder_calls)


def test_full_event_checks_builder_preserves_the_tracked_production_properties() -> None:
    checks = (ROOT / "tests/RunEndRiftEventChecks.ps1").read_text(encoding="utf-8")
    start = checks.index("Invoke-EndRiftStep 'Resourcepack build'")
    end = checks.index("Invoke-EndRiftStep 'Python event contracts'", start)
    resourcepack_step = checks[start:end]
    assert "build-resourcepack.ps1" in resourcepack_step
    assert "-SkipServerProperties" in resourcepack_step
    wrapper = (ROOT / "resourcepacks/build-resourcepack.ps1").read_text(encoding="utf-8")
    assert "--skip-server-properties" in wrapper


def test_pack_contains_the_complete_event_visual_set_and_no_vanilla_texture_override() -> None:
    block_textures = (
        "end_event_core.png",
        "end_event_core_charged.png",
        "end_event_rune.png",
        "end_event_rune_occupied.png",
    )
    item_textures = (
        "end_event_core.png",
        "end_event_core_charged.png",
        "end_event_pad.png",
        "end_event_pad_occupied.png",
        "rift_core_shard.png",
    )
    entity_textures = (
        "end_rift_enderman.png",
        "end_rift_elite.png",
        "end_rift_spider.png",
        "end_rift_shulker.png",
        "end_rift_guardian.png",
    )

    for name in block_textures:
        path = PACK_SOURCE / "assets/copimine/textures/block" / name
        assert path.is_file()
        with Image.open(path) as image:
            assert image.size == (32, 32)
    for name in item_textures:
        assert (PACK_SOURCE / "assets/copimine/textures/item" / name).is_file()
    for name in entity_textures:
        assert (ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity" / name).is_file()

    assert PACK_ZIP.is_file()
    with zipfile.ZipFile(PACK_ZIP) as archive:
        names = set(archive.namelist())
    assert "assets/copimine/models/item/end_event_core.json" in names
    assert "assets/copimine/models/item/end_event_pad.json" in names
    assert "assets/copimine/models/item/end_event_pad_occupied.json" in names
    vanilla_texture_paths = {
        name for name in names
        if name.startswith("assets/minecraft/textures/")
        and not name.startswith("assets/minecraft/textures/gui/")
    }
    assert not vanilla_texture_paths


def test_completion_bug_log_is_traceable_to_current_runtime_and_regression_tests() -> None:
    log = BUG_LOG.read_text(encoding="utf-8")
    for marker in (
        "E-001",
        "E-002",
        "E-003",
        "E-004",
        "E-005",
        "E-006",
        "E-007",
        "E-008",
        "E-009",
        "E-010",
        "E-011",
        "E-012",
        "E-013",
        "E-014",
        "E-015",
        "E-016",
        "E-017",
        "E-018",
        "test_end_event_creative_run_contract.py",
        "RunEndRiftLocalSceneSmoke.ps1",
        "plugin_versions.json",
    ):
        assert marker in log
