from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ELECTION = (ROOT / "copimine-election-core" / "src" / "me" / "copimine" / "electioncore" / "CopiMineElectionCore.java").read_text(encoding="utf-8")
ARTIFACTS = (ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java").read_text(encoding="utf-8")
ITEMS = (ROOT / "copimine-artifacts" / "items.yml").read_text(encoding="utf-8")


def test_election_restore_is_idempotent_and_cleans_player_state():
    assert "officialRestore.remove(playerUuid)" in ELECTION
    assert "officialRestore.computeIfAbsent" in ELECTION
    assert "pendingOfficialRestore" in ELECTION
    assert "officialRestore.remove(playerId)" in ELECTION
    assert "hasOfficialLogicalItem(player, \"PRESIDENT_MANDATE\")" in ELECTION


def test_election_official_items_cannot_move_to_any_external_storage_or_be_cloned():
    assert "PlayerArmorStandManipulateEvent" in ELECTION
    assert "InventoryCreativeEvent" in ELECTION
    assert "isProtectedOfficialItem(event.getCursor())" in ELECTION
    assert "top.getHolder() instanceof Player" in ELECTION
    assert "InventoryType.ENDER_CHEST" in ELECTION
    assert "isExternalOfficialStorage(view, event.getRawSlot())" in ELECTION
    assert "isBundleItem(current) || isBundleItem(cursor)" in ELECTION


def test_election_station_replacement_clears_stale_cancelled_flag():
    section = ELECTION[ELECTION.index("public void onBlockPlace"):ELECTION.index("public void onProtectedBreak")]
    assert "isPollingStationProtection(cached)" in section
    assert "event.setCancelled(false)" in section
    assert "reactivateRpVotingBlockAfterPlacementAsync" in section


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


def test_artifact_compass_is_explicit_teleport_item_with_fifteen_second_cooldown():
    assert "КОМПАС ТЕЛЕПОРТАЦИИ" in ITEMS.upper()
    assert "cooldown-seconds: 15" in ITEMS
    assert "getViewDistance" in ARTIFACTS or "view-distance" in ARTIFACTS
    assert "MAX_COMPASS_TELEPORT_DISTANCE" in ARTIFACTS


def test_artifact_permissions_and_world_mutations_are_closed():
    admin_block = ARTIFACTS[ARTIFACTS.index("private boolean isArtifactsAdmin"):ARTIFACTS.index("private boolean isRestrictedJuniorArtifactsAdmin")]
    assert "copimine.election.cik" not in admin_block
    assert "EntityExplodeEvent" in ARTIFACTS
    assert "BlockExplodeEvent" in ARTIFACTS
    assert "BlockPistonExtendEvent" in ARTIFACTS
    assert "Material.AIR" in ARTIFACTS
    assert "strikeLightningEffect" in ARTIFACTS


def test_artifact_item_meta_is_not_overwritten_after_attack_modifier():
    create = ARTIFACTS[ARTIFACTS.index("private ItemStack createOfficialItem"):ARTIFACTS.index("private double vanillaAttackDamage")]
    assert "ensureAttackDamageAttribute(var6" in create
    assert "ensureAttackDamageAttribute(var5" not in create
