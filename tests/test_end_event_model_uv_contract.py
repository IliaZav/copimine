"""Source-level guard that event role models use the checked UV builder."""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine" / "client"
MODELS = (
    CLIENT / "RiftEventEndermanModel.java",
    CLIENT / "RiftEventSkeletonModel.java",
    CLIENT / "RiftSpiderModel.java",
)
UV_HELPER = CLIENT / "ModelUvBounds.java"
SKELETON_SOURCE = ROOT / "CopiMineClient/src/main/asset-source/end-event-mobs/skeleton.png"
SKELETON_ATLAS = (
    ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/end_rift_user_skeleton.png"
)


def _ordinary_skeleton_body_uv_origin() -> tuple[int, int]:
    source = (CLIENT / "RiftEventSkeletonModel.java").read_text(encoding="utf-8")
    match = re.search(
        r'root\.addChild\("body",\s*ModelUvBounds\.cuboid\('
        r'TEXTURE_WIDTH,\s*TEXTURE_HEIGHT,\s*16,\s*(bodyUvV|\d+),',
        source,
    )
    assert match, "ordinary skeleton body must use a declared atlas UV origin"
    v_token = match.group(1)
    if v_token.isdigit():
        return 16, int(v_token)
    conditional = re.search(r"int bodyUvV = userSkin \? (\d+) : (\d+);", source)
    assert conditional, "skeleton body UV must follow the selected atlas layout"
    shared_atlas = re.search(
        r"private static boolean usesSharedSkeletonAtlas\(Variant variant\)\s*\{"
        r"\s*return switch \(variant\) \{(?P<cases>[\s\S]*?)\};\s*\}",
        source,
    )
    assert shared_atlas, "all role atlas selection must remain explicit"
    assert re.search(
        r"case ORDINARY,\s*ELITE,\s*WAVE_GUARDIAN,\s*RITUAL_GUARD -> true;",
        shared_atlas.group("cases"),
    ), "ordinary skeletons must use the supplied shared atlas"
    return 16, int(conditional.group(1))


def _cuboid_face_rectangles(
    u: int, v: int, width: int, height: int, depth: int
) -> dict[str, tuple[int, int, int, int]]:
    """Half-open UV rectangles for the six standard ModelPart cuboid faces."""
    return {
        "top": (u + depth, v, u + depth + width, v + depth),
        "bottom": (u + depth + width, v, u + depth + 2 * width, v + depth),
        "left": (u, v + depth, u + depth, v + depth + height),
        "front": (u + depth, v + depth, u + depth + width, v + depth + height),
        "right": (u + depth + width, v + depth, u + 2 * depth + width, v + depth + height),
        "back": (u + 2 * depth + width, v + depth,
                 u + 2 * depth + 2 * width, v + depth + height),
    }


def _rectangles_overlap(
    first: tuple[int, int, int, int], second: tuple[int, int, int, int]
) -> bool:
    return (
        first[0] < second[2]
        and second[0] < first[2]
        and first[1] < second[3]
        and second[1] < first[3]
    )


def test_standard_box_helper_uses_the_declared_footprint_formula() -> None:
    source = UV_HELPER.read_text(encoding="utf-8")
    assert "maxU = (double) u + 2.0D * width + 2.0D * depth" in source
    assert "maxV = (double) v + depth + height" in source
    assert "Float.isFinite" in source
    assert "atlasWidth" in source and "atlasHeight" in source


def test_every_custom_role_model_routes_cuboids_through_uv_validation() -> None:
    raw_cuboid = re.compile(r"(?<!ModelUvBounds)\.cuboid\(")
    for model in MODELS:
        source = model.read_text(encoding="utf-8")
        assert "ModelUvBounds.cuboid" in source, model.name
        assert "ModelUvBounds.boxes" in source, model.name
        assert not raw_cuboid.search(source), model.name


def test_uv_footprint_test_is_present_in_the_client_project() -> None:
    test = ROOT / "CopiMineClient/src/test/java/me/copimine/client/ModelUvBoundsTest.java"
    source = test.read_text(encoding="utf-8")
    assert "standardBoxRejectsUvFootprintOutsideAtlas" in source
    assert "standardBoxAcceptsFootprintInsideAtlas" in source


def test_repaired_layout_uses_dedicated_lower_islands_for_moved_parts() -> None:
    enderman = (CLIENT / "RiftEventEndermanModel.java").read_text(encoding="utf-8")
    skeleton = (CLIENT / "RiftEventSkeletonModel.java").read_text(encoding="utf-8")
    generator = (ROOT / "CopiMineClient/tools/generate_end_rift_texture_atlases.py").read_text(encoding="utf-8")
    assert "50, 30, -3.0F, -0.4F, -4.15F" in enderman
    assert "addBipedSkeletonArm(root," in skeleton
    assert "addBipedSkeletonLeg(root," in skeleton
    assert "int uv = userSkin ? 40 : mirrored ? 48 : 32;" in skeleton
    assert "int upperV = 16;" in skeleton
    assert "int lowerV = upperV + 6;" in skeleton
    assert "jaw_patch" in generator
    assert "elite_shoulder_patch" in generator
    assert "shared spider shell island" in generator


def test_ordinary_skeleton_torso_uv_does_not_alias_the_inherited_skull_faces() -> None:
    """The ordinary torso art must not repaint the back/side UV faces of its skull."""
    u, v = _ordinary_skeleton_body_uv_origin()
    head_bounds = (0, 0, 32, 16)  # inherited 8x8x8 BipedEntityModel head at uv(0, 0)
    torso_bounds = (u, v, u + 2 * 8 + 2 * 4, v + 4 + 12)
    assert not _rectangles_overlap(head_bounds, torso_bounds), (
        "ordinary skeleton torso UV rectangle aliases the inherited skull: "
        f"head={head_bounds}, torso={torso_bounds}"
    )


def test_ordinary_skeleton_body_uv_faces_match_the_supplied_archive_layout() -> None:
    u, v = _ordinary_skeleton_body_uv_origin()
    expected_faces = {
        "top": (20, 16, 28, 20),
        "bottom": (28, 16, 36, 20),
        "left": (16, 20, 20, 32),
        "front": (20, 20, 28, 32),
        "right": (28, 20, 32, 32),
        "back": (32, 20, 40, 32),
    }
    expected_opaque_pixels = {
        "top": 0,
        "bottom": 32,
        "left": 20,
        "front": 58,
        "right": 20,
        "back": 54,
    }
    face_rectangles = _cuboid_face_rectangles(u, v, 8, 12, 4)
    assert face_rectangles == expected_faces
    with Image.open(SKELETON_SOURCE).convert("RGBA") as source:
        assert source.size == (64, 32)
        for face, (left, top, right, bottom) in face_rectangles.items():
            opaque_pixels = sum(
                source.getpixel((x, y))[3] > 0
                for y in range(top, bottom)
                for x in range(left, right)
            )
            assert opaque_pixels == expected_opaque_pixels[face], (
                f"ordinary skeleton archive {face} face has unexpected UV coverage: "
                f"uv=({left},{top})..({right},{bottom}), opaque={opaque_pixels}"
            )


def test_ordinary_skeleton_atlas_is_an_unchanged_copy_of_the_supplied_archive_png() -> None:
    import hashlib

    supplied = SKELETON_SOURCE.read_bytes()
    runtime_copy = SKELETON_ATLAS.read_bytes()
    expected_sha256 = "4744a2c76285b1fa06f6fa64bff5573bea88d63ae7050912b138b830b573ee80"
    assert hashlib.sha256(supplied).hexdigest() == expected_sha256
    assert runtime_copy == supplied
