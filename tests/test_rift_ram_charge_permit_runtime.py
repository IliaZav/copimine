"""Catch raw Ravager teleports against the actual owned-entity permit gate."""
import subprocess
from pathlib import Path
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]
DOMAIN = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain'


def test_charge_moves_through_permit_and_refused_move_has_no_hit(tmp_path):
    runtime = (ROOT / 'copimine-end-event/src/me/copimine/endevent/runtime/RiftRamEncounter.java').read_text('utf-8')
    server = (ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    charge = extract(runtime, 'void charge(List<Player> players)')
    # Execute the complete production admission branch, stopping before the
    # independent arena-placement correction that this flat safe fixture avoids.
    listener = extract(server.replace('    public ', '    private '),
                       'void onOwnedEntityTeleport(EntityTeleportEvent event)')
    listener = listener[:listener.index('        if (activeWave == 6 && event.isCancelled())')] + '\n    }'
    probe = r'''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.RiftRamPolicy;
import me.copimine.endevent.domain.BossTeleportPermitPolicy;
public class RamChargePermitProbe {
  static final String EVENT_KIND_BOSS="boss"; Object keyKind;
  String eventId="attempt"; long generation=9; int activeWave=6;
  Map<UUID,Entity> ownedEntities=new HashMap<>();
  Map<UUID,BossTeleportPermitPolicy.Permit> combatTeleportPermits=new HashMap<>();
  Map<UUID,String> combatTeleportRejectionReasons=new HashMap<>();
  Map<UUID,Long> blockedTeleportLogAt=new HashMap<>();
  static class Vector {
    double x,y,z;Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    public Vector clone(){return new Vector(x,y,z);}
    Vector multiply(double k){x*=k;y*=k;z*=k;return this;}Vector setY(double v){y=v;return this;}
  }
  static class Location {
    double x,y,z;Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    public Location clone(){return new Location(x,y,z);}
    Location add(Vector v){x+=v.x;y+=v.y;z+=v.z;return this;}
    double getX(){return x;}double getY(){return y;}double getZ(){return z;}Object getWorld(){return "world";}
  }
  class Entity {
    UUID uuid=UUID.randomUUID();Location at=new Location(0,65,0);
    UUID getUniqueId(){return uuid;}Location getLocation(){return at;}
    boolean teleport(Location to){
      EntityTeleportEvent e=new EntityTeleportEvent(this,to);onOwnedEntityTeleport(e);
      if(e.cancelled)return false;at=to;return true;
    }
  }
  class Enderman extends Entity {}
  class Ravager extends Entity {World getWorld(){return new World();}}
  class Player extends Entity {
    double getX(){return at.x;}double getY(){return at.y;}double getZ(){return at.z;}
    void setVelocity(Vector v){}void addPotionEffect(PotionEffect e){}
  }
  static class EntityTeleportEvent {
    Object entity;Location to;boolean cancelled;
    EntityTeleportEvent(Object e,Location to){entity=e;this.to=to;}
    RamChargePermitProbe.Entity getEntity(){return (RamChargePermitProbe.Entity)entity;}
    Location getTo(){return to;}void setCancelled(boolean v){cancelled=v;}
  }
  enum Sound {ENTITY_RAVAGER_ATTACK}
  enum PotionEffectType {BLINDNESS,SLOWNESS}
  record PotionEffect(PotionEffectType type,int ticks,int amp){}
  static class World {void playSound(Location l,Sound s,float v,float p){}}
  String readString(Entity e,Object key){return "wave";}
  boolean isWaveCombatKind(String kind){return true;}
  boolean isOwnedRitualSphereDestination(Entity e,Location at){return false;}
  Logger getLogger(){return Logger.getLogger("RamChargePermitProbe");}
  class Host {
    int moves,damages;boolean refuse;
    boolean move(Entity e,Location to){
      moves++;if(refuse)return false;
      long now=System.currentTimeMillis();
      combatTeleportPermits.put(e.uuid,BossTeleportPermitPolicy.issue(eventId,generation,e.uuid.toString(),now,now+10000));
      try{return e.teleport(to);}finally{combatTeleportPermits.remove(e.uuid);}
    }
    boolean damage(Ravager source,Player player,double amount){damages++;return true;}
  }
  Ravager ram=new Ravager();Host host=new Host();Vector direction=new Vector(1,0,0);
  int tick=1,actionTick,nextPillarTick,recoveries,impacts;boolean banked;UUID pillarId;Location pillar;
  Set<UUID> chargeHits=new HashSet<>();
  boolean safeVolume(Location at){return true;}void recover(){recoveries++;}void impact(){impacts++;}
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    RamChargePermitProbe p=new RamChargePermitProbe();p.ownedEntities.put(p.ram.uuid,p.ram);
    check(!p.ram.teleport(new Location(1,65,0)),"owned raw Ravager teleport must be refused");
    Player player=p.new Player();player.at=new Location(1,65,0);
    p.charge(List.of(player));
    check(p.host.moves==1&&p.ram.at.x>1,"charge must move through internal permit");
    check(p.host.damages==1&&p.recoveries==1,"accepted charge contact damages once");
    check(p.combatTeleportPermits.isEmpty(),"permit is released after move");
    RamChargePermitProbe denied=new RamChargePermitProbe();denied.ownedEntities.put(denied.ram.uuid,denied.ram);
    denied.host.refuse=true;Player target=denied.new Player();target.at=new Location(1,65,0);
    denied.charge(List.of(target));
    check(denied.host.moves==1&&denied.ram.at.x==0,"denied move stays put");
    check(denied.recoveries==1&&denied.host.damages==0&&denied.impacts==0,"refused move cannot hit");
  }
'''
    java = tmp_path / 'RamChargePermitProbe.java'
    java.write_text(probe + charge + listener + '\n}', encoding='utf-8')
    built = subprocess.run(['javac', '-d', str(tmp_path), str(DOMAIN / 'RiftRamPolicy.java'),
                            str(DOMAIN / 'BossTeleportPermitPolicy.java'), str(java)], capture_output=True, text=True)
    assert built.returncode == 0, built.stderr
    ran = subprocess.run(['java', '-cp', str(tmp_path), 'RamChargePermitProbe'], capture_output=True, text=True)
    assert ran.returncode == 0, ran.stderr
