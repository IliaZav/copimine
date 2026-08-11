"""Contracts for 32x32 animated vanilla-style bow/crossbow art."""

from __future__ import annotations

import json
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
TEXTURE_DIR = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts"
GENERATOR = ROOT / "resourcepacks" / "generate_new_item_textures.py"
BUILDER = ROOT / "resourcepacks" / "build-resourcepack.py"


class ResourcepackVisualContractTest(unittest.TestCase):
    def test_generated_bows_and_crossbows_are_32x32_per_frame(self) -> None:
        names = [
            "teleport_bow.png",
            "teleport_bow_pulling_0.png",
            "teleport_bow_pulling_1.png",
            "teleport_bow_pulling_2.png",
            "cobblestone_trail_bow.png",
            "cobblestone_trail_bow_pulling_0.png",
            "cobblestone_trail_bow_pulling_1.png",
            "cobblestone_trail_bow_pulling_2.png",
            "explosive_crossbow.png",
            "explosive_crossbow_pulling_0.png",
            "explosive_crossbow_pulling_1.png",
            "explosive_crossbow_pulling_2.png",
        ]
        for name in names:
            with Image.open(TEXTURE_DIR / name) as image:
                self.assertEqual(image.size, (32, 32), name)

    def test_generator_draws_explicit_arrow_and_animated_tnt_frames(self) -> None:
        source = GENERATOR.read_text(encoding="utf-8")
        self.assertIn("draw_arrow", source)
        self.assertIn("save_animation_texture", source)
        self.assertIn("frametime", source)

    def test_builder_keeps_vanilla_pull_and_loaded_arrow_predicates(self) -> None:
        source = BUILDER.read_text(encoding="utf-8")
        for marker in (
            '"pulling": 1',
            '"pull": 0.65',
            '"pull": 0.9',
            '"charged": 1',
            '"model": "minecraft:item/crossbow_arrow"',
        ):
            self.assertIn(marker, source)

    def test_charged_explosive_crossbow_has_animation_metadata(self) -> None:
        metadata_path = TEXTURE_DIR / "explosive_crossbow_charged.png.mcmeta"
        self.assertTrue(metadata_path.exists())
        animation = json.loads(metadata_path.read_text(encoding="utf-8"))["animation"]
        self.assertEqual(animation["frametime"], 3)
        self.assertFalse(animation["interpolate"])


if __name__ == "__main__":
    unittest.main()
