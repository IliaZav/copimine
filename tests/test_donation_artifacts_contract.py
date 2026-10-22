from __future__ import annotations

import re
import importlib.util
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[1]
ITEMS = ROOT / "copimine-artifacts" / "items.yml"
JAVA = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"
COMMERCE_CATALOG = ROOT / "admin-web" / "backend" / "commerce_catalog.py"


EXPECTED = {
    "batin_remen_sudnogo_dnya": (500, "BATIN_REMEN", "NETHERITE_AXE", True),
    "nu_ty_i_nakopal_blyat_pickaxe": (400, "NAKOPAL_PICKAXE", "NETHERITE_PICKAXE", True),
    "kosa_nalogovoy_inspekcii": (200, "NALOGOVAYA_KOSA", "NETHERITE_HOE", True),
    "kaska_prorab_huev": (50, "PRORAB_HELMET", "NETHERITE_HELMET", True),
    "mne_pohuy_ya_v_tanke_vest": (150, "TANK_VEST", "NETHERITE_CHESTPLATE", True),
    "ne_segodnya_suka_shield": (50, "NOT_TODAY_SHIELD", "SHIELD", True),
    "pohuy_na_debaffy_amulet": (150, "DEBUFF_AMULET", "HEART_OF_THE_SEA", True),
    "vremya_platit_nalogi_clock": (300, "TAX_CLOCK", "CLOCK", True),
    "gde_moy_lut_blyat_compass": (100, "LOOT_COMPASS", "COMPASS", True),
    "berserker_heart": (200, "BERSERKER_HEART", "NETHER_STAR", True),
    "zhilorez_pickaxe": (250, "VEIN_MINER", "NETHERITE_PICKAXE", True),
    "donation_night_cloak": (150, "NIGHT_CLOAK", "PHANTOM_MEMBRANE", False),
}


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

    for item_id, (price, effect, material, owner_bound) in EXPECTED.items():
        block = blocks[item_id]
        assert int(_field(block, "price-donation")) == price
        assert _field(block, "effect-profile-id") == effect
        assert _field(block, "base-material") == material
        assert _field(block, "source") == "DONATION_SHOP"
        expected_enabled = item_id != "gde_moy_lut_blyat_compass"
        assert _field(block, "enabled").lower() == str(expected_enabled).lower()
        assert int(_field(block, "max-stack")) == 1
        assert _field(block, "owner-bound").lower() == str(owner_bound).lower()
        assert _field(block, "effect-description")

    java = JAVA.read_text(encoding="utf-8")
    interact = _method_body(java, "artifactInteractEffects")
    combat = _method_body(java, "artifactCombatEffects")
    defense = _method_body(java, "artifactDefenseEffects")
    assert {"DEBUFF_AMULET", "TAX_CLOCK", "LOOT_COMPASS"} <= set(re.findall(r'"([A-Z_]+)"', interact))
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
    assert "this.applyCombatEffect(attacker, PotionEffectType.NAUSEA" in block
    assert "this.applyCombatEffect(attacker, PotionEffectType.WEAKNESS" in block
    assert "attacker.getWorld().strikeLightningEffect(strikeLocation)" in block
    assert "attacker.getWorld().spawnParticle(Particle.ELECTRIC_SPARK" in block
    assert "strikeLightning(" not in block, "shield lightning must remain visual and deal no extra damage"
    assert "this.shieldLightningCooldowns" in block
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
    assert 'case "LOOT_COMPASS" -> this.activateLootCompass(var2, var1.getItem())' in interact_body

    batin = re.search(r'(?ms)case "BATIN_REMEN":(.*?)(?=\n\s*case "NAKOPAL_PICKAXE":)', combat_body)
    pickaxe = re.search(r'(?ms)case "NAKOPAL_PICKAXE":(.*?)(?=\n\s*case "NALOGOVAYA_KOSA":)', combat_body)
    kosa = re.search(r'(?ms)case "NALOGOVAYA_KOSA":(.*?)(?=\n\s*case "DUTY_ARGUMENT":)', combat_body)
    assert batin and pickaxe and kosa
    assert "strikeLightning" in batin.group(1)
    assert "PotionEffectType.SPEED" in batin.group(1)
    assert "PotionEffectType.SLOWNESS" in batin.group(1)
    assert "PotionEffectType.WEAKNESS" in batin.group(1)
    assert "applyTemporaryBurial" in pickaxe.group(1)
    assert "applyTemporaryCobwebSnare" in pickaxe.group(1)
    assert "PotionEffectType.BLINDNESS" in pickaxe.group(1)
    assert "healPlayerCapped" in kosa.group(1)
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
    # The shield now owns a separate 20-second lightning gate; only helmet
    # and vest retain the generic cooldown-message path.
    assert defense_body.count("sendCooldownMessage(var2,") >= 2
    assert "player.sendMessage" in re.search(
        r"(?ms)private void sendCooldownMessage\(.*?(?=\n\s*private boolean suppressCooldownMessage)", java
    ).group(0)


def test_cooldown_messages_are_not_broadcast() -> None:
    java = JAVA.read_text(encoding="utf-8")
    helper = re.search(
        r"(?ms)private void sendCooldownMessage\(.*?(?=\n\s*private boolean suppressCooldownMessage)", java
    )
    assert helper, "cooldown message helper is missing"
    body = helper.group(0)
    assert "player.sendMessage" in body
    assert "Bukkit.broadcastMessage" not in body


def test_retired_compass_is_disabled_in_player_catalog_and_retained_for_admin_gifts() -> None:
    import yaml

    document = yaml.safe_load(ITEMS.read_text(encoding="utf-8"))
    compass = next(
        item for item in document["donation-catalog"]["items"]
        if item["item-id"] == "gde_moy_lut_blyat_compass"
    )
    assert compass["enabled"] is False
    assert compass["base-material"] == "COMPASS"
    assert compass["price-donation"] == 100
    assert compass["effect-profile-id"] == "LOOT_COMPASS"
    assert compass["owner-bound"] is True

    spec = importlib.util.spec_from_file_location("commerce_catalog_for_artifact_test", COMMERCE_CATALOG)
    assert spec and spec.loader
    commerce = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(commerce)

    player_catalog = commerce.donation_catalog_snapshot(ITEMS)
    assert "gde_moy_lut_blyat_compass" not in player_catalog["byId"]
    assert all(row["item_id"] != "gde_moy_lut_blyat_compass" for row in player_catalog["items"])

    admin_catalog = commerce.admin_gift_catalog_snapshot(ITEMS)
    admin_compass = next(
        item for item in admin_catalog["categories"]["DONATION"]
        if item["item_id"] == "gde_moy_lut_blyat_compass"
    )
    assert admin_compass["enabled"] is False

    java = JAVA.read_text(encoding="utf-8")
    assert "if (item.enabled() || adminGift)" in java
    assert "liveItem == null || !liveItem.enabled()" in java


def test_artifact_validator_comment_filter_preserves_urls_and_following_code() -> None:
    validator = (ROOT / "tests" / "ValidateCopiMineArtifactsUsesEconomyCoreDirectly.ps1").read_text(
        encoding="utf-8"
    )
    match = re.search(r"\[regex\]::Replace\(\$artifacts,\s*'([^']+)'", validator)
    assert match, "artifact validator comment filter is missing"
    pattern = match.group(1)
    assert pattern == r"(?s:/\*.*?\*/)|(?<!:)//[^\r\n]*"

    source = '''
class Dependencies {
  static String metadata = "https://repo.example/CopiMineEconomyCore";
  // remove this comment and retain the next declaration
  static String dependency = "CopiMineEconomyCore";
  /* remove this multi-line comment
     without consuming later lines */
  static String finalDependency = "CopiMineArtifacts";
}
'''
    stripped = re.sub(pattern, " ", source)
    assert "https://repo.example/CopiMineEconomyCore" in stripped
    assert "remove this comment" not in stripped
    assert "static String dependency = \"CopiMineEconomyCore\";" in stripped
    assert "static String finalDependency = \"CopiMineArtifacts\";" in stripped
