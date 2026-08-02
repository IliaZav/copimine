from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ELECTION = (ROOT / "copimine-election-core" / "src" / "me" / "copimine" / "electioncore" / "CopiMineElectionCore.java").read_text(encoding="utf-8")
ARTIFACTS = (ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java").read_text(encoding="utf-8")
ITEMS = (ROOT / "copimine-artifacts" / "items.yml").read_text(encoding="utf-8")


def test_election_restore_is_idempotent_and_cleans_player_state():
    assert "officialRestore.get(playerUuid)" in ELECTION
    assert "removeQueuedOfficialItem(playerUuid" in ELECTION
    assert "officialRestore.computeIfAbsent" in ELECTION
    assert "loadOfficialRestoreQueue" in ELECTION
    assert "saveOfficialRestoreQueue" in ELECTION
    # A quit/reconnect must not erase a death/recovery item before delivery.
    quit_section = ELECTION[ELECTION.index("public void onQuit"):ELECTION.index("public void onRespawn")]
    assert "officialRestore.remove(playerId)" not in quit_section
    assert "hasOfficialLogicalItem(player, \"PRESIDENT_MANDATE\")" in ELECTION


def test_election_restore_queue_keeps_each_item_until_inventory_accepts_it():
    """A full inventory must not drop an item just because a delivery retry ran."""
    assert "Map<String, ItemStack>" in ELECTION
    assert "officialRestore.computeIfAbsent(playerUuid, key -> new ConcurrentHashMap<>())" in ELECTION
    assert "leftovers" in ELECTION
    assert "playerInventorySurfaces" in ELECTION
    assert "getArmorContents()" in ELECTION
    assert "getItemInOffHand()" in ELECTION
    assert "pendingOfficialRestore" not in ELECTION[ELECTION.index("private void addOrQueueOfficialItem"):ELECTION.index("private String officialRestoreKey")]
    assert "queueOfficialRestore(player.getUniqueId()" in ELECTION


def test_election_item_and_visual_handlers_cover_all_hands_and_cancelled_protection():
    assert "event.getHand() != EquipmentSlot.HAND" in ELECTION
    assert "event.getClick() == ClickType.NUMBER_KEY" in ELECTION
    assert "InventoryAction.HOTBAR_SWAP" in ELECTION
    assert "InventoryAction.HOTBAR_MOVE_AND_READD" in ELECTION
    assert "ClickType.SWAP_OFFHAND" in ELECTION
    assert "event.getHotbarButton()" in ELECTION
    assert "getItemInOffHand()" in ELECTION
    assert "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)\n    public void onInteract" in ELECTION
    assert "InventoryAction.COLLECT_TO_CURSOR" in ELECTION
    assert "bundleContainsProtectedOfficial" in ELECTION


def test_election_president_menus_are_snapshot_only_on_bukkit_thread():
    admin = ELECTION[ELECTION.index("private void openPresidentAdminMenu(Player player, int selectedPeriodHours)"):ELECTION.index("private void openPresidentMandateMenu(Player player)")]
    mandate = ELECTION[ELECTION.index("private void openPresidentMandateMenu(Player player, int selectedPeriodHours)"):ELECTION.index("private PresidentTaxRoster loadPresidentTaxRoster")]
    assert "activeTax()" not in admin
    assert "activeTax()" not in mandate
    assert "snapshot.get()" in admin or "snapshot.get()" in mandate
    assert "taxPeriodHours()" in admin or "taxPeriodHours()" in mandate
    assert "openPresidentAdminMenuAsync" in ELECTION
    assert "openPresidentMandateMenuAsync" in ELECTION


def test_election_web_snapshot_is_atomic_and_votes_are_split_from_voter_identity():
    assert "ATOMIC_MOVE" in ELECTION
    assert "vote_participation" in ELECTION
    assert "anonymous_token" in ELECTION
    assert "candidate_uuid" not in ELECTION[ELECTION.index("INSERT INTO vote_participation"):ELECTION.index("INSERT INTO vote_participation") + 600]
    # A deposited ballot must not remain a voter↔candidate join table.
    deposit = ELECTION[ELECTION.index("private void depositBallot"):ELECTION.index("private void annulBallot")]
    assert "confirmed_candidate_uuid=''" in deposit
    assert "confirmed_candidate_name=''" in deposit
    assert "player_uuid=''" in deposit
    assert "player_name=''" in deposit


def test_election_official_items_cannot_move_to_any_external_storage_or_be_cloned():
    assert "PlayerArmorStandManipulateEvent" in ELECTION
    assert "InventoryCreativeEvent" in ELECTION
    assert "EntityPickupItemEvent" in ELECTION
    assert "InventoryPickupItemEvent" in ELECTION
    assert "BlockDispenseEvent" in ELECTION
    assert "ownerUuid.isBlank()" in ELECTION
    assert "isElectionOwnedItem(event.getCursor())" in ELECTION
    assert "isApplicationBook" in ELECTION
    assert "legacyElectionTypeKey" in ELECTION and "copimineelectionflow" in ELECTION
    assert "electionItemOwner" in ELECTION
    assert "top.getHolder() instanceof Player" in ELECTION
    assert "InventoryType.ENDER_CHEST" in ELECTION
    assert "isExternalOfficialStorage(view, event.getRawSlot())" in ELECTION
    assert "isBundleItem(current) || isBundleItem(cursor)" in ELECTION


def test_election_station_replacement_clears_stale_cancelled_flag():
    section = ELECTION[ELECTION.index("public void onBlockPlace"):ELECTION.index("public void onProtectedBreak")]
    assert "isPollingStationProtection(cached)" in section
    assert "event.setCancelled(false)" in section
    assert "reactivateRpVotingBlockAfterPlacementAsync" in section


def test_election_restore_entitlement_refresh_uses_one_connection_and_legacy_filter():
    restore = ELECTION[ELECTION.index("private void restoreOfficialItems"):ELECTION.index("private void removeOfficialItemsFromPlayer")]
    assert "try (Connection connection = openConnection())" in restore
    assert restore.count("queryOne(connection") >= 2
    assert "LOWER(COALESCE(notes,''))='rp-two-stage'" in restore


def test_adminplus_delegates_election_item_authority_to_electioncore():
    admin_path = ROOT / "copimine-admin-plugin" / "src" / "me" / "copimine" / "ultimateplus" / "CopiMineUltimateAdminPlus.java"
    admin = admin_path.read_text(encoding="utf-8")
    for method in (
        "onPickup", "onDrop", "onProtectedItemDamage", "onProtectedItemDespawn",
        "onProtectedItemMerge", "onProtectedInventoryPickup", "onProtectedInventoryMove",
        "onProtectedBlockDispense", "onOfficialItemDeath", "onOfficialItemRespawn",
        "onProtectedItemClick", "onProtectedItemDrag", "onProtectedItemMove",
        "onOfficialItemInteract", "onProtectedEntityDisplay", "onProtectedArmorStand",
    ):
        start = admin.index("public void " + method)
        end = admin.find("\n    @EventHandler", start + 1)
        if end < 0:
            end = min(len(admin), start + 2500)
        body = admin[start:end]
        assert "electionCoreOwns" in body or method == "onOfficialItemRespawn", method
    entity = admin[admin.index("public void onProtectedEntityDisplay"):admin.index("public void onProtectedArmorStand")]
    assert "e.getHand() != EquipmentSlot.HAND" in entity
    # Retired AdminPlus station handling must not perform a DB lookup on every click.
    interact = admin[admin.index("public void onInteract"):admin.index("public void onPlace")]
    assert "!legacyElectionRuntimeDisabled()" in interact


def test_election_uses_cached_president_state_for_event_paths():
    assert "AtomicReference<PresidentCache>" in ELECTION
    assert "refreshPresidentCacheAsync" in ELECTION
    assert "queryOne(\"SELECT COUNT(*) FROM president_terms" not in ELECTION
    assert "AtomicLong snapshotRefreshGeneration" in ELECTION
    assert "taxPaymentInFlight" in ELECTION
    assert "uq_president_terms_single_active" in ELECTION


def test_election_financial_history_is_optional_and_uses_one_time_unit():
    assert "artifactRevenuePayoutsAvailable" in ELECTION
    assert "created_at / 1000" in ELECTION or "created_at * 1000" in ELECTION
    section = ELECTION[ELECTION.index("private List<Map<String, Object>> currentTaxPayments"):ELECTION.index("private Map<String, Object> activeTaxClockExemption")]
    assert "UNION ALL" not in section


def test_artifact_reclaim_covers_loss_sources_and_durable_journal():
    for source in ("void", "cactus", "creative-delete", "break", "merge"):
        assert source in ARTIFACTS
    assert '"entity-" + cause.name().toLowerCase(Locale.ROOT)' in ARTIFACTS
    assert "ENTITY_EXPLOSION" in ARTIFACTS and "BLOCK_EXPLOSION" in ARTIFACTS
    assert "FileChannel" in ARTIFACTS
    assert ".force(true)" in ARTIFACTS
    assert "ATOMIC_MOVE" in ARTIFACTS
    assert "onQuit" in ARTIFACTS and "actionCooldowns.entrySet().removeIf" in ARTIFACTS


def test_foreign_donation_pickup_is_quarantined_before_storage_or_duplication():
    """A non-owner pickup must be journaled first and removed by unique id."""
    assert "EntityPickupItemEvent" in ARTIFACTS
    assert "foreignDonationRef" in ARTIFACTS
    assert "rawDonationIdentity" in ARTIFACTS
    assert "onForeignDonationPickup" in ARTIFACTS
    assert "onForeignDonationDrop" in ARTIFACTS
    assert "onForeignDonationPlace" in ARTIFACTS
    assert "onDonationInventoryOpen" in ARTIFACTS
    assert "quarantineForeignDonation" in ARTIFACTS
    assert "recordDonationLossOnce(ref, reason)" in ARTIFACTS
    assert "removeDonationInstanceFromOnlineInventories(ref.uniqueItemId())" in ARTIFACTS
    assert "removeUniqueItemFromInventory(var1.getSource(), donation.uniqueItemId())" in ARTIFACTS
    assert "event.getClick() == ClickType.NUMBER_KEY" in ARTIFACTS
    assert "event.getClick() == ClickType.SWAP_OFFHAND" in ARTIFACTS
    assert "getItem(event.getHotbarButton())" in ARTIFACTS
    # Physical removal is guarded by the durable append in both pickup and
    # drop handlers, so a DB/journal failure leaves the only copy intact.
    pickup = ARTIFACTS[ARTIFACTS.index("public void onForeignDonationPickup"):ARTIFACTS.index("public void onForeignDonationDrop")]
    drop = ARTIFACTS[ARTIFACTS.index("public void onForeignDonationDrop"):ARTIFACTS.index("public void onDonationInventoryOpen")]
    assert "if (this.quarantineForeignDonation(player, ref, \"foreign-pickup\"))" in pickup
    assert "if (this.quarantineForeignDonation(event.getPlayer(), ref, \"foreign-drop\"))" in drop


def test_artifact_compass_is_explicit_teleport_item_with_fifteen_second_cooldown():
    assert "КОМПАС ТЕЛЕПОРТАЦИИ" in ITEMS.upper()
    assert "cooldown-seconds: 15" in ITEMS
    assert "getViewDistance" in ARTIFACTS or "view-distance" in ARTIFACTS
    assert "MAX_COMPASS_TELEPORT_DISTANCE" in ARTIFACTS


def test_artifact_permissions_and_world_mutations_are_closed():
    admin_block = ARTIFACTS[ARTIFACTS.index("private boolean isArtifactsAdmin"):ARTIFACTS.index("private boolean isRestrictedJuniorArtifactsAdmin")]
    # The existing CIK/admin roles are trusted service roles for the shared
    # admin hub.  Junior staff are still denied by the guard immediately
    # before this role bridge is evaluated.
    assert "copimine.election.cik" in admin_block
    assert "isRestrictedJuniorArtifactsAdmin" in ARTIFACTS
    assert "EntityExplodeEvent" in ARTIFACTS
    assert "BlockExplodeEvent" in ARTIFACTS
    assert "BlockPistonExtendEvent" in ARTIFACTS
    assert "Material.AIR" in ARTIFACTS
    assert "strikeLightningEffect" in ARTIFACTS


def test_artifact_item_meta_is_not_overwritten_after_attack_modifier():
    create = ARTIFACTS[ARTIFACTS.index("private ItemStack createOfficialItem"):ARTIFACTS.index("private double vanillaAttackDamage")]
    assert "ensureAttackDamageAttribute(var6" in create
    assert "ensureAttackDamageAttribute(var5" not in create
