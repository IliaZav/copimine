from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "copimine-artifacts" / "src" / "me" / "copimine" / "artifacts" / "CopiMineArtifacts.java"


def between(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    finish = source.index(end, begin + len(start))
    return source[begin:finish]


def test_donation_items_use_vanilla_transfer_and_do_not_quarantine_foreign_holders():
    source = JAVA.read_text(encoding="utf-8")

    boundary = between(source, "private boolean customShopItemsAreVanilla()", "private boolean isRepairLockedItem")
    assert "return true;" in boundary

    for start, end in (
        ("public void onForeignDonationPlace", "public void onForeignDonationPickup"),
        ("public void onForeignDonationPickup", "public void onForeignDonationDrop"),
        ("public void onForeignDonationDrop", "public void onDonationInventoryOpen"),
        ("public void onDonationInventoryOpen", "public void onOfficialBlockCook"),
    ):
        handler = between(source, start, end)
        assert "if (this.customShopItemsAreVanilla())" in handler
        assert handler.index("if (this.customShopItemsAreVanilla())") < handler.index("return;")


def test_donation_owner_is_entitlement_metadata_not_a_gameplay_use_gate():
    source = JAVA.read_text(encoding="utf-8")

    owner_logic = between(
        source,
        "private boolean isOwnerBoundGameplayItem",
        "private boolean isAdminOnlyCatalogItem",
    )
    assert "if (this.isDonationCatalogItem(itemId))" in owner_logic
    assert owner_logic.index("if (this.isDonationCatalogItem(itemId))") < owner_logic.index("return false;")
    assert "isAdminOnlyCatalogItem(itemId)" in owner_logic

    auth = between(source, "private CopiMineArtifacts.CatalogItem authenticCatalogItem", "private void ensureOfficialBindingAvailable")
    # Owner-bound checks may still protect admin-only/non-donation items. The
    # donation catalog path above deliberately returns false before this gate.
    assert "ownerBound && (" in auth
    assert "ownerBound && !var13.ownerUuid().equalsIgnoreCase(var12)" in auth


def test_transfers_keep_purchase_identity_without_rewriting_or_quarantining_the_item():
    source = JAVA.read_text(encoding="utf-8")
    create_body = between(source, "private ItemStack createOfficialItem", "private void decorateNarcoticRecipeBook")
    assert "keyOwnerUuid" in create_body
    assert "keyUniqueItemId" in create_body
    assert "artifact_owner_uuid" in source
    assert "foreign_donation_quarantined" in source  # migration/audit code may remain, but is unreachable for shop transfers
