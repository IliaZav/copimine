"""Manual boss death is finite, idempotent and cannot enter official victory."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT=Path(__file__).resolve().parents[1]


def test_real_disposable_death_cleans_once_and_rejects_stale_callback(tmp_path):
    method=extract((ROOT/"copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8"),"void commitDisposableBossDefeat(")
    java=tmp_path/"DisposableBossDefeatProbe.java"
    java.write_text("""import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.*;
public class DisposableBossDefeatProbe {
 static class Location{} static class Vector{}
 static class LivingEntity{UUID id=new UUID(0,1);boolean test=true;UUID getUniqueId(){return id;}
  Location getLocation(){return new Location();}void setInvulnerable(boolean b){} }
 static class Pathfinder{void stopPathfinding(){}}
 static class Mob extends LivingEntity{void setTarget(Object o){} Pathfinder getPathfinder(){return new Pathfinder();}void setAI(boolean b){} }
 static class Task{Runnable callback;Task(Runnable r){callback=r;}}
 static class Scheduler{int scheduled;Task runTaskLater(Object plugin,Runnable r,long ticks){
  if(ticks!=40)throw new AssertionError("death clip lifetime");scheduled++;return new Task(r);}}
 static class Bukkit{static Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;}}
 static class Registry{boolean owns(long generation){return true;}}
 LivingEntity boss=new Mob();boolean testCombatAiMode=true,bossDefeatCinematicStarted;
 UUID bossUuid=boss.id,bossDefeatCinematicBossUuid;String eventId="event";long generation=7;
 Registry taskRegistry=new Registry();Task bossDefeatCinematicTask;
 BossAbilityState bossAbilityState=BossAbilityState.NONE;int cleanup,stopped,effects;
 boolean isTestBoss(LivingEntity b){return b!=null&&b.test;}
 boolean isBossCombatRuntimeActive(LivingEntity b){return !bossDefeatCinematicStarted&&b==boss;}
 void cancelBossCastTask(){}void cancelBossFinalStrike(){}void clearTentacles(String reason){}
 void clearBossServants(){}void clearArenaInferno(){}void clearVoidMarkZones(){}
 void clearActiveRiftProjectiles(){}void clearCurrentRiftFractures(String reason){}
 void sendBossAnimationVisualUpdate(LivingEntity b,String animation){if(!animation.equals("DYING"))throw new AssertionError("wrong clip");}
 void playBossImpactPresentation(Object... args){effects++;}Vector contactFacing(Location l){return new Vector();}
 void stopEventMusic(){stopped++;}void clearBossOnly(){cleanup++;bossDefeatCinematicStarted=false;bossUuid=null;}
 void registerEncounterTask(Task task){}Logger getLogger(){return Logger.getAnonymousLogger();}
"""+method+"""
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  var p=new DisposableBossDefeatProbe();p.commitDisposableBossDefeat(p.boss);
  check(p.bossDefeatCinematicStarted&&p.effects==1,"death not committed");
  p.commitDisposableBossDefeat(p.boss);check(p.effects==1,"duplicate lethal replayed scene");
  p.bossDefeatCinematicTask.callback.run();check(p.cleanup==1&&p.stopped==1,"death did not clean music and model");
  p.bossDefeatCinematicTask.callback.run();check(p.cleanup==1,"duplicate cleanup");
  var stale=new DisposableBossDefeatProbe();stale.commitDisposableBossDefeat(stale.boss);stale.generation++;
  stale.bossDefeatCinematicTask.callback.run();check(stale.cleanup==0,"old callback cleared new generation");
  var official=new DisposableBossDefeatProbe();official.boss.test=false;official.commitDisposableBossDefeat(official.boss);
  check(!official.bossDefeatCinematicStarted&&official.effects==0,"official boss entered diagnostic path");
 }
}
""",encoding="utf-8")
    result=subprocess.run(["javac","-encoding","UTF-8","-sourcepath",str(ROOT/"copimine-end-event/src"),"-d",str(tmp_path),str(java)],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
    result=subprocess.run(["java","-cp",str(tmp_path),"DisposableBossDefeatProbe"],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
