"""TDD contracts for the server-side Angel Seal artifact."""

from __future__ import annotations

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
POLICY = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "AngelSealDeathPolicy.java"
POLICY_TEST = ROOT / "tests" / "AngelSealDeathPolicyTest.java"


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


class AngelSealContractTest(unittest.TestCase):
    def test_catalog_has_exact_server_side_contract(self) -> None:
        block = item_block("angel_seal")
        require_all(
            block,
            "category: TOOL",
            "material: FEATHER",
            "source: AR_SHOP",
            "custom_model_data: 0",
            "custom-texture-mode-allowed: false",
            'name: "&dПечать ангела"',
            "rarity: EPIC",
            "price_ar: 1000",
            "cooldown_seconds: 0",
            "effect: ANGEL_SEAL",
            "max_stack_size: 1",
            '"&7Сохраняет вещи после смерти"',
        )

    def test_pure_policy_compiles_and_covers_selection_and_prevention(self) -> None:
        self.assertTrue(POLICY.exists(), "AngelSealDeathPolicy.java is missing")
        self.assertTrue(POLICY_TEST.exists(), "AngelSealDeathPolicyTest.java is missing")
        with tempfile.TemporaryDirectory() as output:
            result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", output, str(POLICY), str(POLICY_TEST)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            run = subprocess.run(
                ["java", "-cp", output, "AngelSealDeathPolicyTest"],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)

    def test_real_death_path_preserves_all_player_surfaces_without_xp(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "AngelSealDeathPolicy.decide",
            "getInventory().getContents()",
            "getArmorContents()",
            "getItemInOffHand()",
            "authenticCatalogItem",
            "setKeepInventory(true)",
            "getDrops().clear()",
            "ANGEL_SEAL",
            "onAngelSealDeathMonitor",
        )
        self.assertNotIn("setKeepLevel(true)", source)
        self.assertNotIn("setDroppedExp(0)", source)

    def test_only_one_authentic_seal_is_consumed_and_exact_instance_is_checked(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        require_all(
            source,
            "findAngelSeal",
            "uniqueItemIdOf",
            "consumeAngelSeal",
            "setItemInOffHand",
            "setArmorContents",
            "getAmount() > 1",
            "setAmount(current.getAmount() - 1)",
            "isUtilityArtifactItem",
            "hasInvalidAngelSealAmount",
        )

    def test_prevented_death_and_tampering_do_not_consume(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        death = source[source.index("onAngelSealDeath") : source.index("onAngelSealDeathMonitor")]
        require_all(death, "event.isCancelled()", "event.getKeepInventory()")
        require_all(
            source,
            "EntityResurrectEvent",
            "resurrect_infinite_totem",
            "ANGEL_SEAL",
            "authenticCatalogItem",
        )

    def test_preserved_items_are_not_reclassified_as_donation_loss(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        death = source[source.index("onAngelSealDeath") : source.index("onAngelSealDeathMonitor")]
        require_all(death, "setKeepInventory(true)", "getDrops().clear()")
        self.assertNotIn("recordDonationLossJournal", death)


if __name__ == "__main__":
    unittest.main()
