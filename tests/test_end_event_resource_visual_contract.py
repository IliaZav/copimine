import hashlib
import json
import math
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "resourcepacks" / "src" / "assets"
COPIMINE = ASSET_ROOT / "copimine"
PACK_MANIFEST = ROOT / "resourcepacks" / "models_manifest.json"
BOSS_TEXTURE = (
    ROOT
    / "CopiMineClient"
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "copimineclient"
    / "textures"
    / "entity"
    / "end_rift_user_boss.png"
)
CLIENT_ENTITY_TEXTURES = (
    ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets"
    / "copimineclient" / "textures" / "entity"
)
SUPPLIED_SKINS = ROOT / "CopiMineClient" / "src" / "main" / "asset-source" / "end-event-mobs"
KAGUNE_SOURCE_MODEL = (
    ROOT / "CopiMineClient" / "src" / "main" / "asset-source"
    / "end-rift-tentacle" / "kagune.bbmodel"
)
SUPPLIED_SKELETON = (
    ROOT / "CopiMineClient" / "src" / "main" / "asset-source"
    / "end-event-mobs" / "skeleton.png"
)
KAGUNE_IMPORT = (
    ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets"
    / "copimineclient" / "geometry" / "end_rift_tentacle.json"
)
TENTACLE_SCALING_POLICY = (
    ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
    / "domain" / "TentacleScalingPolicy.java"
)


def _read_json(relative: str) -> dict:
    path = ASSET_ROOT / relative
    assert path.is_file(), f"missing resource-pack asset: {relative}"
    return json.loads(path.read_text(encoding="utf-8"))


def _texture_path(resource_location: str) -> Path:
    namespace, path = resource_location.split(":", 1)
    return ASSET_ROOT / namespace / "textures" / f"{path}.png"


def test_wave_three_portal_models_resolve_to_the_event_texture():
    texture = COPIMINE / "textures" / "item" / "end_event_portal.png"
    assert texture.is_file()
    with Image.open(texture) as image:
        assert image.width >= 512 and image.height >= 512
        assert image.getbbox() is not None

    for model_name in ("end_event_portal", "end_event_portal_inner", "end_event_portal_shard"):
        model = _read_json(f"copimine/models/block/{model_name}.json")
        if model_name == 'end_event_portal_shard':
            assert model['textures']['frame'] == 'copimine:item/end_event_rift_structure_stone'
            assert model['textures']['energy'] == 'copimine:item/end_event_rift_structure_energy'
        else:
            expected = "copimine:item/end_event_portal" if model_name == "end_event_portal" else "copimine:item/end_event_rift_membrane"
            assert model["textures"]["rift"] == expected
        for texture in model['textures'].values():
            if texture.startswith('copimine:'):
                assert _texture_path(texture).is_file()
            else:
                assert model_name == 'end_event_portal' and texture == 'minecraft:block/obsidian'
    membrane = _texture_path("copimine:item/end_event_rift_membrane")
    with Image.open(membrane) as image:
        assert image.size == (32, 1024) and image.mode == 'RGBA'
        assert image.getchannel('A').getextrema()[0] == 0
    animation = json.loads(Path(str(membrane) + '.mcmeta').read_text(encoding='utf-8'))['animation']
    assert animation['width'] == 32 and animation['height'] == 128 and animation['frames'] == list(range(8))


def test_wave_six_ritual_membrane_is_packaged_with_real_transparency():
    client = CLIENT_ENTITY_TEXTURES / "end_event_ritual_membrane.png"
    server = COPIMINE / "textures" / "item" / "end_event_ritual_membrane.png"
    assert client.read_bytes() == server.read_bytes()
    with Image.open(client) as image:
        assert image.mode == "RGBA" and min(image.size) >= 512
        lo, hi = image.getchannel("A").getextrema()
        assert lo == 0 and hi > 100, "material requires real transparency and readable energy veins"
    shield = _read_json("copimine/models/item/end_event_ritual_caster_shield.json")
    assert shield["parent"] == "copimine:item/end_event_rift_guardian_shield"
    inherited = _read_json("copimine/models/item/end_event_rift_guardian_shield.json")
    assert inherited["elements"], "Wave 6 must inherit the actual boss shield geometry"
    assert inherited["textures"]["shield"] == "copimine:item/end_event_rift_guardian_shield_hd"
    assert (CLIENT_ENTITY_TEXTURES / "end_rift_guardian_shield_hd.png").read_bytes() == (
        _texture_path(inherited["textures"]["shield"]).read_bytes()
    ), "the client and pack must use the same boss shield atlas"


def test_wave_six_ritual_sphere_has_a_dedicated_visible_paper_model():
    """The Wave 6 sphere must be an actual transparent 3D shell, not shard cuboids."""
    model = _read_json("copimine/models/item/end_event_ritual_sphere.json")
    assert model.get("parent") == "minecraft:item/generated"
    assert model.get("render_type") == "minecraft:translucent"
    assert model["textures"]["shell"] == "copimine:item/end_event_ritual_shell"
    with Image.open(_texture_path(model["textures"]["shell"])) as image:
        assert image.mode == "RGBA", "shell must retain real transparency"
        histogram = image.getchannel("A").histogram()
        assert histogram[0] / sum(histogram) >= 0.5, "at least half the outer shell is empty"
    client_shell = CLIENT_ENTITY_TEXTURES / "end_event_ritual_shell.png"
    assert client_shell.read_bytes() == _texture_path(model["textures"]["shell"]).read_bytes()
    assert "rift_core_shard" not in json.dumps(model)
    elements = model.get("elements", ())
    assert len(elements) == 192, "the shell must use the bounded spherical voxel mesh"
    centers = []
    occupied = set()
    for element in elements:
        lower, upper = element["from"], element["to"]
        assert [high - low for low, high in zip(lower, upper)] == [2, 2, 2]
        assert all(0 <= low < high <= 16 for low, high in zip(lower, upper))
        assert len(element.get("faces", {})) == 6
        cell = tuple(lower)
        assert cell not in occupied
        occupied.add(cell)
        center = tuple((low + high) / 2 - 8 for low, high in zip(lower, upper))
        radius = math.sqrt(sum(axis * axis for axis in center))
        assert 5.2 <= radius <= 7.8
        centers.append(center)
    assert all(max(abs(center[axis]) for center in centers) == 7 for axis in range(3))

    generator = ROOT / "resourcepacks" / "tools" / "generate_ritual_sphere_model.py"
    subprocess.run([sys.executable, str(generator), "--check"], check=True, capture_output=True)

    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
              / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    start = source.index("private void spawnRitualSphereVisual")
    end = source.index("private void tagRitualPrisoner", start)
    body = source[start:end]
    assert "RITUAL_SPHERE_DISPLAY_SCALE" in body
    assert "setBillboard(Display.Billboard.FIXED)" in body, "3D shell must not rotate to face each viewer"
    assert "new Vector3f()" in body, "display is centred on the server sphere anchor"


def test_wave_six_ritual_sphere_uses_a_unique_custom_model_data_entry():
    manifest = json.loads(PACK_MANIFEST.read_text(encoding="utf-8"))
    entries = [entry for entry in manifest["items"]
               if entry.get("id") == "end_event_ritual_sphere"]
    assert len(entries) == 1
    assert entries[0]["base_material"] == "paper"
    assert entries[0]["custom_model_data"] == 830019
    assert entries[0]["model"] == "copimine:item/end_event_ritual_sphere"


def test_obelisk_portrait_is_only_projected_on_front_and_back_faces():
    for model_name in (
        "end_event_rift_obelisk_full",
        "end_event_rift_obelisk_damaged",
        "end_event_rift_obelisk_critical",
    ):
        model = _read_json(f"copimine/models/item/{model_name}.json")
        assert model["textures"]["rift"].endswith(f"{model_name}_hd")
        assert _texture_path(model["textures"]["rift"]).is_file()
        for element in model["elements"]:
            faces = element["faces"]
            for side in ("up", "down", "east", "west"):
                assert faces.get(side, {}).get("texture") != "#rift", (
                    f"{model_name} stretches the portrait over {side}"
                )


def test_obelisk_hd_states_are_real_high_resolution_assets():
    for state in ("full", "damaged", "critical"):
        texture = COPIMINE / "textures" / "item" / f"end_event_rift_obelisk_{state}_hd.png"
        with Image.open(texture) as image:
            assert max(image.size) >= 512
            assert image.getbbox() is not None


def test_end_rift_vanilla_models_use_only_supported_element_rotation_angles():
    """Minecraft rejects arbitrary element rotations instead of rendering a fallback."""
    supported_angles = {-45, -22.5, 0, 22.5, 45}
    models_root = COPIMINE / "models"
    for path in models_root.rglob("*.json"):
        model = json.loads(path.read_text(encoding="utf-8"))
        for index, element in enumerate(model.get("elements", ())):
            rotation = element.get("rotation")
            if rotation is not None:
                assert rotation["angle"] in supported_angles, (
                    f"{path.relative_to(ASSET_ROOT)} element {index} has unsupported "
                    f"rotation angle {rotation['angle']}"
                )


def test_guardian_texture_matches_supplied_source_asset():
    """The client must render the artist's unmodified 128x128 boss atlas."""
    assert BOSS_TEXTURE.is_file()
    assert hashlib.sha256(BOSS_TEXTURE.read_bytes()).hexdigest() == (
        "f298ed322335c5439c19dddb8014aa0960b83f3fb27d692580a75e051516c45d"
    )


def test_heavy_tentacle_is_opaque_and_guardian_shield_blends_as_translucent_violet():
    tentacle_path = CLIENT_ENTITY_TEXTURES / "end_rift_tentacle_hd.png"
    server_tentacle_path = COPIMINE / "textures" / "item" / "end_event_rift_tentacle_hd.png"
    assert tentacle_path.is_file() and server_tentacle_path.is_file()
    tentacle_bytes = tentacle_path.read_bytes()
    assert hashlib.sha256(tentacle_bytes).hexdigest() == (
        "5817936653025968abdd07003eba29e4820b09b33ebac4708e463f3b8b4f6bbc"
    ), "the runtime tentacle must use the unmodified texture embedded in kagune.bbmodel"
    assert server_tentacle_path.read_bytes() == tentacle_bytes, (
        "the client and resource-pack fallback must use the same artist texture"
    )
    with Image.open(tentacle_path) as tentacle:
        assert tentacle.size == (64, 64)

    assert "models" not in KAGUNE_IMPORT.parts, (
        "the custom Kagune descriptor must not be discovered as a vanilla item/block model"
    )
    imported = json.loads(KAGUNE_IMPORT.read_text(encoding="utf-8"))
    assert imported["format"] == "copimine:kagune-import-v1"
    assert imported["texture"]["uv_size"] == [8, 8]
    assert len(imported["groups"]) == 6
    assert len(imported["elements"]) == 6
    assert len(imported["animations"]) == 12
    assert "trow" in imported["animations"]

    fallback_model = _read_json("copimine/models/item/end_event_rift_tentacle.json")
    assert fallback_model["textures"]["tentacle"] == "copimine:item/end_event_rift_tentacle_hd"
    assert len(fallback_model["elements"]) == 6
    assert all(set(element["faces"]) == {"down", "up", "north", "south", "west", "east"}
               for element in fallback_model["elements"])

    shield = Image.open(CLIENT_ENTITY_TEXTURES / "end_rift_guardian_shield_hd.png").convert("RGBA")
    assert shield.size == (32, 32), "user-requested Minecraft pixel atlas"
    shield_alpha = shield.getchannel("A")
    assert shield_alpha.getbbox() is not None
    assert any(0 < alpha < 255 for alpha in shield_alpha.getdata()), (
        "the shield atlas must retain translucent violet detail"
    )

    renderer = (ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine"
                / "client" / "EndRiftGuardianShieldRenderer.java").read_text(encoding="utf-8")
    assert "RenderLayer.getEntityTranslucent(TEXTURE)" in renderer
    assert "EndRiftGuardianShieldModel.renderTint()" in renderer
    assert "EndRiftGuardianShieldModel.worldRenderScale(pose)" in renderer

    server_shield = COPIMINE / "textures" / "item" / "end_event_rift_guardian_shield_hd.png"
    with Image.open(server_shield).convert("RGBA") as fallback:
        assert fallback.size == (32, 32)
        fallback_alpha = fallback.getchannel("A")
        assert fallback_alpha.getbbox() is not None
        assert fallback_alpha.getextrema()[0] == 0
        assert 160 <= fallback_alpha.getextrema()[1] < 255
        center_alpha = fallback_alpha.getpixel((fallback.width // 2, fallback.height // 2))
        assert 96 <= center_alpha <= 224, "the shield plate itself must be translucent"
    model = _read_json("copimine/models/item/end_event_rift_guardian_shield.json")
    assert model["textures"]["shield"] == "copimine:item/end_event_rift_guardian_shield_hd"
    assert model.get("parent") != "minecraft:item/generated", (
        "the server fallback must be a shaped 3D item model, not a flat generated square"
    )
    assert len(model.get("elements", [])) == 1
    shield_element = model["elements"][0]
    assert shield_element["to"][2] - shield_element["from"][2] >= 0.5
    assert "north" in shield_element["faces"] and "south" in shield_element["faces"]

    manifest = json.loads(PACK_MANIFEST.read_text(encoding="utf-8"))
    entries = [entry for entry in manifest["items"]
               if entry.get("custom_model_data") == 830020]
    assert entries == [{
        "id": "end_event_rift_guardian_shield",
        "custom_model_data": 830020,
        "base_material": "paper",
        "model": "copimine:item/end_event_rift_guardian_shield",
        "texture": "copimine:item/end_event_rift_guardian_shield_hd",
    }]


def test_client_and_server_use_the_same_transparent_guardian_shield_silhouette():
    client_path = CLIENT_ENTITY_TEXTURES / "end_rift_guardian_shield_hd.png"
    server_path = (COPIMINE / "textures" / "item" / "end_event_rift_guardian_shield_hd.png")
    with Image.open(client_path).convert("RGBA") as client, Image.open(server_path).convert("RGBA") as server:
        assert client.size == server.size == (32, 32)
        assert list(client.getdata()) == list(server.getdata()), (
            "the native client renderer and resource-pack fallback must share one shield atlas"
        )
        alpha = client.getchannel("A")
        assert alpha.getpixel((0, 0)) == 0
        assert alpha.getpixel((31, 0)) == 0
        assert alpha.getpixel((0, 31)) == 0
        assert alpha.getpixel((31, 31)) == 0
        bounds = alpha.getbbox()
        assert bounds is not None
        assert bounds[0] > 0 and bounds[1] > 0
        assert bounds[2] < 32 and bounds[3] < 32
        assert bounds[2] - bounds[0] < bounds[3] - bounds[1], (
            "the shield texture must be a tall shaped plate, not a square card"
        )
        assert 96 <= alpha.getpixel((16, 16)) <= 224
        pixels = list(client.getdata())
        assert any(a > 0 and r < 100 and g < 100 and b > r * 1.5
                   for r, g, b, a in pixels), "the shield needs a dark-violet bevel"
        assert not any(a > 180 and r > 180 and g < 100 and b > 160
                       for r, g, b, a in pixels), (
            "the shield must not read as permanently red/magenta"
        )
        assert any(a > 150 and 80 <= r < 180 and g < 130 and b > 180
                   for r, g, b, a in pixels), "the shield needs a bright blue-violet fracture accent"
        assert any(a > 96 and r < 120 and g > 145 and b > 170
                   for r, g, b, a in pixels), "the shield needs small cyan fracture glints"
        assert max(a for _, _, _, a in pixels) < 255, "the shield must remain translucent"

        assert not _shield_has_horizontal_face_cut(client), (
            "the shield atlas must not contain a horizontal black cut through its face"
        )


def _shield_has_horizontal_face_cut(image):
    """Measure each tapered row's face, excluding its two-texel metal rim."""
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        return True
    left, top, right, bottom = bounds
    end_margin = max(2, round((bottom - top) * 0.14))
    for y in range(top + end_margin, bottom - end_margin):
        row = [x for x in range(left, right) if image.getpixel((x, y))[3] > 120]
        if len(row) < 5:
            continue
        rim = max(2, (row[-1] - row[0] + 1) // 8)
        face_left, face_right = row[0] + rim, row[-1] + 1 - rim
        face_width = face_right - face_left
        if face_width < 3:
            continue
        dark = sum(1 for x in range(face_left, face_right)
                   if (lambda p: p[3] > 120 and p[0] < 48 and p[1] < 20 and p[2] < 75)(image.getpixel((x, y))))
        if dark >= max(1, face_width // 3):
            return True
    return False


def test_32px_shield_cut_detector_rejects_face_stripe_but_preserves_metal_rim():
    atlas = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(3, 29):
        for x in range(7, 25):
            atlas.putpixel((x, y), (90, 55, 180, 200))
        for x in (7, 8, 23, 24):
            atlas.putpixel((x, y), (24, 10, 55, 200))
    assert not _shield_has_horizontal_face_cut(atlas), "a dark two-pixel rim is intentional"
    for x in range(9, 23):
        atlas.putpixel((x, 15), (24, 10, 55, 200))
    assert _shield_has_horizontal_face_cut(atlas), "a real black face stripe still fails validation"


def test_archived_mob_skins_are_byte_exact_client_inputs():
    for source_name, client_name, expected_sha256 in (
        ("enderman-1.png", "end_rift_user_enderman.png",
         "a9a154f232919627451431e3f3874c9e850f23e531eae2cfe2a4a9cc16edf447"),
        ("spider.png", "end_rift_user_spider.png",
         "19c46ff4aa829e7101b25a50a55090cd1d8145c2f83b95d64c13a20f6b5c9abf"),
    ):
        source = SUPPLIED_SKINS / source_name
        target = CLIENT_ENTITY_TEXTURES / client_name
        assert source.is_file() and target.is_file()
        assert hashlib.sha256(source.read_bytes()).hexdigest() == expected_sha256
        assert target.read_bytes() == source.read_bytes()


def test_configured_tentacle_hitbox_covers_the_full_authored_rig_length():
    policy = TENTACLE_SCALING_POLICY.read_text(encoding="utf-8")
    config = (ROOT / "copimine-end-event" / "config.yml").read_text(encoding="utf-8")
    logical_length = re.search(
        r"DEFAULT_LOGICAL_LENGTH\s*=\s*([0-9]+(?:\.[0-9]+)?)D", policy
    )
    hitbox_height = re.search(
        r"(?m)^\s*hitbox-height:\s*([0-9]+(?:\.[0-9]+)?)\s*$", config
    )
    assert logical_length and hitbox_height, "tentacle dimensions must remain explicit"
    assert float(hitbox_height.group(1)) >= float(logical_length.group(1)), (
        "the server Interaction must cover the entire imported tentacle so every visible "
        "segment remains hittable"
    )
    target = CLIENT_ENTITY_TEXTURES / "end_rift_user_skeleton.png"
    assert SUPPLIED_SKELETON.is_file() and target.is_file()
    assert hashlib.sha256(target.read_bytes()).hexdigest() == hashlib.sha256(SUPPLIED_SKELETON.read_bytes()).hexdigest()


def test_generated_mob_faces_put_eyes_on_the_visible_front_uv_islands():
    """A UV-valid atlas can still paint both eyes on the top of a skull."""
    def violet_count(image: Image.Image, box: tuple[int, int, int, int]) -> int:
        return sum(1 for red, green, blue, alpha in image.crop(box).getdata()
                   if alpha == 255 and red >= 105 and green < 90 and blue >= 125)

    for name in (
        "end_rift_wave_guardian_enderman.png",
        "end_rift_ritual_guard_enderman.png",
        "end_rift_ritual_caster.png",
    ):
        with Image.open(CLIENT_ENTITY_TEXTURES / name).convert("RGBA") as image:
            assert violet_count(image, (8, 8, 16, 16)) >= 2, name
            # The armoured body shell hides the vanilla torso, so its own
            # front UV island needs a readable rift accent.
            if name != "end_rift_ritual_caster.png":
                assert violet_count(image, (5, 13, 13, 24)) >= 2, name

    for name in (
        "end_rift_skeleton.png",
        "end_rift_wave_guardian_skeleton.png",
        "end_rift_ritual_guard_skeleton.png",
    ):
        with Image.open(CLIENT_ENTITY_TEXTURES / name).convert("RGBA") as image:
            face_pixels = list(image.crop((7, 7, 14, 14)).getdata())
            assert sum(1 for red, green, blue, alpha in face_pixels
                       if alpha == 255 and red >= 105 and green < 90 and blue >= 125) >= 2, name
            assert violet_count(image, (14, 0, 18, 3)) >= 1, name


def test_archived_skeleton_is_preserved_and_authored_rift_atlases_are_render_ready():
    supplied = CLIENT_ENTITY_TEXTURES / "end_rift_user_skeleton.png"
    assert SUPPLIED_SKELETON.is_file() and supplied.is_file()
    expected_hash = "4744a2c76285b1fa06f6fa64bff5573bea88d63ae7050912b138b830b573ee80"
    assert hashlib.sha256(SUPPLIED_SKELETON.read_bytes()).hexdigest() == expected_hash
    assert supplied.read_bytes() == SUPPLIED_SKELETON.read_bytes()

    for name in (
        "end_rift_skeleton.png",
        "end_rift_wave_guardian_skeleton.png",
        "end_rift_ritual_guard_skeleton.png",
    ):
        with Image.open(CLIENT_ENTITY_TEXTURES / name).convert("RGBA") as image:
            assert image.size == (64, 32), name
            assert all(alpha == 255 for alpha in image.getchannel("A").getdata()), name
            face_pixels = list(image.crop((7, 7, 14, 14)).getdata())
            assert sum(1 for red, green, blue, alpha in face_pixels
                       if alpha == 255 and red >= 105 and green < 90 and blue >= 125) >= 2, name
            torso_pixels = list(image.crop((16, 0, 28, 16)).getdata())
            assert sum(1 for red, green, blue, _ in torso_pixels
                       if red >= 150 and green >= 130 and blue >= 160) >= 8, name
            assert not any(green > red * 1.5 and blue > red * 1.5
                           for red, green, blue, _ in image.getdata()), name


def test_elite_mob_atlases_match_the_supplied_archive_exactly():
    expected = {
        "end_rift_elite.png": "5923111b4ac459daee04aeeea4930dcac4b1156ed3d94bbaaa4b89b987fc6bdc",
        "end_rift_elite_skeleton.png": "df29a577e2cc5896507044db37349216c2401311e458576ecbb2e0fe8ce65514",
        "end_rift_elite_spider.png": "40ab699e7dc46d50a26728679539b30ced556269b795b06ed7bf498dd1b4d052",
    }
    for name, expected_hash in expected.items():
        path = CLIENT_ENTITY_TEXTURES / name
        assert path.is_file(), name
        assert hashlib.sha256(path.read_bytes()).hexdigest() == expected_hash, name
        with Image.open(path).convert("RGBA") as image:
            assert image.size == (64, 32), name
            alpha = set(image.getchannel("A").getdata())
            assert alpha <= {0, 255}, (name, alpha)
            assert 0 in alpha and 255 in alpha, name


def test_runtime_kagune_import_is_generated_from_the_supplied_blockbench_file():
    assert KAGUNE_SOURCE_MODEL.is_file()
    assert hashlib.sha256(KAGUNE_SOURCE_MODEL.read_bytes()).hexdigest() == (
        "9f3e661b9b70c0a6a906c592bc2a643bbe1a19abd5906d841b0e0aa9cb5f92dc"
    )
    imported = json.loads(KAGUNE_IMPORT.read_text(encoding="utf-8"))
    assert [group["name"] for group in imported["groups"]] == [
        "1layer", "1layer2", "2layer", "2layer2", "3layer", "3layer2"
    ]
    assert imported["animations"]["telegraph_grab"]["length"] == 1.25
    assert imported["animations"]["telegraph_grab"]["tracks"]["1layer"]["rotation"][-1]["value"] == [
        0.0, -45.0, 0.0
    ]


def test_event_spider_and_skeleton_geometry_routes_use_their_model_registry_as_authority():
    mixin = (ROOT / "CopiMineClient" / "src" / "main" / "java" / "me" / "copimine"
             / "client" / "mixin" / "LivingEntityRendererMixin.java").read_text(encoding="utf-8")
    assert "isEventSpiderVisual(" not in mixin
    assert "isEventSkeletonVisual(" not in mixin
    assert "copimine$spiderRenderer.modelFor(visual)" in mixin
    assert "copimine$skeletonRenderer.modelFor(visual)" in mixin
    assert 'logGeometrySelection("spider", visual' in mixin
    assert 'logGeometrySelection("skeleton", visual' in mixin
