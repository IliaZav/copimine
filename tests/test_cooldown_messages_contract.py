from __future__ import annotations

import re
import unittest
from pathlib import Path


JAVA = Path(__file__).parents[1] / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java"


class CooldownMessageContractTest(unittest.TestCase):
    def test_sword_and_armor_cooldown_feedback_is_silent_but_other_items_remain(self) -> None:
        source = JAVA.read_text(encoding="utf-8")

        suppressor = re.search(
            r"(?ms)private boolean suppressCooldownMessage\((?:CopiMineArtifacts\.)?CatalogItem item\)\s*\{.*?(?=\n\s*private long now\()",
            source,
        )
        self.assertIsNotNone(suppressor, "a single category guard for cooldown feedback is required")
        suppressor_body = suppressor.group(0)
        self.assertIn("item.material()", suppressor_body)
        self.assertIn('endsWith("_SWORD")', suppressor_body)
        self.assertIn('endsWith("_HELMET")', suppressor_body)
        self.assertIn('endsWith("_CHESTPLATE")', suppressor_body)
        self.assertIn('endsWith("_LEGGINGS")', suppressor_body)
        self.assertIn('endsWith("_BOOTS")', suppressor_body)
        self.assertNotIn("Category.WEAPON", suppressor_body)
        self.assertNotIn("Category.ARMOR", suppressor_body)

        sender = re.search(
            r"(?ms)private void sendCooldownMessage\(Player player, (?:CopiMineArtifacts\.)?CatalogItem item, long cooldownUntil, long current\)\s*\{.*?(?=\n\s*private boolean suppressCooldownMessage)",
            source,
        )
        self.assertIsNotNone(sender, "cooldown message helper is missing")
        sender_body = sender.group(0)
        self.assertRegex(
            sender_body,
            r"if\s*\(this\.suppressCooldownMessage\(item\)\)\s*\{\s*return;\s*\}",
            "weapon/armor feedback must be suppressed before player.sendMessage",
        )
        self.assertIn("player.sendMessage", sender_body, "other artifact cooldown feedback must remain available")

        # The existing handlers must still call the shared helper: this assertion
        # protects the cooldown mechanics while only changing player-facing output.
        combat = re.search(
            r"(?ms)public void onArtifactDamage\(EntityDamageByEntityEvent var1\).*?(?=\n\s*public boolean onCommand)",
            source,
        )
        defense = re.search(
            r"(?ms)public void onArtifactDefend\(EntityDamageEvent var1\).*?(?=\n\s*@EventHandler)",
            source,
        )
        self.assertTrue(combat and "sendCooldownMessage" in combat.group(0))
        self.assertTrue(defense and "sendCooldownMessage" in defense.group(0))


if __name__ == "__main__":
    unittest.main()
