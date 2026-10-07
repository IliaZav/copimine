"""Execute the fog freeze/resume methods; NoAI and awareness have separate owners."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


@pytest.fixture(scope="module")
def freeze_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("fog-ai-owner")
    source = SOURCE.read_text("utf-8")
    methods = extract(source, "void freezeWaveMobs(boolean frozen)") + extract(source, "void freezeFogCombatMob(Entity entity)")
    if "private boolean isWaveFogAiHeld(" in source:
        methods += extract(source, "boolean isWaveFogAiHeld(")
    java = out / "FogAiOwnerProbe.java"
    java.write_text('''
import java.util.*;import me.copimine.endevent.runtime.WaveCombatCoordinator;
public class FogAiOwnerProbe {
    long generation=3,eventTickCounter=40,nextWaveTargetMillis;
    int activeWave=5,audioCalls;boolean waveFogFreezeAudible;Object keyWave,keyKind;
    Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();
    Map<UUID,Long> waveFrozenCueRefreshTicks=new HashMap<>(),nextWavePathRequestMillis=new HashMap<>(),waveDashGenerations=new HashMap<>();
    Map<UUID,Object> waveLastPathDestinations=new HashMap<>();Map<UUID,String> waveCombatCueKeys=new HashMap<>();
    record WaveFogAiLease(long generation,boolean ai,boolean aware){}
    Map<UUID,WaveFogAiLease> waveFogAiLeases=new HashMap<>();Set<UUID> waveFogResumeHolds=new HashSet<>();
    WaveCombatCoordinator waveCombatCoordinator=new WaveCombatCoordinator();
    static class Vector {}static class Location {}
    static class Entity {UUID id=UUID.randomUUID();int wave=5;boolean live=true;UUID getUniqueId(){return id;}}
    static class Mob extends Entity {
        boolean ai=true,aware=true;void setAI(boolean v){ai=v;}boolean hasAI(){return ai;}
        void setAware(boolean v){aware=v;}boolean isAware(){return aware;}void setTarget(Object p){}void setVelocity(Vector v){}
        Location getEyeLocation(){return new Location();}Path getPathfinder(){return new Path();}
    }
    static class Path {void stopPathfinding(){}}
    enum Sound {BLOCK_GLASS_PLACE,BLOCK_BEACON_ACTIVATE}enum SoundCategory {HOSTILE}
    static class Player {void playSound(Location p,Sound s,SoundCategory c,float v,float pitch){}}
    List<Player> eventAudience(){return List.of();}boolean isEventParticleViewer(Player p,Location l){return false;}
    Location coreCombatAnchorLocation(){return new Location();}String readString(Entity e,Object key){return "MOB";}
    int readInt(Entity e,Object key,int fallback){return e.wave;}long readEntityGeneration(Entity e){return generation;}
    boolean isWaveCombatKind(String kind){return "MOB".equals(kind);}boolean isLiveOwnedEntity(UUID id){return ownedEntities.get(id).live;}
    void clearWaveCombatCue(UUID id){waveCombatCueKeys.remove(id);}void sendWaveMobPose(Entity e,String p,long d){}
    void playWaveFeedback(String cue,Location point){audioCalls++;}
    void renderWaveCombatCue(Mob m,Location l,String a,String b){}
    void finishWaveMobDash(Mob m,long g){waveDashGenerations.remove(m.id,g);}
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
''' + methods + '''
    public static void main(String[] args){
        var p=new FogAiOwnerProbe();p.waveCombatCoordinator.begin(p.generation);
        var m=new Mob();p.ownedEntities.put(m.id,m);
        switch(args[0]){
            case "foreign-noai" -> m.ai=false;
            case "foreign-unaware" -> m.aware=false;
            case "foreign-both" -> {m.ai=false;m.aware=false;}
            case "own-warning" -> {m.ai=false;m.aware=false;p.waveCombatCoordinator.reserve(p.generation,m.id,UUID.randomUUID(),40,100);}
            case "own-dash" -> {m.ai=false;m.aware=false;p.waveDashGenerations.put(m.id,p.generation);}
        }
        boolean expectedAi=m.ai,expectedAware=m.aware;
        if(args[0].startsWith("own-")){expectedAi=true;expectedAware=true;}
        p.freezeWaveMobs(true);p.freezeWaveMobs(true);
        check(!m.ai&&!m.aware,"dangerous fog must stay deliberately frozen through repeat heartbeat");
        p.freezeWaveMobs(false);
        check(m.ai==expectedAi&&m.aware==expectedAware,"resume overwrote another controller's AI/awareness ownership");
        check(p.waveDashGenerations.isEmpty(),"old dash remained armed through fog");
        m.ai=false;m.aware=false;p.freezeWaveMobs(false);
        check(!m.ai&&!m.aware,"a duplicate resume must not reacquire already-released ownership");
        check(p.audioCalls==2,"frozen and resume audio must each fire once, never restart on a heartbeat");
        p.waveCombatCoordinator.begin(++p.generation);
        check(!p.waveCombatCoordinator.busy(p.generation,100),"old special attack survived generation change");
    }
}
''', encoding="utf-8")
    coordinator = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/WaveCombatCoordinator.java"
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(java), str(coordinator)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return out


@pytest.mark.parametrize("scenario", ["ordinary", "foreign-noai", "foreign-unaware", "foreign-both", "own-warning", "own-dash"])
def test_fog_releases_only_its_ai_lease(freeze_probe, scenario):
    result = subprocess.run(["java", "-cp", str(freeze_probe), "FogAiOwnerProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
