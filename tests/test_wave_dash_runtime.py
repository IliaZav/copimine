"""Tick the real scheduled dash: movement, collision and lifecycle cancellation."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_dash_moves_on_ticks_stops_at_wall_and_cancels_stale_or_dead_targets(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = extract(source, "void launchWaveMobDash(LivingEntity caster, Player target, Location mark, long expectedGeneration,")
    adapter += extract(source, "void finishWaveMobDash(LivingEntity caster, long expectedGeneration)")
    java = tmp_path / "WaveDashProbe.java"
    java.write_text('''
import java.util.*;
import me.copimine.endevent.runtime.WaveCombatCoordinator;
public class WaveDashProbe {
  static final double MIN_WAVE_CORE_DISTANCE_BLOCKS=1;long generation=5,eventTickCounter;
  Map<UUID,Long> waveDashGenerations=new HashMap<>();Map<UUID,Object> waveLeashReturns=new HashMap<>();
  WaveCombatCoordinator waveCombatCoordinator=new WaveCombatCoordinator(); Registry taskRegistry=new Registry();
  int impacts,cancelled,recoverCues;double wall=100;
  class Registry {boolean owns(long expected){return generation==expected;}}
  static class Vector implements Cloneable {
    double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}Vector setY(double n){y=n;return this;}
    double length(){return Math.sqrt(x*x+y*y+z*z);}Vector normalize(){double n=length();x/=n;y/=n;z/=n;return this;}
    Vector multiply(double n){x*=n;y*=n;z*=n;return this;}public Vector clone(){return new Vector(x,y,z);}
    double getX(){return x;}double getY(){return y;}double getZ(){return z;}
  }
  static class Location implements Cloneable {
    double x,y,z;Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    Object getWorld(){return "world";}Vector toVector(){return new Vector(x,y,z);}
    public Location clone(){return new Location(x,y,z);}Location add(Vector v){x+=v.x;y+=v.y;z+=v.z;return this;}
  }
  static class LivingEntity {
    UUID id=UUID.randomUUID();boolean valid=true;Location pos=new Location(3,68,0);Vector velocity=new Vector(0,0,0);
    UUID getUniqueId(){return id;}Object getWorld(){return "world";}Location getLocation(){return pos;}
    boolean isValid(){return valid;}boolean isDead(){return !valid;}Vector getVelocity(){return velocity;}
    void setVelocity(Vector v){velocity=v;}
  }
  static class Mob extends LivingEntity {void setTarget(Player p){}Pathfinder getPathfinder(){return new Pathfinder();}
    void setAI(boolean b){}void setAware(boolean b){}}
  static class Player extends LivingEntity {}
  static class Pathfinder {void stopPathfinding(){}}
  static class BukkitTask {Runnable action;boolean active=true;void cancel(){active=false;}}
  static class Scheduler {BukkitTask task;BukkitTask runTaskTimer(Object plugin,Runnable action,long delay,long period){
    task=new BukkitTask();task.action=action;return task;}}
  static class Bukkit {static Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;}}
  Location coreCombatAnchorLocation(){return new Location(0,68,0);}double waveMovementRadius(){return 19;}
  boolean isLiveOwnedEntity(UUID id){return true;}boolean isMiniBossCombatPhase(){return true;}
  boolean isMiniBossTargetAllowed(LivingEntity caster,Player target){return caster.valid&&target.valid;}
  boolean isFogFrozenCombatEntity(LivingEntity caster){return false;}
  boolean isWaveFogAiHeld(LivingEntity caster){return false;}
  int realitySplitChamberId(LivingEntity caster){return 0;}
  boolean isSafeCombatStep(Location anchor,Location p,double r,double min,int room,LivingEntity caster){return p.x<=wall&&p.x<=19;}
  void miniBossCommittedStep(LivingEntity caster,Player target,Location mark){impacts++;}
  void renderWaveCombatCue(LivingEntity caster,Location p,String spell,String stage){if(stage.equals("recover"))recoverCues++;}
  void playWaveAbilitySound(LivingEntity caster,String spell,String stage){}
  void cancelWaveCombatAttack(UUID id,WaveCombatCoordinator.Lease lease,long expected){cancelled++;}
  void registerEncounterTask(BukkitTask task){}
  void tick(Mob mob){eventTickCounter++;var task=Bukkit.scheduler.task;if(task.active)task.action.run();mob.pos.add(mob.velocity);}
  public static void main(String[] args){
    var p=new WaveDashProbe();var caster=new Mob();var target=new Player();target.pos=new Location(10,68,0);
    p.launchWaveMobDash(caster,target,target.pos.clone(),5,null);
    if(caster.pos.x!=3)throw new AssertionError("launch teleported the caster");
    p.tick(caster);if(caster.pos.x<=3||caster.pos.x>3.55)throw new AssertionError("dash is not a bounded physical step");
    for(int i=0;i<10;i++)p.tick(caster);
    if(caster.pos.x>7.401||caster.pos.z!=0||p.impacts!=1||!p.waveDashGenerations.isEmpty())
      throw new AssertionError("dash overshot, changed locked heading or retained its state");
    p=new WaveDashProbe();p.wall=4;caster=new Mob();
    p.launchWaveMobDash(caster,target,target.pos.clone(),5,null);for(int i=0;i<10;i++)p.tick(caster);
    if(caster.pos.x>4||p.impacts!=1)throw new AssertionError("dash crossed a wall or skipped recovery");
    p=new WaveDashProbe();caster=new Mob();p.launchWaveMobDash(caster,target,target.pos.clone(),5,null);
    p.tick(caster);double stopped=caster.pos.x;p.generation=6;p.tick(caster);p.tick(caster);
    if(caster.pos.x!=stopped||p.impacts!=0||p.cancelled!=1||!p.waveDashGenerations.isEmpty())
      throw new AssertionError("old generation continued a scheduled attack");
    if(p.recoverCues!=0)throw new AssertionError("old dash published a recovery effect into the new generation");
    p=new WaveDashProbe();caster=new Mob();p.launchWaveMobDash(caster,target,target.pos.clone(),5,null);
    target.valid=false;p.tick(caster);
    if(p.impacts!=0||p.cancelled!=1||!p.waveDashGenerations.isEmpty())throw new AssertionError("dead/quit target retained dash");
  }
''' + adapter + "\n}\n", encoding="utf-8")
    coordinator = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/WaveCombatCoordinator.java"
    result = subprocess.run(["javac", "-J-Xmx128m", "-d", str(tmp_path), str(coordinator), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-Xmx128m", "-cp", str(tmp_path), "WaveDashProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
