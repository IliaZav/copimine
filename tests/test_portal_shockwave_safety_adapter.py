"""Execute the real impulse safety adapter against safe floor, holes and bounds."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT=Path(__file__).resolve().parents[1]


def test_portal_impulse_cannot_cross_a_hole_or_arena_boundary(tmp_path):
    source=(ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    method=extract(source,'Vector safePortalShockwaveVelocity(Player player, Vector requested)')
    java='''public class PortalImpulseSafetyProbe {
 static final double PORTAL_SHOCKWAVE_STRENGTH=1.35,PORTAL_SHOCKWAVE_LIFT=.42,MIN_WAVE_CORE_DISTANCE_BLOCKS=0;
 static class World {} final World world=new World();boolean hole,immediateHole;double edge=20;
 static class Vector {double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
 public Vector clone(){return new Vector(x,y,z);}double getX(){return x;}double getY(){return y;}double getZ(){return z;}
 Vector multiply(double f){x*=f;y*=f;z*=f;return this;}Vector setY(double value){y=value;return this;}}
 static class Location {World world;double x,y,z;Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
 World getWorld(){return world;}public Location clone(){return new Location(world,x,y,z);}
 Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}void setY(double value){y=value;}}
 static class Player {Location at;Player(Location at){this.at=at;}Location getLocation(){return at;}World getWorld(){return at.world;}}
 Location coreCombatAnchorLocation(){return new Location(world,-5,65,0);}int combatFloorY(){return 64;}
 double waveMovementRadius(){return 20;}
 boolean isArenaLocation(Location p){return p.x<=edge;}
 boolean isSafeCombatStep(Location core,Location p,double radius,double minimum,int chamber){return !(hole&&p.x>=1.8&&p.x<=2.2)&&!(immediateHole&&p.x>=.1&&p.x<=.3);}
 boolean isSafeCombatParticipantLocation(Location p,Player player){return true;}
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
 var p=new PortalImpulseSafetyProbe();var player=new Player(new Location(p.world,0,65,0));
 Vector safe=p.safePortalShockwaveVelocity(player,new Vector(1,.4,0));
 check(safe!=null&&safe.x==1,"valid floor preserves meaningful knockback");
 p.hole=true;Vector bounded=p.safePortalShockwaveVelocity(player,new Vector(1,.4,0));
 check(bounded!=null&&bounded.x<=.125,"swept path cannot tunnel across hole");
 p.hole=false;p.edge=3;bounded=p.safePortalShockwaveVelocity(player,new Vector(1,.4,0));
 check(bounded!=null&&bounded.x<=.25,"landing remains within arena");
 p.edge=20;p.immediateHole=true;
 check(p.safePortalShockwaveVelocity(player,new Vector(1,.4,0))==null,"unsafe impulse is refused");
 check(p.safePortalShockwaveVelocity(player,new Vector(Double.NaN,0,0))==null,"non-finite rejected");
 player.at.world=new World();check(p.safePortalShockwaveVelocity(player,new Vector(1,.4,0))==null,"unrelated world rejected");
 }
'''
    path=tmp_path/'PortalImpulseSafetyProbe.java';path.write_text(java+method+'}',encoding='utf-8')
    built=subprocess.run(['javac','-d',str(tmp_path),str(path)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    result=subprocess.run(['java','-cp',str(tmp_path),'PortalImpulseSafetyProbe'],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
