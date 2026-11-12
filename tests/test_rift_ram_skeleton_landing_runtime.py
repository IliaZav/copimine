"""Exercise the real final-pillar landing adapter and its durable commit."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract
ROOT=Path(__file__).resolve().parents[1]
@pytest.mark.parametrize("mode",["accept","cancel","wrong-destination","save-failure"])
def test_landing_requires_accepted_move_and_checkpoint(tmp_path,mode):
    source=(ROOT/"copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java").read_text("utf-8")
    method=extract(source,"void dropPillarSkeleton()")
    java=tmp_path/"RamLandingProbe.java"
    java.write_text(r'''
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy;
public class RamLandingProbe {
 enum Sound {ENTITY_SKELETON_STEP}
 static class World {void playSound(Location l,Sound s,float a,float b){}}
 static class Location {double y=76.6;World w=new World();
  public Location clone(){var p=new Location();p.y=y;p.w=w;return p;}
  void setY(double y){this.y=y;}World getWorld(){return w;}
  double distanceSquared(Location other){return (y-other.y)*(y-other.y);}}
 static class Skeleton {Location at=new Location();Location getLocation(){return at;}void setGravity(boolean b){}}
 class Host {int saves;int floorY(){return 68;}boolean inArena(Location l){return l.y>=65&&l.y<=71;}
  boolean move(Skeleton e,Location at){if(mode.equals("cancel"))return false;
   if(!mode.equals("wrong-destination"))e.at=at.clone();return true;}
  boolean checkpoint(){saves++;return !mode.equals("save-failure");}}
 Skeleton pillarSkeleton=new Skeleton(),ram=new Skeleton();Host host=new Host();
 String mode;boolean skeletonFallen,closed;int impacts=7,cues;
 boolean skeletonAlive(){return true;}void fail(String reason){closed=true;}
 void dust(Location at,int count,double spread,double height){cues++;}
'''+method+r'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){var p=new RamLandingProbe();p.mode=args[0];p.dropPillarSkeleton();
  boolean accepted=args[0].equals("accept");
  if(accepted){check(p.skeletonFallen&&!p.closed&&p.host.saves==1&&p.cues==1,"landing not durably accepted");
   p.dropPillarSkeleton();check(p.host.saves==1&&p.cues==1,"landing replayed");}
  else {check(p.closed&&p.cues==0,"rejected landing produced success presentation");
   if(!args[0].equals("save-failure"))check(!p.skeletonFallen&&p.host.saves==0,"rejected movement published fallen");}
 }
}
''',encoding="utf-8")
    domain=ROOT/"copimine-end-event/src/me/copimine/endevent/domain/WaveSixPillarSkeletonPolicy.java"
    built=subprocess.run(["javac","-d",str(tmp_path),str(domain),str(domain.with_name("RiftRamPolicy.java")),str(java)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    ran=subprocess.run(["java","-cp",str(tmp_path),"RamLandingProbe",mode],capture_output=True,text=True)
    assert ran.returncode==0,ran.stderr

