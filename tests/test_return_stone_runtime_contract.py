"""Regression contracts for return-stone activation and restart safety."""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


def require_all(text: str, *needles: str) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"missing markers: {missing}")


class ReturnStoneRuntimeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_return_stone_claims_the_interaction_before_deferred_channel(self) -> None:
        branch_start = self.source.index('if ("RETURN_STONE".equals(var4))')
        branch_end = self.source.index("// The ability owns the interaction", branch_start)
        branch = self.source[branch_start:branch_end]
        require_all(
            branch,
            "var1.setUseItemInHand(Event.Result.DENY)",
            "var1.setUseInteractedBlock(Event.Result.DENY)",
            "var1.setCancelled(true)",
        "beginReturnStoneChannel(var2, hand, held, var3)",
        )
        self.assertNotIn("if (!var1.isCancelled())", branch)

    def test_return_stone_completion_is_identity_and_cooldown_bound(self) -> None:
        completion = self.source[
            self.source.index("private void completeReturnStoneChannel") : self.source.index("private Location findReturnStoneDestination")
        ]
        require_all(
            completion,
            "authenticCatalogItem(held, player, \"return_stone_complete\")",
            "Objects.equals(channel.uniqueItemId, this.uniqueItemIdOf(held))",
            "keyReturnStoneCooldownUntil",
            "this.now() + 300L",
            "player.teleport(destination)",
        )

    def test_return_stone_destination_checks_loaded_safe_candidates(self) -> None:
        destination = self.source[
            self.source.index("private Location findSafeReturnStoneLocation") : self.source.index("private void cancelReturnStoneChannel")
        ]
        require_all(
            destination,
            "world.isChunkLoaded(candidate.getBlockX() >> 4, candidate.getBlockZ() >> 4)",
            "world.getWorldBorder().isInside(candidate)",
            "isSafeCompassLocation(candidate)",
            "getMinHeight()",
            "getMaxHeight()",
        )

    def test_restart_authentication_has_a_durable_binding_fallback(self) -> None:
        require_all(
            self.source,
            "loadInstanceCache()",
            "refreshOfficialBindingAsync",
            "ensureOfficialBindingAvailable",
            "artifact_item_instances",
            "status IN ('DELIVERED','ACTIVE')",
        )


if __name__ == "__main__":
    unittest.main()
