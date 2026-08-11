from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ITEMS = (ROOT / "copimine-artifacts" / "items.yml").read_text(encoding="utf-8")
SOURCE = (ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java").read_text(
    encoding="utf-8"
)


def _block(text: str, marker: str, end_marker: str) -> str:
    start = text.index(marker)
    end = text.index(end_marker, start)
    return text[start:end]


def test_ar_swords_declare_the_shared_12_5_damage_target():
    zmei = _block(ITEMS, "  - id: zmei_gorynych\n", "  - id: repair_kit\n")
    duty = _block(ITEMS, "  - id: dezhurniy_argument_sword\n", "  - id: vechniy_razgon_firework\n")

    assert "source: AR_SHOP" in zmei
    assert "material: NETHERITE_SWORD" in zmei
    assert "source: AR_SHOP" in duty
    assert "material: NETHERITE_SWORD" in duty
    assert "AR_SWORD_ATTACK_DAMAGE = 12.5D" in SOURCE
    assert "isArSwordCatalogItem" in SOURCE


def test_existing_inventory_items_are_normalized_after_join():
    assert "normalizeExistingArCombatItems" in SOURCE
    join_start = SOURCE.index("public void onJoin(PlayerJoinEvent")
    join = SOURCE[join_start : SOURCE.index("@EventHandler", join_start + 1)]
    assert "normalizeExistingArCombatItems(var1.getPlayer())" in join
    assert "getStorageContents()" in SOURCE
    assert "getItemInOffHand()" in SOURCE
    assert "getEnderChest()" in SOURCE


def test_custom_pickaxes_get_efficiency_two_except_effect_pickaxe():
    assert "PICKAXE_EFFICIENCY_LEVEL = 2" in SOURCE
    assert "Enchantment.EFFICIENCY, PICKAXE_EFFICIENCY_LEVEL" in SOURCE
    assert "HASTE_MAX_AMPLIFIER = 255" in SOURCE
    assert "PotionEffectType.HASTE, HASTE_BURST_LONG_TICKS, HASTE_MAX_AMPLIFIER" in SOURCE
    smena = _block(ITEMS, "  - id: smena_bez_perekura_pickaxe\n", "  - id: lesnoy_bespredel_axe\n")
    assert "effect: HASTE_BURST_LONG" in smena


def test_compass_has_ten_second_cooldown_and_no_negative_visual_effect():
    compass = _block(ITEMS, "    - item-id: gde_moy_lut_blyat_compass\n", "      lore:\n")
    assert "cooldown-seconds: 10" in compass
    assert "visual-effect-id: \"\"" in compass
    assert "COMPASS_COOLDOWN_SECONDS = 10" in SOURCE
    interact = _block(SOURCE, "public void onArtifactInteract(PlayerInteractEvent", "public void onArtifactDefend")
    assert '"LOOT_COMPASS".equals(var4)' in interact
    assert '"LOOT_COMPASS".equals(var4)' in SOURCE
    assert "visualEffects.applyTo" in SOURCE
    assert "!\"LOOT_COMPASS\".equalsIgnoreCase(var4)" in SOURCE
