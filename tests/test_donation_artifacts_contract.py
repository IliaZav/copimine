from __future__ import annotations

import re
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
JAVA = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


EXPECTED = {
    "batin_remen_sudnogo_dnya": (500, "BATIN_REMEN", "NETHERITE_AXE"),
    "nu_ty_i_nakopal_blyat_pickaxe": (400, "NAKOPAL_PICKAXE", "NETHERITE_PICKAXE"),
    "kosa_nalogovoy_inspekcii": (200, "NALOGOVAYA_KOSA", "NETHERITE_HOE"),
    "kaska_prorab_huev": (50, "PRORAB_HELMET", "NETHERITE_HELMET"),
    "mne_pohuy_ya_v_tanke_vest": (150, "TANK_VEST", "NETHERITE_CHESTPLATE"),
    "ne_segodnya_suka_shield": (50, "NOT_TODAY_SHIELD", "SHIELD"),
    "pohuy_na_debaffy_amulet": (150, "DEBUFF_AMULET", "HEART_OF_THE_SEA"),
    "vremya_platit_nalogi_clock": (300, "TAX_CLOCK", "CLOCK"),
    "berserker_heart": (200, "BERSERKER_HEART", "NETHER_STAR"),
    "zhilorez_pickaxe": (250, "VEIN_MINER", "NETHERITE_PICKAXE"),
    "night_cloak": (150, "NIGHT_CLOAK", "PHANTOM_MEMBRANE"),
}

OWNER_BOUND_OVERRIDES = {"night_cloak": False}


def _donation_blocks(text: str) -> dict[str, str]:
    blocks: dict[str, str] = {}
    matches = list(
        re.finditer(
            r"(?ms)^    - item-id:\s*(\S+)\s*(.*?)(?=^    - item-id:|\Z)",
            text,
        )
    )
    for match in matches:
        block = match.group(2)
        if "source: DONATION_SHOP" in block:
            blocks[match.group(1)] = block
    return blocks


def _field(block: str, name: str) -> str:
    match = re.search(rf"(?m)^      {re.escape(name)}:\s*(.+)$", block)
    assert match, f"missing donation catalog field: {name}"
    return match.group(1).strip().strip('"')


def _method_body(java: str, name: str) -> str:
    match = re.search(
        rf"(?ms)private\s+Set<String>\s+{re.escape(name)}\(\)\s*\{{(.*?)\n\s*\}}",
        java,
    )
    assert match, f"missing runtime effect set: {name}"
    return match.group(1)


def test_donation_catalog_has_one_backend_and_runtime_contract_per_item() -> None:
    blocks = _donation_blocks(ITEMS.read_text(encoding="utf-8"))
    assert set(blocks) == set(EXPECTED)

    for item_id, (price, effect, material) in EXPECTED.items():
        block = blocks[item_id]
        assert int(_field(block, "price-donation")) == price
        assert _field(block, "effect-profile-id") == effect
        assert _field(block, "base-material") == material
        assert _field(block, "source") == "DONATION_SHOP"
        assert _field(block, "enabled").lower() == "true"
        assert int(_field(block, "max-stack")) == 1
        expected_owner_bound = OWNER_BOUND_OVERRIDES.get(item_id, True)
        assert _field(block, "owner-bound").lower() == str(expected_owner_bound).lower()
        assert _field(block, "effect-description")

    java = JAVA.read_text(encoding="utf-8")
    interact = _method_body(java, "artifactInteractEffects")
    combat = _method_body(java, "artifactCombatEffects")
    defense = _method_body(java, "artifactDefenseEffects")
    assert {"DEBUFF_AMULET", "TAX_CLOCK"} <= set(re.findall(r'"([A-Z_]+)"', interact))
    assert {"BATIN_REMEN", "NAKOPAL_PICKAXE", "NALOGOVAYA_KOSA"} <= set(re.findall(r'"([A-Z_]+)"', combat))
    assert {"PRORAB_HELMET", "TANK_VEST", "NOT_TODAY_SHIELD"} <= set(re.findall(r'"([A-Z_]+)"', defense))


def test_vest_damage_reduction_is_separate_from_chance_gated_buff() -> None:
    java = JAVA.read_text(encoding="utf-8")
    match = re.search(
        r'(?ms)if \(var4 != null && "TANK_VEST"\.equalsIgnoreCase\(var4\.effect\(\)\)\) \{(.*?)(?=\n\s*if \(var5 != null && "NOT_TODAY_SHIELD")',
        java,
    )
    assert match, "TANK_VEST defense handler is missing"
    block = match.group(1)
    chance = "else if (this.rollEffectChance(var4))"
    assert chance in block
    reduction = block.index("var1.setDamage(var1.getDamage() * 0.8);")
    chance_index = block.index(chance)
    resistance = block.index("PotionEffectType.RESISTANCE")
    assert reduction < chance_index < resistance, "vest buff must be inside its configured chance branch"


def test_equipped_armor_tick_does_not_turn_vest_buff_into_a_permanent_buff() -> None:
    java = JAVA.read_text(encoding="utf-8")
    match = re.search(
        r"(?ms)private void tickEquippedArmor\(\)\s*\{(.*?)(?=\n\s*/\*\* Restores the same official AR totem)",
        java,
    )
    assert match, "equipped armor tick is missing"
    block = match.group(1)
    assert "TANK_VEST" not in block
    assert "addPotionEffect" not in block, "passive armor tick must not grant the vest's random buff"


def test_shield_debuffs_are_applied_to_direct_or_projectile_attacker() -> None:
    java = JAVA.read_text(encoding="utf-8")
    match = re.search(
        r'(?ms)if \(var5 != null && "NOT_TODAY_SHIELD"\.equalsIgnoreCase\(var5\.effect\(\)\).*?(?=\n\s*\}\n\s*\}\n\s*\}\n\s*@EventHandler)',
        java,
    )
    assert match, "custom shield defense branch is missing"
    block = match.group(0)
    assert "var1 instanceof EntityDamageByEntityEvent" in block
    assert "this.resolveDamageAttacker(var14)" in block
    attacker = re.search(r"(?ms)if \(attacker != null && attacker != var2\) \{(.*?)\n\s*\}", block)
    assert attacker, "shield must guard the resolved attacker before applying effects"
    attacker_body = attacker.group(1)
    assert "PotionEffectType.NAUSEA" in attacker_body
    assert "SHIELD_NAUSEA_PROC_CHANCE" in block
    assert "SHIELD_WEAKNESS_PROC_CHANCE" in block
    assert "PotionEffectType.WEAKNESS" in block
    assert "attacker.getWorld().strikeLightning(attacker.getLocation())" in block
    assert "SHIELD_LIGHTNING_COOLDOWN_SECONDS" in block
    assert "SHIELD_OWNER_BUFF_PROC_CHANCE" in block
    assert "PotionEffectType.REGENERATION" in block
    assert "PotionEffectType.SPEED" in block
    assert "strikeLightningEffect" not in block
    assert "var2.addPotionEffect" not in attacker_body
    resolver = re.search(
        r"(?ms)private LivingEntity resolveDamageAttacker\(EntityDamageByEntityEvent event\)\s*\{(.*?)(?=\n\s*public void onDeath)",
        java,
    )
    assert resolver, "damage attacker resolver is missing"
    resolver_body = resolver.group(1)
    assert "event.getDamager() instanceof LivingEntity" in resolver_body
    assert "projectile.getShooter() instanceof LivingEntity" in resolver_body


def test_all_custom_armor_effects_have_their_expected_runtime_hooks() -> None:
    java = JAVA.read_text(encoding="utf-8")
    defense = _method_body(java, "artifactDefenseEffects")
    assert {"PRORAB_HELMET", "TANK_VEST", "NOT_TODAY_SHIELD", "POZDNYAKOV_ACE"} <= set(
        re.findall(r'"([A-Z_]+)"', defense)
    )
    handler = re.search(r"(?ms)public void onArtifactDefend\(EntityDamageEvent var1\).*?(?=\n\s*@EventHandler)", java)
    assert handler, "armor defense event handler is missing"
    body = handler.group(0)
    assert 'DamageCause.FALL' in body and 'var1.setDamage(var1.getDamage() * 0.4)' in body
    assert 'var1.setDamage(var1.getDamage() * 0.8)' in body
    assert '"POZDNYAKOV_ACE".equalsIgnoreCase(pozdnyakovAce.effect())' in body


def test_each_donation_ability_is_wired_to_its_specific_effect_logic() -> None:
    java = JAVA.read_text(encoding="utf-8")
    interact = re.search(
        r"(?ms)public void onArtifactInteract\(PlayerInteractEvent var1\).*?(?=\n\s*private boolean triggerWindHammer)",
        java,
    )
    combat = re.search(
        r"(?ms)public void onArtifactDamage\(EntityDamageByEntityEvent var1\).*?(?=\n\s*public boolean onCommand)",
        java,
    )
    assert interact and combat
    interact_body = interact.group(0)
    combat_body = combat.group(0)

    assert 'case "DEBUFF_AMULET" -> this.cleanseAllowedDebuff(var2)' in interact_body
    assert 'this.activateTaxClock(var2, var1.getItem())' in interact_body

    batin = re.search(r'(?ms)case "BATIN_REMEN":(.*?)(?=\n\s*case "NAKOPAL_PICKAXE":)', combat_body)
    pickaxe = re.search(r'(?ms)case "NAKOPAL_PICKAXE":(.*?)(?=\n\s*case "STREAMER_STICK_ARC":)', combat_body)
    assert batin and pickaxe
    assert "strikeLightning" in batin.group(1)
    assert "PotionEffectType.SPEED" in batin.group(1)
    assert "PotionEffectType.SLOWNESS" in batin.group(1)
    assert "PotionEffectType.WEAKNESS" in batin.group(1)
    assert "applyTemporaryBurial" in pickaxe.group(1)
    assert "applyTemporaryCobwebSnare" in pickaxe.group(1)
    assert "PotionEffectType.BLINDNESS" in pickaxe.group(1)
    assert "applyKosaCombatEffects" in java
    assert "tryKosaRareDebuffs" not in combat_body
    assert "PotionEffectType.HUNGER" in java
    assert "PotionEffectType.BLINDNESS, 140, 2" in java
    assert "tryRareArTheft" in combat_body


def test_ar_and_admin_custom_armor_use_the_same_checked_defense_path() -> None:
    items = ITEMS.read_text(encoding="utf-8")
    treasurer = re.search(r"(?ms)^  - id: treasurer_chestplate\s*(.*?)(?=^  - id:|^donation-catalog:|\Z)", items)
    ace = re.search(r"(?ms)^  - id: kozyrny_tuz_pozdnyakova\s*(.*?)(?=^  - id:|^donation-catalog:|\Z)", items)
    assert treasurer and ace
    assert "material: NETHERITE_CHESTPLATE" in treasurer.group(1)
    assert "effect: TANK_VEST" in treasurer.group(1)
    assert "material: NETHERITE_LEGGINGS" in ace.group(1)
    assert "effect: POZDNYAKOV_ACE" in ace.group(1)

    java = JAVA.read_text(encoding="utf-8")
    defense = re.search(r"(?ms)public void onArtifactDefend\(EntityDamageEvent var1\).*?(?=\n\s*@EventHandler)", java)
    tick = re.search(r"(?ms)private void tickPozdnyakovAce\(\).*?(?=\n\s*/\*\* Refreshes passive armor effects)", java)
    assert defense and tick
    assert "getInventory().getHelmet()" in defense.group(0)
    assert "getInventory().getChestplate()" in defense.group(0)
    assert "getInventory().getLeggings()" in defense.group(0)
    assert "player.getInventory().getLeggings()" in tick.group(0)
    assert "POZDNYAKOV_LAVA_RADIUS" in tick.group(0)


def test_donation_combat_and_defense_cooldowns_message_only_the_owner() -> None:
    java = JAVA.read_text(encoding="utf-8")
    damage = re.search(
        r"(?ms)public void onArtifactDamage\(EntityDamageByEntityEvent var1\).*?(?=\n\s*public boolean onCommand)",
        java,
    )
    assert damage, "combat artifact handler is missing"
    damage_body = damage.group(0)
    assert "sendCooldownMessage(var2, var15, var19, var8)" in damage_body

    defense = re.search(
        r"(?ms)public void onArtifactDefend\(EntityDamageEvent var1\).*?(?=\n\s*@EventHandler)",
        java,
    )
    assert defense, "defense artifact handler is missing"
    defense_body = defense.group(0)
    assert defense_body.count("sendCooldownMessage(var2,") >= 2
    assert "player.sendMessage" in re.search(
        r"(?ms)private void sendCooldownMessage\(.*?\n\s*\}", java
    ).group(0)


def test_cooldown_messages_are_not_broadcast() -> None:
    java = JAVA.read_text(encoding="utf-8")
    helper = re.search(r"(?ms)private void sendCooldownMessage\(.*?\n\s*\}", java)
    assert helper, "cooldown message helper is missing"
    body = helper.group(0)
    assert "player.sendMessage" in body
    assert "Bukkit.broadcastMessage" not in body
