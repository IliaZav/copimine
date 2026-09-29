import hashlib
import json
import re
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
SUPPLIED_SKINS = ROOT / "artifacts" / "source-inspect" / "end-event" / "chameleon" / "models" / "enderboss" / "skins"
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
        assert model["textures"]["rift"] == "copimine:item/end_event_portal"
        assert _texture_path(model["textures"]["rift"]).is_file()


def test_wave_six_ritual_sphere_has_a_dedicated_visible_paper_model():
    """The Wave 6 sphere must be a faceted transparent crystal, not a flat card."""
    model = _read_json("copimine/models/item/end_event_ritual_sphere.json")
    assert model.get("parent") == "minecraft:item/generated"
    assert model["textures"]["core"] == "copimine:item/rift_core_shard"
    texture = _texture_path(model["textures"]["core"])
    assert texture.is_file()
    with Image.open(texture).convert("RGBA") as image:
        alpha = image.getchannel("A")
        assert alpha.getbbox() is not None
        assert any(value == 0 for value in alpha.getdata())
        assert any(0 < value < 255 for value in alpha.getdata())
    assert len(model.get("elements", ())) >= 3, "the sphere must have readable 3D facets"
    assert all(len(element.get("faces", {})) == 6 for element in model["elements"])

    source = (ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent"
              / "CopiMineEndEvent.java").read_text(encoding="utf-8")
    start = source.index("private void spawnRitualSphereVisual")
    end = source.index("private void tagRitualPrisoner", start)
    body = source[start:end]
    assert "RITUAL_SPHERE_DISPLAY_SCALE" in body
    assert "setBillboard(Display.Billboard.CENTER)" in body


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
    assert shield.size == (512, 512)
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
        assert fallback.width == fallback.height and fallback.width >= 512
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
        assert client.size == server.size == (512, 512)
        assert list(client.getdata()) == list(server.getdata()), (
            "the native client renderer and resource-pack fallback must share one shield atlas"
        )
        alpha = client.getchannel("A")
        assert alpha.getpixel((0, 0)) == 0
        assert alpha.getpixel((511, 0)) == 0
        assert alpha.getpixel((0, 511)) == 0
        assert alpha.getpixel((511, 511)) == 0
        bounds = alpha.getbbox()
        assert bounds is not None
        assert bounds[0] > 0 and bounds[1] > 0
        assert bounds[2] < 512 and bounds[3] < 512
        assert bounds[2] - bounds[0] < bounds[3] - bounds[1], (
            "the shield texture must be a tall shaped plate, not a square card"
        )
        assert 96 <= alpha.getpixel((256, 256)) <= 224
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

        left, top, right, bottom = alpha.getbbox()
        inner_left = left + (right - left) // 8
        inner_right = right - (right - left) // 8
        inner_width = inner_right - inner_left
        dark_counts = []
        for y in range(top + 56, bottom - 56):
            dark_counts.append(sum(
                1 for x in range(inner_left, inner_right)
                if (lambda pixel: pixel[3] > 120 and pixel[0] < 48
                    and pixel[1] < 20 and pixel[2] < 75)(client.getpixel((x, y)))
            ))
        assert max(dark_counts) < inner_width // 3, (
            "the shield atlas must not contain a horizontal black cut through its face"
        )


def test_archived_mob_skins_are_byte_exact_client_inputs():
    for source_name, client_name in (
        ("enderman-1.png", "end_rift_user_enderman.png"),
        ("spider.png", "end_rift_user_spider.png"),
    ):
        source = SUPPLIED_SKINS / source_name
        target = CLIENT_ENTITY_TEXTURES / client_name
        assert source.is_file() and target.is_file()
    assert hashlib.sha256(target.read_bytes()).hexdigest() == hashlib.sha256(source.read_bytes()).hexdigest()


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
        "end_rift_elite.png",
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
        "end_rift_elite_skeleton.png",
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
        "end_rift_elite_skeleton.png",
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
