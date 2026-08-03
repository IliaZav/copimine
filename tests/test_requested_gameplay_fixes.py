from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def between(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    finish = source.index(end, begin + len(start))
    return source[begin:finish]


def test_custom_artifacts_are_free_in_player_inventory_but_guarded_in_processing_slots():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    owns = between(
        admin,
        "private boolean artifactsCoreOwns(ItemStack stack)",
        "private boolean artifactsCoreOwns(ItemStack... stacks)",
    )
    assert "artifact_unique_item_id" in owns
    assert "!uniqueId.isBlank()" in owns

    artifacts = read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    click = between(
        artifacts,
        "public void onInventoryClick(InventoryClickEvent var1)",
        "private boolean quarantineForeignDonationClick",
    )
    assert "quarantineForeignDonationClick(var1, var2)" in click
    assert "shouldBlockOfficialArtifactInsertion(var1)" in click
    assert click.index("quarantineForeignDonationClick(var1, var2)") < click.index("if (var1.isCancelled())")

    drag = between(
        artifacts,
        "public void onInventoryDrag(InventoryDragEvent var1)",
        "public void onInventoryMoveItem(InventoryMoveItemEvent var1)",
    )
    assert "shouldBlockOfficialArtifactDrag(var1)" in drag


def test_atm_has_a_visible_title_and_shop_has_a_no_president_treasury_fallback():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    atm = between(economy, "private void spawnAtmTitleDisplay", "private void ensureAtmTitleDisplay")
    assert "ArmorStand.class" in atm
    assert 'stand.setCustomName(color("&eБанкомат"))' in atm
    assert "stand.setCustomNameVisible(true)" in atm

    artifacts = read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    purchase = between(artifacts, "private void executePurchase", "private void deliverPurchase")
    resolver = between(artifacts, "private CopiMineArtifacts.ShopRevenueRecipient resolveActivePresidentRevenueRecipient", "private void triggerRevenuePayoutAsync")
    assert "treasuryRevenueRecipient()" in resolver
    assert "PRESIDENT_BUDGET_ACCOUNT_ID" in resolver
    assert "var6x == null" in purchase
    assert "нет активного президента" not in purchase.lower()


def test_president_mandate_is_deduplicated_on_death_and_drop():
    election = read("copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java")
    death = between(election, "public void onOfficialDeath(PlayerDeathEvent event)", "public void onOfficialDamage")
    drop = between(election, "public void onDrop(PlayerDropItemEvent event)", "public void onMoveOfficial")
    assert "deduplicatePresidentMandates(event.getEntity())" in death
    assert "preserved.setAmount(1)" in death
    assert "event.setCancelled(true)" in drop
    assert "deduplicatePresidentMandates(player)" in drop


def test_silk_touch_diamond_ore_path_certifies_an_ore_even_when_vanilla_drop_is_empty():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    drop = between(admin, "public void onArDrop(BlockDropItemEvent e)", "private int naturalSilkTouchOreAmount")
    amount = between(admin, "private int naturalSilkTouchOreAmount", "private NamespacedKey arKey")
    assert "ignoreCancelled=false" in admin[admin.index("public void onArDrop") - 180 : admin.index("public void onArDrop")]
    assert "naturalSilkTouchOreAmount(e)" in drop
    assert "return Math.max(1,total)" in amount


def test_brewing_accepts_any_full_vanilla_water_cauldron():
    narcotics = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    supported = between(narcotics, "public boolean isSupportedCauldron", "public boolean tryAddIngredient")
    level_change = between(narcotics, "public void handleCauldronLevelChange", "public int cachedStateCount")
    assert "Material.WATER_CAULDRON" in supported
    assert "Levelled" in supported
    assert "requireFullWater" in supported
    assert "hasBrewingRig" not in supported
    assert "hasBrewingRig" not in level_change
