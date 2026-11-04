"""Exercise the production Wave 1 holder target and native target event together."""
from pathlib import Path
import subprocess

from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_holder_overrides_old_target_and_releases_it_on_drop(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    helper = extract(source, "Player waveOneChargeHolderTarget(Entity entity)")
    start = source.index("    public void onWaveNavigationTarget(EntityTargetLivingEntityEvent event)")
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    handler = source[start:end]
    java = tmp_path / "CarrierTargetProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RiftCarrierPolicy;
public class CarrierTargetProbe {
  long generation=7; int activeWave=1; Object keyWave=new Object(),keyKind=new Object(),keyTrialRole=new Object();
  String EVENT_KIND_REALITY_SPLIT_TRIAL="TRIAL";
  RiftCarrierPolicy.State currentCarrierState; Map<UUID,Object> waveLeashReturns=new HashMap<>();
  static class Entity { UUID id=UUID.randomUUID(); int wave=1; UUID getUniqueId(){return id;} }
  static class Player extends Entity { boolean charge,allowed=true; }
  static class Mob extends Entity { Player target; Player getTarget(){return target;} void setTarget(Player p){target=p;} }
  static class Event { }
  static class EntityTargetLivingEntityEvent {
    Mob mob; Entity target; boolean cancelled;
    EntityTargetLivingEntityEvent(Mob m,Entity t){mob=m;target=t;}
    Entity getEntity(){return mob;} Entity getTarget(){return target;}
    void setTarget(Player p){target=p;} void setCancelled(boolean v){cancelled=v;}
  }
  static class Bukkit { static Map<UUID,Player> players=new HashMap<>(); static Player getPlayer(UUID id){return players.get(id);} }
  static class NamespacedKey { NamespacedKey(Object p,String s){} }
  static class RealitySplitTrialController { enum NamedTrial { PERSONAL_ECHO } }
  int readInt(Entity e,Object key,int fallback){return e.wave;}
  String readString(Entity e,Object key){return "WAVE_MOB";}
  boolean isWaveCombatKind(String s){return true;} boolean isLiveOwnedEntity(UUID id){return true;}
  boolean isRitualConvertedMob(Mob m){return false;} void onRitualConvertedMobTarget(EntityTargetLivingEntityEvent e){}
  UUID parseUuidOrNull(String s){return null;}
  boolean isCurrentRealitySplitTrialTarget(Mob m,Player p){return false;}
  boolean hasCurrentCarrierInventoryCharge(Player p){return p.charge;}
  boolean isWaveTargetAllowed(Entity e,Player p){return p.allowed;}
''' + helper + handler + r'''
  static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
  public static void main(String[] args){
    CarrierTargetProbe p=new CarrierTargetProbe(); Mob mob=new Mob(); Player old=new Player(),holder=new Player();
    Bukkit.players.put(holder.id,holder); holder.charge=true; mob.target=old;
    UUID carrier=UUID.randomUUID(),charge=UUID.randomUUID();
    var s=RiftCarrierPolicy.selectCarrier(RiftCarrierPolicy.initial(7),7,carrier);
    s=RiftCarrierPolicy.carrierDied(s,7,carrier,charge,0);
    p.currentCarrierState=RiftCarrierPolicy.pickUp(s,7,holder.id,charge,1);
    var event=new EntityTargetLivingEntityEvent(mob,old); p.onWaveNavigationTarget(event);
    check(event.target==holder&&!event.cancelled,"holder must replace the previous valid target");
    holder.charge=false; event=new EntityTargetLivingEntityEvent(mob,old); p.onWaveNavigationTarget(event);
    check(event.target==old,"missing inventory token must not attract mobs");
    holder.charge=true; holder.allowed=false; event=new EntityTargetLivingEntityEvent(mob,old); p.onWaveNavigationTarget(event);
    check(event.target==old,"ineligible holder must not attract mobs");
    holder.allowed=true; p.currentCarrierState=RiftCarrierPolicy.releaseHolder(p.currentCarrierState,7,holder.id,2);
    event=new EntityTargetLivingEntityEvent(mob,old); p.onWaveNavigationTarget(event);
    check(event.target==old,"dropping charge must release the priority");
  }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java),
        str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RiftCarrierPolicy.java")], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "CarrierTargetProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
