"""TDD contracts for the physical CopiMine Repair Kit.

The tests intentionally describe the requested behavior before the production
catalog entry and implementation exist.  They stay Bukkit-free except for the
standalone pure Java math seam, so the RED/GREEN cycle is deterministic.
"""

from __future__ import annotations

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
MATH_SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "RepairKitMath.java"
MATH_TEST = ROOT / "tests" / "RepairKitMathTest.java"


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


class RepairKitCatalogContractTest(unittest.TestCase):
    def test_catalog_has_exact_repair_kit_contract(self) -> None:
        block = item_block("repair_kit")
        require_all(
            block,
            "material: SHEARS",
            "source: AR_SHOP",
            'name: "&dРемкомплект"',
            "rarity: EPIC",
            "price_ar: 10",
            "effect: REPAIR_KIT",
            "custom_model_data: 0",
            "lore:",
            "Чинит обычный предмет в другой руке",
        )

    def test_catalog_keeps_repair_kit_stack_safe(self) -> None:
        block = item_block("repair_kit")
        self.assertNotIn("source: DONATION_SHOP", block)
        self.assertNotIn("max-stack: 2", block)
        self.assertNotIn("max_stack: 2", block)


class RepairKitSourceContractTest(unittest.TestCase):
    def test_source_has_persistent_five_use_and_real_durability_state(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "keyRepairKitUses",
            '"REPAIR_KIT"',
            "RepairKitMath.MAX_USES",
            "RepairKitMath.repairedDamage",
            "RepairKitMath.remainingUsesAfterSuccess",
            "RepairKitMath.canRepair",
            "Damageable",
            "setMaxDamage",
            "setMaxStackSize(1)",
            "setDamage",
            "setLore",
            "Осталось использований",
        )

    def test_source_has_safe_hand_and_custom_item_guards(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "EquipmentSlot.HAND",
            "EquipmentSlot.OFF_HAND",
            "getItemInOffHand",
            "setItemInOffHand",
            "isRepairKitItem",
            "hasCopiMineOfficialMetadata",
            "artifact_item_id",
            "artifact_unique_item_id",
            "narcotic_id",
            "ar_serial",
            "isOfficialArtifactItem(target)",
            "isOfficialDonationItem(target)",
        )

    def test_source_has_no_vanilla_processing_or_duplication_bypass(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "PlayerItemMendEvent",
            "PrepareAnvilEvent",
            "PrepareGrindstoneEvent",
            "PrepareSmithingEvent",
            "PrepareItemCraftEvent",
            "InventoryDragEvent",
            "getOldCursor()",
            "getNewItems()",
            "BlockDispenseEvent",
            "InventoryMoveItemEvent",
            "ItemMergeEvent",
            "onRepairKit",
            "hasRepairKitIngredient",
            "setResult(null)",
            "getAmount() != 1",
            "new ItemStack(Material.AIR)",
        )

    def test_paid_artifact_repair_cannot_reset_repair_kit(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        execute = source[source.index("private void executeRepair"):source.index("private void persistRepair")]
        open_repair = source[source.index("private void openRepair"):source.index("private void executeRepair")]
        require_all(execute, "isRepairKitItem", "REPAIR_KIT")
        require_all(open_repair, "isRepairKitItem", "REPAIR_KIT")


class RepairKitMathContractTest(unittest.TestCase):
    def test_math_seam_compiles_and_runs(self) -> None:
        self.assertTrue(MATH_SOURCE.exists(), "repair kit math seam is missing")
        self.assertTrue(MATH_TEST.exists(), "repair kit math test is missing")
        with tempfile.TemporaryDirectory() as output:
            result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", output, str(MATH_SOURCE), str(MATH_TEST)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            run = subprocess.run(
                ["java", "-cp", output, "RepairKitMathTest"],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)


if __name__ == "__main__":
    unittest.main()
