from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def extract_method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise AssertionError(f"Unterminated method: {signature}")


def test_wave7_recovery_loads_arena_chunks_before_indexing_persisted_entities():
    source = SOURCE.read_text(encoding="utf-8")
    restore = extract_method(source, "private void restorePersistedCombatRuntime()")
    load_chunks = restore.index("ensureOverlayChunksLoaded(world)")
    reindex = restore.index("reindexPersistedCombatEntities()")
    assert load_chunks < reindex, (
        "Wave 7 trial entity chunks must load before the startup PDC entity scan; "
        "Paper starts with zero persistent chunks and must not mistake an unloaded actor for a loss"
    )


def test_wave7_actor_chunk_tickets_cover_the_bounded_arena_only_while_wave7_is_owned():
    source = SOURCE.read_text(encoding="utf-8")
    overlay = extract_method(source, "private void ensureOverlayChunksLoaded(World world)")
    assert "shouldRetainWave7ActorChunksLoaded()" in overlay
    assert "boundedCombatRadius(config.arenaRadius())" in overlay
    assert "world.getChunkAt(chunkX, chunkZ, true)" in overlay
    assert "chunk.addPluginChunkTicket(this)" in overlay
    assert "chunk.getEntities()" in overlay
    assert "indexPersistedNamedWave7Actor(entity)" in overlay
    assert "core.getBlockX() - radius" in overlay
    assert "core.getBlockX() + radius" in overlay
    assert "core.getBlockZ() - radius" in overlay
    assert "core.getBlockZ() + radius" in overlay


def test_wave7_entities_loaded_after_bootstrap_rejoin_only_current_schema_actors():
    source = SOURCE.read_text(encoding="utf-8")
    handler = extract_method(source, "public void onPersistedCombatEntitiesLoad(EntitiesLoadEvent event)")
    indexer = extract_method(source, "private boolean indexPersistedNamedWave7Actor(Entity entity)")
    normalized_indexer = " ".join(indexer.split())
    assert "indexPersistedNamedWave7Actor(entity)" in handler
    for required_guard in (
        "ownedBySession(entity, eventId, generation)",
        "readInt(entity, keyWave, 0) != 7",
        "EVENT_KIND_REALITY_SPLIT_TRIAL",
        "EVENT_KIND_REALITY_SPLIT_OBJECTIVE",
        '"2".equals(readString(entity, new NamespacedKey(this, "end_event_named_trial_schema")))',
        "role.isBlank()",
        "realitySplitTrialEntityUuids.add(entity.getUniqueId())",
        "registerOwnedEntity(entity)",
    ):
        assert required_guard in normalized_indexer
