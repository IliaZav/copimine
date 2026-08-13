"""Regression contract for the combat projectile materials and enchantments."""

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


class TeleportCrossbowAndNoInfinityContractTest(unittest.TestCase):
    def test_teleport_item_is_a_crossbow_in_the_ar_catalog(self) -> None:
        block = item_block("combat_crossbow")
        self.assertIn("material: CROSSBOW", block)
        self.assertIn('name: "&6Арбалет телепортации"', block)
        self.assertIn("source: AR_SHOP", block)
        self.assertIn("effect: AR_CROSSBOW_TELEPORT", block)

    def test_trail_bow_has_no_infinity_enchantment(self) -> None:
        self.assertIn("material: BOW", item_block("cobblestone_trail_bow"))
        self.assertNotIn("enchantment: INFINITY", item_block("cobblestone_trail_bow"))

        source = SOURCE.read_text(encoding="utf-8")
        self.assertNotIn("Enchantment.INFINITY", source)


if __name__ == "__main__":
    unittest.main()
