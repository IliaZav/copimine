from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ECONOMY = (ROOT / "copimine-economy-core/src/me/copimine/economycore/CopiMineEconomyCore.java").read_text(encoding="utf-8")
ADMIN = (ROOT / "copimine-admin-plugin/src/me/copimine/ultimateplus/CopiMineUltimateAdminPlus.java").read_text(encoding="utf-8")
NARCOTICS_FACTORY = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/item/NarcoticItemFactory.java").read_text(encoding="utf-8")
NARCOTICS_DB = (ROOT / "copimine-narcotics/src/me/copimine/narcotics/db/NarcoticsDatabase.java").read_text(encoding="utf-8")


def test_ar_is_fungible_and_stack_identity_does_not_bind_to_quantity() -> None:
    assert "AR_FUNGIBLE:" in ECONOMY
    assert "OFFICIAL_AR_FUNGIBLE_SIGNATURE_VERSION" in ECONOMY
    assert "officialArFungibleSignature" in ECONOMY
    create = ECONOMY[ECONOMY.index("private ItemStack createOfficialArStack"):ECONOMY.index("private String officialArSerial")]
    assert "UUID.randomUUID()" not in create
    assert "arFungibleSerial(arMaterial)" in create
    assert "int denomination = 1" in create


def test_silk_touch_replaces_the_vanilla_drop_without_unique_world_metadata() -> None:
    assert "ar_world_drop_token" not in ECONOMY
    assert "authorizeWorldDrop" not in ECONOMY

    start = ADMIN.index("public void onArDrop(BlockDropItemEvent e)")
    finish = ADMIN.index("public void onBook(PlayerEditBookEvent e)", start)
    drop = ADMIN[start:finish]
    assert "e.setCancelled" not in drop
    assert "dropItemNaturally" not in drop
    assert "createPreparedStack" in drop
    assert "item.setItemStack(official)" in drop


def test_only_valid_ar_is_canonicalized_to_the_single_stackable_form() -> None:
    assert "private ItemStack canonicalOfficialArStack" in ECONOMY
    canonical = ECONOMY[
        ECONOMY.index("private ItemStack canonicalOfficialArStack"):
        ECONOMY.index("private String officialArSerial", ECONOMY.index("private ItemStack canonicalOfficialArStack"))
    ]
    assert "if (!isOfficialAr(stack))" in canonical
    assert "createOfficialArStack(stack.getType(), stack.getAmount()" in canonical
    assert "officialArIssuanceTokenKey" in canonical


def test_ar_has_no_automatic_inventory_resync_during_player_actions() -> None:
    """AR must not be rewritten or force-synced from an inventory event path."""
    assert "normalizeFungibleArOnPickup" not in ECONOMY
    assert "void normalizePlayer(Player player)" not in ECONOMY
    assert "officialArService.normalizePlayer(player)" not in ECONOMY


def test_narcotics_are_stackable_with_a_fungible_signed_key() -> None:
    create = NARCOTICS_FACTORY[NARCOTICS_FACTORY.index("public ItemStack createOfficialItem"):NARCOTICS_FACTORY.index("public NarcoticDefinition resolveOfficial")]
    resolve = NARCOTICS_FACTORY[NARCOTICS_FACTORY.index("public NarcoticDefinition resolveOfficial"):NARCOTICS_FACTORY.index("public boolean isOfficialFinishedItem")]
    assert "NARCOTIC_STACK:" in NARCOTICS_FACTORY
    assert "int stackAmount = Math.max(1, Math.min(Math.max(1, base.getMaxStackSize()), amount))" in create
    assert "UUID.randomUUID()" not in create
    assert "stack.getAmount() != 1" not in resolve
    assert "boolean stackIdentity" in resolve
    assert "signatureVersion == STACK_SIGNATURE_VERSION" in resolve
    assert "currentQuantity >= Math.max(1, quantityBefore)" in (
        (ROOT / "copimine-narcotics/src/me/copimine/narcotics/CopiMineNarcotics.java").read_text(encoding="utf-8")
    )


def test_narcotic_consumption_reservations_are_per_operation_not_per_stack() -> None:
    assert "reservation_id" in NARCOTICS_DB
    assert "PRIMARY KEY (reservation_id)" in NARCOTICS_DB
    assert "instance_id TEXT NOT NULL" in NARCOTICS_DB
    assert "status='CONSUMED'" not in NARCOTICS_DB[NARCOTICS_DB.index("public CompletableFuture<Void> savePlayerState"):NARCOTICS_DB.index("public CompletableFuture<Boolean> tombstoneBrewingState")]
