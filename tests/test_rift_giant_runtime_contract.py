"""Source contract for Rift Giant phase, ability, and summon ownership guards."""
from pathlib import Path


SOURCE = Path(__file__).resolve().parents[1] / (
    "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
)


def method(source: str, signature: str) -> str:
    start = source.index("    " + signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_rift_giant_aura_is_limited_to_its_active_wave():
    source = SOURCE.read_text(encoding="utf-8")
    aura = method(source, "private void tickRiftGiantAura(")

    gate = "RiftGiantPolicy.shouldRefreshAura(isWaveAiCombatPhase(giantWave), now,"
    assert gate in aura
    assert aura.index(gate) < aura.index("world.spawnParticle")


def test_giant_aura_refresh_and_cleanup_preserve_unowned_effects():
    source = SOURCE.read_text(encoding="utf-8")
    aura = method(source, "private void tickRiftGiantAura(")
    cleanup = method(source, "private void cleanupRiftGiantRuntime(")
    refresh = method(source, "private void refreshRiftGiantAuraEffect(")
    remove = method(source, "private void removeOwnedRiftGiantAuraEffect(")

    assert "refreshRiftGiantAuraEffect(ally, PotionEffectType.REGENERATION" in aura
    assert "refreshRiftGiantAuraEffect(ally, PotionEffectType.RESISTANCE" in aura
    assert "RiftGiantAuraEffectPolicy.mayApply(" in refresh
    assert "RiftGiantAuraEffectPolicy.mayRemove(" in remove
    assert "removeOwnedRiftGiantAuraEffect(living, PotionEffectType.REGENERATION, sourceId)" in cleanup
    assert "removeOwnedRiftGiantAuraEffect(living, PotionEffectType.RESISTANCE, sourceId)" in cleanup
    assert "living.removePotionEffect(PotionEffectType.REGENERATION)" not in cleanup
    assert "living.removePotionEffect(PotionEffectType.RESISTANCE)" not in cleanup


def test_pending_summon_telegraph_owns_the_giants_major_ability_slot():
    source = SOURCE.read_text(encoding="utf-8")
    offense = method(source, "private boolean tickRiftGiantOffense(")

    assert "boolean summonTelegraphPending = riftGiantSummonCharges.containsKey(giantId);" in offense
    assert "now >= strikeCooldown, false, summonTelegraphPending)" in offense


def test_unloaded_summons_are_reconciled_against_loaded_giant_owners():
    source = SOURCE.read_text(encoding="utf-8")
    load = method(source, "public void onPersistedCombatEntitiesLoad(")
    cleanup = method(source, "private void cleanupRiftGiantRuntime(")

    assert "registerLoadedRiftGiantOwners(event.getEntities())" in load
    assert "retireOrphanedRiftGiantSummons(" in load and "chunksLoadedByCurrentEvent" in load
    assert "unloadedWaveMobs.isUnloaded(ownerId, eventId, generation)" in source
    assert "ownerWorld.isChunkLoaded(ownerState.chunkX(), ownerState.chunkZ())" in source
    retire = method(source, "private boolean reconcileRiftGiantSummonOwner(")
    assert "ownerWorld.isChunkLoaded(ownerState.chunkX(), ownerState.chunkZ())" in retire
    assert "ownerState.status() == RiftGiantOwnerLocationRegistry.Status.UNLOADED" in retire
    assert "unloadedWaveMobs.onDeath(ownerId);" in retire
    assert "keyRiftGiantSummonOwnerWorld" not in retire
    summon = method(source, "private Entity spawnRiftGiantSummon(")
    assert "recordRiftGiantSummonOwnerLocation(summon, giant)" in summon
    assert "Bukkit.getWorlds()" in cleanup
    assert "keyRiftGiantSummonOwner" in cleanup


def test_giant_owner_unload_location_is_authoritative_across_restart_and_load_order():
    source = SOURCE.read_text(encoding="utf-8")
    load = method(source, "public void onPersistedCombatEntitiesLoad(")
    register_owners = method(source, "private void registerLoadedRiftGiantOwners(")
    retire = method(source, "private boolean reconcileRiftGiantSummonOwner(")
    remove = method(source, "public void onOwnedEntityRemove(")
    reindex = method(source, "private void reindexPersistedCombatEntities(")
    owner_loaded = method(source, "private void markRiftGiantOwnerLoaded(")
    owner_unloaded = method(source, "private void recordRiftGiantOwnerUnload(")
    owner_dead = method(source, "private void recordRiftGiantOwnerDeath(")
    loaded_chunks = method(source, "private Set<RiftGiantOwnerChunk> loadedRiftGiantChunks(")
    loaded_batch = method(source, "private Set<?> loadedRiftGiantChunkBatch(")
    preserve = method(source, "private boolean preserveRiftGiantOwnerInRecovery(")

    assert "loadedRiftGiantChunkBatch(event)" in load
    assert "preserveRiftGiantOwnerInRecovery(entity)" in load
    assert "markRiftGiantOwnerLoaded((Zombie) entity)" in register_owners
    assert "riftGiantOwnerLocations.recordLoadedOwner(" in owner_loaded
    assert "riftGiantOwnerLocations.mayRetireAbsentOwner(" in retire
    assert "keyRiftGiantSummonOwnerWorld" not in retire
    assert "recordRiftGiantOwnerUnload(giant)" in remove
    assert "recordRiftGiantOwnerDeath(giant)" in remove
    assert "recordUnloadedOwner(" in owner_unloaded
    assert "recordDeadOwner(" in owner_dead
    assert "loadedRiftGiantChunks(world)" in reindex
    assert "getLoadedChunks()" in loaded_chunks
    assert "event.getChunk()" in loaded_batch
    assert "EventPhase.RECOVERY_REQUIRED" in preserve


def test_giant_summon_budget_is_encounter_wide_and_restart_durable():
    source = SOURCE.read_text(encoding="utf-8")
    enable = method(source, "public void onEnable(")
    shared = method(source, "private int sharedRiftGiantSummonsUsed(")
    store = method(source, "private void storeSharedRiftGiantSummonsUsed(")
    unload = method(source, "private void recordRiftGiantOwnerUnload(")
    death = method(source, "private void recordRiftGiantOwnerDeath(")

    assert "resumeLoadedOwnersAfterRestart(eventId, generation)" in enable
    assert "maxSummonsUsed(eventId, generation, wave)" in shared
    assert "recordWaveSummonsUsed(eventId, generation, wave, totalUsed)" in store
    assert "recordUnloadedOwner(" in unload and "keyRiftGiantSummonsUsed" in unload
    assert "recordDeadOwner(" in death and "keyRiftGiantSummonsUsed" in death


def test_giant_heavy_strike_retains_its_committed_target_through_the_cast():
    source = SOURCE.read_text(encoding="utf-8")
    charge = method(source, "private boolean tickRiftGiantOffense(")

    assert "new RiftGiantStrikeCharge(selectedTarget.getUniqueId(), generation, deadline)" in charge
    retained = charge[charge.index("if (charge != null)"):charge.index("boolean targetEligible =")]
    assert "Bukkit.getPlayer(charge.target())" in retained
    assert "charge.generation() != generation" in retained
    assert "isRiftGiantTargetAllowed(giant, target)" in retained
    assert "selectedTarget" not in retained
    assert "RIFT_GIANT_STRIKE_RESOLVED entity=" in retained
