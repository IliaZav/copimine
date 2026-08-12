"""Regression contracts for transferable custom artifacts.

The purchase/entitlement owner must remain durable for reclaim and audit, while
the current holder may change when a dropped artifact is picked up by another
player.  Gameplay authentication must not confuse those two identities.
"""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


class ArtifactOwnershipTransferContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_pickup_updates_holder_without_overwriting_entitlement_owner(self) -> None:
        source = self.source
        for marker in (
            'new NamespacedKey(this, "artifact_holder_uuid")',
            "keyHolderUuid",
            "public void onOfficialArtifactPickup(EntityPickupItemEvent event)",
            "public void onOfficialArtifactDrop(PlayerDropItemEvent event)",
            "transferArtifactHolderAfterPickup",
            "event.isCancelled()",
            "Bukkit.getScheduler().runTask(this",
        ):
            self.assertIn(marker, source)

        create_start = source.index("private ItemStack createOfficialItem")
        create_body = source[create_start : source.index("private void decorateNarcoticRecipeBook", create_start)]
        self.assertIn("keyOwnerUuid", create_body)
        self.assertIn("keyHolderUuid", create_body)

    def test_authentication_keeps_instance_binding_but_does_not_lock_use_to_purchase_owner(self) -> None:
        source = self.source
        start = source.index("private CopiMineArtifacts.CatalogItem authenticCatalogItem")
        body = source[start : source.index("private void ensureOfficialBindingAvailable", start)]
        self.assertIn("var13.itemId()", body)
        self.assertIn("var13.ownerUuid().equalsIgnoreCase(var12)", body)
        self.assertNotIn("ownerBound && (", body)
        self.assertNotIn("ownerBound && !var13.ownerUuid().equalsIgnoreCase(var12)", body)

    def test_original_owner_is_still_used_by_durable_donation_identity(self) -> None:
        source = self.source
        start = source.index("private OfficialDonationRef rawDonationIdentity")
        body = source[start : source.index("private OfficialDonationRef foreignDonationRef", start)]
        self.assertIn("keyOwnerUuid", body)
        self.assertIn("binding.ownerUuid()", body)
        self.assertIn("ownerText", body)
        self.assertIn("holderUuidOf", self.source)


if __name__ == "__main__":
    unittest.main()
