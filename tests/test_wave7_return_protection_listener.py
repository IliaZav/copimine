"""Exercise the actual Paper listener's scoped protection and offensive cancellation."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_staged_owner_cannot_attack_while_protected_and_outsiders_are_unchanged(tmp_path):
    sources = {
        "org/bukkit/entity/Player.java": "package org.bukkit.entity; public class Player {}",
        "org/bukkit/entity/Projectile.java": "package org.bukkit.entity; public record Projectile(Object shooter) {public Object getShooter(){return shooter;}}",
        "org/bukkit/event/Listener.java": "package org.bukkit.event; public interface Listener {}",
        "org/bukkit/event/EventPriority.java": "package org.bukkit.event; public enum EventPriority {LOWEST,HIGHEST}",
        "org/bukkit/event/EventHandler.java": "package org.bukkit.event; public @interface EventHandler {EventPriority priority();boolean ignoreCancelled();}",
        "org/bukkit/event/entity/EntityDamageEvent.java": "package org.bukkit.event.entity;public class EntityDamageEvent {public boolean cancelled;public Object entity;public EntityDamageEvent(Object entity){this.entity=entity;}public Object getEntity(){return entity;}public void setCancelled(boolean cancelled){this.cancelled=cancelled;}}",
        "org/bukkit/event/entity/EntityDamageByEntityEvent.java": "package org.bukkit.event.entity;public class EntityDamageByEntityEvent extends EntityDamageEvent {Object damager;public EntityDamageByEntityEvent(Object damager,Object victim){super(victim);this.damager=damager;}public Object getDamager(){return damager;}}",
        "org/bukkit/event/entity/EntityShootBowEvent.java": "package org.bukkit.event.entity;public class EntityShootBowEvent extends EntityDamageEvent {public EntityShootBowEvent(Object shooter){super(shooter);}}",
        "org/bukkit/event/entity/ProjectileLaunchEvent.java": "package org.bukkit.event.entity;import org.bukkit.entity.Projectile;public class ProjectileLaunchEvent extends EntityDamageEvent {public ProjectileLaunchEvent(Projectile shot){super(shot);}public Projectile getEntity(){return (Projectile)entity;}}",
    }
    for relative, source in sources.items():
        target = tmp_path / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")
    probe = tmp_path / "ReturnProtectionProbe.java"
    probe.write_text(r'''
import org.bukkit.entity.*;import org.bukkit.event.entity.*;
import me.copimine.endevent.runtime.Wave7ReturnProtectionListener;
public class ReturnProtectionProbe {
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var owner=new Player();var outsider=new Player();boolean[] protectedNow={true};int[] revoked={0};
        var listener=new Wave7ReturnProtectionListener(p->p==owner&&protectedNow[0],p->{protectedNow[0]=false;revoked[0]++;});
        var incoming=new EntityDamageEvent(owner);listener.onIncomingDamage(incoming);
        require(incoming.cancelled&&protectedNow[0],"incoming damage is blocked only during server-owned staging");
        var ordinary=new EntityDamageEvent(outsider);listener.onIncomingDamage(ordinary);
        require(!ordinary.cancelled,"outsider environmental damage remains ordinary");
        var melee=new EntityDamageByEntityEvent(owner,new Object());listener.onOutgoingDamage(melee);
        require(melee.cancelled&&!protectedNow[0]&&revoked[0]==1,"outgoing hit revokes protection and cannot execute while immune");
        protectedNow[0]=true;var bow=new EntityShootBowEvent(owner);listener.onBowRelease(bow);
        require(bow.cancelled&&!protectedNow[0]&&revoked[0]==2,"bow release aborts staging before native shot");
        protectedNow[0]=true;var launched=new ProjectileLaunchEvent(new Projectile(owner));listener.onProjectileLaunch(launched);
        require(launched.cancelled&&!protectedNow[0]&&revoked[0]==3,"crossbow, trident, potion or other projectile launch also aborts staging");
        protectedNow[0]=true;var old=new EntityDamageByEntityEvent(new Projectile(owner),new Object());listener.onOutgoingDamage(old);
        require(old.cancelled&&!protectedNow[0],"already-flying owner's projectile cannot attack while owner stages");
        var done=new EntityDamageEvent(owner);listener.onIncomingDamage(done);
        require(!done.cancelled,"revoked/completed protection does not survive as permanent invulnerability");
        var visitorBow=new EntityShootBowEvent(outsider);listener.onBowRelease(visitorBow);
        require(!visitorBow.cancelled,"unregistered ordinary player can shoot normally");
    }
}
''', encoding="utf-8")
    listener = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/Wave7ReturnProtectionListener.java"
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(listener), *map(str, tmp_path.rglob("*.java"))], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ReturnProtectionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
