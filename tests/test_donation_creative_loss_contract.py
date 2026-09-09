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
        start = source.index("private void handleCreativeDonationLoss(InventoryCreativeEvent event, Player player)")
        body = source[start : source.index("\n    private boolean isCreativeDeletion", start)]

        self.assertIn("candidate = event.getCursor();", body)
        self.assertIn("candidate = event.getCurrentItem();", body)
        self.assertIn("event.setCancelled(true);", body)
        self.assertIn("player.updateInventory();", body)
        self.assertIn("recordDonationLossOnce(ref", body)
        self.assertIn("flushPendingDonationLossJournalAsync();", body)
        self.assertIn('if (this.recordDonationLossOnce(ref, "creative"))', body)
        self.assertNotIn("setItemOnCursor", body)
        self.assertNotIn("setCursor", body)
        self.assertNotIn("setCurrentItem", body)

        self.assertLess(body.index("event.setCancelled(true);"), body.index("recordDonationLossOnce(ref"))
        self.assertLess(body.index("recordDonationLossOnce(ref"), body.index("flushPendingDonationLossJournalAsync();"))

        journal_start = source.index("private boolean recordDonationLossJournal")
        journal = source[journal_start : source.index("/** Append and fsync", journal_start)]
        self.assertLess(
            journal.index("appendDonationLossJournalDurable(payload)"),
            journal.index("removeDonationInstanceFromOnlineInventories(uniqueId)"),
        )
        durable_start = source.index("private boolean appendDonationLossJournalDurable")
        durable = source[durable_start : source.index("private CompletableFuture", durable_start)]
        self.assertIn("channel.force(true);", durable)

    def test_creative_policy_keeps_player_inventory_moves_but_catches_drop_and_outside_window(self) -> None:
        source = self.source
        start = source.index("private boolean isCreativeDeletion")
        helper = source[start : source.index("\n    @EventHandler", start)]

        self.assertIn("event.getRawSlot() < 0", helper)
        self.assertIn("event.getClick() == ClickType.DROP", helper)
        self.assertIn("event.getClick() == ClickType.CONTROL_DROP", helper)
        self.assertIn("clicked != player.getInventory()", helper)
        self.assertIn("event.getRawSlot() < view.getTopInventory().getSize()", helper)


if __name__ == "__main__":
    unittest.main()
