"""Regression contracts for the corrected scythe and shield combat rules."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
SOURCE = (
    ROOT
    / "copimine-artifacts"
    / "src"
    / "me"
    / "copimine"
    / "artifacts"
    / "CopiMineArtifacts.java"
)
ECONOMY_SOURCE = (
    ROOT
    / "copimine-economy-core"
    / "src"
    / "me"
    / "copimine"
    / "economycore"
    / "CopiMineEconomyCore.java"
)


def donation_item_block(item_id: str) -> str:
    text = ITEMS.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^    - item-id: {re.escape(item_id)}\s*$.*?(?=^    - item-id:|\Z)",
        text,
    )
    if not match:
        raise AssertionError(f"missing donation catalog item {item_id}")
    return match.group(0)


class KosaShieldFollowupContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.economy_source = ECONOMY_SOURCE.read_text(encoding="utf-8")

    def test_kosa_has_the_requested_independent_proc_chances(self) -> None:
        self.assertIn("AR_THEFT_PROC_CHANCE = 0.025D", self.source)
        self.assertIn("KOSA_HEALTH_PROC_CHANCE = 0.30D", self.source)
        self.assertIn("KOSA_HUNGER_PROC_CHANCE = 0.20D", self.source)
        self.assertIn("KOSA_WITHER_PROC_CHANCE = 0.20D", self.source)
        self.assertIn("KOSA_BLINDNESS_PROC_CHANCE = 0.10D", self.source)

        combat = self.source[
            self.source.index('if ("NALOGOVAYA_KOSA".equals(var16))') : self.source.index(
                'if (this.artifactCombatEffects().contains(var16))'
            )
        ]
        self.assertEqual(combat.count("this.random.nextDouble()"), 0)
        self.assertIn("this.tryRareArTheft(var2, var10)", combat)
        self.assertIn("this.applyKosaCombatEffects(var1, var2, var10)", combat)
        self.assertNotIn("tryKosaRareDebuffs", combat)

    def test_kosa_applies_three_hp_hunger_wither_and_blindness(self) -> None:
        helper = self.source[
            self.source.index("private void applyKosaCombatEffects") : self.source.index(
                "private void sendArTheftMessages"
            )
        ]
        for marker in (
            "event.setDamage(event.getDamage() + 3.0)",
            "healPlayerCapped(attacker, 3.0)",
            "PotionEffectType.HUNGER, 40, 0",
            "PotionEffectType.WITHER, 40, 0",
            "PotionEffectType.BLINDNESS, 140, 2",
            "random.nextDouble() < KOSA_HEALTH_PROC_CHANCE",
            "random.nextDouble() < KOSA_HUNGER_PROC_CHANCE",
            "random.nextDouble() < KOSA_WITHER_PROC_CHANCE",
            "random.nextDouble() < KOSA_BLINDNESS_PROC_CHANCE",
        ):
            self.assertIn(marker, helper)
        self.assertNotIn("PotionEffectType.SLOWNESS", helper)
        self.assertNotIn("tryKosaRareDebuffs", self.source)

    def test_kosa_ar_theft_uses_a_random_amount_from_one_to_three(self) -> None:
        theft = self.source[
            self.source.index("private void tryRareArTheft") : self.source.index(
                "private void applyKosaCombatEffects"
            )
        ]
        self.assertIn("random.nextDouble() >= AR_THEFT_PROC_CHANCE", theft)
        self.assertIn("1 + random.nextInt(3)", theft)
        self.assertIn("stolenAmount", theft)
        self.assertIn("stolenAmount", theft[theft.index("stealFromPlayerAccount") :])
        self.assertIn('"Kosa 2.5% proc"', theft)
        self.assertIn("boolean fromInventory, int amount", self.source)

    def test_shield_has_independent_attacker_effects_and_real_lightning_cooldown(self) -> None:
        self.assertIn("SHIELD_NAUSEA_PROC_CHANCE = 0.10D", self.source)
        self.assertIn("SHIELD_WEAKNESS_PROC_CHANCE = 0.10D", self.source)
        self.assertIn("SHIELD_OWNER_BUFF_PROC_CHANCE = 0.01D", self.source)
        self.assertIn("SHIELD_LIGHTNING_COOLDOWN_SECONDS = 20L", self.source)
        self.assertIn("shieldLightningCooldowns", self.source)
        self.assertIn("SHIELD_EFFECT_DURATION_TICKS = 200", self.source)
        self.assertNotIn("strikeLightningEffect", self.source)

        start = self.source.index('if (var5 != null && "NOT_TODAY_SHIELD"')
        end = self.source.index("\n         }\n      }\n   }", start)
        shield = self.source[start:end]
        for marker in (
            "random.nextDouble() < SHIELD_NAUSEA_PROC_CHANCE",
            "random.nextDouble() < SHIELD_WEAKNESS_PROC_CHANCE",
            "PotionEffectType.NAUSEA, SHIELD_EFFECT_DURATION_TICKS, 2",
            "PotionEffectType.WEAKNESS, SHIELD_EFFECT_DURATION_TICKS, 2",
            "attacker.getWorld().strikeLightning(attacker.getLocation())",
            "current + SHIELD_LIGHTNING_COOLDOWN_SECONDS",
            "random.nextDouble() < SHIELD_OWNER_BUFF_PROC_CHANCE",
            "PotionEffectType.REGENERATION, SHIELD_EFFECT_DURATION_TICKS, 1",
            "PotionEffectType.SPEED, SHIELD_EFFECT_DURATION_TICKS, 0",
        ):
            self.assertIn(marker, shield)
        self.assertNotIn("PotionEffectType.RESISTANCE", shield)
        self.assertNotIn("var2.getWorld().spawnParticle", shield)

    def test_shield_catalog_exposes_the_twenty_second_cooldown(self) -> None:
        block = donation_item_block("ne_segodnya_suka_shield")
        self.assertIn("cooldown-seconds: 20", block)

    def test_target_debuffs_force_refresh_when_the_same_effect_is_already_active(self) -> None:
        """A second proc must refresh the debuff instead of being silently ignored."""
        helper_start = self.source.index("private void applyCombatEffect")
        helper_end = self.source.index("private void sendArTheftMessages")
        combat_effect_helper = self.source[helper_start:helper_end]
        self.assertIn(
            "target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, false, true), true)",
            combat_effect_helper,
        )

        kosa = self.source[self.source.index("private void applyKosaCombatEffects") : helper_end]
        for effect in ("HUNGER", "WITHER", "BLINDNESS"):
            self.assertIn(f"this.applyCombatEffect(target, PotionEffectType.{effect}", kosa)

        shield_start = self.source.index('if (var5 != null && "NOT_TODAY_SHIELD"')
        shield_end = self.source.index("\n         }\n      }\n   }", shield_start)
        shield = self.source[shield_start:shield_end]
        for effect in ("NAUSEA", "WEAKNESS"):
            self.assertIn(f"this.applyCombatEffect(attacker, PotionEffectType.{effect}", shield)

    def test_kosa_theft_destination_must_be_the_attackers_player_account(self) -> None:
        theft_start = self.economy_source.index("private TxnResult stealFromPlayerAccount")
        theft_end = self.economy_source.index(
            "private CompletableFuture<TxnResult> chargeTreasuryWithPinAsync", theft_start
        )
        theft = self.economy_source[theft_start:theft_end]

        self.assertIn("String toId = bankAccountId(toUuid.toString());", theft)
        self.assertIn("String resolvedToId = string(toAccount.get(\"account_id\"));", theft)
        self.assertIn("!toId.equalsIgnoreCase(resolvedToId)", theft)
        self.assertIn("!\"PLAYER\".equalsIgnoreCase(resolvedToType)", theft)
        self.assertIn("TREASURY_ACCOUNT_ID.equalsIgnoreCase(resolvedToId)", theft)
        self.assertIn(
            "UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",
            theft,
        )
        self.assertIn("toAfter, when, resolvedToId", theft)
        self.assertNotIn("treasuryAccountId()", theft)


if __name__ == "__main__":
    unittest.main()
