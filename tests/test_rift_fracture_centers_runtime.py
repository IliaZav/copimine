"""Run actual fracture state and collision with multiple independently selected cells."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract
ROOT=Path(__file__).resolve().parents[1]
def test_fracture_columns_follow_floor_cells_and_hit_once(tmp_path):
 source=(ROOT/"copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text("utf-8")
 state=extract(source,"static final class RiftFractureRuntimeState")
 damage=extract(source,"void damageRiftFractureColumn(RiftFractureRuntimeState state,")
 java=tmp_path/"FractureCentersProbe.java"
 java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RiftFractureEruptionPolicy;
public class FractureCentersProbe {
 static class World {UUID id=UUID.randomUUID();UUID getUID(){return id;}}
 static class Location {World world;double x,y,z;Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
  public Location clone(){return new Location(world,x,y,z);}World getWorld(){return world;}
  double getX(){return x;}double getY(){return y;}double getZ(){return z;}int getBlockY(){return (int)Math.floor(y);}
  Vector toVector(){return new Vector(x,y,z);}}
 static class Vector {double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
  Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}Vector setY(double v){y=v;return this;}
  double lengthSquared(){return x*x+y*y+z*z;}Vector normalize(){return this;}Vector multiply(double f){return this;}}
 static class LivingEntity {}
 static class Player extends LivingEntity {Location at;UUID id=UUID.randomUUID();int hits,impulses;boolean reject;
  Player(Location l){at=l;}Location getLocation(){return at;}World getWorld(){return at.world;}
  UUID getUniqueId(){return id;}void damage(double d,LivingEntity e){hits++;}void setVelocity(Vector v){impulses++;}}
 record BlockCellKey(UUID worldId,int x,int y,int z){}
 long eventTickCounter;String eventId="probe";
 LivingEntity liveBoss(){return new LivingEntity();}
 boolean applyBossAbilityDamage(LivingEntity boss,Player player,double damage){if(player.reject)return false;player.damage(damage,boss);return true;}
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
'''+state+damage+r'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){var p=new FractureCentersProbe();var w=new World();
  var cells=List.of(new RiftFractureEruptionPolicy.Cell(-8,3),new RiftFractureEruptionPolicy.Cell(8,-3));
  var originals=Map.of(new BlockCellKey(w.id,-8,64,3),"stone",new BlockCellKey(w.id,8,64,-3),"stone");
  var stage=RiftFractureEruptionPolicy.Stage.forBatch(0,7);p.eventTickCounter=stage.eruptionTick();
  var state=new RiftFractureRuntimeState(UUID.randomUUID(),new Location(w,0,65,0),cells,originals,stage,200);
  var left=new Player(new Location(w,-7.5,65,3.5));var right=new Player(new Location(w,8.5,65,-2.5));
  var unrelated=new Player(new Location(w,0,65,0));var high=new Player(new Location(w,8.5,100,-2.5));
  var blocked=new Player(left.at);blocked.reject=true;
  var players=List.of(left,right,unrelated,high,blocked);
  p.damageRiftFractureColumn(state,stage,players);p.damageRiftFractureColumn(state,stage,players);
  check(left.hits==1&&right.hits==1,"selected floor geysers did not own real collision");
  check(unrelated.hits==0&&high.hits==0,"invisible central or height-mismatched volume hit a player");
  left.at=right.at;p.damageRiftFractureColumn(state,stage,players);
  check(left.hits==1,"one batch eruption hit a moving player twice");
  check(blocked.hits==0&&blocked.impulses==0,"canceled hit produced damage or knockback");
 }
}
''',encoding="utf-8")
 domain=ROOT/"copimine-end-event/src/me/copimine/endevent/domain/RiftFractureEruptionPolicy.java"
 built=subprocess.run(["javac","-d",str(tmp_path),str(domain),str(java)],capture_output=True,text=True)
 assert built.returncode==0,built.stderr
 ran=subprocess.run(["java","-cp",str(tmp_path),"FractureCentersProbe"],capture_output=True,text=True)
 assert ran.returncode==0,ran.stderr
