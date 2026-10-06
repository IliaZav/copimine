"""Drive the actual Wave 4 tick/cast adapters with the real single-slot coordinator."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_pulses_cannot_starve_reflectable_shots_or_other_obelisks(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = ["boolean tickWave4ObeliskAssault()", "void tickWave4Pulse(",
                  "boolean prepareWave4FireCast("]
    if "private ObeliskFireDirectorPolicy.SpecialCandidate selectWave4Special(" in source:
        signatures.append("ObeliskFireDirectorPolicy.SpecialCandidate selectWave4Special(")
    adapters = "\n".join(extract(source, s) for s in signatures)
    probe = tmp_path / "Wave4SpecialProbe.java"
    probe.write_text(r'''
import java.util.*;import me.copimine.endevent.domain.*;import me.copimine.endevent.runtime.WaveCombatCoordinator;
public class Wave4SpecialProbe {
    enum EventPhase { COLLECTING,READY_FOR_PLAYERS,WAVE_4,RECOVERY_REQUIRED }
    enum ObeliskStage { ACTIVE,COLLAPSING }
    enum Sound { BLOCK_BEACON_AMBIENT,BLOCK_RESPAWN_ANCHOR_CHARGE }
    enum SoundCategory { HOSTILE }
    static class World {void playSound(Location p,Sound s,SoundCategory c,float v,float pitch){}}
    static class Location {World getWorld(){return new World();}public Location clone(){return new Location();}}
    static class Bukkit {static World getWorld(String name){return new World();}}
    static class Player {UUID getUniqueId(){return new UUID(0,99);}Location getEyeLocation(){return new Location();}}
    static class EventConfig {
        RiftObeliskTuning riftObeliskTuning(){return new RiftObeliskTuning();}
        static class RiftObeliskTuning {int maxActiveFireballs(){return 1;}int pulseIntervalTicks(){return 40;}
            int fireIntervalTicks(){return 80;}double pulseRadius(){return 5;}}
    }
    static class Wave4ObeliskRuntimeState {
        UUID id;long pulse=40,fire=80,lastFire,fireReleaseTick,pulseReleaseTick;
        WaveCombatCoordinator.Lease pulseLease,fireLease;Location fireAim;
        Wave4ObeliskRuntimeState(int id){this.id=new UUID(0,id);fire+=id*6;}
        UUID id(){return id;}ObeliskStage stage(){return ObeliskStage.ACTIVE;}int health(){return 3;}
        long nextFireTick(){return fire;}long nextPulseTick(){return pulse;}long destroyAtTick(){return Long.MAX_VALUE;}
        void scheduleNextPulse(long tick){pulse=tick;}void scheduleNextFire(long tick){fire=tick;}
        void lastFireTick(long tick){lastFire=tick;}long lastFireTick(){return lastFire;}
        UUID lastTargetUuid(){return null;}Location base(){return new Location();}
    }
    long generation=43,eventTickCounter;String eventId="synthetic-attempt",worldName="configured-arena",recoveryReason;
    boolean testWave4ObeliskMode,currentWave4StartupFailed,currentWave4MobsStarted=true,currentWave4ObeliskAssaultComplete,waveObjectiveComplete;
    EventPhase phase=EventPhase.WAVE_4;EventConfig config=new EventConfig();int currentWaveScalePlayers=2,waveSpawnGroupIndex;
    List<Object> waveSpawnSchedule=List.of();Map<UUID,Wave4ObeliskRuntimeState> activeWave4Obelisks=new LinkedHashMap<>();
    Map<UUID,UUID> waveChannelerObelisks=Map.of();Map<UUID,Object> ownedEntities=Map.of();Object keyObeliskWave4Role;
    WaveCombatCoordinator waveCombatCoordinator=new WaveCombatCoordinator();
    Map<UUID,Integer> shots=new HashMap<>(),pulses=new HashMap<>();List<Long> releases=new ArrayList<>();
    boolean targetAlive=true;
    boolean isOfficialCurrentAttempt(){return true;}void clearWave4Obelisks(String reason){}
    void forcePhase(EventPhase p,String reason){phase=p;}Location coreCombatAnchorLocation(){return new Location();}
    void spawnWave4PressurePack(World w,Location p,ObeliskScalingPolicy.Profile profile,boolean probe){}
    void renderObeliskCollapse(Wave4ObeliskRuntimeState s){}void removeWave4Obelisk(UUID id,String reason){}
    void advanceObeliskEmergence(Wave4ObeliskRuntimeState s){}void renderObeliskActive(Wave4ObeliskRuntimeState s){}
    void renderWave4ChannelerBeams(){}int activeWave4FireballCount(){return 0;}void tickRiftFireballs(){}
    boolean isLiveOwnedEntity(UUID id){return false;}String readString(Object e,Object key){return "";}
    void clearWaveCombatCue(UUID id){}void renderWave4ObeliskCue(Wave4ObeliskRuntimeState s,String phase,String ability,Location p,double radius){}
    void applyCurrentObeliskPulse(Location p,double radius){}
    void renderObeliskPulse(Wave4ObeliskRuntimeState s,double radius){pulses.merge(s.id(),1,Integer::sum);releases.add(eventTickCounter);}
    boolean isActiveArenaParticipant(Player p){return targetAlive;}
    boolean launchWave4Fireball(Wave4ObeliskRuntimeState state){
        if(!prepareWave4FireCast(state,new Player()))return false;
        require(eventTickCounter>=state.fireReleaseTick,"projectile released before real warning");
        shots.merge(state.id(),1,Integer::sum);releases.add(eventTickCounter);return true;
    }
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
''' + adapters + r'''
    public static void main(String[] args){
        var main=new Wave4SpecialProbe();main.waveCombatCoordinator.begin(43);
        for(int id=1;id<=4;id++){var state=new Wave4ObeliskRuntimeState(id);main.activeWave4Obelisks.put(state.id(),state);}
        for(main.eventTickCounter=0;main.eventTickCounter<1200;main.eventTickCounter+=5)main.tickWave4ObeliskAssault();
        require(main.shots.size()==4,"real pulse-first controller starved reflectable fireballs: "+main.shots);
        require(main.pulses.size()==4,"special scheduling starved another tower's pulse: "+main.pulses);
        for(int i=1;i<main.releases.size();i++)require(main.releases.get(i)-main.releases.get(i-1)>=40,
            "major attacks overlapped their release/recovery reservation");
        main.waveCombatCoordinator.clear();
        require(!main.waveCombatCoordinator.busy(43,1200),"cleanup retained special authority");
        main.waveCombatCoordinator.begin(44);
        for(var state:main.activeWave4Obelisks.values())require(!main.waveCombatCoordinator.valid(state.fireLease,44,1200),
            "old generation cast retained release authority");
    }
}
''', encoding="utf-8")
    base = ROOT / "copimine-end-event/src/me/copimine/endevent"
    production = [base / p for p in ("domain/ObeliskFireDirectorPolicy.java", "domain/ObeliskScalingPolicy.java",
                                    "runtime/WaveCombatCoordinator.java")]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, production),
                             str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave4SpecialProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
