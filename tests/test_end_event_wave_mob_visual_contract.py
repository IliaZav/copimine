"""Visual contracts for the wave skeleton, elite and ritual caster assets.

These checks are intentionally small and deterministic.  The PNGs are Minecraft
UV sheets, not presentation renders, so a valid sheet must be opaque, compact,
and use the same dark-purple family with deliberate bone accents as the
supplied wave-mob references.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient"
ENTITY = (
    CLIENT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "entity"
)
CLIENT_JAVA = CLIENT / "src" / "main" / "java" / "me" / "copimine" / "client"


def _texture_colors(path: Path) -> tuple[set[int], set[tuple[int, int, int]]]:
    with Image.open(path).convert("RGBA") as image:
        return (
            {pixel[3] for pixel in image.getdata()},
            {pixel[:3] for pixel in image.getdata()},
        )


def test_wave_skeleton_atlases_are_opaque_and_reference_purple() -> None:
    for name in ("end_rift_skeleton.png", "end_rift_elite_skeleton.png"):
        path = ENTITY / name
        assert path.is_file(), name
        with Image.open(path).convert("RGBA") as image:
            assert image.size == (64, 32), name
        alpha, colors = _texture_colors(path)
        assert alpha == {255}, f"{name} contains transparent or semitransparent UV pixels"
        assert len(colors) <= 16, f"{name} has pixel-noise palette: {len(colors)} colours"
        assert all(
            (green <= 90 and blue >= green and blue >= red)
            or (red >= 90 and green >= 80 and blue >= 90)
            for red, green, blue in colors
        ), f"{name} contains an uncontrolled surface colour"
        assert sum(1 for red, green, blue in colors
                   if red >= 180 and green >= 170 and blue >= 180) <= 3


def test_other_wave_mob_atlases_keep_the_same_clean_surface_contract() -> None:
    for name in (
        "end_rift_enderman.png",
        "end_rift_elite.png",
        "end_rift_spider.png",
    ):
        path = ENTITY / name
        assert path.is_file(), name
        with Image.open(path).convert("RGBA") as image:
            assert image.size == (64, 32), name
        alpha, colors = _texture_colors(path)
        assert alpha == {255}, f"{name} contains UV gaps"
        assert len(colors) <= 14, f"{name} has pixel-noise palette: {len(colors)} colours"
        assert all(
            green <= 80 and blue >= green
            for red, green, blue in colors
        ), f"{name} leaks a non-purple surface colour"


def test_ritual_caster_atlas_is_opaque_and_has_a_controlled_channel_accent() -> None:
    path = ENTITY / "end_rift_ritual_caster.png"
    assert path.is_file(), path
    with Image.open(path).convert("RGBA") as image:
        assert image.size == (64, 32)
    alpha, colors = _texture_colors(path)
    assert alpha == {255}
    assert len(colors) <= 12
    assert any(red >= 180 and blue >= 200 for red, _, blue in colors)
    assert all(
        (green <= 100 and blue >= green)
        or (red >= 180 and green >= 170 and blue >= 180)
        for red, green, blue in colors
    )


def test_wave_skeletons_use_a_dedicated_long_rig_and_scoped_renderer_swap() -> None:
    model_path = CLIENT_JAVA / "RiftEventSkeletonModel.java"
    renderer_path = CLIENT_JAVA / "RiftEventSkeletonModelRenderer.java"
    mixin_path = CLIENT_JAVA / "mixin" / "LivingEntityRendererMixin.java"
    assert model_path.is_file(), "wave skeletons need a dedicated model class"
    assert renderer_path.is_file(), "wave skeletons need a model owner"

    model = model_path.read_text(encoding="utf-8")
    renderer = renderer_path.read_text(encoding="utf-8")
    mixin = mixin_path.read_text(encoding="utf-8")

    assert "extends SkeletonEntityModel" in model
    assert "getTexturedModelData(boolean elite)" in model
    for part in (
        "left_forearm",
        "right_forearm",
        "left_lower_leg",
        "right_lower_leg",
        "elite_shoulder_left",
        "elite_shoulder_right",
        "jaw",
        "chest_rift",
        "left_knee_bone",
        "left_ankle_bone",
    ):
        assert part in model, part
    assert "modelFor(boolean elite)" in renderer
    assert "AbstractSkeletonEntity" in mixin
    assert "SkeletonEntityModel" in mixin
    assert "RiftEventSkeletonModelRenderer" in mixin
