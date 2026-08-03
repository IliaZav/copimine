"""Regression contracts for the current release-readiness report.

These tests intentionally inspect the source boundaries that are easy to
reopen by a later refactor.  Runtime tests are added alongside them where the
plugin API makes that practical.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WEB_BACKEND = (ROOT / "admin-web/backend/main.py").read_text(encoding="utf-8")
ECONOMY = (ROOT / "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java").read_text(encoding="utf-8")
ARTIFACTS = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(encoding="utf-8")
ELECTION = (ROOT / "copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java").read_text(encoding="utf-8")
NARCOTICS = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/cauldron/CauldronBrewingService.java").read_text(encoding="utf-8")
NARCOTICS_DB = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java").read_text(encoding="utf-8")
WORLD = (ROOT / "copimine-world-core/src/me/copimine/worldcore/CopiMineWorldCore.java").read_text(encoding="utf-8")
AUTH = (ROOT / "minecraft/server/plugins/AuthEffects/src/main/java/me/serverrp/autheffects/AuthEffectsPlugin.java").read_text(encoding="utf-8")
ADMIN = (ROOT / "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java").read_text(encoding="utf-8")
CI = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
INSTALLER = (ROOT / "deploy/ubuntu/install_release.sh").read_text(encoding="utf-8")
UNPACK = (ROOT / "deploy/ubuntu/copimine_unpack_and_verify.sh").read_text(encoding="utf-8")
FULL_REPLACE = (ROOT / "deploy/ubuntu/copimine_full_replace.sh").read_text(encoding="utf-8")
COMMON = (ROOT / "deploy/shared/common.sh").read_text(encoding="utf-8")
NGINX_HTTPS = (ROOT / "admin-web/deploy/nginx-copimine-admin-https.conf").read_text(encoding="utf-8")


def test_ci_runs_the_entire_python_suite_with_real_pytest_and_junit_output():
    assert "python -m pytest" in CI
    assert "--junitxml" in CI
    assert "Get-ChildItem tests -Filter 'test_*.py'" not in CI
    assert "-SkipReleaseArtifacts" not in CI


def test_ci_pins_external_compile_artifacts_with_independent_sha256_digests():
    assert "Get-FileHash -Algorithm SHA256" in CI
    assert ".jar.sha1" not in CI
    assert "SHA1" not in CI
    for digest in (
        "b8df3e7f2739e21072a5263e41b307bd30cfa8d8f72258ce27973167f8ad07c0",
        "c80905a70192b4d1d9fd5d2ab7cf7879b928c71d5c0bf3715f8806dc338d4c46",
        "fde03259f5af6938f3c33eeb4d814000a1adabf1d2304ce14970be81f609a437",
    ):
        assert digest in CI


def test_release_verification_does_not_bootstrap_trust_from_upload_directory():
    assert 'source_path="${2:-$PROJECT_ROOT/deploy/release-signing.allowed}"' in INSTALLER
    assert "controlled upload directory" not in INSTALLER
    assert "pinned release signing key" in INSTALLER
    for helper in (UNPACK, FULL_REPLACE):
        assert "/etc/copimine/release-signing.allowed" in helper
        assert "/etc/copimine/verify_payload_manifest.py" in helper
    assert "copimine-install-release" in COMMON
    assert "copimine_harden_release_ownership" in COMMON
    assert "chown -R \"$COPIMINE_APP_USER:$COPIMINE_APP_GROUP\" \"$COPIMINE_ROOT\"" not in COMMON


def test_ubuntu_replace_backups_redact_runtime_secrets_and_require_db_backup():
    for helper in (UNPACK, FULL_REPLACE):
        assert 'cp -a "$PROJECT_ROOT" "$backup_dir/copimine-pre-replace"' not in helper
        assert 'copimine-pre-replace.tar.gz' in helper
        assert '--exclude="$root_name/admin-web/.env"' in helper
        assert '--exclude="$root_name/admin-web/data"' in helper
        assert 'sha256sum "$backup_dir/copimine-pre-replace.tar.gz"' in helper
    assert 'command -v pg_dump >/dev/null 2>&1 || die "pg_dump is required' in UNPACK
    assert 'command -v pg_dump >/dev/null 2>&1 || fail "pg_dump is required' in FULL_REPLACE
    assert 'WARNING: PostgreSQL backup skipped.' not in UNPACK


def test_ar_deposit_credit_and_asset_lifecycle_share_one_transaction():
    assert "commitArDepositAtomically" in ECONOMY
    atomic = ECONOMY[ECONOMY.index("commitArDepositAtomically"):ECONOMY.index("private List<ArDepositIntent>")]
    assert "cmv8_ar_asset_movements" in atomic
    assert "FOR UPDATE" in atomic
    assert "creditAccountAsync" not in ECONOMY[ECONOMY.index("advanceArDepositIntent"):ECONOMY.index("advanceArDepositIntent") + 7000]
    assert "INSERT INTO cmv8_ar_assets" not in atomic


def test_physical_ar_issuance_requires_an_idempotent_durable_prepare_step():
    assert "cmv8_ar_issuance_intents" in ECONOMY
    assert "prepareIssuanceAsync" in ECONOMY
    assert "createPreparedStack" in ECONOMY
    assert "registerArAssetAsync" not in ECONOMY
    assert ".createStack(" not in ECONOMY + ADMIN
    assert "removeAmount(" not in ECONOMY + ADMIN
    mining = ADMIN[ADMIN.index("if (eligible && !reissuePlaced)"):ADMIN.index("int amount=0", ADMIN.index("if (eligible && !reissuePlaced)"))]
    assert "prepareIssuanceAsync" in mining
    assert "e.setCancelled(true)" in mining
    assert "dropItemNaturally" in mining


def test_physical_ar_theft_does_not_remove_one_item_and_refund_separately():
    theft = ARTIFACTS[ARTIFACTS.index("private void tryRareArTheft"):ARTIFACTS.index("private boolean isArtifactsAdmin")]
    assert "removeAmount" not in theft
    assert "createStack" not in theft
    assert "dropItemNaturally" not in theft
    assert "legacyTryRareArTheft" not in ARTIFACTS


def test_bulk_artifact_delivery_guard_is_released_by_delivery_completion():
    claim = ARTIFACTS[ARTIFACTS.index("private void claimPendingV2"):ARTIFACTS.index("private void claimOne")]
    assert "AtomicInteger claimAllRemaining" in claim
    assert "claimAllCompletion" in claim
    assert "runTaskLater(this, () -> this.claimAllInFlight.remove" not in claim
    assert "deliverPendingRowV2(var1, var7, this.claimAllCompletion" in claim
    assert "deliverDonationClaimRowV2(var1, var10, this.claimAllCompletion" in claim


def test_personal_pin_is_verified_inside_the_same_mutation_transaction():
    assert "verifyPersonalPinForMutation" in ECONOMY
    style_start = ECONOMY.index("private TxnResult artifactStyleTxn")
    style = ECONOMY[style_start:style_start + 12000]
    assert "verifyPersonalPinForMutation(" in style
    assert style.index("verifyPersonalPinForMutation(") < style.index("INSERT INTO cmv4_bank_ledger")


def test_ar_withdrawal_delivery_conflict_matches_the_partial_unique_index():
    assert "CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_pending_ar_idempotency ON cmv4_pending_ar_settlements(idempotency_key) WHERE idempotency_key<>''" in ECONOMY
    pending = ECONOMY[ECONOMY.index("cmv4_pending_ar_settlements"):]
    assert "ON CONFLICT (idempotency_key) WHERE idempotency_key <> '' DO NOTHING" in pending
    assert "ON CONFLICT(idempotency_key) DO NOTHING" not in pending


def test_treasury_pin_status_and_first_setup_treat_empty_hash_as_unset():
    assert "COALESCE(pin_hash,'')<>''" in WEB_BACKEND
    setup = WEB_BACKEND[WEB_BACKEND.index("def set_player_pin_sync"):WEB_BACKEND.index("def player_site_bank_profile_sync")]
    assert 'row_get(current, "pin_hash", "")' in setup
    assert "Treasury PIN was not persisted" in setup
    admin_setup = WEB_BACKEND[WEB_BACKEND.index("def admin_set_treasury_pin_sync"):WEB_BACKEND.index("def admin_reset_treasury_sync")]
    assert "Treasury PIN was not persisted" in admin_setup


def test_election_repair_never_overrides_an_external_protection_cancel():
    section = ELECTION[ELECTION.index("public void onBlockPlace"):ELECTION.index("public void onProtectedBreak")]
    assert "event.setCancelled(false)" not in section
    assert "event.isCancelled()" in section
    assert "event.getPlayer()" not in section[section.index("runTask"):]


def test_election_boolean_active_flags_are_not_treated_as_zero():
    assert "if (value instanceof Boolean booleanValue)" in ELECTION
    assert "return booleanValue ? 1 : 0;" in ELECTION


def test_election_station_removal_uses_persisted_material_and_preserves_replacements():
    removal = ELECTION[ELECTION.index("private void removePollingStation"):ELECTION.index("private void issueOrRefreshSeal")]
    assert "SELECT expected_material FROM election_voting_blocks WHERE id=?" in removal
    assert "current.getBlock().getType() == finalExpectedMaterial" in removal
    assert "setType(Material.AIR, false)" in removal


def test_election_and_background_workers_are_bounded_and_shutdown_gracefully():
    assert "ArrayBlockingQueue" in ELECTION
    assert "shutdownNow()" not in ELECTION


def test_brewing_owner_is_preserved_and_final_ingredient_is_durable_before_consume():
    assert "prepareBrewingCompletionIntent" in NARCOTICS
    assert "base.ownerUuid()" in NARCOTICS
    assert "nearby.damage" not in NARCOTICS
    final_section = NARCOTICS[NARCOTICS.index("NarcoticDefinition exact"):NARCOTICS.index("private boolean queueIngredients")]
    assert final_section.index("prepareBrewingCompletionIntent") < final_section.index("consumeOneExact")


def test_failed_multi_ingredient_brew_refunds_every_consumed_ingredient():
    refund = NARCOTICS[NARCOTICS.index("private void refundFailedIngredient"):NARCOTICS.index("private void clearState")]
    assert "frozen.getLast()" not in refund
    assert "queuePendingIngredientRefunds(ownerUuid, frozen)" in refund
    assert "public CompletableFuture<Void> queuePendingIngredientRefunds" in NARCOTICS_DB
    assert "statement.addBatch()" in NARCOTICS_DB
    assert "for (int index = 0; index < rows.size(); index++)" in NARCOTICS_DB


def test_brewing_is_order_agnostic_and_shared_between_players():
    # Recipe matching is deliberately count-based, not positional.  The
    # owner UUID remains a refund/delivery identity, not an access lock.
    recipe = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/recipe/NarcoticsRecipeService.java").read_text(encoding="utf-8")
    assert "matchesCounts(definition.recipeCounts(), counts(ingredientEntries))" in recipe
    assert "base.ownerUuid() != null && !base.ownerUuid().equals(playerUuid)" not in NARCOTICS
    assert "Этот котёл уже используется другим игроком" not in NARCOTICS


def test_brewing_completion_survives_legacy_schema_and_stops_particle_loop():
    completion = NARCOTICS_DB[NARCOTICS_DB.index("prepareBrewingCompletionIntent"):NARCOTICS_DB.index("/** Reserve one unit")]
    assert "ON CONFLICT DO NOTHING" in completion
    assert "ON CONFLICT (world_name,x,y,z,state_version) DO UPDATE" not in completion
    assert "completionInFlight.contains(key)" in NARCOTICS


def test_world_teleport_tokens_are_bound_and_border_replacement_is_atomic():
    assert "TeleportToken" in WORLD
    assert "Files.move" in WORLD
    assert "ATOMIC_MOVE" in WORLD
    assert "FileChannel" in WORLD


def test_auth_effects_covers_attacker_side_events_and_fails_closed_without_login_logout_api():
    assert "EntityDamageByEntityEvent" in AUTH
    assert "Projectile" in AUTH
    assert "PlayerInteractEntityEvent" in AUTH
    assert "login hook" in AUTH.lower()
    assert "logout hook" in AUTH.lower()


def test_release_reset_clears_game_issuance_state_but_keeps_bank_pin_rows():
    reset = (ROOT / "db/runtime/reset_game_state_preserve_accounts.sql").read_text(encoding="utf-8")
    clean = (ROOT / "db/runtime/clean_world_state.sql").read_text(encoding="utf-8")
    for table in ("cmv8_ar_asset_movements", "cmv8_ar_issuance_intents"):
        assert f"'{table}'" in reset
        assert f"'{table}'" in clean
    clean_tables = clean[clean.index("reset_tables"):]
    for table in ("bank_pin_hashes", "bank_account_pins"):
        assert f"'{table}'" not in clean_tables


def test_deploy_handles_custom_level_name_worlds_and_fresh_paper_cache():
    for helper in (UNPACK, FULL_REPLACE):
        assert "configured_world_base" in helper
        assert "runtime_world_paths" in helper
        assert '"${base}_nether"' in helper
        assert '"${base}_the_end"' in helper
        assert "runtime_world_paths \"$PROJECT_ROOT/minecraft/server\"" in helper or "runtime_world_paths \"$server_dir\"" in helper
    assert "copimine_configured_world_base" in COMMON
    assert "copimine_runtime_world_names" in COMMON
    assert '"$COPIMINE_SERVER_DIR/cache"' in COMMON
    for runtime_dir in ("config", "libraries", "versions", "emotes"):
        assert f'"$COPIMINE_SERVER_DIR/{runtime_dir}"' in COMMON
    for runtime_file in (
        "bukkit.yml",
        "commands.yml",
        "spigot.yml",
        "paper.yml",
        "paper-world-defaults.yml",
        "pufferfish.yml",
        "purpur.yml",
        "permissions.yml",
        "help.yml",
    ):
        assert f'"$COPIMINE_SERVER_DIR/{runtime_file}"' in COMMON
    assert '"$COPIMINE_SERVER_DIR/crash-reports"' in COMMON
    assert 'chown "$COPIMINE_APP_USER:$COPIMINE_APP_GROUP" "$COPIMINE_SERVER_DIR"' in COMMON
    assert 'chown "$COPIMINE_APP_USER:$COPIMINE_APP_GROUP" "$COPIMINE_SERVER_DIR/plugins"' in COMMON
    assert 'chown "$runtime_user:$runtime_group" "$COPIMINE_SERVER_PROPERTIES"' in COMMON
    assert "copimine_harden_release_ownership" in COMMON[COMMON.index("copimine_refresh_release_artifacts"):]
    hardening_unit = (ROOT / "admin-web/deploy/copimine-game-hardening.service").read_text(encoding="utf-8")
    assert "TimeoutStartSec=960" in hardening_unit
    assert "COPIMINE_GAME_HARDENING_RCON_TIMEOUT_SECONDS:-900" in COMMON


def test_windows_release_helpers_target_the_current_ssh_endpoint():
    bat = (ROOT / "deploy/windows/send_copimine_release.bat").read_text(encoding="utf-8")
    ps1 = (ROOT / "deploy/windows/send_copimine_release.ps1").read_text(encoding="utf-8")
    uploader = (ROOT / "deploy/windows/upload_release.ps1").read_text(encoding="utf-8")
    for source in (bat, ps1, uploader):
        assert "90.188.115.155" in source
        assert "2222" in source


def test_public_site_has_only_the_direct_https_443_listener():
    assert "listen 443 ssl http2 default_server;" in NGINX_HTTPS
    assert "proxy_pass http://127.0.0.1:8090;" in NGINX_HTTPS
    assert "listen 80" not in NGINX_HTTPS
    assert "18080" not in NGINX_HTTPS
    for helper in (UNPACK, FULL_REPLACE):
        assert "nginx-copimine-admin-https.conf" in helper
        assert "https://copimine.ru/downloads/CopiMineMods.zip" in helper
        assert "https://copimine.ru/resourcepacks/CopiMineResourcePack.zip" in helper
    assert "Plain HTTP public mode is retired" in COMMON
    assert "Only HTTPS public access on port 443 is supported" in COMMON


def test_release_retires_legacy_public_vhost_before_nginx_validation():
    assert "/etc/nginx/sites-available/copimine-public" in COMMON
    assert "/etc/nginx/sites-enabled/copimine-public" in COMMON
    assert 'rm -f -- "$legacy_path"' in COMMON
    assert "legacy_backup_root=\"/opt/copimine-backups/nginx-legacy-" in COMMON
    assert "/etc/nginx/sites-available/copimine-admin.conf" in INSTALLER
    assert "/etc/nginx/sites-enabled/copimine-admin.conf" in INSTALLER
    assert "ports 80 and 18080 are disabled" in INSTALLER


def test_https_release_smoke_checks_bypass_retired_proxy_environment():
    assert "curl --noproxy '*'" in COMMON
    for helper in (UNPACK, FULL_REPLACE):
        assert "curl --noproxy '*' -kfsS" in helper
        assert "--resolve copimine.ru:443:127.0.0.1" in helper


def test_atm_visual_has_a_visible_label_and_repairs_or_removes_it_with_the_atm():
    assert "import org.bukkit.entity.TextDisplay;" in ECONOMY
    assert 'stand.setCustomName(color("&eБанкомат"));' in ECONOMY
    assert '"ATM_TITLE_DISPLAY"' in ECONOMY
    assert "ensureAtmTitleDisplay(location, linkedId);" in ECONOMY
    assert "cleanupAtmTitleDisplay(" in ECONOMY


def test_atm_visual_has_a_reliable_fallback_for_clients_without_text_display_support():
    assert 'spawn(location, ArmorStand.class' in ECONOMY
    assert 'stand.setCustomName(color("&eБанкомат"));' in ECONOMY
    assert "stand.setCustomNameVisible(true);" in ECONOMY
    assert "entity instanceof ArmorStand" in ECONOMY


def test_artifact_shop_uses_the_treasury_when_no_president_is_active():
    assert "return treasuryRevenueRecipient();" in ARTIFACTS
    assert '"NO_ACTIVE_PRESIDENT"' in ARTIFACTS
    assert "EMPTY_UUID," in ARTIFACTS
    assert "PRESIDENT_BUDGET_ACCOUNT_ID" in ARTIFACTS


def test_artifact_shop_requires_only_a_ready_economy_bridge_not_a_president():
    purchase = ARTIFACTS[ARTIFACTS.index("ShopRevenueRecipient var6x"):ARTIFACTS.index("String var6 = UUID.randomUUID", ARTIFACTS.index("ShopRevenueRecipient var6x"))]
    assert "if (this.bridge == null || var6x == null)" in purchase
    assert "revenueRecipient().budgetAccountId()" not in purchase


def test_artifacts_items_are_explicitly_delegated_before_adminplus_inventory_guards():
    assert "private boolean artifactsCoreOwns(ItemStack stack)" in ADMIN
    click = ADMIN[ADMIN.index("public void onProtectedItemClick"):ADMIN.index("public void onProtectedItemDrag")]
    drag = ADMIN[ADMIN.index("public void onProtectedItemDrag"):ADMIN.index("public void onProtectedItemMove")]
    move = ADMIN[ADMIN.index("public void onProtectedItemMove"):ADMIN.index("public void onOfficialArCreative")]
    assert "artifactsCoreOwns(cursor,current,hotbar)" in click
    assert "artifactsCoreOwns(e.getOldCursor())" in drag
    assert "artifactsCoreOwns(e.getItem())" in move
    assert '"copimineartifacts"' in ADMIN
    assert '"artifact_unique_item_id"' in ADMIN


def test_president_mandate_never_enters_adminplus_drop_or_death_recovery_queues():
    drop = ADMIN[ADMIN.index("public void onDrop"):ADMIN.index("public void onSealDropLowest")]
    death = ADMIN[ADMIN.index("public void onOfficialItemDeath"):ADMIN.index("public void onOfficialItemRespawn")]
    restore = ADMIN[ADMIN.index("private void restorePendingOfficialItems"):ADMIN.index("private boolean isProtectedOfficialItem")]
    assert "if(isPresidentMandate(" in drop
    assert "if(isPresidentMandate(drop)) continue;" in death
    assert "if(isPresidentMandate(item))continue;" in restore


def test_silk_touch_ar_certification_handles_a_cancelled_or_empty_vanilla_drop_event():
    assert "@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)\n    public void onArDrop" in ADMIN
    drop = ADMIN[ADMIN.index("public void onArDrop"):ADMIN.index("private void", ADMIN.index("public void onArDrop"))]
    assert "naturalSilkTouchOreAmount(e)" in drop
    assert "return Math.max(1" in ADMIN


def test_brewing_completion_retries_until_the_durable_tombstone_is_resolved():
    assert "public CompletableFuture<Boolean> brewingCompletionResolved" in NARCOTICS_DB
    assert "database.brewingCompletionResolved" in NARCOTICS
    assert "scheduleBrewingCompletionRetry" in NARCOTICS
    finish = NARCOTICS[NARCOTICS.index("private void finishBrewing"):NARCOTICS.index("private void simulateWrongMixExplosion")]
    assert "if (resolveError == null && Boolean.TRUE.equals(resolved))" in finish
    assert "scheduleBrewingCompletionRetry" in finish
