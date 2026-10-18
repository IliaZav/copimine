"""Contract tests for durable handling of Creative donation-item deletion."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = (
    ROOT
    / "copimine-artifacts"
    / "src"
    / "me"
    / "copimine"
    / "artifacts"
    / "CopiMineArtifacts.java"
)


class DonationCreativeLossContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_creative_deletion_handler_is_durable_first_and_preserves_item_on_failure(self) -> None:
        source = self.source
        body = self.method_body("public void onDonationCreative(InventoryCreativeEvent event)")

        self.assertIn("event.getRawSlot() >= 0", body)
        self.assertIn("candidate = event.getCursor();", body)
        self.assertIn("candidate = event.getCurrentItem();", body)
        self.assertIn("event.setCancelled(true);", body)
        self.assertIn("this.handleCreativeDonationLoss(event, player, ref);", body)

        loss = self.method_body(
            "private void handleCreativeDonationLoss(InventoryCreativeEvent event, Player player, OfficialDonationRef ref)"
        )
        self.assertIn('this.recordDonationLossOnce(ref, "creative-delete")', loss)
        self.assertIn("player.updateInventory();", loss)
        self.assertIn("return;", loss)
        self.assertIn("this.flushPendingDonationLossJournalAsync();", loss)
        self.assertLess(loss.index("recordDonationLossOnce(ref"), loss.index("flushPendingDonationLossJournalAsync();"))
        self.assertNotIn("setCursor", loss)
        self.assertNotIn("setCurrentItem", loss)

        journal_start = source.index("private boolean recordDonationLossJournal")
        journal = source[journal_start : source.index("/** Append and fsync", journal_start)]
        self.assertLess(
            journal.index("appendDonationLossJournalDurable(payload)"),
            journal.index("removeDonationInstanceFromOnlineInventories(uniqueId)"),
        )
        durable_start = source.index("private boolean appendDonationLossJournalDurable")
        durable = source[durable_start : source.index("private CompletableFuture", durable_start)]
        self.assertIn("channel.force(true);", durable)

    def method_body(self, signature: str) -> str:
        opening = self.source.index("{", self.source.index(signature))
        depth = 0
        for index in range(opening, len(self.source)):
            if self.source[index] == "{":
                depth += 1
            elif self.source[index] == "}":
                depth -= 1
                if depth == 0:
                    return self.source[opening + 1 : index]
        self.fail(f"unterminated method: {signature}")

    def test_creative_loss_is_scoped_to_outside_window_delete_and_preserves_normal_moves(self) -> None:
        source = self.source
        handler = self.method_body("public void onDonationCreative(InventoryCreativeEvent event)")
        self.assertIn("event.getRawSlot() >= 0", handler)
        self.assertIn("this.customShopItemsAreVanilla()", handler)
        self.assertIn("this.officialDonationRef(candidate)", handler)
        self.assertIn("event.setCancelled(true);", handler)

        loss = self.method_body(
            "private void handleCreativeDonationLoss(InventoryCreativeEvent event, Player player, OfficialDonationRef ref)"
        )
        self.assertIn('this.recordDonationLossOnce(ref, "creative-delete")', loss)
        self.assertIn("this.flushPendingDonationLossJournalAsync();", loss)
        # A raw-slot click inside the player's inventory is intentionally left
        # to the existing item movement/drop and entity-loss handlers.
        self.assertNotIn("event.getClick() == ClickType.DROP", handler)
        self.assertNotIn("event.getClick() == ClickType.CONTROL_DROP", handler)



if __name__ == "__main__":
    unittest.main()
