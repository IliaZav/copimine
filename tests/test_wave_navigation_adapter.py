"""Run the actual Paper leash method against ordinary movement and emergency cases."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def extract(source, signature):
    start = source.index("    private " + signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_wave_edge_walks_back_jumping_is_not_teleported_and_boss_keeps_its_leash(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = extract(source, "void enforceCombatLeash(Entity entity, Location anchor, double radius, String logMarker)")
    for helper in ["boolean maintainWaveLeashReturn(Mob mob, Location anchor, double radius)",
                   "void beginWaveLeashReturn(Mob mob, Location destination)"]:
        if "    private " + helper in source:
            adapter += extract(source, helper)
    java = tmp_path / "WaveLeashProbe.java"
    java.write_text('''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.CombatMovementPolicy;
public class WaveLeashProbe {
  static final String EVENT_KIND_BOSS = "BOSS"; Object keyKind = new Object();
  static final double MIN_BOSS_CORE_DISTANCE_BLOCKS = 3.5, MIN_WAVE_CORE_DISTANCE_BLOCKS = 1.5;
  static final long WAVE_PATH_REQUEST_INTERVAL_MILLIS = 500;
  long generation = 5; String phase = "WAVE"; int teleports, requests, fallbacks; boolean pathWorks=true;
  Map<UUID,WaveLeashReturn> waveLeashReturns = new HashMap<>();
  Map<UUID,Long> nextWavePathRequestMillis = new HashMap<>();
  Map<UUID,Location> waveLastPathDestinations = new HashMap<>();
  record WaveLeashReturn(long generation, Location destination, long deadlineMillis, long refreshMillis) { }
  static class Location implements Cloneable {
    double x,y,z; Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    Object getWorld(){return "world";} double getX(){return x;} double getY(){return y;} double getZ(){return z;}
    public Location clone(){return new Location(x,y,z);} Location add(double a,double b,double c){x+=a;y+=b;z+=c;return this;}
  }
  static class Entity {
    UUID id=UUID.randomUUID(); Location position=new Location(0,68,0); String kind="MOB";
    boolean ground=true, valid=true; Object getWorld(){return "world";} Location getLocation(){return position;}
    UUID getUniqueId(){return id;} boolean isOnGround(){return ground;} boolean isValid(){return valid;}
    boolean isDead(){return !valid;}
  }
  class Mob extends Entity {
    Player target; Player getTarget(){return target;} void setTarget(Player p){target=p;}
    void setAI(boolean active){} void setAware(boolean active){}
    Pathfinder getPathfinder(){return new Pathfinder();}
  }
  class Player extends Entity { }
  class Pathfinder { boolean moveTo(Location point,double speed){requests++;return pathWorks;} void stopPathfinding(){} }
  boolean closed, safeFloor=true, core;
  boolean isFogFrozenCombatEntity(Entity entity){return false;}
  Controller waveCombatCoordinator=new Controller();
  void clearWaveCombatCue(UUID id){} void finishWaveMobDash(Entity mob,long expected){}
  class Controller {
    void remove(UUID id){}
    boolean allChambersComplete(long gen){return !closed;} Controller assignment(){return this;}
    int chamberCount(){return 2;}
  }
  Controller realitySplitChamberController=new Controller();
  static class ChamberIsolationPolicy { static boolean containsPoint(int room,double x,double z,int count){return true;} }
  Logger getLogger(){return Logger.getLogger("WaveLeashProbe");}
  String readString(Entity e,Object key){return e.kind;}
  boolean isWaveCombatKind(String kind){return "MOB".equals(kind);}
  boolean isLiveOwnedEntity(UUID id){return true;}
  boolean isCoreBlockPosition(Location loc){return core;}
  boolean outsideCombatVertical(Location loc,Location anchor){return Math.abs(loc.y-anchor.y)>3;}
  double configuredCombatVerticalRadius(){return 3;}
  double horizontalDistanceSquared(Location a,Location b){return Math.pow(a.x-b.x,2)+Math.pow(a.z-b.z,2);}
  int realitySplitChamberId(Entity e){return closed?0:-1;}
  boolean isSafeCombatEntityLocation(Location loc,Entity e){return safeFloor;}
  Location realitySplitLeashPreferred(Location anchor,Entity e,int room,int count){return e.getLocation().clone();}
  Location realitySplitCombatPreferred(Location anchor,Location preferred,Player target,Mob mob){return preferred;}
  Location findSafeCombatLocation(Location anchor,Location preferred,double r,double min,int room,Entity e){return preferred==null?new Location(13,68,-3):preferred;}
  boolean teleportCombatEntity(Entity e,Location loc){teleports++;e.position=loc;return true;}
  boolean requestBoundedCombatMovement(Mob m,Location to,double speed,Location a,double r,double min,int room,String log){fallbacks++;return true;}
  String locationText(Location loc){return "synthetic";}
  public static void main(String[] args){
    var p=new WaveLeashProbe(); var anchor=new Location(0,68,0);
    var edge=p.new Mob();edge.position=new Location(19.15,68,0);
    p.enforceCombatLeash(edge,anchor,19,"WAVE_AI_LEASH");
    if(p.teleports!=0)throw new AssertionError("ordinary edge navigation teleported instead of walking back");
    if(p.requests!=1)throw new AssertionError("edge return did not request a walking path");
    p.enforceCombatLeash(edge,anchor,19,"WAVE_AI_LEASH");
    if(p.requests!=1)throw new AssertionError("watchdog recomputed the return path every tick");
    p.closed=true;p.safeFloor=false;
    var jumping=p.new Mob();jumping.ground=false;jumping.position=new Location(5,69.1,0);
    p.enforceCombatLeash(jumping,anchor,19,"WAVE_AI_LEASH");
    if(p.teleports!=0)throw new AssertionError("normal airborne footprint was treated as invalid floor");
    p.closed=false;p.safeFloor=true;
    var boss=p.new Mob();boss.kind=EVENT_KIND_BOSS;boss.position=new Location(20,68,0);
    p.enforceCombatLeash(boss,anchor,19,"BOSS_LEASH");
    if(p.teleports!=1)throw new AssertionError("wave repair changed the boss emergency leash");
    var fallen=p.new Mob();fallen.position=new Location(5,58,0);
    p.enforceCombatLeash(fallen,anchor,19,"WAVE_AI_LEASH");
    if(p.teleports!=2)throw new AssertionError("void recovery was removed");
    generationProbe(p,anchor);
    p=new WaveLeashProbe();p.pathWorks=false;edge=p.new Mob();edge.position=new Location(19.15,68,0);
    p.enforceCombatLeash(edge,anchor,19,"WAVE_AI_LEASH");
    if(p.teleports!=0||p.fallbacks!=1)throw new AssertionError("failed return path idled instead of using bounded movement");
  }
  static void generationProbe(WaveLeashProbe p,Location anchor){
    var edge=p.new Mob();edge.position=new Location(-19.15,68,0);p.enforceCombatLeash(edge,anchor,19,"WAVE_AI_LEASH");
    p.generation++;
    p.enforceCombatLeash(edge,anchor,19,"WAVE_AI_LEASH");
    var state=p.waveLeashReturns.get(edge.id);
    if(state!=null&&state.generation()!=p.generation)throw new AssertionError("stale generation retained a return route");
  }
''' + adapter + "\n}\n", encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/CombatMovementPolicy.java"
    compiled = subprocess.run(["javac", "-J-Xmx128m", "-d", str(tmp_path), str(policy), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-Xmx128m", "-cp", str(tmp_path), "WaveLeashProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
