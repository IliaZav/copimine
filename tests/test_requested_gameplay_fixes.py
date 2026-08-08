from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def between(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    finish = source.index(end, begin + len(start))
    return source[begin:finish]


def test_custom_artifacts_and_ar_are_free_in_vanilla_inventory_transport():
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
        "public void onInventoryDrag(InventoryDragEvent var1)",
    )
    assert "isAnvilArtifactInteraction(var1)" not in click
    assert "quarantineForeignDonationClick(var1, var2)" not in click
    assert "shouldBlockOfficialArtifactInsertion(var1)" not in click

    drag = between(
        artifacts,
        "public void onInventoryDrag(InventoryDragEvent var1)",
        "public void onInventoryMoveItem(InventoryMoveItemEvent var1)",
    )
    assert "isAnvilArtifactDrag(var1)" not in drag

    admin_click = between(
        admin,
        "public void onProtectedItemClick(InventoryClickEvent e)",
        "public void onProtectedItemDrag(InventoryDragEvent e)",
    )
    admin_drag = between(
        admin,
        "public void onProtectedItemDrag(InventoryDragEvent e)",
        "public void onProtectedItemMove(InventoryMoveItemEvent e)",
    )
    assert "artifactsCoreOwns(cursor,current,hotbar)" in admin_click
    assert "GameMode.CREATIVE" in admin_click
    assert admin_click.index("GameMode.CREATIVE") < admin_click.index("Inventory top")
    assert "artifactsCoreOwns(e.getOldCursor())" in admin_drag
    assert "GameMode.CREATIVE" in admin_drag
    assert admin_drag.index("GameMode.CREATIVE") < admin_drag.index("Inventory top")
    assert "isProtectedItemMove" not in admin_drag


def test_official_ar_drop_and_pickup_use_a_transfer_claim_before_retagging():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    pickup = between(admin, "public void onPickup(EntityPickupItemEvent e)", "public void onDrop(PlayerDropItemEvent e)")
    drop = between(admin, "public void onDrop(PlayerDropItemEvent e)", "public void onSealDropLowest")

    assert "registerArTransferClaim(e.getItemDrop(),e.getPlayer())" in drop
    assert "claimArTransfer(e.getItem(),p)" in pickup
    assert 'retagArOwner(e.getItem(),p,"pickup"' in pickup
    assert pickup.index("claimArTransfer(e.getItem(),p)") < pickup.index("return;", pickup.index("claimArTransfer(e.getItem(),p)"))
    assert 'if("pickup".equals(reason)&&claim==null)return;' in admin


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


def test_silk_touch_diamond_ore_path_replaces_the_existing_vanilla_drop():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    drop = between(admin, "public void onArDrop(BlockDropItemEvent e)", "public void onBook(PlayerEditBookEvent e)")
    assert "ignoreCancelled=false" in admin[admin.index("public void onArDrop") - 180 : admin.index("public void onArDrop")]
    assert "isValidArCertificationDrop(e)" in drop
    assert "item.setItemStack(official)" in drop
    assert "prepareIssuanceAsync" in drop
    assert "e.setCancelled" not in drop
    assert "dropItemNaturally" not in drop


def test_ar_placement_needs_no_custom_block_provenance_or_reissue_state():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    drop = between(admin, "public void onArDrop(BlockDropItemEvent e)", "public void onBook(PlayerEditBookEvent e)")
    assert "public void onArPlace(BlockPlaceEvent e)" not in admin
    assert "arPlacedBlockKeys" not in drop
    assert "arPlacedStacks" not in drop
    assert "item.setItemStack(official)" in drop


def test_brewing_requires_full_water_and_netherrack_fire_rig():
    narcotics = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    supported = between(narcotics, "public boolean isSupportedCauldron", "public boolean tryAddIngredient")
    level_change = between(narcotics, "public void handleCauldronLevelChange", "public int cachedStateCount")
    assert "Material.WATER_CAULDRON" in supported
    assert "Levelled" in supported
    assert "requireFullWater" in supported
    assert "hasBrewingRig" in supported
    assert "hasBrewingRig" in level_change

    rig = between(narcotics, "private boolean hasBrewingRig", "private boolean isFullWaterLevel")
    assert "Material.FIRE" in rig
    assert "Material.SOUL_FIRE" in rig
    assert "Material.NETHERRACK" in rig
    assert "BlockFace.DOWN" in rig


def test_brewing_consumes_the_submitted_ingredient_once_and_has_no_player_owner_gate():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "public boolean tryAddIngredient", "public void handleCauldronBroken")
    assert "itemFactory.consumeOne(player, stack)" in decision
    assert "ownerUuid" not in decision
    assert "Objects.equals" not in decision


def test_brewing_keeps_a_valid_three_of_four_prefix_pending():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "public boolean tryAddIngredient", "public void handleCauldronBroken")
    assert "if (current.size() >= MINIMUM_RECIPE_CHECK_SIZE && exact != null)" in decision
    assert "boolean canStillBecomeRecipe = recipeService.canStillBecomeRecipe(current)" in decision
    assert "if (canStillBecomeRecipe && current.size() < maximumRecipeSize)" in decision
    assert "return queueIngredients(block, key, current, nextVersion, nowMillis);" in decision

    config = read("copimine-narcotics/config.yml")
    chups = between(config, "  chups:", "    normal_effects:")
    assert len(re.findall(r"^      - ", chups, flags=re.MULTILINE)) == 4


def test_validated_narcotic_use_precedes_generic_cancelled_event_guard():
    plugin = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    official_index = plugin.index("if (official != null)")
    cancelled_index = plugin.index("if (event.isCancelled())")
    official = plugin[official_index:cancelled_index]

    assert official_index < cancelled_index
    assert "overdoseService.isStateReady(player)" in official
    assert "event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)" in official
    assert "database.reserveConsumption" in official


def test_chups_has_jump_boost_three_in_normal_effects():
    config = read("copimine-narcotics/config.yml")
    chups = between(config, "  chups:", "  borshevik:")
    normal = between(chups, "    normal_effects:", "    overdose_effects:")
    assert re.search(
        r"^      - type:JUMP_BOOST,amplifier:2,duration_seconds:90$",
        normal,
        flags=re.MULTILINE,
    )


def test_brewing_accepts_arbitrary_items_as_a_three_item_buffer_then_checks_the_recipe():
    service = read("copimine-narcotics/src/me/copimine/narcotics/recipe/NarcoticsRecipeService.java")
    entry = between(service, "public IngredientEntry cauldronIngredientEntry", "public NarcoticDefinition matchExact")
    assert "IngredientEntry recognized = ingredientEntry(stack);" in entry
    assert 'new IngredientEntry("MATERIAL:" + stack.getType().name()' in entry
    assert "genericPotionKey" in entry


def test_brewing_completion_physically_drops_both_success_and_wrong_mix_outputs():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    effects = between(service, "private void finishBrewing", "private void simulateWrongMixExplosion")
    assert "dropItemNaturally" in effects
    assert "simulateWrongMixExplosion(block, initiator)" in effects
    assert "clearState(block, key, version)" in effects


def test_brewing_world_output_has_a_pickup_delay_instead_of_disappearing_instantly():
    plugin = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    output = between(plugin, "public Item dropCompletedBrewingOutput", "private void markPendingRefund")
    assert "Item dropped = location.getWorld().dropItemNaturally(location, output);" in output
    assert "dropped.setPickupDelay(10);" in output
    assert "return dropped;" in output


def test_brewing_rejects_a_three_item_prefix_that_cannot_match_any_recipe():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "public boolean tryAddIngredient", "public void handleCauldronBroken")
    assert "boolean canStillBecomeRecipe = recipeService.canStillBecomeRecipe(current)" in decision
    assert "containsUnrecognizedIngredient" in decision
    assert "if (canStillBecomeRecipe && current.size() < maximumRecipeSize)" in decision
    assert "return finishWrongMix(block, key, nextVersion, current.size(), player);" in decision


def test_wrong_mix_damages_players_only_inside_six_block_radius():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    explosion = between(service, "private void simulateWrongMixExplosion", "private boolean queueIngredients")
    assert "getPlayers" in explosion
    assert "distanceSquared" in explosion
    assert "damage(" in explosion
    assert "WRONG_MIX_DAMAGE_RADIUS = 6.0D" in service
    assert "WRONG_MIX_MIN_DAMAGE = 14.0D" in service
    assert "WRONG_MIX_MAX_DAMAGE = 20.0D" in service


def test_shared_cauldron_lets_a_different_player_finish_and_receive_the_brew():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "public boolean tryAddIngredient", "public void handleCauldronBroken")
    completion = between(service, "private void finishBrewing", "private void simulateWrongMixExplosion")
    assert "ownerUuid" not in decision + completion
    assert "Objects.equals" not in completion


def test_official_ar_uses_vanilla_inventory_transport_and_world_drop_rules():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    for method in (
        "onOfficialArPlace", "onOfficialArCreative", "onOfficialArInventoryClick",
        "onOfficialArInventoryDrag", "onOfficialArInventoryMove", "onOfficialArInventoryPickup",
        "onOfficialArPickup", "onOfficialArDrop", "onOfficialArDeath", "onOfficialArDamage",
        "onOfficialArDespawn", "onOfficialArMerge", "onOfficialArSpawn", "onOfficialArSmelt",
        "onOfficialArEntityInteract", "onOfficialArArmorStand",
    ):
        assert f"public void {method}" not in economy


def test_official_ar_is_authorized_on_player_death_instead_of_being_suppressed():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    assert "import org.bukkit.event.entity.PlayerDeathEvent;" in economy
    assert "public void onOfficialArDeath" not in economy
    assert "public void onOfficialArDrop" not in economy


def test_shop_hides_the_disabled_lost_item_recovery_entry_and_limits_regular_items_to_three():
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
    assert all(re.search(r"^    per_player_limit:\s*3\s*$", block, flags=re.MULTILINE) for block in regular)
    assert "per_player_limit: 3" in artifacts
    assert '"AR_SHOP".equalsIgnoreCase(source)' not in artifacts
    assert "int perPlayerLimit = Math.max(0, this.parseInt" in artifacts


def test_shop_purchase_limit_is_three_and_can_be_changed_or_reset_from_web():
    backend = read("admin-web/backend/main.py")
    frontend = read("admin-web/frontend/assets/js/cabinet-runtime.js")
    assert "class AdminShopLimitUpdateIn" in backend
    assert '@app.post("/api/admin/shop/limit")' in backend
    assert "update_shop_limit_sync" in backend
    assert "per_player_limit" in backend
    assert "limit-reset" in backend
    assert '"limit": 3' in backend
    assert "updateShopLimit" in frontend
    assert '/api/admin/shop/limit' in frontend
    assert "limit-reset" in frontend
    assert "limit_value: 3" in frontend


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


def test_legacy_ar_is_not_silently_reissued_and_failed_issuance_is_token_scoped():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    issue = between(economy, "private boolean issueOfficialArAmount", "private void completeWithdrawOnMainThread")
    assert "normalizeOfficialArItems" not in economy
    assert "normalizeOfficialArStack" not in economy
    assert "officialArIssuanceTokenKey" in issue or "markOfficialArIssuance" in issue
    assert "removeOfficialArIssuance" in issue
    assert "clearOfficialArIssuance" in issue
    assert "removeOfficialArSerial(player.getInventory(), serial, stackAmount)" not in issue


def test_brewing_consumes_the_submitted_ingredient_and_drops_output_in_world():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    queue = between(service, "private boolean queueIngredients", "private void clearState")
    assert "saveBrewingState(key, version, frozen)" in queue
    assert "ownerUuid" not in queue
    assert "dropItemNaturally" in service


def test_brewing_world_output_is_public_and_never_mailbox_delivered():
    plugin = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    cauldron = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    assert "dropItemNaturally" in between(cauldron, "private void finishBrewing", "private void simulateWrongMixExplosion")
    assert "ownerUuid" not in cauldron


def test_brewing_completion_consumes_the_rig_for_a_fresh_second_setup():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    effects = between(service, "private void clearState", "private void particle")
    assert "extinguishRig(block)" in effects
    assert "if (configService.clearCauldronOnCompletion())" in effects


def test_brewing_can_reopen_a_tombstoned_cauldron_state():
    database = read("copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java")
    persist = between(database, "private void persistBrewingState", "private String brewingJournalKey")
    assert "narcotics_brewing_states.deleted=TRUE" in persist
    assert "state_version < EXCLUDED.state_version" in persist


def test_brewing_new_session_uses_a_monotonic_version_after_completion():
    service = read("copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java")
    decision = between(service, "CauldronState base =", "NarcoticDefinition exact")
    assert "base.version() + 1L" in decision
    assert "newBrewingVersion" not in decision


def test_official_ar_inventory_handlers_do_not_mutate_or_cancel_vanilla_clicks():
    economy = read("copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java")
    assert "public void onOfficialArInventoryClick" not in economy
    assert "public void onOfficialArInventoryDrag" not in economy

    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    admin_click = between(admin, "public void onProtectedItemClick(InventoryClickEvent e)", "public void onProtectedItemDrag")
    admin_drag = between(admin, "public void onProtectedItemDrag(InventoryDragEvent e)", "public void onProtectedItemMove")
    assert "GameMode.CREATIVE" in admin_click
    assert admin_click.index("GameMode.CREATIVE") < admin_click.index("Inventory top")
    assert "GameMode.CREATIVE" in admin_drag
    assert admin_drag.index("GameMode.CREATIVE") < admin_drag.index("Inventory top")
    assert "setAmount" not in admin_click + admin_drag


def test_adminplus_does_not_cancel_creative_inventory_transport():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    click = between(admin, "public void onInv(InventoryClickEvent e)", "public void onPrepareCraft")
    assert "if(e instanceof InventoryCreativeEvent" not in click
    assert "isOfficialArCreativeClick" not in admin
    assert "GameMode.CREATIVE" in click
    assert click.index("GameMode.CREATIVE") < click.index("inventoryLocks")


def test_adminplus_bypasses_both_paper_creative_click_paths_before_generic_guards():
    """Paper uses InventoryClickEvent for ordinary Creative container clicks."""
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    click = between(admin, "public void onInv(InventoryClickEvent e)", "public void onPrepareCraft")
    generic_guard = click.index("inventoryLocks")
    assert click.index("getGameMode()==GameMode.CREATIVE") < generic_guard
    assert "InventoryCreativeEvent" not in admin
    assert "isOfficialArCreativeClick" not in admin


def test_creative_ar_transport_never_depends_on_ar_signature_or_admin_lock_state():
    """Creative inventory transport must be a vanilla path for every AR copy."""
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    click = between(admin, "public void onInv(InventoryClickEvent e)", "public void onPrepareCraft")
    protected_click = between(admin, "public void onProtectedItemClick(InventoryClickEvent e)", "public void onProtectedItemDrag")
    protected_drag = between(admin, "public void onProtectedItemDrag(InventoryDragEvent e)", "public void onProtectedItemMove")

    assert "isOfficialArCreativeClick" not in click
    assert "InventoryCreativeEvent" not in admin
    assert "GameMode.CREATIVE" in click
    assert click.index("GameMode.CREATIVE") < click.index("inventoryLocks")
    assert "GameMode.CREATIVE" in protected_click[:protected_click.index("Inventory top")]
    assert "GameMode.CREATIVE" in protected_drag[:protected_drag.index("Inventory top")]


def test_zhuzevo_stale_stack_repair_requires_exact_registered_fungible_identity():
    narcotics = read("copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java")
    factory = read("copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java")
    database = read("copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java")
    interact = between(narcotics, "public void onInteract(PlayerInteractEvent event)", "public void onBreak")

    assert "resolveRegisteredStackCandidate(inHand)" in interact
    assert "isActiveIssuedInstance" in interact
    assert "repairRegisteredStack" in interact
    assert "public NarcoticDefinition resolveRegisteredStackCandidate(ItemStack stack)" in factory
    assert "stackIdentity(definition, version).equals(instanceId)" in factory
    assert '"zhuzevo".equals(definition.id())' in factory
    assert "public CompletableFuture<Boolean> isActiveIssuedInstance" in database
    assert "WHERE instance_id=? AND narcotic_id=? AND status='ACTIVE'" in database


def test_ar_inventory_clicks_have_no_custom_quantity_or_restriction_path():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    click = between(admin, "public void onProtectedItemClick(InventoryClickEvent e)", "public void onProtectedItemDrag")
    drag = between(admin, "public void onProtectedItemDrag(InventoryDragEvent e)", "public void onProtectedItemMove")
    click_early = click[:click.index("Inventory top")]
    drag_early = drag[:drag.index("Inventory top")]
    assert "GameMode.CREATIVE" in click_early
    assert "GameMode.CREATIVE" in drag_early
    assert "setCancelled" not in click_early + drag_early
    assert "updateInventory" not in click_early + drag_early
    assert "AR_RESTRICTED_INVENTORY_TOUCH" not in click + drag
    assert "setAmount" not in click + drag
    assert "return isProtectedOfficialItem(it);" in admin


def test_adminplus_leaves_official_ar_as_a_vanilla_item():
    admin = read("copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java")
    assert "normalizeArInventoryState(e.getPlayer())" not in admin
    assert "normalizeArInventoryState(p)" not in admin
    pickup = between(admin, "public void onPickup(EntityPickupItemEvent e)", "public void onDrop(PlayerDropItemEvent e)")
    drop = between(admin, "public void onDrop(PlayerDropItemEvent e)", "public void onSealDropLowest")
    assert "isOfficialArItem(picked)" not in pickup
    assert "isOfficialArItem(e.getItemDrop().getItemStack())" not in drop
    for method in ("onArHopperPickup", "onArInventoryMove", "onArDispense", "onArSpawn"):
        assert method not in admin
    for method in ("onArBlockPlaceGuard", "onFurnaceSmelt", "onOfficialArCreative"):
        assert f"public void {method}" not in admin


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
