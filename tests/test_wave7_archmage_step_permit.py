"""Exercise Archmage Step through the production teleport permit wrapper."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_archmage_step_uses_the_single_use_event_owned_teleport_permit(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    step = extract(source, "boolean stepNamedTrialArchmage(LivingEntity actor, Player target, int chamber)")
    wrapper = extract(source, "boolean teleportCombatEntity(Entity entity, Location destination)")
    listener_start = source.index("    public void onOwnedEntityTeleport(EntityTeleportEvent event)")
    assert "BossTeleportPermitPolicy.accept(" in source[listener_start:]

    probe = tmp_path / "ArchmageStepPermitProbe.java"
    probe.write_text('''
import java.util.*;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;
public class ArchmageStepPermitProbe {
    static final long COMBAT_TELEPORT_PERMIT_MILLIS=1000L;
    static final UUID ACTOR_ID=UUID.fromString("00000000-0000-0000-0000-000000000071");
    final String eventId="00000000-0000-0000-0000-000000000072";
    final long generation=72L;
    final int activeWave=7;
    final Map<UUID,Object> ownedEntities=new HashMap<>();
    final Map<UUID,BossTeleportPermitPolicy.Permit> combatTeleportPermits=new HashMap<>();
    final Map<UUID,String> combatTeleportRejectionReasons=new HashMap<>();
    final RealitySplitChamberController realitySplitChamberController=new RealitySplitChamberController();
    int acceptedTeleports;
    static final class ProbeLogger { void warning(String ignored){} }
    ProbeLogger getLogger(){return new ProbeLogger();}
    String locationText(Location ignored){return "probe";}
    static final class ChamberIsolationPolicy { static final class Assignment {} }
    static final class RealitySplitChamberController {
        ChamberIsolationPolicy.Assignment assignment(){return new ChamberIsolationPolicy.Assignment();}
    }
    enum Sound { ENTITY_ENDERMAN_TELEPORT }
    enum Particle { PORTAL }
    static final class Vector implements Cloneable {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vector subtract(Vector v){x-=v.x;y-=v.y;z-=v.z;return this;}
        Vector setY(double v){y=v;return this;}
        double getX(){return x;} double getY(){return y;} double getZ(){return z;}
        double lengthSquared(){return x*x+y*y+z*z;}
        Vector normalize(){double length=Math.sqrt(lengthSquared());if(length>0){x/=length;y/=length;z/=length;}return this;}
        Vector multiply(double value){x*=value;y*=value;z*=value;return this;}
        public Vector clone(){return new Vector(x,y,z);}
    }
    static final class Location implements Cloneable {
        final World world; double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        Location add(Vector v){x+=v.x;y+=v.y;z+=v.z;return this;}
        Location add(double x,double y,double z){this.x+=x;this.y+=y;this.z+=z;return this;}
        Location setY(double value){y=value;return this;}
        double getY(){return y;} World getWorld(){return world;}
        Vector toVector(){return new Vector(x,y,z);}
        public Location clone(){return new Location(world,x,y,z);}
    }
    static final class World {
        void playSound(Location at,Sound sound,float volume,float pitch){}
    }
    static class Entity {
        final UUID id; final ArchmageStepPermitProbe owner; final World world;
        Location location;
        Entity(ArchmageStepPermitProbe owner,UUID id,Location location){this.owner=owner;this.id=id;this.location=location;this.world=location.getWorld();}
        UUID getUniqueId(){return id;} Location getLocation(){return location;} World getWorld(){return world;}
        Location getEyeLocation(){return location.clone().add(0.0D,getEyeHeight(),0.0D);}
        double getEyeHeight(){return 1.62D;}
        boolean isValid(){return true;}
        boolean teleport(Location next){
            if(!owner.onOwnedEntityTeleport(this))return false;
            location=next.clone();owner.acceptedTeleports++;return true;
        }
    }
    static final class LivingEntity extends Entity {LivingEntity(ArchmageStepPermitProbe p,UUID id,Location l){super(p,id,l);}}
    static final class Player extends Entity {Player(ArchmageStepPermitProbe p,UUID id,Location l){super(p,id,l);}}
    Vector horizontalDirection(Location from,Location to){return to.toVector().subtract(from.toVector()).setY(0).normalize();}
    boolean isCurrentRealitySplitTrialTarget(Entity actor,Player target){return actor!=null&&target!=null;}
    boolean isRealitySplitDestinationAllowed(UUID owner,Location destination,ChamberIsolationPolicy.Assignment a){return true;}
    boolean isSafeCombatEntityLocation(Location destination,Entity actor){return true;}
    boolean hasNamedTrialLineOfSight(Location from,Location to){return true;}
    void spawnEventParticle(Location at,Particle particle,int count,double x,double y,double z,double speed){}
    boolean onOwnedEntityTeleport(Entity actor){
        UUID id=actor.getUniqueId();
        return BossTeleportPermitPolicy.accept(combatTeleportPermits.get(id),eventId,generation,
                id.toString(),System.currentTimeMillis());
    }
''' + wrapper + "\n" + step + '''
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new ArchmageStepPermitProbe();
        var actor=new LivingEntity(p,ACTOR_ID,new Location(new World(),0,64,0));
        var target=new Player(p,UUID.fromString("00000000-0000-0000-0000-000000000073"),
                new Location(actor.getWorld(),0,64,4));
        p.ownedEntities.put(actor.getUniqueId(),new Object());
        require(!actor.teleport(new Location(actor.getWorld(),5,64,5)),
                "an event-owned actor cannot teleport without a current single-use permit");
        require(p.stepNamedTrialArchmage(actor,target,0),"safe Archmage Step should resolve");
        require(p.acceptedTeleports==1,"Step must traverse the production permit wrapper exactly once");
        require(actor.getLocation().x!=0.0D,"Step must actually move the event-owned actor");
        require(p.combatTeleportPermits.isEmpty(),"the one-shot permit must be removed after teleport");
    }
}
''', encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossTeleportPermitPolicy.java"
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(policy), str(probe)],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(
        ["java", "-cp", str(tmp_path), "ArchmageStepPermitProbe"],
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stdout + result.stderr
