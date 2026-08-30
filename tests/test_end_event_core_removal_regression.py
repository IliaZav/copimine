"""Regression guard for the operator GUI Core-removal path."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def _method_body(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


def test_gui_removal_has_a_coordinate_bound_visual_cleanup_before_state_is_cleared() -> None:
    source = MAIN.read_text(encoding="utf-8")
    remove = _method_body(source, "private void removeCore(CommandSender sender)", "private void resetEventSafely")

    assert "removeCoreVisualsAtConfiguredLayout();" in remove
    assert remove.index("removeCoreVisualsAtConfiguredLayout();") < remove.index('eventId = "";')

    cleanup = _method_body(
        source,
        "private int removeCoreVisualsAtConfiguredLayout()",
        "private boolean sameCoreTextDisplay(Entity entity, Block core)",
    ) + _method_body(
        source,
        "private boolean isCoreVisualAtConfiguredLayout(Entity entity, String kind, Block core, World world)",
        "private boolean sameCoreTextDisplay(Entity entity, Block core)",
    )
    for marker in (
        "world.getEntities()",
        "entity instanceof ItemDisplay",
        "entity instanceof TextDisplay",
        "entity instanceof BlockDisplay",
        "sameCoreOverlayBlock",
        "sameRuneOverlayBlock",
        "entity.remove();",
    ):
        assert marker in cleanup


def test_admin_hit_on_core_overlay_routes_to_the_same_removal_gui() -> None:
    source = MAIN.read_text(encoding="utf-8")

    assert "public void onCoreOverlayDamage(EntityDamageByEntityEvent event)" in source
    handler = _method_body(
        source,
        "public void onCoreOverlayDamage(EntityDamageByEntityEvent event)",
        "public void onOwnedDisplayDamage(EntityDamageEvent event)",
    )
    for marker in (
        "event.getDamager() instanceof Player player",
        "entity instanceof ItemDisplay",
        "sameCoreOverlayBlock",
        "event.setCancelled(true)",
        "openCoreRemovalConfirm(player)",
    ):
        assert marker in handler


def test_core_cleanup_catches_all_visual_entity_types_and_memorial_text() -> None:
    source = MAIN.read_text(encoding="utf-8")
    cleanup = _method_body(
        source,
        "private int removeCoreVisualsAtConfiguredLayout()",
        "private boolean sameCoreTextDisplay(Entity entity, Block core)",
    )

    for marker in (
        "TextDisplay",
        "ItemDisplay",
        "BlockDisplay",
        "missing/renamed role tag",
        "entity.remove();",
    ):
        assert marker in cleanup


def test_core_cleanup_does_not_require_a_persistent_role_tag() -> None:
    source = MAIN.read_text(encoding="utf-8")
    cleanup = _method_body(
        source,
        "private int removeCoreVisualsAtConfiguredLayout()",
        "private boolean isCoreVisualAtConfiguredLayout(Entity entity, String kind, Block core, World world)",
    ) + _method_body(
        source,
        "private boolean isCoreVisualAtConfiguredLayout(Entity entity, String kind, Block core, World world)",
        "private boolean sameCoreTextDisplay(Entity entity, Block core)",
    )

    assert "world.getEntities()" in cleanup
    assert "recognizedRole" not in cleanup
    assert "entity instanceof TextDisplay" in cleanup
    assert "entity instanceof ItemDisplay" in cleanup
    assert "entity instanceof BlockDisplay" in cleanup
    assert "sameCoreTextDisplay(entity, core)" in cleanup


def test_legacy_core_confirmation_floating_text_is_removed_without_removing_the_gui_title() -> None:
    source = MAIN.read_text(encoding="utf-8")
    assert "removeLegacyCoreRemovalTextDisplays()" in source
    assert "getText()" in source
    assert "подтверждение снятия core" in source.lower()
    assert "Component.text(\"Подтверждение снятия Core\"" in source


def test_legacy_core_confirmation_is_blocked_on_spawn_and_chunk_load() -> None:
    source = MAIN.read_text(encoding="utf-8")
    for marker in (
        "public void onLegacyCoreRemovalEntitySpawn(EntitySpawnEvent event)",
        "public void onLegacyCoreRemovalEntitiesLoad(EntitiesLoadEvent event)",
        "isLegacyCoreRemovalText(event.getEntity())",
        "entity.getCustomName()",
        "event.setCancelled(true)",
    ):
        assert marker in source


def test_victory_keeps_a_persisted_core_memorial_with_official_names() -> None:
    source = MAIN.read_text(encoding="utf-8")
    rebuild = _method_body(
        source,
        "private void rebuildPersistedVisuals()",
        "private int removeCoreVisualsAtConfiguredLayout()",
    )
    victory = _method_body(
        source,
        "private void checkVictoryRewardCompletion()",
        "private void resumeVictorySaga()",
    )
    memorial = _method_body(
        source,
        "private void spawnVictoryMemorialVisuals(World world)",
        "private void announceVictory()",
    ) + _method_body(
        source,
        "private String victoryMemorialNames()",
        "private void spawnVictoryMemorialVisuals(World world)",
    )

    for marker in (
        "VICTORY_COMPLETE",
        "officialBossDeathCommitted",
        "officialRewardRoster",
        "spawnVictoryMemorialVisuals(world)",
    ):
        assert marker in source
    assert "spawnVictoryMemorialVisuals(world)" in rebuild or "spawnVictoryMemorialVisuals(world)" in victory
    for marker in (
        "namesSource.stream()",
        "offlineName",
        "Победители",
        "EVENT_KIND_MEMORIAL",
        "spawnCoreOverlay(world, core)",
    ):
        assert marker in memorial
