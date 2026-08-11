"""Regression contract for the AR shop's recipe-notes category."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


def item_block(item_id: str) -> str:
    text = ITEMS.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^  - id: {re.escape(item_id)}\s*$.*?(?=^  - id:|^donation-catalog:|\Z)",
        text,
    )
    if not match:
        raise AssertionError(f"missing catalog item {item_id}")
    return match.group(0)


class NotesCategoryContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = SOURCE.read_text(encoding="utf-8")

    def test_notes_is_a_real_catalog_category_with_menu_label(self) -> None:
        enum_block = self.source[self.source.index("private static enum Category") :]
        self.assertIn("NOTES", enum_block.split("}", 1)[0])
        self.assertIn('case NOTES -> "Записки"', self.source)
        self.assertIn("case NOTES -> Material.WRITTEN_BOOK", self.source)
        self.assertIn("case NOTES -> this.button", self.source)

    def test_public_shop_replaces_help_with_notes_in_both_menu_paths(self) -> None:
        self.assertGreaterEqual(self.source.count('this.categoryIcon(CopiMineArtifacts.Category.NOTES)'), 2)
        self.assertGreaterEqual(self.source.count('"cat:NOTES"'), 2)
        self.assertNotIn('"&eПомощь"', self.source)
        self.assertNotIn('),\n            "help"', self.source)

    def test_recipe_books_are_listed_in_notes_and_streamer_stays_admin_only(self) -> None:
        for item_id in (
            "narcotic_recipe_feta",
            "narcotic_recipe_kola",
            "narcotic_recipe_girion",
            "narcotic_recipe_sbp",
            "narcotic_recipe_sos",
            "narcotic_recipe_drun",
            "narcotic_recipe_chups",
            "narcotic_recipe_borshevik",
        ):
            block = item_block(item_id)
            self.assertIn("category: NOTES", block)

        streamer = item_block("streamer_stick")
        self.assertIn("source: ADMIN_ONLY", streamer)
        self.assertNotIn("source: AR_SHOP", streamer)


if __name__ == "__main__":
    unittest.main()
