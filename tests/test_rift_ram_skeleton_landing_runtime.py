"""Execute the real final-pillar drop: gravity, ground contact, durable commit.

The shipped drop teleported the actor to the floor and declared itself landed in
the same call, so there was no fall to see and a checkpoint could record a
landing before any physics ran. These probes run the production
``dropPillarSkeleton`` / ``tickSkeletonFall`` / ``landPillarSkeleton`` against a
deterministic body and prove the landing is only committed after the actor's own
ground contact, with a bounded fallback when physics cannot settle.
"""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
RUNTIME = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java"

PROBE = r'''
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy;
public class RamLandingProbe {
    enum Sound { ENTITY_SKELETON_STEP }
    static class World { int sounds; void playSound(Location l,Sound s,float a,float b){sounds++;} }
    static class Vector {
        final double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        double getY(){return y;}
    }
    static class Location {
        double x,y,z;World world;
        Location(World w,double x,double y,double z){world=w;this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(world,x,y,z);}
        void setY(double value){y=value;}
        World getWorld(){return world;}
        double getY(){return y;}
    }
    static class Skeleton {
        final World world=new World();
        Location at;
        boolean grounded,dead,ai=true,aware=true;
        Skeleton(double y){at=new Location(world,0,y,0);}
        Location getLocation(){return at;}
        World getWorld(){return world;}
        boolean isOnGround(){return grounded;}
        Vector getVelocity(){return new Vector(0,0,0);}
        void setVelocity(Vector v){}
        void setGravity(boolean value){}
        void setAI(boolean value){ai=value;}
        void setAware(boolean value){aware=value;}
        void setTarget(Object target){}
    }
    class Host {
        int saves;
        boolean acceptMove=true;
        int floorY(){return 68;}
        boolean inArena(Location l){return l.y>=65&&l.y<=71;}
        boolean move(Skeleton e,Location at){if(!acceptMove)return false;e.at=at.clone();return true;}
        boolean checkpoint(){saves++;return !saveFailure;}
        boolean saveFailure;
    }
    Skeleton pillarSkeleton=new Skeleton(76.6);
    Host host=new Host();
    boolean skeletonFallen,skeletonFalling,closed;
    int impacts=7,tick,skeletonFallStart,cues;
    String failure;
    boolean skeletonAlive(){return !pillarSkeleton.dead;}
    void fail(String reason){failure=reason;closed=true;}
    void dust(Location at,int count,double spread,double height){cues++;}
''' + "\n" + "{METHODS}" + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        String mode=args[0];
        var p=new RamLandingProbe();
        p.tick=10;
        p.dropPillarSkeleton();
        // The drop is a release, not a completed landing: no checkpoint and no
        // "fallen" flag may be published before any physics runs.
        check(p.skeletonFalling&&!p.skeletonFallen,"drop did not start a real fall");
        check(p.host.saves==0,"drop committed a checkpoint before physics");
        check(p.pillarSkeleton.ai==false&&p.pillarSkeleton.aware==false,"drop left the platform AI in control");
        switch(mode){
            case "airborne": {
                p.tick=11;p.pillarSkeleton.at.y=76.6;p.pillarSkeleton.grounded=false;
                p.tickSkeletonFall();
                check(!p.skeletonFallen&&p.host.saves==0,"airborne body was declared landed");
                check(!p.closed,"airborne fall failed closed prematurely");
                break;
            }
            case "grounded": {
                p.tick=20;p.pillarSkeleton.at.y=68.0;p.pillarSkeleton.grounded=true;
                p.tickSkeletonFall();
                check(p.skeletonFallen&&!p.skeletonFalling,"grounded body did not land");
                check(p.host.saves==1,"grounded landing was not durably committed");
                check(p.cues==2,"landing cues were not published");
                break;
            }
            case "timeout-refused": {
                p.tick=10+WaveSixPillarSkeletonPolicy.FALL_TIMEOUT_TICKS;
                p.pillarSkeleton.at.y=70.5;p.pillarSkeleton.grounded=false;
                p.host.acceptMove=false;
                p.tickSkeletonFall();
                check(p.closed&&"skeleton-landing-refused".equals(p.failure),"unsettled fall did not fail closed");
                check(!p.skeletonFallen,"failed fall published fallen");
                break;
            }
            case "timeout-accepted": {
                p.tick=10+WaveSixPillarSkeletonPolicy.FALL_TIMEOUT_TICKS;
                p.pillarSkeleton.at.y=70.5;p.pillarSkeleton.grounded=false;
                p.tickSkeletonFall();
                check(p.skeletonFallen&&!p.closed,"bounded fallback did not settle on the floor");
                check(p.host.saves==1,"bounded fallback was not committed");
                check(p.pillarSkeleton.at.y==69.0,"fallback did not use the arena floor");
                break;
            }
            case "save-failure": {
                p.tick=20;p.pillarSkeleton.at.y=68.0;p.pillarSkeleton.grounded=true;
                p.host.saveFailure=true;
                p.tickSkeletonFall();
                check(p.closed&&"skeleton-landing-checkpoint-failed".equals(p.failure),"checkpoint failure was swallowed");
                break;
            }
            case "dead": {
                p.tick=20;p.pillarSkeleton.dead=true;p.pillarSkeleton.grounded=true;
                p.tickSkeletonFall();
                check(!p.skeletonFallen&&!p.skeletonFalling,"a dead marksman was landed");
                check(!p.closed,"a dead marksman failed the wave");
                break;
            }
            case "drop-once": {
                p.tick=20;p.pillarSkeleton.at.y=68.0;p.pillarSkeleton.grounded=true;
                p.tickSkeletonFall();
                p.dropPillarSkeleton();
                check(p.skeletonFallen&&!p.skeletonFalling,"falling restarted after a landing");
                check(p.host.saves==1,"landing replayed");
                break;
            }
            default: throw new AssertionError("unknown mode "+mode);
        }
    }
}
'''


@pytest.fixture(scope="module")
def landing_probe(tmp_path_factory):
    folder = tmp_path_factory.mktemp("ram-landing")
    source = RUNTIME.read_text("utf-8")
    methods = "\n".join(extract(source, signature) for signature in (
        "void dropPillarSkeleton()", "void tickSkeletonFall()", "void landPillarSkeleton(Location at)",
    ))
    java = folder / "RamLandingProbe.java"
    java.write_text(PROBE.replace("{METHODS}", methods), encoding="utf-8")
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(folder),
         str(DOMAIN / "WaveSixPillarSkeletonPolicy.java"),
         str(DOMAIN / "RiftRamPolicy.java"), str(java)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    return folder


@pytest.mark.parametrize("mode", ["airborne", "grounded", "timeout-refused",
                                  "timeout-accepted", "save-failure", "dead", "drop-once"])
def test_landing_is_real_physics_and_durably_committed(landing_probe, mode):
    ran = subprocess.run(["java", "-cp", str(landing_probe), "RamLandingProbe", mode],
                         capture_output=True, text=True)
    assert ran.returncode == 0, ran.stdout + ran.stderr
