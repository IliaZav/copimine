from pathlib import Path
import re


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


def test_brewing_final_ingredient_cannot_consume_a_replaced_stack():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    factory = read("copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java")
    completion = between(service, "private boolean prepareFinalIngredient", "private void finishBrewing")
    assert "ItemStack expectedIngredient = stack == null ? null : stack.clone()" in completion
    assert "itemFactory.consumeOneExact(player, expectedIngredient)" in completion
    assert "abortBrewingCompletionIntent" in completion
    assert "public boolean consumeOneExact(Player player, ItemStack expected)" in factory
    assert "candidate.isSimilar(expected)" in factory


def test_brewing_keeps_a_valid_three_of_four_prefix_pending():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "public boolean tryAddIngredient", "public void handleCauldronBroken")
    assert "if (current.size() >= MINIMUM_RECIPE_CHECK_SIZE && exact != null)" in decision
    assert "boolean canStillBecomeRecipe = recipeService.canStillBecomeRecipe(current)" in decision
    assert "if (canStillBecomeRecipe && current.size() < maximumRecipeSize)" in decision
    assert "return queueIngredients(block, key, current, nextVersion, nowMillis, player, stack);" in decision

    config = read("copimine-narcotics/config.yml")
    chups = between(config, "  chups:", "    normal_effects:")
    assert len(re.findall(r"^      - ", chups, flags=re.MULTILINE)) == 4


def test_brewing_completion_physically_drops_both_success_and_wrong_mix_outputs():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    effects = between(service, "private void completeBrewingEffects", "private void simulateWrongMixExplosion")
    plugin = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    drop = between(plugin, "public Item dropCompletedBrewingOutput", "private void markPendingRefund")
    assert "dropCompletedBrewingOutput" in effects
    assert "simulateWrongMixExplosion(block)" in effects
    assert "definition" in drop
    assert "dropItemNaturally" in drop
    assert "markPendingOutput" in drop


def test_official_ar_can_move_in_personal_inventory_and_is_normalized_after_world_drop():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    click = between(economy, "private boolean officialArTouchesContainer", "private boolean containsOfficialAr")
    drag = between(economy, "public void onOfficialArInventoryDrag", "public void onOfficialArInventoryMove")
    spawn = between(economy, "public void onOfficialArSpawn", "public void onOfficialArSmelt")
    drop = between(economy, "public void onOfficialArDrop", "public void onOfficialArDamage")
    assert "InventoryType.CRAFTING" in click
    assert "top.getHolder() instanceof Player" in click
    assert "InventoryType.CRAFTING" in drag
    assert "authorizeWorldDrop" in drop
    assert "if (!officialArService.authorizeWorldDrop" in drop
    assert "remove(officialArWorldDropTokenKey)" in spawn
    assert "event.getEntity().setItemStack" in spawn


def test_official_ar_is_authorized_on_player_death_instead_of_being_suppressed():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    assert "import org.bukkit.event.entity.PlayerDeathEvent;" in economy
    start = economy.index("public void onOfficialArDeath(PlayerDeathEvent event)")
    death = economy[start : start + 900]
    assert "event.getDrops()" in death
    assert "authorizeWorldDrop" in death


def test_shop_hides_the_disabled_lost_item_recovery_entry_and_limits_regular_items_to_one():
    artifacts = read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    for start, end in (
        ("private void openMain(Player var1, CopiMineArtifacts.Shop var2, boolean var3)", "private void openMainV2"),
        ("private void openMainV2", "private void openDonationRoot"),
        ("private void openDonationRoot", "private void openDonationReclaim"),
    ):
        menu = between(artifacts, start, end)
        assert "RECOVERY_COMPASS" not in menu
        assert "donation:disabled" not in menu

    catalog = read("copimine-artifacts/items.yml")
    blocks = re.split(r"(?=^  - id:)", catalog, flags=re.MULTILINE)
    regular = [block for block in blocks if "source: AR_SHOP" in block]
    assert regular
    assert all(re.search(r"^    per_player_limit:\s*1\s*$", block, flags=re.MULTILINE) for block in regular)
    assert 'defaultItemsYaml().replace("per_player_limit: 5", "per_player_limit: 1")' in artifacts
    assert '"AR_SHOP".equalsIgnoreCase(source)' in artifacts
    assert "int perPlayerLimit =" in artifacts


def test_atm_title_is_spawned_above_the_block_not_inside_it():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    atm = between(economy, "private void spawnAtmTitleDisplay", "private void ensureAtmTitleDisplay")
    match = re.search(r"Location location = base\.clone\(\)\.add\(0\.5D,\s*([0-9.]+)D,\s*0\.5D\)", atm)
    assert match
    assert float(match.group(1)) >= 1.0


def test_resource_pack_preserves_the_vanilla_blue_stained_glass_pane_parent():
    builder = read("resourcepacks/build-resourcepack.py")
    assert 'if material == "blue_stained_glass_pane":' in builder
    assert 'parent = "minecraft:block/blue_stained_glass_pane"' in builder


def test_legacy_ar_is_migrated_to_the_shared_fungible_serial_and_failed_issuance_is_token_scoped():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    normalize = between(economy, "private ItemStack normalizeOfficialArStack", "private boolean needsOfficialArNormalization")
    issue = between(economy, "private boolean issueOfficialArAmount", "private void completeWithdrawOnMainThread")
    assert "if (!isFungibleArSerial(serial))" in normalize
    assert "return normalized;" in normalize
    assert "normalizeOfficialArItems(player)" in issue
    assert "officialArIssuanceTokenKey" in issue or "markOfficialArIssuance" in issue
    assert "removeOfficialArIssuance" in issue
    assert "clearOfficialArIssuance" in issue
    assert "removeOfficialArSerial(player.getInventory(), serial, stackAmount)" not in issue


def test_brewing_does_not_resolve_a_global_three_ingredient_prefix_as_zhuzevo():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "public boolean tryAddIngredient", "public void handleCauldronBroken")
    assert "if (current.size() < maximumRecipeSize)" in decision
    assert decision.index("if (current.size() < maximumRecipeSize)") < decision.index("return prepareFinalIngredient(block, key, configService.items().get(\"zhuzevo\")")


def test_brewing_consumes_only_the_exact_submitted_ingredient_and_world_output_is_durable():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    database = read("copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java")
    plugin = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    queue = between(service, "private boolean queueIngredients", "private void refundFailedIngredient")
    assert "consumeOneExact" in queue
    assert "queuePendingIngredientRefunds(ownerUuid, frozen)" in service
    assert "WORLD_DROPPED" in database
    assert "pendingWorldOutputClaims" in plugin
    assert "onPendingOutputPickup" in plugin
    assert "onPendingOutputDespawn" in plugin


def test_shop_revenue_starts_pending_and_waits_for_the_async_credit_worker():
    artifacts = read("copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java")
    persist = between(artifacts, "private void persistPaidPurchase", "private void deliverPurchase")
    assert 'var8.setString(10, "PENDING")' in persist
    assert 'var8.setString(10, "CREDITED")' not in persist


def test_atm_label_is_above_the_block_model():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    atm = between(economy, "private void spawnAtmTitleDisplay", "private void ensureAtmTitleDisplay")
    match = re.search(r"Location location = base\.clone\(\)\.add\(0\.5D,\s*([0-9.]+)D,\s*0\.5D\)", atm)
    assert match
    assert float(match.group(1)) >= 1.8
