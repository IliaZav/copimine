from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = (
    ROOT
    / "copimine-artifacts"
    / "src"
    / "me"
    / "copimine"
    / "artifacts"
    / "CopiMineArtifacts.java"
).read_text(encoding="utf-8")


def block(text: str, start: str, end: str) -> str:
    start_at = text.index(start)
    return text[start_at : text.index(end, start_at)]


class CompassTeleportContractTest(unittest.TestCase):
    def test_compass_has_hard_fifty_block_limit(self) -> None:
        self.assertIn("MAX_COMPASS_TELEPORT_DISTANCE = 50.0D", SOURCE)
        distance = block(
            SOURCE,
            "private double compassTeleportDistance(Player player)",
            "private boolean isLoadedCompassChunk",
        )
        self.assertIn("return MAX_COMPASS_TELEPORT_DISTANCE;", distance)
        self.assertNotIn("viewDistanceChunks * 16.0D", distance)
        self.assertNotIn("Math.max(MAX_COMPASS_TELEPORT_DISTANCE", distance)

    def test_compass_destination_cannot_cross_the_first_block_hit(self) -> None:
        compass = block(
            SOURCE,
            "private boolean activateLootCompass(Player player, ItemStack ignored)",
            "private double compassTeleportDistance(Player player)",
        )
        self.assertIn("FluidCollisionMode.ALWAYS", compass)
        self.assertIn("hit.getHitPosition()", compass)
        self.assertIn("findSafeCompassLocation(origin, requested, maxDistance, hit != null)", compass)

        safe_location = block(
            SOURCE,
            "private Location findSafeCompassLocation(",
            "private boolean isSafeCompassLocation",
        )
        self.assertIn("boolean blockedByRay", safe_location)
        self.assertIn("hasClearCompassLine(origin, candidate)", safe_location)

    def test_compass_cooldown_is_checked_before_use_and_persisted(self) -> None:
        interaction = block(
            SOURCE,
            "public void onArtifactInteract(PlayerInteractEvent",
            "public void onArtifactDefend",
        )
        self.assertIn('"LOOT_COMPASS".equals(var4)', interaction)
        self.assertIn("actionCooldownUntil(var2, var3)", interaction)
        self.assertIn('"LOOT_COMPASS".equals(var4)\n                     ? COMPASS_COOLDOWN_SECONDS', interaction)
        self.assertIn("this.storeActionCooldown(var2, var3, var6 + cooldownSeconds)", interaction)

        cooldown = block(
            SOURCE,
            "private long actionCooldownUntil(Player player, CopiMineArtifacts.CatalogItem item)",
            "private void sendCooldownMessage",
        )
        self.assertIn('"LOOT_COMPASS".equalsIgnoreCase(item.effect())', cooldown)
        self.assertIn("keyCompassCooldownUntil", cooldown)
        self.assertIn("PersistentDataType.LONG", cooldown)


if __name__ == "__main__":
    unittest.main()
