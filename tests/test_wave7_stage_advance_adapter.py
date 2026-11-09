"""Execute the production completion adapter with real trial/room state owners."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'copimine-end-event/src'


def test_sequential_completion_advances_after_durable_cleanup_and_parallel_opens_only_its_room(tmp_path):
    source=(SRC/'me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    method=extract(source,'void finishNamedRealitySplitTrial(int chamber, String source)')
    java=r'''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.runtime.*;
public class Wave7AdvanceAdapterProbe {
 enum EventPhase { WAVE_7, RECOVERY_REQUIRED }
 enum Sound { BLOCK_BEACON_ACTIVATE } enum SoundCategory { PLAYERS }
 static class World {}
 static class Location { World getWorld(){return new World();} }
 static class Entity {}
 static class Player { boolean isOnline(){return true;}Location getLocation(){return new Location();}
 void playSound(Location p,Sound s,SoundCategory c,float v,float pitch){} }
 static class Bukkit {static Entity getEntity(UUID id){return null;}static Player getPlayer(UUID id){return null;} }
 final RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
 final RealitySplitTrialController realitySplitTrialController=new RealitySplitTrialController();
 final Set<UUID> realitySplitTrialEntityUuids=new HashSet<>();
 final Map<UUID,Entity> ownedEntities=new HashMap<>();
 Object keyChamberId=new Object();long generation=7;String eventId=UUID.randomUUID().toString();
 EventPhase phase=EventPhase.WAVE_7;boolean durable=true,namedWave7InitialActorCreation;
 Location namedEchoOrdinaryCore;int saves,starts,opens,cleanups;
 boolean saveStateSync(){saves++;return durable;}void saveStateAsync(){}
 int readInt(Entity e,Object key,int fallback){return fallback;}
 void removeRealitySplitTrialEntity(UUID id,String why){cleanups++;}
 void openCompletedRealitySplitRoom(int chamber,int count){opens++;}
 void releaseCompletedNamedEchoStage(String why){cleanups++;}
 void enterNamedRealitySplitRecovery(String why){phase=EventPhase.RECOVERY_REQUIRED;}
 Location coreCombatAnchorLocation(){return new Location();}
 UUID parseUuidOrNull(String id){return UUID.fromString(id);}
 Object currentNamedRealitySplitTrial(){return realitySplitTrialController.namedTrial(0).trial();}
 boolean startNamedRealitySplitTrials(World world,Location core){starts++;return true;}
 Logger getLogger(){return Logger.getLogger("wave7-advance");}
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 void begin(int players){
  var roster=new ArrayList<UUID>();for(int i=0;i<players;i++)roster.add(new UUID(0,i+1));
  realitySplitChamberController.beginWave7(generation,roster);
  realitySplitTrialController.beginNamed(UUID.fromString(eventId),generation,
    realitySplitChamberController.assignment(),RealitySplitTrialController.sequentialRoute());
 }
 void defeat(int room,RealitySplitTrialController.NamedTrial trial){
  check(realitySplitTrialController.completeNamedActor(UUID.fromString(eventId),generation,room,trial),"real death receipt");
  finishNamedRealitySplitTrial(room,"real-death");
 }
 public static void main(String[] args){
  for(int size:new int[]{2,3}){
   var p=new Wave7AdvanceAdapterProbe();p.begin(size);
   p.defeat(0,RealitySplitTrialController.NamedTrial.WARDEN);
   check(p.realitySplitTrialController.namedTrial(0).trial()==RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN,"Warden death must start Marksman");
   check(p.starts==1&&p.opens==0&&!p.realitySplitChamberController.allChambersComplete(7),"no premature chamber completion");
   p.finishNamedRealitySplitTrial(0,"duplicate");check(p.starts==1,"duplicate cannot advance active stage");
   p.defeat(0,RealitySplitTrialController.NamedTrial.MOBILE_MARKSMAN);
   p.defeat(0,RealitySplitTrialController.NamedTrial.RIFT_ARCHMAGE);
   check(p.realitySplitTrialController.namedTrial(0).trial()==RealitySplitTrialController.NamedTrial.PERSONAL_ECHO,"all four trials remain required");
   check(p.starts==3&&p.opens==0&&!p.realitySplitTrialController.allComplete(7),"Echo cannot be skipped");
  }
  var failed=new Wave7AdvanceAdapterProbe();failed.begin(2);failed.durable=false;
  failed.defeat(0,RealitySplitTrialController.NamedTrial.WARDEN);
  check(failed.phase==EventPhase.RECOVERY_REQUIRED&&failed.starts==0&&failed.opens==0,"failed checkpoint blocks access and next actor");
  for(int size:new int[]{4,5,10,11,20}){
   var p=new Wave7AdvanceAdapterProbe();p.begin(size);
   check(p.realitySplitChamberController.assignment().chamberCount()==4,"large party uses four rooms");
   check(p.realitySplitTrialController.presentableNamedTrials().size()==4,"four named trials are live");
   p.defeat(0,RealitySplitTrialController.NamedTrial.WARDEN);
   check(p.opens==1&&p.starts==0,"completed room opens without advancing parallel peers");
   check(p.realitySplitTrialController.presentableNamedTrials().size()==3,"three peers remain active");
   check(!p.realitySplitTrialController.allComplete(7),"one room cannot finish the encounter");
  }
 }
'''
    path=tmp_path/'Wave7AdvanceAdapterProbe.java';path.write_text(java+method+'}',encoding='utf-8')
    built=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(SRC),'-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    result=subprocess.run(['java','-cp',str(tmp_path),'Wave7AdvanceAdapterProbe'],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
