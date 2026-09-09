"""Regression contracts for the restart, transport, and combat edge cases."""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


class ArtifactRuntimeRegressionContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_player_crafting_view_is_never_treated_as_external_transport(self) -> None:
        source = self.source
        self.assertIn("isPlayerCraftingInventory", source)
        helper_start = source.index("private boolean isPlayerCraftingInventory")
        helper = source[helper_start : helper_start + 700]
        self.assertIn("InventoryType.CRAFTING", helper)
        self.assertIn("holder == null", helper)
        for method in ("shouldBlockInfiniteTorchContainerTransfer",):
            start = source.index("private boolean " + method)
            next_method = source.find("private ", start + 10)
            body = source[start : next_method if next_method >= 0 else start + 1200]
            self.assertIn("isPlayerCraftingInventory", body, method)

    def test_physical_pending_item_is_finalized_after_restart_without_reissuing_it(self) -> None:
        source = self.source
        self.assertIn('readPendingByStatus(var2.toString(), "PENDING")', source)
        self.assertIn("finalizePendingPhysicalDelivery", source)
        self.assertIn("autoClaimPendingDeliveries", source)
        self.assertIn("status='PENDING'", source)
        self.assertIn("status='CLAIMED'", source)
        self.assertIn("playerHasOfficialInstance", source)

    def test_auto_claim_does_not_run_before_authme_login(self) -> None:
        source = self.source
        auto_claim_start = source.index("private void autoClaimPendingDeliveries")
        auto_claim = source[auto_claim_start : source.index("private void reconcilePendingPhysicalDeliveries", auto_claim_start)]
        self.assertIn("isAuthenticatedByAuthMe", auto_claim)
        self.assertIn("runTaskLater", auto_claim)
        self.assertIn("Class.forName(\"fr.xephi.authme.api.v3.AuthMeApi\")", source)

    def test_return_stone_retries_admission_after_binding_refresh(self) -> None:
        source = self.source
        self.assertIn("retryReturnStoneAfterBindingRefresh", source)
        self.assertIn('"return_stone_retry"', source)
        self.assertIn("bindingRefreshInFlight", source)
        self.assertIn("beginReturnStoneChannel", source)

    def test_return_stone_does_not_install_inventory_transport_guards(self) -> None:
        source = self.source
        for marker in (
            "onReturnStoneInventoryMove",
            "onReturnStoneInventoryTransportClick",
            "onReturnStoneInventoryDrag",
            "shouldBlockReturnStoneContainerTransfer",
            "isReturnStonePlayerInventoryClick",
            "isReturnStonePlayerInventoryDrag",
        ):
            self.assertNotIn(marker, source)

    def test_angel_wings_is_reusable_and_expires_without_sticking_flight(self) -> None:
        source = self.source
        self.assertIn("AngelWingsFlightPolicy", source)
        self.assertIn("ANGEL_WINGS_FLIGHT_TICKS", source)
        self.assertIn("setAllowFlight(true)", source)
        self.assertIn("setFlying(true)", source)
        self.assertIn("finishAngelWingsFlight", source)
        self.assertIn("onQuit", source)
        self.assertIn("keyAngelWingsCooldownUntil", source)
        # Angel wings no longer use the retired AngelSeal death workflow.
        # PlayerDeathEvent remains valid for the independent gravedigger
        # contract, so it must not be treated as an obsolete marker.
        for retired in ("AngelSeal", "ANGEL_SEAL"):
            self.assertNotIn(retired, source)

    def test_adminplus_does_not_queue_artifacts_again_on_the_same_death(self) -> None:
        admin = (ROOT / "copimine-admin-plugin" / "src" / "me" / "copimine" / "ultimateplus" / "CopiMineUltimateAdminPlus.java").read_text(encoding="utf-8")
        start = admin.index("public void onOfficialItemDeath(PlayerDeathEvent e)")
        death = admin[start : admin.index("public void onOfficialItemRespawn", start)]
        self.assertIn("if(artifactsCoreOwns(drop)) continue;", death)
        self.assertIn("pendingOfficialReturns", death)

    def test_normal_loss_handler_ignores_cancelled_deaths(self) -> None:
        source = self.source
        start = source.index("public void onDeath(PlayerDeathEvent")
        header = source[source.rfind("@EventHandler", 0, start) : start]
        body = source[start : source.index("private boolean isDonationCatalogItem", start)]
        self.assertIn("ignoreCancelled = true", header)
        self.assertIn("var1.isCancelled()", body)

    def test_grounded_streamer_arc_stays_in_the_ten_block_envelope(self) -> None:
        source = ROOT / "tests" / "CombatArtifactMathTest.java"
        text = source.read_text(encoding="utf-8")
        self.assertIn("testGroundedStreamerKnockbackUsesTheSameEnvelope", text)


if __name__ == "__main__":
    unittest.main()
