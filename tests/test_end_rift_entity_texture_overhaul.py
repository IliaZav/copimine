"""Owner's 2026-11-21 art brief: re-authored End Rift skins and last stand.

The skins are generated deterministically by
``scripts/minecraft/GenerateEndRiftEntityTextures.py``.  These tests pin the
things the owner actually asked for:

* every re-authored sheet is byte-identical to a fresh paint (the asset is the
  generator, not a hand-edited binary);
* only the sanctioned actors are repainted - the Archmage's servant and Echo
  keep their sheets;
* the Guardian's stages are gradual and the last stage is drained and torn;
* the client resolves each boss phase to its own stage sheet and the Ram to its
  authored hide;
* the last-stand intro clip is wired into both animation catalogues.
"""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity"
ANIM = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/models/entity/end_rift_guardian/animations"


def _load_generator():
    path = ROOT / "scripts/minecraft/GenerateEndRiftEntityTextures.py"
    spec = importlib.util.spec_from_file_location("end_rift_entity_textures", path)
    module = importlib.util.module_from_spec(spec)
    # dataclasses resolve their own module through sys.modules.
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
    finally:
        sys.modules.pop(spec.name, None)
    return module


def _mean_chroma(image: Image.Image) -> float:
    """Average (max-min) channel spread: the honest 'colourfulness' measure."""
    total = 0.0
    opaque = 0
    for r, g, b, a in image.convert("RGBA").getdata():
        if a <= 8:
            continue
        total += max(r, g, b) - min(r, g, b)
        opaque += 1
    return total / max(1, opaque)


def _mean_luminance(image: Image.Image) -> float:
    total = 0.0
    opaque = 0
    for r, g, b, a in image.convert("RGBA").getdata():
        if a <= 8:
            continue
        total += 0.299 * r + 0.587 * g + 0.114 * b
        opaque += 1
    return total / max(1, opaque)


def _dark_ratio(image: Image.Image) -> float:
    dark = 0
    total = 0
    for r, g, b, a in image.convert("RGBA").getdata():
        if a <= 8:
            continue
        total += 1
        if 0.299 * r + 0.587 * g + 0.114 * b < 44:
            dark += 1
    return dark / max(1, total)


def test_generated_sheets_are_reproducible():
    generator = _load_generator()
    for name, image in generator.all_outputs().items():
        committed = ENTITY / f"{name}.png"
        assert committed.is_file(), name
        assert committed.read_bytes() == generator.encode(image), name


def test_only_sanctioned_actors_are_repainted():
    generator = _load_generator()
    produced = {spec.name for spec in generator.SPECS}
    assert produced == {
        "end_rift_ram",
        "end_rift_endermite",
        "end_rift_spire_harbinger",
        "end_rift_wave7_warden",
        "end_rift_wave7_archmage",
        "end_rift_wave7_marksman",
    }
    # The Archmage's servant is explicitly "good" and must not be touched.
    assert "end_rift_wave7_vex_servant" not in produced
    servant = ENTITY / "end_rift_wave7_vex_servant.png"
    assert servant.is_file()
    assert Image.open(servant).size == (64, 64)


def test_sheets_keep_their_alpha_and_dimensions():
    generator = _load_generator()
    for name in (spec.name for spec in generator.SPECS):
        image = generator.all_outputs()[name]
        source = Image.open(generator.SOURCES[name]).convert("RGBA")
        assert image.size == source.size, name
        assert list(image.getchannel("A").getdata()) == list(
            source.getchannel("A").getdata()), name


def test_guardian_stages_get_gradually_drained_and_torn():
    stages = [Image.open(ENTITY / f"end_rift_user_boss_stage{i}.png") for i in range(6)]
    for stage in stages:
        assert stage.size == (128, 128)
    chroma = [_mean_chroma(stage) for stage in stages]
    luminance = [_mean_luminance(stage) for stage in stages]
    dark = [_dark_ratio(stage) for stage in stages]
    # Gradual: every step drains a little, and no step snaps.
    for earlier, later in zip(chroma, chroma[1:]):
        assert later <= earlier + 0.1, (earlier, later)
        assert earlier - later < 8.0, (earlier, later)
    for earlier, later in zip(luminance, luminance[1:]):
        assert later <= earlier + 0.02, (earlier, later)
        assert earlier - later < 3.0, (earlier, later)
    # Last breath: the final stage is visibly drained, darker and torn.
    assert chroma[-1] < chroma[0] * 0.4
    assert luminance[-1] < luminance[0] * 0.85
    assert dark[-1] > dark[0]
    assert stages[-1].tobytes() != stages[4].tobytes()


def test_client_resolves_phases_to_their_own_stage_sheets():
    server = (ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossAnimationId.java").read_text("utf-8")
    assert 'LAST_STAND_INTRO("LAST_STAND_INTRO", "LAST_STAND")' in server
    for tree in ("tools/minecraft-26.3/client", "CopiMineClient"):
        renderer = (ROOT / tree / "src/main/java/me/copimine/client/RiftGuardianModelRenderer.java").read_text("utf-8")
        for stage in range(6):
            assert f"end_rift_user_boss_stage{stage}.png" in renderer
        assert "stageFor" in renderer
        catalog = (ROOT / tree / "src/main/java/me/copimine/client/EndEventTextureCatalog.java").read_text("utf-8")
        if "RiftRamVisualPolicy" in catalog:
            assert 'entityTexture("end_rift_ram.png")' in catalog
            assert "minecraft\", \"textures/entity/illager/ravager.png" not in catalog
        animator = (ROOT / tree / "src/main/java/me/copimine/client/UserEndBossAnimationPlayer.java").read_text("utf-8")
        assert '"last_stand.animation.json", "LAST_STAND_INTRO"' in animator


def test_last_stand_clip_is_authored():
    clip = json.loads((ANIM / "last_stand.animation.json").read_text("utf-8"))
    animation = clip["animations"]["animation.guardian.last_stand"]
    assert animation["loop"] is False
    assert 5.0 <= animation["animation_length"] <= 6.0
    head = animation["bones"]["head"]["rotation"]
    # Head-down hold, then a snap up and a roar, then back to neutral.
    assert head["0.45"]["vector"][0] >= 30
    assert head["2.9"]["vector"][0] >= 30
    assert head["3.25"]["vector"][0] <= -20
    assert head["3.7"]["vector"][0] <= -25
    assert head["5.2"]["vector"] == [0, 0, 0]
    # The body follows: slumped, then thrown back on the roar.
    body = animation["bones"]["body"]["rotation"]
    assert body["1.6"]["vector"][0] >= 5
    assert body["3.7"]["vector"][0] <= -8