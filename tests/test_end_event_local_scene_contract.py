from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SETUP = (ROOT / "tests/SetupEndRiftLocalScene.ps1").read_text(encoding="utf-8")
PACK = ROOT / "tests/assets/copimine-local-spawn"
PLUGIN = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")


def test_one_shot_scene_setup_is_strictly_local_and_not_a_new_game_command() -> None:
    for marker in (
        "local-runtime",
        "environment:\\s*local",
        "server-port=25566",
        "rcon.port=25576",
        "Remove-Item -LiteralPath $datapackTarget",
        "arenaMinX = -12",
        "arenaMaxX = 28",
        "gateX = 29",
        "gateMinY = 68",
        "gateMaxY = 71",
        "gateMinZ = -40",
        "gateMaxZ = -38",
        "minecraft:obsidian",
        "minecraft:end_portal",
        "minecraft:stone",
    ):
        assert marker in SETUP
    assert "test layout" not in PLUGIN
    assert "test spawnstation" not in PLUGIN
    assert "test gate" not in PLUGIN


def test_spawn_station_uses_two_buttons_two_functions_and_never_changes_vanilla_textures() -> None:
    for marker in (
        "copimine-local-spawn",
        "function copimine:spawn/max_on",
        "function copimine:spawn/max_clear",
        "minecraft:stone_button[face=wall,facing=south,powered=false]",
        "minecraft:oak_sign[rotation=0]",
        "$spawnButtonZ = $spawnStationZ - 1",
        "setblock $spawnStationX1 $spawnStationY $spawnButtonZ minecraft:stone_button[face=wall,facing=south,powered=false]",
        "data merge block $spawnStationX1 $spawnStationY $spawnStationZ {Command:",
        "arena's z=-19 boundary",
        "cmend gate info",
        "cmend portalroom info",
    ):
        assert marker in SETUP
    assert "LocalEndRiftBot.js" not in SETUP
    assert "Start-Process" not in SETUP
    assert (PACK / "pack.mcmeta").is_file()
    on = (PACK / "data/copimine/function/spawn/max_on.mcfunction").read_text(encoding="utf-8")
    clear = (PACK / "data/copimine/function/spawn/max_clear.mcfunction").read_text(encoding="utf-8")
    for marker in ("health_boost", "resistance", "instant_health"):
        assert marker in on
    for marker in ("effect clear @s minecraft:health_boost", "effect clear @s minecraft:resistance"):
        assert marker in clear
    assert "assets/minecraft" not in SETUP


def test_room_has_no_roof_and_gate_is_exactly_twelve_obsidian_blocks() -> None:
    for marker in (
        "3-wide by 4-high",
        "roomTopY = 70",
        "gateX $gateMinY $gateMinZ $gateX $gateMaxY $gateMaxZ",
        "portal plane 33..35,68,-40..-38",
        "cmend gate info",
        "cmend portalroom info",
    ):
        assert marker in SETUP
