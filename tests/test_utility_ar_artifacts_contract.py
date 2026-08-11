"""RED contracts for the utility AR artifacts in the attached release spec."""

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


def require_all(text: str, *needles: str) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"missing markers: {missing}")


class UtilityArtifactCatalogContractTest(unittest.TestCase):
    def test_repair_kit_uses_spec_material_and_five_real_uses(self) -> None:
        block = item_block("repair_kit")
        require_all(
            block,
            "material: SHEARS",
            "source: AR_SHOP",
            "name: \"&dРемкомплект\"",
            "rarity: EPIC",
            "price_ar: 10",
            "effect: REPAIR_KIT",
            "custom_model_data: 10025",
            "custom-texture-mode-allowed: true",
        )

    def test_return_stone_catalog_contract(self) -> None:
        block = item_block("return_stone")
        require_all(
            block,
            "material: ECHO_SHARD",
            "source: AR_SHOP",
            "name: \"&dКамень возвращения\"",
            "rarity: EPIC",
            "price_ar: 300",
            "cooldown_seconds: 300",
            "effect: RETURN_STONE",
            "custom_model_data: 10026",
            "custom-texture-mode-allowed: true",
        )

    def test_infinite_torch_catalog_contract(self) -> None:
        block = item_block("infinite_torch")
        require_all(
            block,
            "material: TORCH",
            "source: AR_SHOP",
            "name: \"&dБесконечный факел\"",
            "rarity: EPIC",
            "price_ar: 100",
            "effect: INFINITE_TORCH",
            "custom_model_data: 10027",
            "custom-texture-mode-allowed: true",
        )

    def test_return_stone_is_player_scoped_and_channelled(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "RETURN_STONE",
            "getRespawnLocation()",
            "runTaskLater(this",
            "300L",
            "isSafeCompassLocation",
            "resolveMainWorld",
            "Bukkit.getWorld(\"world\")",
            "World.Environment.NORMAL",
            "keyReturnStoneCooldownUntil",
            "returnStoneChannels",
            "PlayerQuitEvent",
            "PlayerDeathEvent",
        )
        self.assertNotIn("this.actionCooldowns.put(this.actionCooldownKey(var2, var3), var6 + 300L)", source)
        death_handler = source[source.index("public void onPlayerDeath"):source.index("public void onRepairKitInteract")]
        require_all(death_handler, "cancelReturnStoneChannel(player)")

    def test_infinite_torch_restores_only_after_successful_placement(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "INFINITE_TORCH",
            "BlockPlaceEvent",
            "event.canBuild()",
            "restoreInfiniteTorchAfterSuccessfulPlacement",
            "getUniqueId()",
            "setMaxStackSize(1)",
            "BlockDispenseEvent",
            "InventoryMoveItemEvent",
            "InventoryCreativeEvent",
            "onUtilityArtifactCreative",
        )
        placement_start = source.index("restoreInfiniteTorchAfterSuccessfulPlacement")
        self.assertIn("containsUniqueItem", source[placement_start:])

    def test_utility_items_have_anti_duplication_boundaries(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "hasUtilityArtifactIngredient",
            "onPrepareItemCraft",
            "onPrepareSmithing",
            "onPrepareGrindstone",
            "onInfiniteTorchMerge",
            "onInfiniteTorchCraft",
            "onInfiniteTorchAnvil",
            "onInfiniteTorchGrindstone",
            "onInfiniteTorchSmithing",
            "onInfiniteTorchInventoryMove",
            "onInfiniteTorchDispense",
            "getNewItems()",
            "onReturnStoneDamage",
            "onReturnStoneDrop",
            "onReturnStoneClick",
            "onReturnStoneDrag",
            "cancelReturnStoneChannel",
        )

    def test_return_stone_is_not_a_vanilla_processing_ingredient(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        craft_handler = source[source.index("public void onPrepareItemCraft"):source.index("public void onPrepareAnvil")]
        require_all(craft_handler, "hasUtilityArtifactIngredient", "setResult(null)")


if __name__ == "__main__":
    unittest.main()
