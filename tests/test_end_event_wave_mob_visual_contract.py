"""Visual contracts for the wave skeleton, elite and ritual caster assets.

These checks are intentionally small and deterministic. Most generated PNGs
are opaque Minecraft UV sheets in the project palette; the three elite sheets
are preserved byte-for-byte from the supplied archive and keep its transparency
and authored colors.
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
    for name in (
        "end_rift_skeleton.png",
        "end_rift_wave_guardian_skeleton.png",
        "end_rift_ritual_guard_skeleton.png",
    ):
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
        "end_rift_spider.png",
        "end_rift_wave_guardian_enderman.png",
        "end_rift_ritual_guard_enderman.png",
        "end_rift_wave_guardian_spider.png",
        "end_rift_ritual_guard_spider.png",
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


def test_elite_mob_atlases_are_the_exact_supplied_archive_files() -> None:
    import hashlib

    expected = {
        "end_rift_elite.png": "5923111b4ac459daee04aeeea4930dcac4b1156ed3d94bbaaa4b89b987fc6bdc",
        "end_rift_elite_skeleton.png": "df29a577e2cc5896507044db37349216c2401311e458576ecbb2e0fe8ce65514",
        "end_rift_elite_spider.png": "40ab699e7dc46d50a26728679539b30ced556269b795b06ed7bf498dd1b4d052",
    }
    for name, expected_hash in expected.items():
        path = ENTITY / name
        assert hashlib.sha256(path.read_bytes()).hexdigest() == expected_hash, name
        with Image.open(path).convert("RGBA") as image:
            assert image.size == (64, 32), name
            assert set(image.getchannel("A").getdata()) <= {0, 255}, name


def test_every_runtime_mob_role_has_a_native_uv_sheet() -> None:
    from CopiMineClient.tools.validate_end_rift_mob_uv import EXPECTED_MOB_ATLASES, validate_atlases

    report = validate_atlases(ENTITY)
    assert set(report) == set(EXPECTED_MOB_ATLASES)
    assert all(not issues for issues in report.values()), report


def test_role_models_have_explicit_variant_parts_and_visual_routing() -> None:
    enderman = (CLIENT_JAVA / "RiftEventEndermanModel.java").read_text(encoding="utf-8")
    spider = (CLIENT_JAVA / "RiftSpiderModel.java").read_text(encoding="utf-8")
    spider_renderer = (CLIENT_JAVA / "RiftSpiderModelRenderer.java").read_text(encoding="utf-8")
    for role in ("WAVE_GUARDIAN", "RITUAL_GUARD"):
        assert role in enderman
        assert role in spider
    for part in ("body_shell", "horn_left", "guardian_mantle", "guard_seal"):
        assert part in enderman, part
    for part in ("elite_carapace", "guardian_spine", "ritual_focus"):
        assert part in spider, part
    assert "modelFor(String visualId)" in spider_renderer


def test_assembled_preview_board_covers_every_runtime_mob_role() -> None:
    preview_tool = CLIENT / "tools" / "render_end_rift_mob_previews.py"
    evidence = ROOT / "artifacts" / "end-rift-v3-evidence"
    board = evidence / "end-rift-mob-model-board-20260916.png"
    previews = evidence / "model-previews"
    assert preview_tool.is_file()
    assert board.is_file()
    assert len(list(previews.glob("*.png"))) == 13
    assert "Static assembled previews" in preview_tool.read_text(encoding="utf-8")


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
        "chest_rift",
        "guard_crest",
        "guard_chest_seal",
        "guardian_spine",
        "elite_mantle",
        "elite_horn_crown",
    ):
        assert part in model, part
    assert "modelFor(boolean elite)" in renderer
    assert "AbstractSkeletonEntity" in mixin
    assert "SkeletonEntityModel" in mixin
    assert "RiftEventSkeletonModelRenderer" in mixin
