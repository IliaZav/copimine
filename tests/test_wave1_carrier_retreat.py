"""The selected carrier's production route is short, cached, and releasable."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def retreat_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("carrier-retreat")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text("utf-8")
    # Before this feature the existing controller has no carrier retreat path.
    # Return its existing contact/chase destination as the baseline behavior.
    method = (extract(source, "Location currentCarrierRetreatDestination(")
              if "private Location currentCarrierRetreatDestination(" in source
              else "private Location currentCarrierRetreatDestination(Mob mob,Player target,long now){return target.getLocation();}")
    java = out / "CarrierRetreatProbe.java"
    java.write_text('''
import java.util.*;
public class CarrierRetreatProbe {
    long generation=8,currentCarrierNextRetreatMillis;UUID currentCarrierUuid;
    record CarrierRetreat(long generation,UUID carrier,Location destination,long untilMillis){}
    CarrierRetreat currentCarrierRetreat;Object keyWave,keyKind;Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();
    static class Vector {
        double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}Vector setY(double v){y=v;return this;}
        double lengthSquared(){return x*x+y*y+z*z;}Vector normalize(){double n=Math.sqrt(lengthSquared());return multiply(1/n);}
        Vector multiply(double v){x*=v;y*=v;z*=v;return this;}
    }
    static class Location {
        double x,z;Location(double x,double z){this.x=x;this.z=z;}Object getWorld(){return "arena";}
        public Location clone(){return new Location(x,z);}Vector toVector(){return new Vector(x,68,z);}
        Location add(Vector v){x+=v.x;z+=v.z;return this;}
    }
    static class Entity {UUID id=UUID.randomUUID();Location point;boolean live=true;Entity(double x,double z){point=new Location(x,z);}
        UUID getUniqueId(){return id;}Location getLocation(){return point;}Object getWorld(){return "arena";}}
    static class Mob extends Entity {Mob(double x,double z){super(x,z);}}
    static class Player extends Entity {Player(double x,double z){super(x,z);}}
    boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id)&&ownedEntities.get(id).live;}
    int readInt(Entity e,Object k,int fallback){return 1;}String readString(Entity e,Object k){return "MOB";}
    boolean isWaveCombatKind(String kind){return "MOB".equals(kind);}
    double horizontalDistanceSquared(Location a,Location b){return Math.pow(a.x-b.x,2)+Math.pow(a.z-b.z,2);}
    static void check(boolean v,String reason){if(!v)throw new AssertionError(reason);}
''' + method + '''
    public static void main(String[] args){
        var p=new CarrierRetreatProbe();var carrier=new Mob(8,0);var ally=new Mob(12,2);var target=new Player(6,0);
        p.ownedEntities.put(carrier.id,carrier);p.ownedEntities.put(ally.id,ally);p.currentCarrierUuid=carrier.id;
        switch(args[0]){
            case "support-and-cache" -> {
                var route=p.currentCarrierRetreatDestination(carrier,target,1000);
                check(route.x>carrier.point.x,"carrier must create space toward escort, not chase approaching player");
                check(p.horizontalDistanceSquared(route,carrier.point)<=9.001,"retreat exceeds bounded three-block step");
                target.point.x=9;var repeated=p.currentCarrierRetreatDestination(carrier,target,1100);
                check(route.x==repeated.x&&route.z==repeated.z,"same retreat must not flip heading with every target sample");
                check(p.currentCarrierRetreatDestination(carrier,target,2501)==null,"retreat must have a real end and recovery window");
                check(p.currentCarrierRetreatDestination(carrier,target,3000)==null,"carrier must not retreat indefinitely");
            }
            case "ordinary-escort" -> check(p.currentCarrierRetreatDestination(ally,target,1000)==null,"escort cannot inherit carrier retreat");
            case "distant" -> {target.point.x=-6;check(p.currentCarrierRetreatDestination(carrier,target,1000)==null,"far player cannot force carrier retreat");}
            case "stale-generation" -> {
                p.currentCarrierRetreatDestination(carrier,target,1000);p.generation++;
                var fresh=p.currentCarrierRetreatDestination(carrier,target,1100);
                check(fresh==null,"stale retreat must not control a new attempt");
                check(p.currentCarrierRetreat==null,"generation change must release retained route");
            }
            case "carrier-reassigned" -> {
                p.currentCarrierRetreatDestination(carrier,target,1000);p.currentCarrierUuid=ally.id;
                check(p.currentCarrierRetreatDestination(carrier,target,1100)==null,"previous carrier cannot keep retreat role");
                check(p.currentCarrierRetreat==null,"role reassignment must release old route");
            }
        }
    }
}
''', encoding="utf-8")
    built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(java)], capture_output=True, text=True)
    assert built.returncode == 0, built.stdout + built.stderr
    return out


@pytest.mark.parametrize("scenario", ["support-and-cache", "ordinary-escort", "distant", "stale-generation", "carrier-reassigned"])
def test_actual_carrier_retreat(retreat_probe, scenario):
    result = subprocess.run(["java", "-cp", str(retreat_probe), "CarrierRetreatProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
