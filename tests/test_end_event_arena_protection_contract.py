from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
CONFIG = (ROOT / "copimine-end-event/config.yml").read_text(encoding="utf-8")
EVENT_CONFIG = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")
PLUGIN = (ROOT / "copimine-end-event/plugin.yml").read_text(encoding="utf-8")


def _method_body(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


def test_default_arena_is_twenty_blocks_horizontal_and_three_blocks_vertical() -> None:
    assert "radius: 20.0" in CONFIG
    assert "vertical-radius: 3.0" in CONFIG
    assert "double arenaVerticalRadius" in EVENT_CONFIG
    assert 'positiveDouble(arena, "vertical-radius")' in EVENT_CONFIG

    set_core = _method_body(MAIN, "private void setCore(Player player, int players)", "private void calculateAndPlacePads")
    assert "config.arenaVerticalRadius()" in set_core
    assert "coreY - 16" not in set_core
    assert "coreY + 16" not in set_core

    layout = _method_body(MAIN, "private void handleLayout(CommandSender sender, String group, String[] args)", "private EventLayoutState.Point pointAt")
    assert "config.arenaVerticalRadius()" in layout
    assert "coreY - 16" not in layout
    assert "coreY + 16" not in layout


def test_core_setup_and_boundary_command_start_a_visible_wireframe_preview() -> None:
    assert "DEFAULT_ARENA_PREVIEW_SECONDS = 10" in MAIN
    assert "showArenaBoundary(player, DEFAULT_ARENA_PREVIEW_SECONDS)" in MAIN
    assert "drawArenaBoundaryFrame" in MAIN
    assert "Particle.DUST" in MAIN
    assert "arenaBoundaryTask" in MAIN
    assert "runTaskTimer(this" in MAIN
    assert "cancelArenaBoundaryPreview" in MAIN

    layout = _method_body(MAIN, "private void handleLayout(CommandSender sender, String group, String[] args)", "private EventLayoutState.Point pointAt")
    assert '"border".equalsIgnoreCase(args[1])' in layout or '"boundary".equalsIgnoreCase(args[1])' in layout
    assert "showArenaBoundary(sender" in layout
    assert "parseInt(args" in layout
    assert "arena border <seconds>" in layout
    assert "Линии:" in MAIN


def test_arena_boundary_draws_top_bottom_edges_and_vertical_edges() -> None:
    draw = _method_body(MAIN, "private void drawArenaBoundaryFrame(World world)", "private void spawnArenaBoundaryPoint")
    assert "arenaMinX" in draw and "arenaMaxX" in draw
    assert "arenaMinY" in draw and "arenaMaxY" in draw
    assert "arenaMinZ" in draw and "arenaMaxZ" in draw
    assert "spawnArenaBoundaryPoint(world" in draw
    assert "for (double x" in draw
    assert "for (double z" in draw
    assert "for (double y" in draw


def test_breaking_core_is_cancelled_and_admin_gets_explicit_confirmation_gui() -> None:
    block_break = _method_body(MAIN, "public void onArenaBlockBreak(BlockBreakEvent event)", "public void onArenaBlockPlace")
    assert "sameCore(event.getBlock())" in block_break
    assert "event.setCancelled(true)" in block_break
    assert "openCoreRemovalConfirm" in block_break
    assert "event.getPlayer().isOp()" in block_break
    assert "isAdmin(event.getPlayer())" in block_break

    for marker in (
        "CoreRemovalConfirmHolder",
        "onEndEventInventoryClick",
        "onEndEventInventoryDrag",
        "Подтвердить снятие Core",
        "Отмена",
        "removeCore(player)",
        "restoreCoreAndPads()",
        "cancelSessionTasks()",
    ):
        assert marker in MAIN


def test_confirmation_gui_cannot_be_modified_or_used_by_another_player() -> None:
    click = _method_body(MAIN, "public void onEndEventInventoryClick(InventoryClickEvent event)", "public void onEndEventInventoryDrag")
    drag = _method_body(MAIN, "public void onEndEventInventoryDrag(InventoryDragEvent event)", "public void onArenaBlockBreak")
    assert "event.setCancelled(true)" in click
    assert "holder.ownerUuid()" in click
    assert "holder.eventId()" in click
    assert "holder.generation()" in click
    assert "isAdmin(player)" in click or "player.isOp()" in click
    assert "event.setCancelled(true)" in drag
    assert "CoreRemovalConfirmHolder" in drag


def test_runes_are_repaired_while_collect_players_phase_is_live() -> None:
    tick = _method_body(MAIN, "private void tick()", "private void updatePadOccupancy")
    occupancy = _method_body(MAIN, "private void updatePadOccupancy()", "private String padKey")
    maintenance = _method_body(MAIN, "private void maintainRitualVisuals()", "private String padKey")
    assert "maintainRitualVisuals();" in tick or "maintainRitualVisuals();" in occupancy
    assert "READY_FOR_PLAYERS" in maintenance
    assert "COUNTDOWN" in maintenance
    assert "EVENT_KIND_PAD" in maintenance
    assert "rebuildPersistedVisuals();" in maintenance
    assert "pads.size()" in maintenance


def test_command_usage_and_tab_completion_advertise_boundary_preview() -> None:
    assert "arena border <seconds>" in PLUGIN
    tab = _method_body(MAIN, "public List<String> onTabComplete", "private static final class CoreRemovalConfirmHolder")
    assert 'case "arena" -> List.of("pos1", "pos2", "info", "clear", "border", "boundary")' in tab
