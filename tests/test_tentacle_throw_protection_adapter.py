"""Execute the real throw release against an accepted/cancelled damage boundary."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_throw_cannot_bypass_protection_or_emit_a_rejected_impulse(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = _method(source, "private void releaseTentaclePlayer(")
    helper = "private boolean applyTentacleReleaseHit("
    if helper in source:
        methods += "\n" + _method(source, helper)
    methods += "\n" + _method(source, "public void onGravityAnchorFallDamage(")
    methods += "\n" + _method(source, "private void retireWave6AnchorFallLeases(")
    probe = tmp_path / "TentacleThrowProtectionProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.TentacleThrowPolicy;
import me.copimine.endevent.domain.GravityAnchorLandingPolicy;
public class TentacleThrowProtectionProbe {
    enum EventPhase { BOSS_ACTIVE }
    enum EntityEffect { HURT }
    record Vector(double x,double y,double z) { }
    static class Location { double getX(){return 2;} double getZ(){return 3;} }
    static class World { UUID getUID(){return UUID.fromString("00000000-0000-0000-0000-000000000001");} }
    static final World WORLD=new World();
    static class Entity {
        final UUID id=UUID.randomUUID(); boolean valid=true,dead=false,owned=true;
        UUID getUniqueId(){return id;} World getWorld(){return WORLD;}
        Location getLocation(){return new Location();}
        boolean isValid(){return valid;} boolean isDead(){return dead;}
    }
    static class ItemDisplay extends Entity { }
    static class LivingEntity extends Entity { }
    static class Player extends LivingEntity {
        double hp=20; boolean cancelled,onGround; int events,hurts,impulses;
        boolean isOnline(){return true;} boolean isInvulnerable(){return false;}
        boolean isOnGround(){return onGround;}
        double getHealth(){return hp;} void setHealth(double value){hp=value;}
        void damage(double amount,Entity source){
            if (!(source instanceof LivingEntity)) throw new AssertionError("throw needs a living carrier");
            events++; if(!cancelled) hp-=amount;
        }
        void playEffect(EntityEffect effect){hurts++;}
        void setVelocity(Vector velocity){impulses++;} void setFallDistance(float distance){ }
    }
    record Config(String environment){ }
    final Config config=new Config("production");
    final boolean localTextureShowcase=false,testCombatAiMode=false;
    final EventPhase phase=EventPhase.BOSS_ACTIVE;
    final UUID eventId=UUID.randomUUID(); final long generation=9;
    final Map<UUID,TentacleThrowPolicy.Launch> tentacleThrowLaunches=new HashMap<>();
    final Map<UUID,UUID> tentacleHitboxesByDisplay=new HashMap<>();
    final Map<UUID,Entity> ownedEntities=new HashMap<>();
    record Wave6AnchorFallLease(UUID worldId,long untilMillis) { }
    final Map<UUID,Wave6AnchorFallLease> wave6AnchorFallLeases=new HashMap<>();
    final Map<UUID,Set<UUID>> wave6AnchorLiftedPlayers=new HashMap<>();
    final Map<UUID,Set<UUID>> wave6AnchorLandingResolvedPlayers=new HashMap<>();
    static class EntityDamageEvent {
        enum DamageCause { FALL }
        final Entity player;boolean cancelled;
        EntityDamageEvent(Entity player){this.player=player;}
        DamageCause getCause(){return DamageCause.FALL;}
        Entity getEntity(){return player;}void setCancelled(boolean value){cancelled=value;}
    }
    static class Bukkit {
        static Player player;static Player getPlayer(UUID id){return player!=null&&player.id.equals(id)?player:null;}
    }
    boolean ownedBySession(Entity e,UUID event,long gen){return e.owned;}
    Location coreCombatAnchorLocation(){return new Location();}
    TentacleThrowPolicy.Launch safeTentacleThrowLaunch(ItemDisplay d,Player p,TentacleThrowPolicy.Launch launch){return launch;}
    boolean isActiveBossParticipant(Player p){return true;}
    boolean isTestBoss(LivingEntity b){return false;} LivingEntity liveBoss(){return null;}
    Logger getLogger(){return Logger.getLogger("throw-protection");}
''' + methods + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new TentacleThrowProtectionProbe(); var d=new ItemDisplay();
        var carrier=new LivingEntity(); var target=new Player();
        Bukkit.player=target;
        p.ownedEntities.put(d.id,d); p.ownedEntities.put(carrier.id,carrier);
        p.tentacleHitboxesByDisplay.put(d.id,carrier.id);
        target.cancelled=true;
        p.releaseTentaclePlayer(d,target);
        check(target.hp==20 && target.hurts==0 && target.impulses==0,
                "cancelled throw bypassed protection or applied a fake impulse");
        check(p.wave6AnchorFallLeases.isEmpty(),"a refused throw created unrelated fall protection");
        target.cancelled=false;
        p.releaseTentaclePlayer(d,target);
        check(target.hp==10 && target.hurts==1 && target.impulses==1 && target.events==2,
                "accepted throw must use one ordinary player damage transaction");
        var landing=new EntityDamageEvent(target);p.onGravityAnchorFallDamage(landing);
        check(landing.cancelled,"accepted throw can apply an additional vanilla landing hit");
        var unrelated=new EntityDamageEvent(target);p.onGravityAnchorFallDamage(unrelated);
        check(!unrelated.cancelled,"throw protection leaked into an unrelated later fall");
        target.hp=20;
        p.releaseTentaclePlayer(d,target);target.onGround=true;p.retireWave6AnchorFallLeases();
        var laterFall=new EntityDamageEvent(target);p.onGravityAnchorFallDamage(laterFall);
        check(!laterFall.cancelled,"a safe first landing retained fall protection");
        target.onGround=false;
        p.ownedEntities.remove(carrier.id); p.releaseTentaclePlayer(d,target);
        check(target.hp==10 && target.impulses==2,"missing carrier inflicted invisible damage");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
                             "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "TentacleThrowProtectionProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
