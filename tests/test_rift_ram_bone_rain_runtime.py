"""Execute the real rain volley adapter: arrows spawn above and fall.

The shipped "bone rain" was a second horizontal fan, i.e. the triple shot with a
different spread. This probe runs the production ``fireBoneRain`` and proves the
arrows are spawned BONE_RAIN_HEIGHT above the target's feet, are pushed straight
down, and are handed to the host's arrow path with gravity ON -- while the triple
shot stays a straight, gravity-off fan.
"""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java"

PROBE = r'''
import java.util.*;
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy;
public class RamBoneRainProbe {
    enum Sound { ENTITY_SKELETON_SHOOT }
    static class World {
        int sounds;
        void playSound(Location l,Sound s,float a,float b){sounds++;}
        public boolean equals(Object o){return o instanceof World;}
        public int hashCode(){return 1;}
    }
    static class Vector {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double getY(){return y;} Vector normalize(){return this;}
        Vector setY(double v){y=v;return this;}
    }
    static class Location {
        World world;double x,y,z;
        Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(world,x,y,z);}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
        World getWorld(){return world;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    }
    static class Arrow {
        final World world;final double x,y,z;final double dirX,dirY,dirZ;
        Object shooter;boolean gravitySet,gravity;double damage,customDamage;
        Arrow(Location o,Vector d){world=o.world;x=o.x;y=o.y;z=o.z;dirX=d.x;dirY=d.y;dirZ=d.z;}
        void setShooter(Object s){shooter=s;}void setDamage(double d){customDamage=d;}
    }
    static class Skeleton {World world=new World();
        Location getLocation(){return new Location(world,0,69,0);}
        double getX(){return 0;}double getY(){return 69;}double getZ(){return 0;}
        World getWorld(){return world;}}
    static class Player {
        final World world;double x,y,z;
        Player(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
        World getWorld(){return world;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        Location getLocation(){return new Location(world,x,y,z);}
        Location getEyeLocation(){return new Location(world,x,y+1.6,z);}
    }
    Skeleton pillarSkeleton=new Skeleton();
    int removed,prepared,preparedRain;
    final List<Arrow> arrows=new ArrayList<>();
    boolean prepareResult=true;
    class Host {
        Arrow spawnBoneArrow(Location o,Vector d,double speed,double damage){
            Arrow a=new Arrow(o,d);arrows.add(a);return a;
        }
        boolean prepareBoneArrow(Arrow a,boolean rain){prepared++;if(rain)preparedRain++;
            a.gravitySet=true;a.gravity=rain;return prepareResult;}
        void remove(Arrow a){removed++;}
    }
    Host host=new Host();
''' + "\n" + "{METHODS}" + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new RamBoneRainProbe();
        World world=p.pillarSkeleton.world;
        Player target=new Player(world,10,69,0);
        p.fireBoneRain(target);
        int expected=WaveSixPillarSkeletonPolicy.BONE_RAIN_ARROWS;
        check(p.arrows.size()==expected,"rain volley size, was "+p.arrows.size());
        check(p.prepared==expected&&p.preparedRain==expected,"every rain arrow must be handed over with rain=true");
        for(var a:p.arrows){
            check(Math.abs(a.y-(69+WaveSixPillarSkeletonPolicy.BONE_RAIN_HEIGHT))<1e-6,
                    "rain arrow must spawn above the target, was y="+a.y);
            check(a.dirX==0&&a.dirY==-1&&a.dirZ==0,"rain arrow must be pushed straight down, dir="+a.dirX+","+a.dirY+","+a.dirZ);
            check(a.gravity&&a.gravitySet,"rain arrow must have gravity enabled");
            check(a.shooter==p.pillarSkeleton,"rain arrow must be attributed to the marksman");
            check(Math.abs(a.customDamage-WaveSixPillarSkeletonPolicy.ARROW_DAMAGE)<1e-9,"rain arrow damage");
        }
        // Horizontal pattern is centred on the victim and spreads.
        boolean offCentre=false;double maxR=0;
        for(var a:p.arrows){
            double r=Math.hypot(a.x-10,a.z-0);
            maxR=Math.max(maxR,r);
            if(r>1e-9)offCentre=true;
            check(r<=WaveSixPillarSkeletonPolicy.BONE_RAIN_SPREAD+1e-6,"rain arrow outside spread, r="+r);
        }
        check(offCentre,"rain volley must bracket the target, not stack on it");
        check(maxR>0.5,"rain volley must visibly spread");
        // A refused hand-off must not leave the arrow in the world.
        p.arrows.clear();p.removed=0;p.prepared=0;p.preparedRain=0;p.prepareResult=false;
        p.fireBoneRain(target);
        check(p.removed==WaveSixPillarSkeletonPolicy.BONE_RAIN_ARROWS,"refused hand-off leaked "+p.removed+" arrows");
        // The triple shot stays a straight, gravity-off fan.
        p.arrows.clear();p.prepared=0;p.preparedRain=0;p.prepareResult=true;p.removed=0;
        p.fireBoneVolley(target, WaveSixPillarSkeletonPolicy.tripleShotDirections(1.0D,0.0D),
                WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_SPEED);
        check(p.prepared==WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_ARROWS,"triple volley size");
        check(p.preparedRain==0,"the triple shot must never be marked as rain");
        for(var a:p.arrows) check(!a.gravity,"the triple shot must keep gravity off");
    }
}
'''


def test_the_rain_volley_really_falls_and_the_triple_shot_stays_straight(tmp_path):
    source = RUNTIME.read_text("utf-8")
    methods = "\n".join(extract(source, signature) for signature in (
        "void fireBoneRain(Player target)", "void fireBoneVolley(Player target, double[][] directions, double speed)",
    ))
    probe = tmp_path / "RamBoneRainProbe.java"
    probe.write_text(PROBE.replace("{METHODS}", methods), encoding="utf-8")
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(DOMAIN / "WaveSixPillarSkeletonPolicy.java"), str(DOMAIN / "RiftRamPolicy.java"), str(probe)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(["java", "-cp", str(tmp_path), "RamBoneRainProbe"],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
