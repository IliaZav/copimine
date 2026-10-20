import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PLUGIN = ROOT / "copimine-end-event"
SRC = PLUGIN / "src/me/copimine/endevent"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_obelisk_uses_one_coherent_scaled_visual_instead_of_stacking_full_models():
    geometry = read(SRC / "domain/ObeliskGeometryPolicy.java")
    root = read(SRC / "CopiMineEndEvent.java")

    assert "VISUAL_DISPLAY_KEY = 0" in geometry
    assert "VISUAL_DISPLAY_COUNT = 1" in geometry
    assert "VISUAL_FULL_HEIGHT_BLOCKS = 5.0F" in geometry
    assert "visualHeightBlocks(" in geometry
    assert "visualIds().get(ObeliskGeometryPolicy.VISUAL_DISPLAY_KEY)" in root
    assert "value.setDisplayHeight(ObeliskGeometryPolicy.VISUAL_FULL_HEIGHT_BLOCKS)" in root
    assert "ObeliskGeometryPolicy.visualHeightBlocks(highestLayer)" in root
    assert "for (int layer = 0; layer <= highestLayer; layer++)" not in root


def test_obelisk_visual_resource_is_a_single_complete_model():
    # Verify the complete unit model rather than depending on one obsolete
    # cuboid's formatting. ItemDisplay NONE centres a unit model at (8,8,8).
    model = json.loads(read(ROOT / "resourcepacks/src/assets/copimine/models/item/end_event_rift_obelisk_full.json"))
    elements = model["elements"]
    assert len(elements) >= 5
    assert min(element["from"][1] for element in elements) == 0
    assert max(element["to"][1] for element in elements) == 16
    for axis in (0, 2):
        assert min(element["from"][axis] for element in elements) == 0
        assert max(element["to"][axis] for element in elements) == 16
