"""Regression contracts for moving utility artifacts inside creative inventory."""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


class UtilityInventoryTransportContractTest(unittest.TestCase):
    def test_creative_transport_allows_player_inventory_moves_but_not_creation_or_external_storage(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("creativeUtilityActionLeavesPlayerInventory", source)
        self.assertIn("event.getClickedInventory() == player.getInventory()", source)
        self.assertIn("event.getRawSlot() >= top.getSize()", source)
        handler = source[source.index("public void onUtilityArtifactCreative") : source.index("private boolean hasInfiniteTorchIngredient")]
        self.assertIn("creativeUtilityActionLeavesPlayerInventory(event, player)", handler)
        self.assertIn("event.setCancelled(true)", handler)
        self.assertNotIn("ANGEL_WINGS", handler)

    def test_return_stone_and_torch_keep_unique_amount_guard(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn('"return_stone"', source)
        self.assertIn('"infinite_torch"', source)
        self.assertIn("hasInvalidInfiniteTorchAmount", source)
        self.assertNotIn("hasInvalidAngelSealAmount", source)

    def test_angel_wings_has_no_retired_seal_inventory_handlers(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "AngelSeal",
            "ANGEL_SEAL",
            "onAngelSeal",
            "hasInvalidAngelSealAmount",
        ):
            self.assertNotIn(marker, source)

    def test_return_stone_is_created_as_a_singleton_stack(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        create_start = source.index("private ItemStack createOfficialItem")
        create_body = source[create_start : source.index("private void decorateNarcoticRecipeBook", create_start)]
        self.assertIn('"RETURN_STONE".equalsIgnoreCase(var1.effect())', create_body)
        self.assertIn('var6.setMaxStackSize(1)', create_body)

    def test_return_stone_uses_vanilla_inventory_transport(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "onReturnStoneInventoryMove",
            "onReturnStoneInventoryTransportClick",
            "onReturnStoneInventoryDrag",
            "shouldBlockReturnStoneContainerTransfer",
            "isReturnStonePlayerInventoryClick",
            "isReturnStonePlayerInventoryDrag",
            "hasInvalidReturnStoneAmount(InventoryClickEvent",
            "hasInvalidReturnStoneAmount(InventoryDragEvent",
        ):
            self.assertNotIn(marker, source)
        utility_start = source.index("private boolean isUtilityArtifactItem")
        utility_body = source[utility_start : source.index("public void onUtilityArtifactCreative", utility_start)]
        self.assertNotIn("isReturnStoneItem", utility_body)


if __name__ == "__main__":
    unittest.main()
