import hashlib
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "resourcepacks" / "src" / "assets"
COPIMINE = ASSET_ROOT / "copimine"
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


def test_guardian_texture_is_the_supplied_artist_atlas():
    """Keep the exact 128x128 atlas shipped in the user's boss archive.

    The vivid islands are intentional UV content. Recolouring them merely hid
    an importer bug and changed the artist-owned atlas instead of fixing face
    orientation.
    """
    assert BOSS_TEXTURE.is_file()
    with Image.open(BOSS_TEXTURE) as image:
        assert image.size == (128, 128)
        assert image.getbbox() is not None
    assert hashlib.sha256(BOSS_TEXTURE.read_bytes()).hexdigest() == (
        "f298ed322335c5439c19dddb8014aa0960b83f3fb27d692580a75e051516c45d"
    )
