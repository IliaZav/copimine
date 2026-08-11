"""RED contracts for the remaining server-side artifact runtime guards.

These checks are intentionally source-level: they must fail before the
corresponding production guard exists, then pass after the minimal patch.
They cover the event races that cannot be represented by the Bukkit-free math
tests alone.
"""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


def require_all(text: str, *needles: str) -> None:
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise AssertionError(f"missing markers: {missing}")


class ArtifactRuntimeGuardContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = SOURCE.read_text(encoding="utf-8")

    def test_identical_physical_pdc_copies_are_rejected_before_use(self) -> None:
        require_all(
            self.source,
            "countUniqueItemOccurrences",
            "hasDuplicatePhysicalIdentity",
            "artifact_unique_item_id",
            "if (var2 != null && this.hasDuplicatePhysicalIdentity(var2, var7))",
        )

    def test_repair_interaction_is_idempotent_for_duplicate_event_delivery(self) -> None:
        require_all(
            self.source,
            "repairKitInteractionGuards",
            "repairKitInteractionKey",
            "isDuplicateRepairKitInteraction",
            "markRepairKitInteraction",
            "Bukkit.getCurrentTick()",
        )

    def test_projectile_shot_window_is_cleaned_on_quit(self) -> None:
        quit_handler = self.source[self.source.index("public void onQuit"):self.source.index("private void tickPozdnyakovAce")]
        require_all(
            quit_handler,
            "explosiveShotWindows.keySet().removeIf",
            "actionCooldownKey",
        )

    def test_infinite_torch_restore_survives_logout_before_next_tick(self) -> None:
        require_all(
            self.source,
            "pendingInfiniteTorchRestores",
            "savePendingInfiniteTorchRestores",
            "loadPendingInfiniteTorchRestores",
            "restoreInfiniteTorchAfterSuccessfulPlacement",
            "onJoin",
            "onQuit",
        )

    def test_infinite_torch_pending_restores_keep_each_physical_instance(self) -> None:
        require_all(
            self.source,
            "Map<UUID, Map<String, ItemStack>> pendingInfiniteTorchRestores",
            "computeIfAbsent(playerUuid",
            "putIfAbsent(uniqueItemId",
        )


if __name__ == "__main__":
    unittest.main()
