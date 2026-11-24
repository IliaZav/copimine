"""Execute the real moving-earthquake adapter, with a deterministic clock and players.

N23-P1-10 replaced the single stationary radius-12 seam (``stomp``) with a bounded
5..6 footfall Trample. This probe compiles the production ``trample`` and
``drawTrampleFront`` methods against a deterministic Paper stub, so the ordering
and hit-accounting behaviour is exercised as written rather than described:

* each front travels outward, so a player is not an instant radius debuff;
* a grounded player inside a front is hit exactly once per front it sweeps;
* a jump (or an elevated body) clears every front;
* a body beyond the front maximum is never reached;
* a late join is not in the frozen roster and receives no stale cast;
* a refused (protected) damage application is resolved once per front but is
  never recorded as a hit and never receives the secondary slow;
* the cast cleans up its bounded sets when it ends.
"""
import subprocess
from pathlib import Path
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / 'copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java'


def test_trample_travels_skips_jump_and_late_join_and_damages_once_per_front(tmp_path):
    runtime = RUNTIME.read_text('utf-8')
    methods = (extract(runtime, 'void trample(List<Player> players)')
               + '\n' + extract(runtime, 'void drawTrampleFront(List<Player> viewers, double radius)'))
    java = r'''
import java.util.*;
import me.copimine.endevent.domain.RamMovingEarthquakePolicy;
public class TrampleRuntimeProbe {
  enum Action { RECOVER }
  enum Particle { BLOCK }
  enum Material { DEEPSLATE,OBSIDIAN,CRYING_OBSIDIAN; Object createBlockData(){return this;} }
  enum PotionEffectType { SLOWNESS }
  enum Sound { ENTITY_RAVAGER_STEP, BLOCK_ANVIL_LAND }
  record PotionEffect(PotionEffectType type,int ticks,int amp) {}
  static class World {
    int sounds;
    void playSound(Location at,Sound sound,float volume,float pitch){sounds++;}
  }
  static class Location {
    double x,y,z;World world;
    Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
    double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    World getWorld(){return world;}
    public Location clone(){return new Location(world,x,y,z);}
    double distanceSquared(Location o){return (x-o.x)*(x-o.x)+(y-o.y)*(y-o.y)+(z-o.z)*(z-o.z);}
  }
  static class Player {
    UUID id=UUID.randomUUID();Location at;boolean grounded=true;int applications,packets;
    int damageAttempts;boolean refuse;
    Player(World w,double x){at=new Location(w,x,65,0);}
    UUID getUniqueId(){return id;}World getWorld(){return at.world;}Location getLocation(){return at;}
    double getX(){return at.x;}double getY(){return at.y;}double getZ(){return at.z;}
    boolean isOnGround(){return grounded;}
    void spawnParticle(Particle p,double x,double y,double z,int count,double dx,double dy,double dz,double speed,Object data){packets++;}
    void spawnParticle(Particle p,Location l,int count,double a,double b,double c,double d,Object data){packets++;}
    void addPotionEffect(PotionEffect e){
      if(e.type()!=PotionEffectType.SLOWNESS
              ||e.ticks()!=RamMovingEarthquakePolicy.BEAT_SLOW_TICKS
              ||e.amp()!=RamMovingEarthquakePolicy.BEAT_SLOW_AMPLIFIER)throw new AssertionError("wrong effect");
      applications++;
    }
    int damageTaken;
  }
  static class Ram {
    World world;Location at;Player target;
    Ram(World w){world=w;at=new Location(w,0,65,0);}
    World getWorld(){return world;}Location getLocation(){return at;}
    boolean isValid(){return true;}void setAI(boolean b){}void setTarget(Player p){target=p;}
  }
  static class Host {
    List<Player> roster=new ArrayList<>();
    int floorY(){return 64;}
    int particleBudget(){return 256;}
    int damageCalls;double lastAmount;Set<Double> amounts=new HashSet<>();
    boolean damage(Ram source,Player player,double amount){
      damageCalls++;lastAmount=amount;amounts.add(amount);player.damageAttempts++;
      if(source==null||player==null)throw new AssertionError("damage without source/target");
      // A protected/ineligible player refuses the application. The old code
      // recorded the hit and applied the slow anyway; the fix must not.
      if(player.refuse) return false;
      player.damageTaken++;
      return true;
    }
    List<Player> players(){return roster;}
  }
  final Host host=new Host(); int tick,actionTick;
  Ram ram; Location trampleOrigin; double trampleRadius;
  int trampleBeats,trampleBeat=-1,trampleWarnedBeat=-1;
  Set<UUID> trampleHits=new HashSet<>(),trampleRoster=new HashSet<>(),trampleBeatResolved=new HashSet<>();
  boolean recovered; void begin(Action a){recovered=true;}
  void dust(Location at,int count,double spread,double height){}
  Player nearest(Location at,List<Player> players){return players.isEmpty()?null:players.get(0);}
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  public static void main(String[] args){
    for(int size:new int[]{2,5,10,11,20}) {
      TrampleRuntimeProbe cast=new TrampleRuntimeProbe();World world=new World();
      cast.ram=new Ram(world);
      int beats=RamMovingEarthquakePolicy.beatsFor(size);
      cast.trampleBeats=beats;
      Player ground=new Player(world,8), jumper=new Player(world,4),far=new Player(world,40),late=new Player(world,6);
      Player protectedPlayer=new Player(world,7);protectedPlayer.refuse=true;
      jumper.grounded=false;jumper.at.y=67.0;
      List<Player> players=new ArrayList<>(List.of(ground,jumper,far,protectedPlayer));
      while(players.size()<size)players.add(new Player(world,9));
      cast.host.roster=players;
      // The roster is frozen when the Trample opens; a late join is not in it.
      for(Player p:players)cast.trampleRoster.add(p.getUniqueId());
      int total=RamMovingEarthquakePolicy.totalTicks(beats);
      for(int tick=0;tick<=total+2;tick++){
        cast.tick=tick;
        if(tick==total/2)players.add(late);
        cast.trample(players);
        if(tick<=1)check(ground.applications==0,"not an instant radius debuff");
      }
      check(cast.recovered,"the cast ends into recovery");
      check(ground.applications==beats,"a grounded player is hit once per front");
      check(ground.damageTaken==beats,"a grounded player is damaged once per front");
      check(cast.host.amounts.contains(RamMovingEarthquakePolicy.BEAT_DAMAGE),
            "an ordinary footfall used the policy damage amount");
      check(cast.host.amounts.contains(RamMovingEarthquakePolicy.FINAL_BEAT_DAMAGE),
            "the final footfall used the heavier policy amount");
      check(jumper.applications==0,"a jump clears every front");
      check(jumper.damageTaken==0,"a jump also clears the damage");
      check(far.applications==0&&far.damageTaken==0,"beyond the front maximum takes nothing");
      check(late.applications==0,"a late join receives no stale cast");
      check(late.damageTaken==0,"a late join takes no stale damage");
      check(protectedPlayer.damageAttempts==beats,"a refused target is resolved once per front");
      check(protectedPlayer.damageTaken==0,"refused damage is never applied");
      check(protectedPlayer.applications==0,"refused damage grants no secondary slow");
      check(cast.trampleHits.isEmpty()&&cast.trampleRoster.isEmpty()
            &&cast.trampleBeatResolved.isEmpty(),"cast cleanup");
      check(ground.packets>0,"the front is drawn for a nearby viewer");
    }
    System.out.println("OK");
  }
'''
    path = tmp_path / 'TrampleRuntimeProbe.java'
    path.write_text(java + methods + '\n}', encoding='utf-8')
    built = subprocess.run(['javac', '-d', str(tmp_path),
                            str(ROOT / 'copimine-end-event/src/me/copimine/endevent/domain/RamMovingEarthquakePolicy.java'),
                            str(path)], capture_output=True, text=True)
    assert built.returncode == 0, built.stderr
    result = subprocess.run(['java', '-cp', str(tmp_path), 'TrampleRuntimeProbe'], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
