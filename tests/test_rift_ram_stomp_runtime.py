"""Execute the real traveling stomp adapter, with a deterministic clock and players."""
import subprocess
from pathlib import Path
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_stomp_travels_skips_jump_and_late_join_and_never_damages(tmp_path):
    runtime = (ROOT/'copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java').read_text('utf-8')
    method = extract(runtime,'void stomp(List<Player> players)')
    java = r'''
import java.util.*;
import me.copimine.endevent.domain.RiftRamPolicy;
public class StompRuntimeProbe {
  enum Action { RECOVER }
  enum Particle { BLOCK }
  enum Material { DEEPSLATE,OBSIDIAN; Object createBlockData(){return this;} }
  enum PotionEffectType { SLOWNESS }
  record PotionEffect(PotionEffectType type,int ticks,int amp) {}
  static class World {}
  static class Location {
    double x,y,z;World world;
    Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
    double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    World getWorld(){return world;}
    double distanceSquared(Location o){return (x-o.x)*(x-o.x)+(y-o.y)*(y-o.y)+(z-o.z)*(z-o.z);}
  }
  static class Player {
    UUID id=UUID.randomUUID();Location at;boolean grounded=true;int applications,packets;
    Player(World w,double x){at=new Location(w,x,65,0);}
    UUID getUniqueId(){return id;}World getWorld(){return at.world;}Location getLocation(){return at;}
    double getX(){return at.x;}double getY(){return at.y;}double getZ(){return at.z;}
    boolean isOnGround(){return grounded;}
    void spawnParticle(Particle p,double x,double y,double z,int count,double dx,double dy,double dz,int speed,Object data){packets++;}
    void addPotionEffect(PotionEffect e){
      if(e.type()!=PotionEffectType.SLOWNESS||e.ticks()!=60||e.amp()!=1)throw new AssertionError("wrong effect");
      applications++;
    }
    // No damage method: any production damage call makes this probe fail compilation.
  }
  static class Host { int floorY(){return 64;} int particleBudget(){return 256;} }
  final Host host=new Host(); int tick,actionTick;double lastRadius;
  Location stompOrigin;Set<UUID> stompHits=new HashSet<>(),stompRoster=new HashSet<>();
  boolean recovered; void hold(){}void begin(Action a){recovered=true;}
  static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
  public static void main(String[] args){
    for(int size:new int[]{2,5,10,11,20}) {
      StompRuntimeProbe cast=new StompRuntimeProbe();World world=new World();
      cast.stompOrigin=new Location(world,0,65,0);
      Player ground=new Player(world,8), jumper=new Player(world,4),far=new Player(world,13),late=new Player(world,6);
      jumper.grounded=false;jumper.at.y=66.2;
      List<Player> players=new ArrayList<>(List.of(ground,jumper,far));
      while(players.size()<size)players.add(new Player(world,11));
      for(Player p:players)cast.stompRoster.add(p.id);
      for(int tick=0;tick<=28;tick++){
        cast.tick=tick;
        if(tick==5)players.add(late);
        cast.stomp(players);
        if(tick<15)check(ground.applications==0,"not an instant radius debuff");
      }
      check(ground.applications==1,"grounded player hit exactly once");
      check(jumper.applications==0,"jump clears wave");check(far.applications==0,"maximum radius");
      check(late.applications==0,"late join receives no stale cast");
      check(cast.recovered&&cast.stompHits.isEmpty()&&cast.stompRoster.isEmpty(),"cast cleanup");
      check(ground.packets>1000&&ground.packets<5000,"visible bounded particle budget");
    }
    System.out.println("OK");
  }
'''
    path = tmp_path/'StompRuntimeProbe.java'
    path.write_text(java+method+'\n}',encoding='utf-8')
    built = subprocess.run(['javac','-d',str(tmp_path),str(ROOT/'copimine-end-event/src/me/copimine/endevent/domain/RiftRamPolicy.java'),str(path)],capture_output=True,text=True)
    assert built.returncode == 0,built.stderr
    result = subprocess.run(['java','-cp',str(tmp_path),'StompRuntimeProbe'],capture_output=True,text=True)
    assert result.returncode == 0,result.stderr
