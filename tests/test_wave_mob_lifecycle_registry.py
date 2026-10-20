"""Exercise the executable wave mob unload roster used by the Paper adapter."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
REGISTRY = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/WaveMobLifecycleRegistry.java"


@pytest.fixture(scope="module")
def compiled_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("wave-mob-lifecycle")
    probe = out / "WaveMobLifecycleRegistryProbe.java"
    probe.write_text(r'''
import java.util.UUID;
import me.copimine.endevent.runtime.WaveMobLifecycleRegistry;

public final class WaveMobLifecycleRegistryProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int wave : new int[] {1, 3, 5}) {
            var registry = new WaveMobLifecycleRegistry();
            UUID id = UUID.randomUUID();
            String event = "event-" + wave;
            long generation = 20 + wave;

            registry.onUnload(id, event, generation, wave);
            check(registry.count(event, generation, wave) == 1,
                    "unloaded wave " + wave + " vanished from completion count");
            boolean mayTransition = registry.count(event, generation, wave) == 0;
            check(!mayTransition, "wave " + wave + " transitioned while its mob was unloaded");

            UUID portalId = UUID.randomUUID();
            registry.onUnload(portalId, event, generation, 3, true);
            check(registry.countPortalSpawns(event, generation, 3) == 1,
                    "unloaded portal mob vanished from the portal-only cap");
            registry.onLoad(portalId, event, generation, 3, true,
                    event, generation, 3);
            check(registry.countPortalSpawns(event, generation, 3) == 0,
                    "loaded portal mob must move from unload roster to live entity index");

            var disposition = registry.onLoad(id, event, generation, wave, true,
                    event, generation, wave);
            check(disposition == WaveMobLifecycleRegistry.LoadDisposition.REHYDRATE,
                    "current wave " + wave + " should be reindexed on chunk load");
            boolean trackedAfterLoad = true;
            check(registry.count(event, generation, wave) + (trackedAfterLoad ? 1 : 0) == 1,
                    "rehydrated wave " + wave + " disappeared between roster and entity indexes");

            registry.onDeath(id);
            trackedAfterLoad = false;
            check(registry.count(event, generation, wave) + (trackedAfterLoad ? 1 : 0) == 0,
                    "confirmed death did not release wave " + wave + " completion fence");

            UUID staleGenerationId = UUID.randomUUID();
            registry.onUnload(staleGenerationId, event, generation, wave);
            check(registry.count(event, generation + 1, wave) == 0,
                    "stale wave " + wave + " kept counting after generation changed");
            var staleGeneration = registry.onLoad(staleGenerationId, event, generation + 1, wave, true,
                    event, generation, wave);
            check(staleGeneration == WaveMobLifecycleRegistry.LoadDisposition.QUARANTINE,
                    "stale-generation wave " + wave + " mob was rehydrated");

            UUID stalePhaseId = UUID.randomUUID();
            registry.onUnload(stalePhaseId, event, generation, wave);
            var stalePhase = registry.onLoad(stalePhaseId, event, generation, wave + 1, false,
                    event, generation, wave);
            check(stalePhase == WaveMobLifecycleRegistry.LoadDisposition.QUARANTINE,
                    "noncurrent-phase wave " + wave + " mob was rehydrated");
            check(registry.count(event, generation, wave) == 0,
                    "stale wave " + wave + " remained in the outstanding unload count");
        }
        System.out.println("wave-mob-lifecycle OK");
    }
}
''', encoding="utf-8")
    built = subprocess.run(
        ["javac", "-proc:none", "-encoding", "UTF-8", "-d", str(out), str(REGISTRY), str(probe)],
        capture_output=True,
        text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    return out


def test_unload_count_load_and_generation_quarantine(compiled_probe):
    run = subprocess.run(
        ["java", "-cp", str(compiled_probe), "WaveMobLifecycleRegistryProbe"],
        capture_output=True,
        text=True,
    )
    assert run.returncode == 0, run.stdout + run.stderr


def test_paper_adapter_uses_the_unload_roster_and_load_quarantine():
    source = SOURCE.read_text("utf-8")
    remove_start = source.index("public void onOwnedEntityRemove(EntityRemoveEvent event)")
    remove_end = source.index("\n    }", remove_start)
    removal = source[remove_start:remove_end]
    load_start = source.index("public void onPersistedCombatEntitiesLoad(EntitiesLoadEvent event)")
    load_end = source.index("\n    private void suspendWaveTwoRecoveryEntity", load_start)
    load = source[load_start:load_end]
    count_start = source.index("private int countLiveWaveEntitiesForWave(int wave)")
    count_end = source.index("\n    private void clearRitualPressureMobs", count_start)
    count = source[count_start:count_end]

    assert "unloadedWaveMobs.onUnload" in removal
    assert "keyWaveThreePortalSpawn" in removal
    assert "unloadedWaveMobs.onLoad" in load
    assert "unloadedWaveMobs.count(eventId, generation, wave)" in count


def test_wave3_portal_cap_includes_portal_mobs_in_unloaded_chunks():
    source = SOURCE.read_text("utf-8")
    scheduler_start = source.index("private void tickWave3PortalSpawns(long now)")
    scheduler_end = source.index("\n    /** Create one Wave 3 mob", scheduler_start)
    scheduler = source[scheduler_start:scheduler_end]
    assert "unloadedWaveMobs.countPortalSpawns(eventId, generation, 3)" in scheduler


def test_elite_spell_runtime_is_rebuilt_when_a_live_wave_chunk_loads():
    source = SOURCE.read_text("utf-8")
    load_start = source.index("public void onPersistedCombatEntitiesLoad(EntitiesLoadEvent event)")
    load_end = source.index("\n    private void suspendWaveTwoRecoveryEntity", load_start)
    load = source[load_start:load_end]

    assert "restoreEliteSpellRuntime(entity, System.currentTimeMillis());" in load
    assert load.index("registerOwnedEntity(entity);") < load.index(
        "restoreEliteSpellRuntime(entity, System.currentTimeMillis());"
    )
    helper_start = source.index("private void restoreEliteSpellRuntime(Entity entity, long now)")
    helper_end = source.index("\n    private boolean isObsoleteWaveEliteComposition", helper_start)
    helper = source[helper_start:helper_end]
    reindex_start = source.index("private void reindexPersistedCombatEntities()")
    reindex_end = source.index("\n    private boolean isObsoleteWaveEliteComposition", reindex_start)
    reindex = source[reindex_start:reindex_end]

    assert "restoreEliteSpellRuntime(entity, now);" in reindex
    assert "EliteAbilityPolicy.activeCastForWave(wave, role)" in helper
    assert "miniBossSpells.put(entityId, spell);" in helper
    assert "nextMiniBossSpellMillis.put(entityId, now + 3_000L);" in helper
    assert "miniBossSpells.remove(entityId);" in helper
    assert "nextMiniBossSpellMillis.remove(entityId);" in helper
