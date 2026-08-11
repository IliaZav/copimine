"""TDD contracts for replacing the broken death seal with Angel Wings."""

from __future__ import annotations

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
POLICY = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "AngelWingsFlightPolicy.java"
POLICY_TEST = ROOT / "tests" / "AngelWingsFlightPolicyTest.java"
SOURCES = ROOT / "resourcepacks" / "item_texture_sources.json"
MANIFEST = ROOT / "resourcepacks" / "models_manifest.json"
TEXTURE = ROOT / "resourcepacks" / "src" / "assets" / "copimine" / "textures" / "item" / "artifacts" / "angel_seal.png"


def item_block(item_id: str) -> str:
    text = ITEMS.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^  - id: {re.escape(item_id)}\s*$.*?(?=^  - id:|^donation-catalog:|\Z)",
        text,
    )
    if not match:
        raise AssertionError(f"missing catalog item {item_id}")
    return match.group(0)


class AngelWingsContractTest(unittest.TestCase):
    def test_catalog_replaces_seal_with_flight_artifact(self) -> None:
        block = item_block("angel_wings")
        for marker in (
            "category: TOOL",
            "material: FEATHER",
            "source: AR_SHOP",
            "custom_model_data: 10028",
            'name: "&dКрылья ангела"',
            "rarity: EPIC",
            "price_ar: 750",
            "cooldown_seconds: 300",
            "effect: ANGEL_WINGS",
            '"&7Даёт полёт на 15 секунд"',
            "max_stack_size: 1",
        ):
            self.assertIn(marker, block)
        self.assertNotIn("id: angel_seal", ITEMS.read_text(encoding="utf-8"))

    def test_policy_compiles_and_enforces_duration_cooldown_and_restore_boundary(self) -> None:
        self.assertTrue(POLICY.exists(), "AngelWingsFlightPolicy.java is missing")
        self.assertTrue(POLICY_TEST.exists(), "AngelWingsFlightPolicyTest.java is missing")
        with tempfile.TemporaryDirectory() as output:
            result = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", output, str(POLICY), str(POLICY_TEST)],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            run = subprocess.run(
                ["java", "-cp", output, "AngelWingsFlightPolicyTest"],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(run.returncode, 0, run.stdout + run.stderr)

    def test_runtime_uses_server_side_flight_and_removes_death_seal_path(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        for marker in (
            "ANGEL_WINGS",
            "setAllowFlight(true)",
            "setFlying(true)",
            "keyAngelWingsCooldownUntil",
            "runTaskTimer",
            "countdownTask",
            "countdownSecondsAtElapsed",
            "sendTitle(",
            "onQuit",
            "ANGEL_WINGS_FLIGHT_TICKS",
        ):
            self.assertIn(marker, source)
        for retired_marker in (
            "onAngelSealDeath",
            "onAngelSealDeathMonitor",
            "pendingAngelSealConsumptions",
            "ANGEL_SEAL",
            "AngelSealDeathPolicy",
        ):
            self.assertNotIn(retired_marker, source)

    def test_catalog_sync_retire_stale_seal_row(self) -> None:
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn(
            'retired.setString(2, "angel_seal")',
            source,
            "an existing database row must not keep the removed seal in the shop",
        )

    def test_existing_seal_texture_is_reused_by_wings_mapping(self) -> None:
        self.assertTrue(TEXTURE.is_file(), "the approved Angel texture must be kept")
        sources = __import__("json").loads(SOURCES.read_text(encoding="utf-8"))
        manifest = __import__("json").loads(MANIFEST.read_text(encoding="utf-8"))
        source_row = next(row for row in sources["items"] if row["id"] == "angel_wings")
        manifest_row = next(row for row in manifest["items"] if row["id"] == "angel_wings")
        self.assertEqual(source_row["custom_model_data"], 10028)
        self.assertTrue(str(source_row["source_path"]).endswith("angel_seal.png"))
        self.assertEqual(manifest_row["texture"], "copimine:item/artifacts/angel_seal")


if __name__ == "__main__":
    unittest.main()
