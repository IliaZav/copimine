from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[1]
ENTITY_DIR = ROOT / "CopiMineClient/src/main/resources/assets/copimineclient/textures/entity"
ENTITY_NAMES = (
    "end_rift_enderman.png",
    "end_rift_elite.png",
    "end_rift_spider.png",
    "end_rift_shulker.png",
    "end_rift_skeleton.png",
    "end_rift_elite_skeleton.png",
    "end_rift_guardian.png",
)


def _image(name: str) -> Image.Image:
    return Image.open(ENTITY_DIR / name).convert("RGBA")


def test_event_entity_sheets_have_layered_original_palettes() -> None:
    for name in ENTITY_NAMES:
        with _image(name) as image:
            pixels = list(image.getdata())
        visible = [pixel for pixel in pixels if pixel[3] > 0]
        palette = {pixel[:3] for pixel in visible}
        colorful = [pixel for pixel in visible if max(pixel[:3]) - min(pixel[:3]) >= 24]
        assert len(palette) >= 12, f"{name} must have a layered custom palette"
        assert len(colorful) / len(visible) >= 0.45, f"{name} is too close to a flat vanilla tint"


def test_guardian_and_elite_are_not_copies_of_the_common_enderman_sheet() -> None:
    with _image("end_rift_enderman.png") as common, _image("end_rift_elite.png") as elite, _image("end_rift_guardian.png") as guardian:
        assert ImageChops.difference(common, elite).getbbox() is not None
        assert ImageChops.difference(common, guardian).getbbox() is not None
        assert ImageChops.difference(elite, guardian).getbbox() is not None
