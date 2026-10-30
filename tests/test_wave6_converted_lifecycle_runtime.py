"""Real Turncoat damage and chunk-load handlers reject stale/friendly activity."""
import subprocess

import pytest

from tests.test_wave6_converted_contact_runtime import SOURCE, converted_probe
from tests.test_wave7_named_trial_warning_audio_runtime import extract_method


@pytest.fixture(scope="module")
def lifecycle_probe(converted_probe, tmp_path_factory):
    directory = tmp_path_factory.mktemp("wave6-converted-lifecycle")
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(extract_method(source, signature) for signature in (
        "public void onRitualSupportDamage(", "public void onPersistedCombatEntitiesLoad(",
        "private void removeRitualEntity(",
    ))
    support = r'''
    static class Type<T>{}
    static class PersistentDataType {static final Type<Byte> BYTE=new Type<>();}
    static class Data {
        final Map<Object,Object> values=new HashMap<>();
        @SuppressWarnings("unchecked") <T>T getOrDefault(Object key,Type<T> type,T fallback){return (T)values.getOrDefault(key,fallback);}
    }
    static class EntityDamageEvent {
        final Entity entity;double amount=4;boolean cancelled;
        EntityDamageEvent(Entity entity){this.entity=entity;}
        Entity getEntity(){return entity;}double getDamage(){return amount;}void setDamage(double value){amount=value;}
        boolean isCancelled(){return cancelled;}void setCancelled(boolean value){cancelled=value;}
    }
    static class EntityDamageByEntityEvent extends EntityDamageEvent {
        final Entity damager;EntityDamageByEntityEvent(Entity source,Entity target){super(target);damager=source;}
        Entity getDamager(){return damager;}
    }
    record EntitiesLoadEvent(List<Entity> entities){List<Entity> getEntities(){return entities;}}
    static class WaveMobLifecycleRegistry {
        enum LoadDisposition {QUARANTINE,REHYDRATE}
        LoadDisposition onLoad(Object... args){return LoadDisposition.REHYDRATE;}
    }
    enum WaveTwoLoad {IGNORE,REMOVE,REHYDRATE,RECOVERY_REQUIRED}
    enum EventPhase {RECOVERY_REQUIRED}
    final Object keyRitualConverted="converted",keyKind="kind",keyWave="wave",keyEventId="event";
    static final String EVENT_KIND_WAVE_MOB="wave_mob";
    final Map<UUID,Long> ritualGuardianLinkUntilMillis=new HashMap<>(),ritualBattleSurgeUntilMillis=new HashMap<>();
    final WaveMobLifecycleRegistry unloadedWaveMobs=new WaveMobLifecycleRegistry();
    String recoveryReason="";int unregistered,diagnostics,registered,unbound;
    UUID ritualGuardAbilityGuard,ritualGuardAbilityTarget;
    final Map<UUID,UUID> ritualGuardPreferredTargets=new HashMap<>();
    final Map<UUID,Object> waveMobTactics=new HashMap<>(),miniBossSpells=new HashMap<>(),waveLastProgressLocations=new HashMap<>(),ritualCasterAwakened=new HashMap<>();
    final Map<UUID,Long> ritualGuardAbilityCooldownUntilTick=new HashMap<>(),nextMiniBossSpellMillis=new HashMap<>(),nextSkeletonArrowMillis=new HashMap<>(),nextWavePathRequestMillis=new HashMap<>(),waveLastProgressAt=new HashMap<>(),wavePlayerAggroUntil=new HashMap<>();
    static class Bukkit {static Entity getEntity(UUID id){return null;}}
    void cancelRitualGuardAbilityCast(String reason){}void unbindEventEntityClient(UUID id){unbound++;}
    String readString(Entity e,Object key){return (String)e.data.values.getOrDefault(key,"");}
    int readInt(Entity e,Object key,int fallback){return (int)e.data.values.getOrDefault(key,fallback);}
    long readEntityGeneration(Entity e){return e.owned?generation:generation-1;}
    void registerLoadedRiftGiantOwners(List<Entity> entities){}
    Set<?> loadedRiftGiantChunkBatch(EntitiesLoadEvent event){return Set.of();}
    Set<UUID> retireOrphanedRiftGiantSummons(List<Entity> entities,Set<?> chunks){return Set.of();}
    boolean preserveRiftGiantOwnerInRecovery(Entity entity){return false;}
    boolean indexPersistedNamedWave7Actor(Entity entity){return false;}
    boolean isObsoleteWaveEliteComposition(Entity entity){return false;}
    boolean ownedByEvent(Entity entity,String event){return event.equals(readString(entity,keyEventId));}
    void emitUntrackedEntityRemovalDiagnostic(Entity entity,String reason){diagnostics++;}
    Entity unregisterOwnedEntity(UUID id,String reason){unregistered++;return ownedEntities.remove(id);}
    boolean isOfficialWaveLifecycleMob(Entity entity){return false;}
    boolean isOfficialWaveSixPressureMob(Entity entity){return false;}
    boolean isActiveOfficialWavePhase(int wave){return wave==6;}
    void registerOwnedEntity(Entity entity){registered++;ownedEntities.put(entity.id,entity);}
    void ensureEventCombatAi(Entity entity){}void restoreEliteSpellRuntime(Entity entity,long now){}
    boolean isWaveTwoOrdinaryMob(Entity entity){return false;}
    WaveTwoLoad waveTwoEntityLoadDisposition(Entity entity){return WaveTwoLoad.IGNORE;}
    void stampCurrentWaveTwoCleanupEpochIfMissing(Entity entity){}void rehydrateWaveTwoEntity(Entity entity){}
    void forcePhase(EventPhase phase,String reason){}void suspendWaveTwoRecoveryEntity(Entity entity){}
''' + methods + r'''
    void tagConverted(Mob mob){
        mob.converted=true;mob.data.values.put(keyRitualConverted,(byte)1);
        mob.data.values.put(keyKind,EVENT_KIND_WAVE_MOB);mob.data.values.put(keyWave,0);mob.data.values.put(keyEventId,eventId);
    }
    public static void runLifecycle(String scenario){
        var p=new ConvertedContactProbe();var ally=new Mob(p.world);var enemy=new Mob(p.world);enemy.position=new Location(1,0,0);
        p.tagConverted(ally);p.ownedEntities.put(ally.id,ally);p.ownedEntities.put(enemy.id,enemy);
        p.ritualConvertedMobUntilMillis.put(ally.id,Long.MAX_VALUE);p.ritualConversionTargets.add(ally.id);
        if(scenario.startsWith("damage-")){
            LivingEntity victim=enemy;Entity attacker=ally;boolean cancelled=false;
            switch(scenario){
                case "damage-legal" -> {}
                case "damage-player" -> {victim=new Player(p.world);cancelled=true;}
                case "damage-friendly" -> {p.tagConverted(enemy);p.ritualConvertedMobUntilMillis.put(enemy.id,Long.MAX_VALUE);cancelled=true;}
                case "damage-foreign" -> {enemy.owned=false;cancelled=true;}
                case "damage-other-world" -> {enemy.world=new World();cancelled=true;}
                case "damage-source-session" -> {ally.owned=false;cancelled=true;}
                case "damage-expired" -> {p.ritualConvertedMobUntilMillis.put(ally.id,0L);cancelled=true;}
                case "damage-unowned" -> {p.ownedEntities.remove(ally.id);cancelled=true;}
                case "damage-cleared-lease" -> {p.ritualConvertedMobUntilMillis.clear();cancelled=true;}
                case "damage-inactive" -> {p.waveActive=false;cancelled=true;}
                case "damage-prior-cancel" -> cancelled=true;
                case "damage-natural" -> {attacker=new Mob(p.world);victim=new Player(p.world);}
                default -> throw new AssertionError(scenario);
            }
            var event=new EntityDamageByEntityEvent(attacker,victim);
            event.setCancelled(scenario.equals("damage-prior-cancel"));
            p.onRitualSupportDamage(event);
            if(event.isCancelled()!=cancelled)throw new AssertionError("damage cancellation="+event.isCancelled()+" for "+scenario);
            if(event.amount!=4)throw new AssertionError("admission must not rewrite damage amount");
            return;
        }
        boolean remove=false;Entity loaded=ally;
        switch(scenario){
            case "load-live" -> {}
            case "load-orphan" -> {p.ownedEntities.remove(ally.id);p.ritualConvertedMobUntilMillis.clear();remove=true;}
            case "load-missing-owner" -> {p.ownedEntities.remove(ally.id);remove=true;}
            case "load-expired" -> {p.ritualConvertedMobUntilMillis.put(ally.id,0L);remove=true;}
            case "load-old-session" -> {ally.owned=false;ally.data.values.put(p.keyEventId,"previous-event");remove=true;}
            case "load-inactive" -> {p.waveActive=false;remove=true;}
            case "load-natural" -> {ally.data.values.clear();ally.converted=false;}
            case "load-unidentified" -> ally.data.values.remove(p.keyEventId);
            case "load-wrong-kind" -> ally.data.values.put(p.keyKind,"foreign-kind");
            case "load-wrong-wave" -> ally.data.values.put(p.keyWave,5);
            case "load-duplicate-wrapper" -> {var duplicate=new Mob(p.world);duplicate.id=ally.id;duplicate.data.values.putAll(ally.data.values);duplicate.converted=true;loaded=duplicate;}
            default -> throw new AssertionError(scenario);
        }
        p.onPersistedCombatEntitiesLoad(new EntitiesLoadEvent(List.of(loaded)));
        if(loaded.valid==remove)throw new AssertionError("wrong removal result for "+scenario);
        if(remove&&(p.ownedEntities.containsKey(ally.id)||p.ritualConvertedMobUntilMillis.containsKey(ally.id)||p.ritualConversionTargets.contains(ally.id)||p.unbound!=1))
            throw new AssertionError("orphan cleanup must remove conversion bookkeeping");
        if(!remove&&(p.ownedEntities.get(ally.id)!=ally||p.unregistered!=0))
            throw new AssertionError("legitimate actor/other role must remain unchanged");
        if(p.registered!=0)throw new AssertionError("load must never recreate a conversion lease");
    }
'''
    code = (converted_probe / "ConvertedContactProbe.java").read_text(encoding="utf-8")
    cleanup_stub = "    void removeRitualEntity(UUID id){ownedEntities.remove(id);ritualConvertedMobUntilMillis.remove(id);ritualConversionTargets.remove(id);}\n"
    assert code.count(cleanup_stub) == 1
    code = code.replace(cleanup_stub, "", 1)
    code = code.replace("final UUID id=UUID.randomUUID();World world;",
                        "UUID id=UUID.randomUUID();final Data data=new Data();World world;", 1)
    code = code.replace("boolean isValid(){return valid;}boolean isDead(){return dead;}",
                        "Data getPersistentDataContainer(){return data;}void remove(){valid=false;}"
                        "boolean isValid(){return valid;}boolean isDead(){return dead;}", 1)
    anchor = "    public static void main(String[] args){"
    assert code.count(anchor) == 1
    code = code.replace(anchor, support + "\n" + anchor, 1)
    path = directory / "ConvertedContactProbe.java"
    path.write_text(code, encoding="utf-8")
    main = directory / "ConvertedLifecycleMain.java"
    main.write_text("public class ConvertedLifecycleMain {public static void main(String[] args){"
                    "ConvertedContactProbe.runLifecycle(args[0]);}}", encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(path), str(main)],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "damage-legal", "damage-player", "damage-friendly", "damage-foreign", "damage-other-world",
    "damage-source-session", "damage-expired", "damage-unowned", "damage-cleared-lease", "damage-inactive",
    "damage-prior-cancel", "damage-natural", "load-live", "load-orphan", "load-missing-owner",
    "load-expired", "load-old-session", "load-inactive", "load-natural", "load-unidentified",
    "load-wrong-kind", "load-wrong-wave", "load-duplicate-wrapper",
])
def test_converted_faction_and_load_cleanup(lifecycle_probe, scenario):
    result = subprocess.run(["java", "-cp", str(lifecycle_probe), "ConvertedLifecycleMain", scenario],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=15)
    assert result.returncode == 0, result.stdout + result.stderr
