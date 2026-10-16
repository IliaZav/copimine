"""Pending owners cannot damage trial actors through vanilla's fallback health path."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_bed_and_staging_owners_cannot_shoot_or_hit_live_trial_actors(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = extract(source, "void onWave7PendingOwnerDamage(EntityDamageByEntityEvent event)")
    probe = tmp_path / "PendingOffenseProbe.java"
    probe.write_text(r'''
import java.util.*;import me.copimine.endevent.runtime.AttemptLifecycleController;
public class PendingOffenseProbe {
    static class Entity {UUID id=UUID.randomUUID();boolean trial;UUID getUniqueId(){return id;}}
    static class Player extends Entity {}
    static class Projectile extends Entity {Player owner;Projectile(Player owner){this.owner=owner;}}
    static class EntityDamageByEntityEvent {
        Entity attacker,victim;boolean cancelled;
        EntityDamageByEntityEvent(Entity a,Entity v){attacker=a;victim=v;}
        Entity getDamager(){return attacker;}Entity getEntity(){return victim;}void setCancelled(boolean c){cancelled=c;}
    }
    long generation=43;boolean official=true,physical=true;
    Map<UUID,Entity> ownedEntities=new HashMap<>();AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    boolean isOfficialWave7ReturnContext(){return official;}
    boolean isChamberWaveEntity(Entity entity){return entity.trial;}
    boolean isCombatTarget(Player player){return physical;}
    Player playerDamageAttacker(Entity source){return source instanceof Player p?p:source instanceof Projectile shot?shot.owner:null;}
''' + adapter + r'''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var main=new PendingOffenseProbe();var owner=new Player();var actor=new Entity();actor.trial=true;
        main.ownedEntities.put(actor.id,actor);main.attemptLifecycle.begin(43,Set.of(owner.id));main.attemptLifecycle.enableWave7Returns(43);
        var initial=new EntityDamageByEntityEvent(owner,actor);main.onWave7PendingOwnerDamage(initial);
        require(!initial.cancelled,"admitted owner keeps ordinary accepted attacks");
        main.attemptLifecycle.markDead(owner.id,43);main.attemptLifecycle.markAlive(owner.id,43);
        var bedArrow=new EntityDamageByEntityEvent(new Projectile(owner),actor);main.onWave7PendingOwnerDamage(bedArrow);
        require(bedArrow.cancelled,"bed owner cannot damage a trial through vanilla fallback after authoritative handler declines");
        var token=main.attemptLifecycle.beginReturn(owner.id,43,0,40);
        var staged=new EntityDamageByEntityEvent(owner,actor);main.onWave7PendingOwnerDamage(staged);
        require(staged.cancelled,"staging cannot damage a trial actor");
        main.attemptLifecycle.completeReturn(token,40,0);
        var returned=new EntityDamageByEntityEvent(owner,actor);main.onWave7PendingOwnerDamage(returned);
        require(!returned.cancelled,"valid admitted return restores real attacks");
        main.physical=false;var outside=new EntityDamageByEntityEvent(owner,actor);main.onWave7PendingOwnerDamage(outside);
        require(outside.cancelled,"admitted roster owner outside combat cannot use entry as a shooting platform");main.physical=true;
        var visitor=new EntityDamageByEntityEvent(new Player(),actor);main.onWave7PendingOwnerDamage(visitor);
        require(visitor.cancelled,"unregistered visitor cannot acquire trial offense");
        var natural=new EntityDamageByEntityEvent(owner,new Entity());main.onWave7PendingOwnerDamage(natural);
        require(!natural.cancelled,"ordinary unrelated entity combat remains unchanged");
    }
}
''', encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "PendingOffenseProbe"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
