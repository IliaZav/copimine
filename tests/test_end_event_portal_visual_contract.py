from __future__ import annotations

import json
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepacks"
SRC = PACK / "src"
SERVER = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
BUILDER = PACK / "build-resourcepack.py"


def _json(relative: str) -> dict:
    return json.loads((SRC / relative).read_text(encoding="utf-8"))


def test_portal_frame_is_volumetric_and_not_a_flat_icon() -> None:
    model = _json("assets/copimine/models/block/end_event_portal.json")
    elements = model.get("elements", [])
    assert len(elements) >= 4
    volumes = []
    for element in elements:
        start = element["from"]
        end = element["to"]
        sizes = [float(end[index]) - float(start[index]) for index in range(3)]
        assert all(size > 0.0 for size in sizes)
        volumes.append(sizes)
    assert any(min(size) >= 2.0 for size in volumes), "portal needs visible frame thickness"
    assert any(
        float(element["from"][2]) > 0.0 and float(element["to"][2]) < 16.0
        for element in elements
    ), "portal must contain a recessed inner volume"


def test_inner_and_shard_models_exist_and_are_packed() -> None:
    expected = (
        "assets/copimine/models/block/end_event_portal_inner.json",
        "assets/copimine/models/block/end_event_portal_shard.json",
        "assets/copimine/models/item/end_event_portal_inner.json",
        "assets/copimine/models/item/end_event_portal_shard.json",
    )
    for relative in expected:
        assert (SRC / relative).is_file(), relative
        assert relative in BUILDER.read_text(encoding="utf-8"), (
            f"builder must validate and pack {relative}"
        )
    archive = PACK / "build/CopiMineResourcePack.zip"
    assert archive.is_file(), "build the resource pack before archive verification"
    with ZipFile(archive) as zipped:
        names = set(zipped.namelist())
    for relative in expected:
        assert relative in names, relative


def test_portal_models_use_existing_portal_texture_and_stay_in_item_bounds() -> None:
    for relative in (
        "assets/copimine/models/block/end_event_portal.json",
        "assets/copimine/models/block/end_event_portal_inner.json",
        "assets/copimine/models/block/end_event_portal_shard.json",
    ):
        model = _json(relative)
        textures = model.get("textures", {})
        if relative.endswith("end_event_portal.json"):
            assert textures.get("rift") == "copimine:item/end_event_portal"
        assert all(
            0.0 <= float(value) <= 16.0
            for element in model.get("elements", [])
            for side in ("from", "to")
            for value in element[side]
        )
    assert _json("assets/copimine/models/item/end_event_portal_inner.json")["parent"] == (
        "copimine:block/end_event_portal_inner"
    )
    assert _json("assets/copimine/models/item/end_event_portal_shard.json")["parent"] == (
        "copimine:block/end_event_portal_shard"
    )


def test_server_creates_centered_layered_portals_and_one_shared_animation_path() -> None:
    source = SERVER.read_text(encoding="utf-8")
    assert "MODEL_PORTAL_INNER_OVERLAY" in source
    assert "MODEL_PORTAL_SHARD_OVERLAY" in source
    assert "end_event_portal_inner" in source
    assert "end_event_portal_shard" in source
    assert ("portal.x + 0.5D" in source
            or "portal.getX() + 0.5D" in source
            or "Math.floor(portal.getX()) + 0.5D" in source)
    assert ("portal.z + 0.5D" in source
            or "portal.getZ() + 0.5D" in source
            or "Math.floor(portal.getZ()) + 0.5D" in source)
    assert "animatePortalModelVisuals(long now)" in source
    assert "runTaskTimer(this" in source
    assert "5L" in source
    assert "wavePortalModelVisuals" in source
    assert "perPortal" not in source


def test_disposable_wave_three_probe_renders_the_real_portal_objective() -> None:
    source = SERVER.read_text(encoding="utf-8")
    spawn_wave = source[source.index("private void spawnWave("):]
    test_branch = spawn_wave[:spawn_wave.index("boolean pacedTowerWave")]
    assert "else if (test && wave == 1)" in test_branch
    assert "startWaveFrontAnimation(world, core, wave, finalWave)" in test_branch
    assert "else if (test && wave == 3)" in test_branch
    assert "startWaveObjective(wave, world, core)" in test_branch
    assert "spawnPortalObjectiveVisuals(world, portals)" in source


def test_zone_renderer_is_floor_anchored_and_policy_driven() -> None:
    source = SERVER.read_text(encoding="utf-8")
    assert (
        "private void renderZoneVisual(Player viewer, Location floor, "
        "ZoneVisualPolicy.ZoneState state, double radius, long now)"
    ) in source
    assert "ZoneVisualPolicy.profile(state)" in source
    assert "floorY()" in source
    assert "0.04D" in source
    assert "0.12D" in source
    assert "four" not in source.lower() or "corner" in source.lower()
    assert "viewer.spawnParticle" in source
    assert "zone.clone().add(0.0D, 2.0D, 0.0D)" not in source


def test_no_vanilla_block_texture_override_was_added() -> None:
    vanilla_block_textures = SRC / "assets/minecraft/textures/block"
    assert not vanilla_block_textures.exists() or not any(vanilla_block_textures.rglob("*"))
    for forbidden in (
        SRC / "assets/minecraft/blockstates/crying_obsidian.json",
        SRC / "assets/minecraft/blockstates/respawn_anchor.json",
        SRC / "assets/minecraft/models/item/crying_obsidian.json",
        SRC / "assets/minecraft/models/item/respawn_anchor.json",
    ):
        assert not forbidden.exists(), forbidden
