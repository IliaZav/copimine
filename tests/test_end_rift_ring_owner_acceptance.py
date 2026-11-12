# Execute the actual three-row owner. Detached Paper IO never decides collision.
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]

STUBS = {
 'org/bukkit/World.java': 'package org.bukkit; public class World { public java.util.UUID getUID(){return new java.util.UUID(0,1);} }',
 'org/bukkit/Location.java': '''package org.bukkit; public class Location implements Cloneable {
 public World w; public double x,y,z; public Location(World w,double x,double y,double z){this.w=w;this.x=x;this.y=y;this.z=z;}
 public World getWorld(){return w;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;}
 public Location clone(){return new Location(w,x,y,z);} public Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
 public double distanceSquared(Location p){return (x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z);}}
 ''',
 'org/bukkit/util/Vector.java': 'package org.bukkit.util; public record Vector(double x,double y,double z){}',
 'org/bukkit/entity/LivingEntity.java': '''package org.bukkit.entity; public class LivingEntity {
 public org.bukkit.Location at; public boolean valid=true; public LivingEntity(org.bukkit.Location p){at=p;}
 public org.bukkit.Location getLocation(){return at.clone();} public boolean isValid(){return valid;}
 public boolean isDead(){return !valid;} public java.util.UUID getUniqueId(){return new java.util.UUID(1,1);}}''',
 'org/bukkit/entity/ItemDisplay.java': 'package org.bukkit.entity; public class ItemDisplay extends LivingEntity {public ItemDisplay(org.bukkit.Location p){super(p);}}',
 'org/bukkit/entity/Player.java': '''package org.bukkit.entity; public class Player extends LivingEntity {
 public org.bukkit.util.Vector launched; public Player(org.bukkit.Location p){super(p);} public boolean isOnline(){return true;}
 public org.bukkit.World getWorld(){return at.getWorld();} public void setFallDistance(float f){}
 public void setVelocity(org.bukkit.util.Vector v){launched=v;}}'''
}

PROBE = r'''
import java.util.*;
import org.bukkit.*;import org.bukkit.entity.*;
import me.copimine.endevent.runtime.LastSealRingRuntime;
import me.copimine.protocol.KaguneRingFrame;
public class RingRuntimeProbe implements LastSealRingRuntime.Host {
 final World world=new World(); final LivingEntity boss=new LivingEntity(new Location(world,0,64,0));
 final Player player=new Player(new Location(world,2,64,0));
 String mode;int wanted,hits,attempts,removes,finishes,failures;long generation=7;
 boolean blockedRemoval;double hitRadius;ItemDisplay carrier;KaguneRingFrame frame;
 final Map<Integer,List<Double>> frozen=new HashMap<>();
 RingRuntimeProbe(String mode,int row){this.mode=mode;wanted=row;}
 public LastSealRingRuntime.Scope scope(LivingEntity b){return new LastSealRingRuntime.Scope("event",generation,b.getUniqueId());}
 public boolean active(LivingEntity b){return b.valid;}
 public Location floorOrigin(LivingEntity b){return b.getLocation();}
 public List<Player> participants(){return List.of(player);}
 public boolean traversable(Location p){return true;}
 public ItemDisplay createCarrier(Location p){return carrier=new ItemDisplay(p);}
 public void publish(ItemDisplay d,KaguneRingFrame f){
  frame=f;var old=frozen.putIfAbsent(f.row(),f.gaps());
  check(old==null||old.equals(f.gaps()),"gaps changed after publication");
  if(mode.equals("publish") && f.canDamage())throw new IllegalStateException("failed-frame");
  player.at=new Location(world,0,64,0);
  if(mode.equals("gap"))place(f.gaps().getFirst(),f.radius());
  else if((mode.equals("all")||mode.equals("cancelled")||mode.equals("sweep")||f.row()==wanted)&&f.canDamage()){
   int i=0;while(!f.drawsInstance(i))i++;place(f.direction(i),mode.equals("sweep")?9:f.radius());
  }
 }
 void place(double angle,double radius){player.at=new Location(world,Math.cos(angle)*radius,64,Math.sin(angle)*radius);}
 public void remove(ItemDisplay d){removes++;if(!blockedRemoval)d.valid=false;}
 public boolean hit(LivingEntity b,Player p,double damage){
  check(frame.canDamage()&&frame.rise()==1,"damage before visible contact");
  check(damage>0,"zero collision damage");attempts++;
  if(mode.equals("cancelled")&&attempts==1){check(p.launched==null,"cancelled damage launched player");return false;}
  hits++;hitRadius=frame.radius();return true;
 }
 public void finished(LastSealRingRuntime.Scope s,String reason){finishes++;}
 public void failed(String reason){failures++;}
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  String mode=args[0];int row=Integer.parseInt(args[1]),party=Integer.parseInt(args[2]);
  var h=new RingRuntimeProbe(mode,row);var r=new LastSealRingRuntime(h);
  check(r.start(h.boss,party,0,91),"valid arena refused ring");
  check(h.hits==0&&h.frame.stage()==KaguneRingFrame.EMERGE,"cast damaged on arrival");
  if(mode.equals("owner")){h.generation++;r.tick(h.boss,1);check(!r.active()&&h.hits==0&&!h.carrier.valid,"stale generation survived");return;}
  if(mode.equals("cleanup")){
   h.blockedRemoval=true;r.cancel("probe");for(int t=1;t<=100;t++)r.tick(h.boss,t);
   check(h.carrier.valid&&h.removes<=7&&h.failures==1,"cleanup lost ownership or spammed retries");
   h.blockedRemoval=false;r.tick(h.boss,121);check(!h.carrier.valid,"cleanup could not retry");return;
  }
  // The boss moves away; the cast and its visible collision stay at the old origin.
  h.boss.at.x=40;
  for(int t=0;t<=LastSealRingRuntime.CAST_TICKS;t++){r.tick(h.boss,t);r.tick(h.boss,t);}
  check(!r.active()&&!h.carrier.valid&&h.finishes==1,"cast did not retire exactly once");
  if(mode.equals("gap")||mode.equals("publish"))check(h.hits==0,"gap/failed frame caused damage");
  else {
   check(h.hits==1,"row missed or repeated damage: "+h.hits);
   check(h.player.launched!=null&&Math.abs(h.player.launched.y()-.98)<.0001,"launch was missing or not bounded vertically");
   check(r.consumeFall(h.player,7)&&!r.consumeFall(h.player,7),"fall lease was lost or repeated");
  }
  if(mode.equals("cancelled"))check(h.attempts==2,"cancelled hit consumed the cast receipt");
  if(mode.equals("sweep"))check(h.hitRadius>8.1&&h.hitRadius<9.7,"collision did not move with visible radius");
  if(!mode.equals("publish"))check(h.frozen.size()==3,"not all three rows were published");
 }
}
'''

@pytest.fixture(scope='module')
def ring_runtime(tmp_path_factory):
    out=tmp_path_factory.mktemp('last-seal-actual-owner')
    files=[]
    for name,body in STUBS.items():
        p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8');files.append(str(p))
    p=out/'RingRuntimeProbe.java';p.write_text(PROBE,encoding='utf-8');files.append(str(p))
    src=ROOT/'copimine-end-event/src'
    files.extend([str(src/'me/copimine/endevent/runtime/LastSealRingRuntime.java'),str(ROOT/'tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/KaguneRingFrame.java')])
    run=subprocess.run(['javac','-encoding','UTF-8','-sourcepath',str(src),'-d',str(out),*files],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr
    return out

def run_ring(out,mode,row=0,party=2):
    run=subprocess.run(['java','-cp',str(out),'RingRuntimeProbe',mode,str(row),str(party)],capture_output=True,text=True)
    assert run.returncode==0,run.stdout+run.stderr

@pytest.mark.parametrize('row',[0,1,2])
@pytest.mark.parametrize('party',[2,5,10,11,20])
def test_each_real_row_contacts_only_after_its_visible_emergence(ring_runtime,row,party):
    run_ring(ring_runtime,'row',row,party)

@pytest.mark.parametrize('mode',['all','cancelled','gap','owner','publish','cleanup','sweep'])
def test_cast_receipts_motion_gaps_and_cancellation(ring_runtime,mode):
    run_ring(ring_runtime,mode)
