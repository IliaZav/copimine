from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ELECTION = (ROOT / "copimine-election-core/src/me/copimine/electioncore/CopiMineElectionCore.java").read_text(
    encoding="utf-8"
)


def section(source: str, start: str, end: str) -> str:
    return source[source.index(start) : source.index(end, source.index(start))]


def test_president_mandate_creative_transport_allows_only_player_inventory_slots():
    handler = section(
        ELECTION,
        "public void onCreativeInventoryAction(InventoryCreativeEvent event)",
        "public void onMenuClose",
    )

    assert "creativeOfficialActionLeavesPlayerInventory" in handler
    assert "event.getRawSlot() < 0" in handler
    assert "event.getClick() == ClickType.DROP" in handler
    assert "event.setCancelled(true)" in handler
    assert "updateInventory" in handler

    helper = section(
        ELECTION,
        "private boolean creativeOfficialActionLeavesPlayerInventory",
        "private boolean isExternalOfficialStorage",
    )
    assert "event.getClickedInventory() == player.getInventory()" in helper
    assert "InventoryType.CRAFTING" in helper
    assert "event.getRawSlot() >= top.getSize()" in helper


def test_mandate_drop_path_remains_owned_by_election_core():
    handler = section(ELECTION, "public void onDrop(PlayerDropItemEvent event)", "public void onMoveOfficial")
    assert "isPresidentMandate(dropped)" in handler
    assert "event.setCancelled(true)" in handler
    assert "player.updateInventory()" in handler
