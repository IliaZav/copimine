"""Exercise the production summon adapter with a refused special summon."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_skeleton_protection_runtime import declaration
ROOT=Path(__file__).resolve().parents[1]

@pytest.fixture(scope='module')
def probe(tmp_path_factory):
    out=tmp_path_factory.mktemp('rage-summon-owner')
    method=declaration((ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8'),'private void summonServants(')
    f=out/'RageSummonProbe.java'
    f.write_text('''import java.util.*;
import me.copimine.endevent.domain.*;
public class RageSummonProbe {
 BossPhase bossPhase;String eventId="test";boolean decoyAccepted;int decoyCalls,ordinarySpawns;
 static class Config {int maxSummonedServants(){return 4;}} Config config=new Config();
 static class World {} static class Location {World getWorld(){return new World();}}
 static class Entity {UUID id=UUID.randomUUID();UUID getUniqueId(){return id;}}
 static class LivingEntity extends Entity {Location getLocation(){return new Location();}}
 enum EntityType {SPIDER} String EVENT_KIND_WAVE_MOB="wave";
 Set<UUID> spellServants=new HashSet<>();
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
 BossPhasePolicy.CombatProfile currentBossCombatProfile(){return BossPhasePolicy.combatProfile(bossPhase);}
 boolean summonEchoDecoys(LivingEntity boss){decoyCalls++;return decoyAccepted;}
 Entity spawnOwnedMob(World world,Location at,EntityType type,int wave,String kind,boolean test,int slot){ordinarySpawns++;return new Entity();}
''' + method + '''
 public static void main(String[]args){
  var p=new RageSummonProbe();p.bossPhase=BossPhase.valueOf(args[0]);p.decoyAccepted=Boolean.parseBoolean(args[1]);
  p.summonServants(new LivingEntity());
  if(p.ordinarySpawns!=Integer.parseInt(args[2]))throw new AssertionError("refused special summon substituted ordinary enemies: "+p.ordinarySpawns);
  if(p.decoyCalls!=(p.bossPhase==BossPhase.RAGE?1:0))throw new AssertionError("wrong summon owner");
 }
}''',encoding='utf-8')
    r=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(ROOT/'copimine-end-event/src'),'-d',str(out),str(f)],capture_output=True,text=True)
    assert r.returncode==0,r.stderr
    return out

@pytest.mark.parametrize('phase,accepted,ordinary',[('RAGE','false',0),('RAGE','true',0),('RIFT','false',2)])
def test_refused_rage_summon_never_substitutes_other_mobs(probe,phase,accepted,ordinary):
    r=subprocess.run(['java','-cp',str(probe),'RageSummonProbe',phase,accepted,str(ordinary)],capture_output=True,text=True)
    assert r.returncode==0,r.stderr
