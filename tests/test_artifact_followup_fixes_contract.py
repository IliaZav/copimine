"""Regression contracts for the follow-up artifact fixes."""

from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
ADMIN_SOURCE = ROOT / "copimine-admin-plugin" / "src" / "me" / "copimine" / "ultimateplus" / "CopiMineUltimateAdminPlus.java"


class ArtifactFollowupFixesContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.source = SOURCE.read_text(encoding="utf-8")
        self.items = ITEMS.read_text(encoding="utf-8")
        self.admin_source = ADMIN_SOURCE.read_text(encoding="utf-8")

    def test_night_cloak_keeps_a_twenty_second_floor_with_ten_second_refreshes(self) -> None:
        self.assertIn("runTaskTimer(this, this::tickNightCloak, 200L, 200L)", self.source)
        self.assertIn("PotionEffectType.SPEED, 0, 600", self.source)
        self.assertIn("PotionEffectType.NIGHT_VISION, 0, 600", self.source)
        self.assertIn("PotionEffectType.INVISIBILITY, 0, 600", self.source)

    def test_night_cloak_lore_mentions_invisibility(self) -> None:
        cloak = self.items[self.items.index("    - item-id: night_cloak\n") :]
        self.assertIn("невидимость", cloak.lower())

    def test_gravedigger_lore_contains_the_one_shot_return_command(self) -> None:
        block = self.items[self.items.index("  - id: gravedigger_contract\n") :]
        block = block[: block.index("  - id:", len("  - id: gravedigger_contract\n"))]
        self.assertIn("/cmartifacts grave", block)

    def test_gravedigger_notifies_the_player_who_died_with_the_contract(self) -> None:
        death = self.source[self.source.index("private void captureGravediggerContract") : self.source.index("private boolean hasPendingGrave")]
        self.assertIn("sendMessage", death)
        self.assertIn("/cmartifacts grave", death)

    def test_gravedigger_consumption_survives_keep_inventory_and_respawn_restore(self) -> None:
        self.assertIn("keyGraveConsumedUniqueItemId", self.source)
        self.assertIn("purgeConsumedGravediggerContract", self.source)
        self.assertIn("PlayerRespawnEvent", self.source)
        self.assertIn("scheduleGravediggerContractPurge", self.source)
        self.assertIn("runTaskLater(this, () -> this.purgeConsumedGravediggerContract(player, false), 25L)", self.source)
        self.assertIn("runTaskLater(this, () -> this.removeAllGravediggerContracts(player), 40L)", self.source)
        death = self.source[self.source.index("private void captureGravediggerContract") : self.source.index("private boolean hasPendingGrave")]
        self.assertNotIn("|| this.hasPendingGrave(player)", death)
        self.assertIn("event.getDrops().removeIf", death)

    def test_gravedigger_death_cleanup_uses_identity_only_contract_recognition(self) -> None:
        self.assertIn("gravediggerContractItem", self.source)
        death = self.source[self.source.index("private void captureGravediggerContract") : self.source.index("private boolean hasPendingGrave")]
        self.assertIn("this.gravediggerContractItem(stack)", death)
        self.assertIn("this.gravediggerContractItem(offhand)", death)

    def test_gravedigger_final_death_handler_removes_all_contract_drops_after_other_listeners(self) -> None:
        self.assertIn("onGravediggerDeathFinalizer", self.source)
        finalizer = self.source[
            self.source.index("public void onGravediggerDeathFinalizer") :
            self.source.index("private void scheduleGravediggerContractPurge")
        ]
        self.assertIn("priority = EventPriority.MONITOR", finalizer)
        self.assertIn("event.getDrops().removeIf", finalizer)
        self.assertIn("this.gravediggerContractItem(stack)", finalizer)
        self.assertIn("removeAllGravediggerContracts", finalizer)

    def test_gravedigger_contract_item_entity_is_cancelled_before_it_can_enter_world(self) -> None:
        self.assertIn("import org.bukkit.event.entity.ItemSpawnEvent;", self.source)
        annotation_start = self.source.rfind("@EventHandler", 0, self.source.index("public void onGravediggerContractItemSpawn"))
        handler = self.source[
            annotation_start :
            self.source.index("private void scheduleGravediggerContractPurge")
        ]
        self.assertIn("priority = EventPriority.HIGHEST", handler)
        self.assertIn("isGravediggerContractStack", handler)
        self.assertIn("event.setCancelled(true)", handler)
        self.assertIn("event.getEntity().remove()", handler)

    def test_gravedigger_contract_entity_cannot_be_picked_up_if_a_legacy_copy_exists(self) -> None:
        handler = self.source[
            self.source.index("public void onGravediggerContractItemPickup") :
            self.source.index("private void scheduleGravediggerContractPurge")
        ]
        self.assertIn("EntityPickupItemEvent", handler)
        self.assertIn("isGravediggerContractStack", handler)
        self.assertIn("event.setCancelled(true)", handler)
        self.assertIn("event.getItem().remove()", handler)

    def test_gravedigger_removes_consumed_instance_from_drops_and_items_to_keep(self) -> None:
        self.assertIn("consumedGravediggerUniqueItemId", self.source)
        self.assertIn("event.getItemsToKeep().removeIf", self.source)
        self.assertIn("event.getDrops().removeIf", self.source)
        self.assertIn("this.isConsumedGravediggerContract(stack, consumedUniqueItemId)", self.source)

    def test_gravedigger_death_without_contract_invalidates_old_pending_return(self) -> None:
        capture = self.source[
            self.source.index("private void captureGravediggerContract") :
            self.source.index("private void purgeConsumedGravediggerContract")
        ]
        self.assertIn("this.clearPendingGrave(player)", capture)
        self.assertIn("this.clearConsumedGravediggerMarker(player)", capture)
        self.assertIn("return;", capture)

    def test_gravedigger_command_requires_the_consumed_instance_marker_and_is_one_shot(self) -> None:
        command = self.source[
            self.source.index("private void handleGraveCommand") :
            self.source.index("@EventHandler", self.source.index("private void handleGraveCommand"))
        ]
        self.assertIn("consumedGravediggerUniqueItemId", command)
        self.assertIn("clearPendingGrave", command)
        self.assertIn("clearConsumedGravediggerMarker", command)

    def test_admin_plus_does_not_queue_or_restore_gravedigger_contracts(self) -> None:
        self.assertIn("isGravediggerContract", self.admin_source)
        death = self.admin_source[
            self.admin_source.index("public void onOfficialItemDeath") :
            self.admin_source.index("public void onOfficialItemRespawn")
        ]
        restore = self.admin_source[
            self.admin_source.index("private void restorePendingOfficialItems") :
            self.admin_source.index("private boolean isProtectedOfficialItem")
        ]
        self.assertIn("isGravediggerContract(drop)", death)
        self.assertIn("isGravediggerContract(item)", restore)

    def test_denied_cooldown_restores_only_a_real_ammo_delta_for_all_projectiles(self) -> None:
        handler = self.source[self.source.index("public void onCrossbowArtifactShot") : self.source.index("private void markCombatProjectile")]
        denied = handler[handler.index("if (!decision.allowed())") : handler.index("if (explosiveMultishot)")]
        self.assertIn("getArrowItem()", handler)
        self.assertIn("countMatchingAmmo", handler)
        for marker in (
            "scheduleCooldownAmmoRestore",
            "setConsumeArrow(false)",
            "setConsumeItem(false)",
            "setCancelled(true)",
        ):
            self.assertIn(marker, denied)

    def test_bow_cooldown_is_blocked_before_vanilla_can_consume_ammo(self) -> None:
        self.assertIn("onArtifactBowCooldownInteract", self.source)
        handler = self.source[
            self.source.index("public void onArtifactBowCooldownInteract") :
            self.source.index("public void onCrossbowArtifactShot")
        ]
        self.assertIn("setUseItemInHand(Event.Result.DENY)", handler)
        self.assertIn("setUseInteractedBlock(Event.Result.DENY)", handler)
        self.assertIn("event.setCancelled(true)", handler)
        self.assertIn("actionCooldownUntil(player, weapon)", handler)


if __name__ == "__main__":
    unittest.main()
