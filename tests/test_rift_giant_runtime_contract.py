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
    assert "retireOrphanedRiftGiantSummons(event.getEntities())" in load
    assert "unloadedWaveMobs.isUnloaded(ownerId, eventId, generation)" in source
    assert "ownerWorld.isChunkLoaded(ownerChunkX, ownerChunkZ)" in source
    retire = method(source, "private boolean retireOrphanedRiftGiantSummon(")
    defer_unloaded_owner = "if (ownerKnownUnloaded) {\n            if (!ownerChunkLoaded) return false;"
    assert defer_unloaded_owner in retire
    reconcile = retire.index("unloadedWaveMobs.onDeath(ownerId);")
    retire_orphan = retire.index("RiftGiantPolicy.shouldRetireOrphanedSummon(")
    assert reconcile < retire.index("ownerKnownUnloaded = false;") < retire_orphan
    summon = method(source, "private Entity spawnRiftGiantSummon(")
    assert "recordRiftGiantSummonOwnerLocation(summon, giant)" in summon
    assert "Bukkit.getWorlds()" in cleanup
    assert "keyRiftGiantSummonOwner" in cleanup
