"""Focused release contracts for the new local-only artifact scope.

These tests intentionally describe observable catalog/runtime contracts.  They
are added before the implementation so the first run is expected to be red.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
JAVA = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
MODELS = ROOT / "resourcepacks" / "models_manifest.json"
SOURCES = ROOT / "resourcepacks" / "item_texture_sources.json"
CLOAK_POLICY = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "NightCloakPolicy.java"
VEIN_POLICY = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "VeinMinerPolicy.java"
COMMERCE = ROOT / "admin-web" / "backend" / "commerce_catalog.py"


def _block(text: str, marker: str, end: str) -> str:
    start = text.index(marker)
    end_position = text.find(end, start + len(marker))
    return text[start:] if end_position < 0 else text[start:end_position]


class NewArtifactsReleaseContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.items = ITEMS.read_text(encoding="utf-8")
        self.java = JAVA.read_text(encoding="utf-8")
        self.cloak_policy = CLOAK_POLICY.read_text(encoding="utf-8")
        self.vein_policy = VEIN_POLICY.read_text(encoding="utf-8")
        self.commerce = COMMERCE.read_text(encoding="utf-8")

    def test_new_ar_and_donation_catalog_rows_have_exact_prices_and_materials(self) -> None:
        grave = _block(self.items, "  - id: gravedigger_contract\n", "  - id:")
        signal = _block(self.items, "  - id: signal_bell\n", "  - id:")
        berserker = _block(self.items, "    - item-id: berserker_heart\n", "    - item-id:")
        zhilorez = _block(self.items, "    - item-id: zhilorez_pickaxe\n", "    - item-id:")
        cloak = _block(self.items, "    - item-id: night_cloak\n", "    - item-id:")

        self.assertIn("price_ar: 300", grave)
        self.assertIn("effect: GRAVEDIGGER_CONTRACT", grave)
        self.assertIn("price_ar: 150", signal)
        self.assertIn("effect: SIGNAL_BELL", signal)
        self.assertIn("price-donation: 200", berserker)
        self.assertIn("effect-profile-id: BERSERKER_HEART", berserker)
        self.assertIn("price-donation: 250", zhilorez)
        self.assertIn("base-material: NETHERITE_PICKAXE", zhilorez)
        self.assertIn("effect-profile-id: VEIN_MINER", zhilorez)
        self.assertIn('display-name: "&6Кирка «Жилорез»"', zhilorez)
        self.assertIn("repairable: true", zhilorez)
        self.assertIn("price-donation: 150", cloak)
        self.assertIn("owner-bound: false", cloak)
        self.assertIn("effect-profile-id: NIGHT_CLOAK", cloak)

    def test_retired_compass_is_removed_and_has_no_active_teleport_wiring(self) -> None:
        self.assertNotIn("item-id: gde_moy_lut_blyat_compass", self.items)
        interact = self.java[self.java.index("private Set<String> artifactInteractEffects") : self.java.index("private Set<String> artifactCombatEffects")]
        self.assertNotIn("LOOT_COMPASS", interact)
        self.assertNotIn('case "LOOT_COMPASS"', self.java)
        self.assertNotIn("activateLootCompass", self.java)
        self.assertIn("isRetiredArtifact", self.java)

    def test_retired_compass_is_filtered_from_site_and_admin_donation_catalogs(self) -> None:
        self.assertIn('RETIRED_DONATION_ITEM_IDS = frozenset({"gde_moy_lut_blyat_compass"})', self.commerce)
        self.assertGreaterEqual(self.commerce.count("item_id in RETIRED_DONATION_ITEM_IDS"), 2)
        self.assertIn("not bool(entry.get(\"enabled\", True))", self.commerce)

    def test_retired_compass_is_hidden_from_owned_pending_and_reclaim_gui_paths(self) -> None:
        self.assertIn("!this.isRetiredArtifact(var7)", self.java)
        self.assertIn("!this.isRetiredArtifact(var10)", self.java)
        self.assertIn("!this.isRetiredArtifact(var36)", self.java)
        self.assertGreaterEqual(self.java.count("AND item_id <> 'gde_moy_lut_blyat_compass'"), 2)

    def test_zhilorez_is_main_hand_bounded_and_uses_explicit_vein_policy(self) -> None:
        self.assertIn('"VEIN_MINER".equals', self.java)
        self.assertIn("activeVeinMiningPlayers", self.java)
        self.assertIn("VeinMinerPolicy.isWhitelisted", self.java)
        self.assertIn("VeinMinerPolicy.sameFamily", self.java)
        self.assertIn("MAX_BLOCKS = 32", self.vein_policy)
        self.assertIn("player.breakBlock", self.java)
        self.assertIn("tryVeinMine", self.java)
        self.assertIn("for (int dx = -1; dx <= 1; dx++)", self.java)
        self.assertIn("current.getRelative(dx, dy, dz)", self.java)
        self.assertIn("veinBlockKey", self.java)
        self.assertIn("if (player.breakBlock(current))", self.java)
        self.assertIn("Protection plugins and cancelled BlockBreakEvents reject", self.java)
        self.assertIn("authenticCatalogItem(player.getInventory().getItemInMainHand()", self.java)
        self.assertNotIn("name().contains(\"ORE\")", self.java)
        for material in (
            "DIAMOND_ORE",
            "DEEPSLATE_DIAMOND_ORE",
            "ANCIENT_DEBRIS",
            "NETHER_QUARTZ_ORE",
            "NETHER_GOLD_ORE",
        ):
            self.assertIn(material, self.vein_policy)
        self.assertIn("Enchantment.FORTUNE", self.java)
        self.assertIn("Enchantment.EFFICIENCY", self.java)

    def test_night_cloak_is_transferable_inventory_only_and_one_hz(self) -> None:
        self.assertIn('case "NIGHT_CLOAK"', self.java)
        self.assertIn("tickNightCloak", self.java)
        self.assertIn("runTaskTimer(this, this::tickNightCloak, 200L, 200L)", self.java)
        self.assertIn('"NORMAL".equalsIgnoreCase', self.cloak_policy)
        self.assertIn("time >= 13000L && time < 23000L", self.cloak_policy)
        self.assertIn("PotionEffectType.SPEED", self.java)
        self.assertIn("PotionEffectType.NIGHT_VISION", self.java)
        self.assertIn("findDirectPlayerInventoryArtifact", self.java)
        self.assertIn("night_cloak", self.java)
        self.assertIn("donation.ownerBound()", self.java)

    def test_grave_berserker_and_signal_are_event_wired(self) -> None:
        self.assertIn('"GRAVEDIGGER_CONTRACT"', self.java)
        self.assertIn('"grave".equalsIgnoreCase(var4[0])', self.java)
        self.assertIn("pendingGrave", self.java)
        self.assertIn('"BERSERKER_HEART"', self.java)
        self.assertIn("< maxHealth * 0.10D", self.java)
        self.assertIn("420L", self.java)
        self.assertIn('"SIGNAL_BELL"', self.java)
        self.assertIn("onSignalBellInventoryOpen", self.java)
        self.assertIn("signalBell", self.java)
        self.assertIn("current physical holder", self.java)
        self.assertIn("keyGravePendingNonce", self.java)
        self.assertIn("UUID.randomUUID().toString()", self.java)

    def test_signal_bell_rebinding_replaces_the_single_active_binding(self) -> None:
        start = self.java.index('if ("SIGNAL_BELL".equals(var4))')
        branch = self.java[start:self.java.index("// The ability owns the interaction.", start)]
        self.assertIn("this.bindSignalBell(var1.getItem(), var1.getClickedBlock());", branch)
        self.assertNotIn("signalBellIsBound(var1.getItem())", branch)

    def test_custom_models_and_texture_sources_are_unique(self) -> None:
        models = MODELS.read_text(encoding="utf-8")
        sources = SOURCES.read_text(encoding="utf-8")
        for item_id in ("gravedigger_contract", "signal_bell", "berserker_heart", "zhilorez_pickaxe", "night_cloak"):
            self.assertIn(f'"id": "{item_id}"', models)
            self.assertIn(f'"id":"{item_id}"', sources)
        cmds = [int(value) for value in re.findall(r'"custom_model_data"\s*:\s*(\d+)', models)]
        self.assertEqual(len(cmds), len(set(cmds)))

    def test_cooldown_denial_disables_actual_ammo_consumption_for_both_projectiles(self) -> None:
        self.assertIn("setConsumeArrow(false)", self.java)
        self.assertIn("setConsumeItem(false)", self.java)
        self.assertIn("CombatArtifactShotPolicy.Decision", self.java)
        self.assertIn("AR_COBBLESTONE_TRAIL", self.java)
        self.assertIn("AR_CROSSBOW_TELEPORT", self.java)
        self.assertIn("combatProjectileIdentities", self.java)


if __name__ == "__main__":
    unittest.main()
