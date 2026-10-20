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
    enum Wave4PressurePackResult { COMPLETE,RETRYABLE_FAILURE,PERMANENT_FAILURE }
    enum ObeliskStage { ACTIVE,COLLAPSING }
    enum Sound { BLOCK_BEACON_AMBIENT,BLOCK_RESPAWN_ANCHOR_CHARGE }
    enum SoundCategory { HOSTILE }
    static class World {void playSound(Location p,Sound s,SoundCategory c,float v,float pitch){}}
    static class Location {World getWorld(){return new World();}public Location clone(){return new Location();}}
    static class Bukkit {static World getWorld(String name){return new World();}}
    static class Player {UUID getUniqueId(){return new UUID(0,99);}Location getEyeLocation(){return new Location();}}
    static class EventConfig {
        RiftObeliskTuning riftObeliskTuning(){return new RiftObeliskTuning();}
        static class RiftObeliskTuning {int maxActiveFireballs(){return 20;}int pulseIntervalTicks(){return 40;}
            int fireIntervalTicks(){return ObeliskFireDirectorPolicy.DEFAULT_FIRE_INTERVAL_TICKS;}double pulseRadius(){return 5;}}
    }
    static class Wave4ObeliskRuntimeState {
        UUID id;long pulse=40,fire=ObeliskFireDirectorPolicy.DEFAULT_FIRE_INTERVAL_TICKS,lastFire,fireReleaseTick,pulseReleaseTick;
        WaveCombatCoordinator.Lease pulseLease;ObeliskFireDirectorPolicy.FireSequence fireLease;Location fireAim;
        Wave4ObeliskRuntimeState(int id){this.id=new UUID(0,id);fire+=id*6;}
        UUID id(){return id;}ObeliskStage stage(){return ObeliskStage.ACTIVE;}int health(){return 3;}
        long nextFireTick(){return fire;}long nextPulseTick(){return pulse;}long destroyAtTick(){return Long.MAX_VALUE;}
        void scheduleNextPulse(long tick){pulse=tick;}void scheduleNextFire(long tick){fire=tick;}
        void lastFireTick(long tick){lastFire=tick;}long lastFireTick(){return lastFire;}
        UUID lastTargetUuid(){return null;}Location base(){return new Location();}
    }
    record SimulatedFireball(long generation,long expiresTick) { }
    long generation=43,eventTickCounter;String eventId="synthetic-attempt",worldName="configured-arena",recoveryReason;
    boolean testWave4ObeliskMode,currentWave4StartupFailed,currentWave4MobsStarted=true,currentWave4ObeliskAssaultComplete,waveObjectiveComplete;
    EventPhase phase=EventPhase.WAVE_4;EventConfig config=new EventConfig();int currentWaveScalePlayers=2,waveSpawnGroupIndex;
    int currentWave4PressurePackAttempts;long currentWave4PressureRetryAtMillis;
    List<Object> waveSpawnSchedule=List.of();Map<UUID,Wave4ObeliskRuntimeState> activeWave4Obelisks=new LinkedHashMap<>();
    Map<UUID,UUID> waveChannelerObelisks=new LinkedHashMap<>();Map<UUID,Object> ownedEntities=new LinkedHashMap<>();Object keyObeliskWave4Role;
    WaveCombatCoordinator waveCombatCoordinator=new WaveCombatCoordinator();
    Map<UUID,Integer> shots=new HashMap<>(),pulses=new HashMap<>();List<Long> pulseReleases=new ArrayList<>();
    Map<UUID,List<Long>> fireReleases=new HashMap<>();
    List<SimulatedFireball> activeFireballs=new ArrayList<>();
    boolean targetAlive=true,channelerMode=true;
    int fireballAdmissionCap(){return Math.min(ObeliskScalingPolicy.MAX_FIREBALL_ADMISSION_SLOTS,
            Math.max(ObeliskScalingPolicy.profileForPlayers(currentWaveScalePlayers).fireballCap(),
                    config.riftObeliskTuning().maxActiveFireballs()));}
    int reservedFireSequences(){return (int)activeWave4Obelisks.values().stream().filter(state->
            ObeliskFireDirectorPolicy.validFireSequence(state.fireLease,eventId,generation,state.id(),eventTickCounter)).count();}
    boolean isOfficialCurrentAttempt(){return true;}void clearWave4Obelisks(String reason){}
    void forcePhase(EventPhase p,String reason){phase=p;}Location coreCombatAnchorLocation(){return new Location();}
    Wave4PressurePackResult spawnWave4PressurePack(World w,Location p,ObeliskScalingPolicy.Profile profile,boolean probe){return Wave4PressurePackResult.COMPLETE;}
        void cancelWaveSpawnTask(){} int clearWave4PressurePackEntities(String reason){return 0;}
    void renderObeliskCollapse(Wave4ObeliskRuntimeState s){}void removeWave4Obelisk(UUID id,String reason){}
    void advanceObeliskEmergence(Wave4ObeliskRuntimeState s){}void renderObeliskActive(Wave4ObeliskRuntimeState s){}
    void renderWave4ChannelerBeams(){}
    int activeWave4FireballCount(){return (int)activeFireballs.stream().filter(f->f.generation()==generation).count();}
    void tickRiftFireballs(){activeFireballs.removeIf(f->f.generation()!=generation||eventTickCounter>=f.expiresTick());}
    boolean isLiveOwnedEntity(UUID id){return channelerMode;}String readString(Object e,Object key){return channelerMode?"CHANNELER":"";}
    void clearWaveCombatCue(UUID id){}void renderWave4ObeliskCue(Wave4ObeliskRuntimeState s,String phase,String ability,Location p,double radius){}
    void applyCurrentObeliskPulse(Location p,double radius){}
    void renderObeliskPulse(Wave4ObeliskRuntimeState s,double radius){pulses.merge(s.id(),1,Integer::sum);pulseReleases.add(eventTickCounter);}
    boolean isActiveArenaParticipant(Player p){return targetAlive;}
    boolean launchWave4Fireball(Wave4ObeliskRuntimeState state){
        if(!prepareWave4FireCast(state,new Player()))return false;
        require(eventTickCounter>=state.fireReleaseTick,"projectile released before real warning");
        activeFireballs.add(new SimulatedFireball(generation,eventTickCounter+160));
        shots.merge(state.id(),1,Integer::sum);
        fireReleases.computeIfAbsent(state.id(),ignored->new ArrayList<>()).add(eventTickCounter);
        state.fireLease=null;state.fireAim=null;return true;
    }
    java.util.logging.Logger getLogger(){var logger=java.util.logging.Logger.getAnonymousLogger();logger.setLevel(java.util.logging.Level.OFF);return logger;}
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
''' + adapters + r'''
    public static void main(String[] args){
        var main=new Wave4SpecialProbe();main.waveCombatCoordinator.begin(43);
        require(ObeliskScalingPolicy.fireballCap(2)==20&&ObeliskScalingPolicy.fireballCap(10)==25
                &&ObeliskScalingPolicy.fireballCap(20)==30,
                "low-player profiles must reserve five bounded slots per active tower");
        var policySequence=ObeliskFireDirectorPolicy.beginFireSequence("synthetic-attempt",43,
                new UUID(0,1),new UUID(0,99),0,20);
        require(ObeliskFireDirectorPolicy.validFireSequence(policySequence,"synthetic-attempt",43,
                new UUID(0,1),20),"current event/generation/owner lost fire authority");
        require(!ObeliskFireDirectorPolicy.validFireSequence(policySequence,"other-event",43,
                new UUID(0,1),20),"wrong event retained fire authority");
        require(!ObeliskFireDirectorPolicy.validFireSequence(policySequence,"synthetic-attempt",44,
                new UUID(0,1),20),"stale generation retained fire authority");
        require(!ObeliskFireDirectorPolicy.validFireSequence(policySequence,"synthetic-attempt",43,
                new UUID(0,2),20),"wrong obelisk retained fire authority");
        require(!ObeliskFireDirectorPolicy.validFireSequence(policySequence,"synthetic-attempt",43,
                new UUID(0,1),24),"expired sequence retained fire authority");
        for(int id=1;id<=4;id++){
            var state=new Wave4ObeliskRuntimeState(id);main.activeWave4Obelisks.put(state.id(),state);
            UUID channelerId=new UUID(1,id);main.waveChannelerObelisks.put(channelerId,state.id());
            main.ownedEntities.put(channelerId,new Object());
        }
        if(args.length>0&&"full-cap".equals(args[0])){
            int cap=main.fireballAdmissionCap();
            long expiryStep=Math.max(1L,ObeliskFireDirectorPolicy.MAX_FIREBALL_LIFETIME_TICKS/cap);
            for(int index=1;index<=cap;index++)main.activeFireballs.add(new SimulatedFireball(main.generation,index*expiryStep));
            require(main.activeWave4FireballCount()==cap,"full-cap setup did not saturate admission: "+cap);
        }
        int maxAdmissionUse=0;
        for(main.eventTickCounter=0;main.eventTickCounter<1200;main.eventTickCounter++){
            main.tickWave4ObeliskAssault();
            int admissionUse=main.activeWave4FireballCount()+main.reservedFireSequences();
            maxAdmissionUse=Math.max(maxAdmissionUse,admissionUse);
            require(admissionUse<=main.fireballAdmissionCap(),
                    "active projectiles plus reserved fire sequences exceeded the bounded cap");
        }
        require(maxAdmissionUse==main.fireballAdmissionCap(),
                "four channeling towers never exercised the full admission cap: max="+maxAdmissionUse
                        +" cap="+main.fireballAdmissionCap());
        require(main.shots.size()==4,"real pulse-first controller starved reflectable fireballs: "+main.shots);
        int totalShots=main.shots.values().stream().mapToInt(Integer::intValue).sum();
        require(totalShots>=8,"the shorter configured cadence did not increase actual launches: "+main.shots);
        require(main.pulses.size()==4,"special scheduling starved another tower's pulse: "+main.pulses);
        for(var state:main.activeWave4Obelisks.values()){
            var fireTicks=main.fireReleases.getOrDefault(state.id(),List.of());
            require(fireTicks.size()>=2,"each obelisk must produce repeated shots for cadence verification: "+state.id()+" "+fireTicks);
            for(int i=1;i<fireTicks.size();i++)require(fireTicks.get(i)-fireTicks.get(i-1)<=60,
                "one obelisk exceeded the three-second fire release gap: "+state.id()+" "+fireTicks);
        }
        for(int i=1;i<main.pulseReleases.size();i++)require(main.pulseReleases.get(i)-main.pulseReleases.get(i-1)>=44,
            "pulse releases overlapped their global lease");
        main.waveCombatCoordinator.clear();
        require(!main.waveCombatCoordinator.busy(43,1200),"cleanup retained special authority");
        main.waveCombatCoordinator.begin(44);
        for(var state:main.activeWave4Obelisks.values())require(!ObeliskFireDirectorPolicy.validFireSequence(
                state.fireLease,"synthetic-attempt",44,state.id(),1200),
            "old generation cast retained release authority");
    }
}
''', encoding="utf-8")
    base = ROOT / "copimine-end-event/src/me/copimine/endevent"
    production = [base / p for p in ("domain/ObeliskFireDirectorPolicy.java", "domain/ObeliskScalingPolicy.java",
                                    "domain/Wave4PressurePackPolicy.java", "domain/WaveMechanicsPolicy.java",
                                    "domain/PressureBudgetController.java",
                                    "runtime/WaveCombatCoordinator.java")]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, production),
                             str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    for scenario in ("steady", "full-cap"):
        args = [] if scenario == "steady" else [scenario]
        result = subprocess.run(["java", "-cp", str(tmp_path), "Wave4SpecialProbe", *args],
                                capture_output=True, text=True)
        assert result.returncode == 0, f"{scenario}: {result.stdout}{result.stderr}"
