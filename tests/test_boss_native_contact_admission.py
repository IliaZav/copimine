"""Run real Bukkit adapters with native, authored and re-entrant damage events."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_only_the_authored_boss_damage_transaction_can_commit(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(extract(source, signature) for signature in (
        "PendingPlayerDamageOutcome pendingBossDamageAttempt(", "void onBossAuthoredDamageCapture(",
        "void onBossMeleeAttack(", "void onPendingPlayerDamageOutcome("))
    java = tmp_path / "BossNativeContactProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.logging.Logger;
public class BossNativeContactProbe {
    static class Entity {UUID id=UUID.randomUUID();UUID getUniqueId(){return id;}}
    static class LivingEntity extends Entity {}
    static class Player extends LivingEntity {boolean active=true;}
    static class EntityDamageEvent {
        enum DamageCause { CUSTOM, ENTITY_ATTACK }
        final Player victim;boolean cancelled;double damage=8;
        EntityDamageEvent(Player victim){this.victim=victim;}
        Player getEntity(){return victim;}boolean isCancelled(){return cancelled;}
        void setCancelled(boolean value){cancelled=value;}double getDamage(){return damage;}
        void setDamage(double value){damage=value;}DamageCause getCause(){return DamageCause.ENTITY_ATTACK;}
    }
    static class EntityDamageByEntityEvent extends EntityDamageEvent {
        final Entity source;EntityDamageByEntityEvent(Entity source,Player victim){super(victim);this.source=source;}
        Entity getDamager(){return source;}
    }
    static class PendingPlayerDamageOutcome {
        String eventId="event";long generation=3;UUID shooterId,playerId;EntityDamageEvent outcome,admittedBossEvent;boolean bossProfileApplied;
        PendingPlayerDamageOutcome(LivingEntity source,Player target){shooterId=source.id;playerId=target.id;}
    }
    enum EventPhase { BOSS_ACTIVE, BOSS_FINISH }
    static class BossPhasePolicy {record CombatProfile(double meleeDamageBonus){}}
    LivingEntity boss=new LivingEntity();UUID bossUuid=boss.id;String eventId="event",bossPhase="HUNT";
    long generation=3;Object keyKind;static final String EVENT_KIND_BOSS="BOSS";
    EventPhase phase=EventPhase.BOSS_ACTIVE;boolean testCombatAiMode,owns=true;
    PendingPlayerDamageOutcome pendingPlayerDamageOutcome;
    boolean isCombatTarget(Player player){return player.active;}
    boolean isActiveBossParticipant(Player player){return player.active;}
    boolean isTestBoss(LivingEntity boss){return false;}
    boolean ownedBySession(LivingEntity source,String event,long generation){return owns;}
    String readString(LivingEntity entity,Object key){return EVENT_KIND_BOSS;}
    BossPhasePolicy.CombatProfile currentBossCombatProfile(){return new BossPhasePolicy.CombatProfile(2);}
    Logger getLogger(){return Logger.getAnonymousLogger();}
''' + methods + r'''
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        var owner=new BossNativeContactProbe();var player=new Player();
        var nativeHit=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossMeleeAttack(nativeHit);
        check(nativeHit.cancelled,"native AI hit outside the authored contact");
        owner.pendingPlayerDamageOutcome=new PendingPlayerDamageOutcome(owner.boss,player);
        var authored=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossAuthoredDamageCapture(authored);owner.onBossMeleeAttack(authored);
        check(!authored.cancelled&&authored.damage==10,"authored hit or phase bonus changed");
        owner.onBossMeleeAttack(authored);check(authored.damage==10,"same transaction received bonus twice");
        var nested=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossAuthoredDamageCapture(nested);owner.onBossMeleeAttack(nested);
        check(nested.cancelled,"second re-entrant damage event borrowed the contact receipt");
        owner.onPendingPlayerDamageOutcome(authored);owner.onPendingPlayerDamageOutcome(nested);
        check(owner.pendingPlayerDamageOutcome.outcome==authored,"nested event replaced the accepted outcome");
        owner.pendingPlayerDamageOutcome=new PendingPlayerDamageOutcome(owner.boss,player);
        player.active=false;var outsider=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossAuthoredDamageCapture(outsider);owner.onBossMeleeAttack(outsider);
        check(outsider.cancelled,"nonparticipant took boss damage");player.active=true;
        owner.pendingPlayerDamageOutcome.generation--;var stale=new EntityDamageByEntityEvent(owner.boss,player);
        owner.onBossAuthoredDamageCapture(stale);owner.onBossMeleeAttack(stale);check(stale.cancelled,"stale generation borrowed contact");
        owner.pendingPlayerDamageOutcome=new PendingPlayerDamageOutcome(owner.boss,player);owner.phase=EventPhase.BOSS_FINISH;
        var finished=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossAuthoredDamageCapture(finished);owner.onBossMeleeAttack(finished);
        check(finished.cancelled,"dying boss still damaged the party");owner.phase=EventPhase.BOSS_ACTIVE;
        var cancelled=new EntityDamageByEntityEvent(owner.boss,player);cancelled.cancelled=true;
        owner.onBossAuthoredDamageCapture(cancelled);owner.onBossMeleeAttack(cancelled);check(cancelled.damage==8,"earlier cancellation changed");
        owner.pendingPlayerDamageOutcome=new PendingPlayerDamageOutcome(owner.boss,player);
        var denied=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossAuthoredDamageCapture(denied);denied.cancelled=true;
        var recursive=new EntityDamageByEntityEvent(owner.boss,player);owner.onBossAuthoredDamageCapture(recursive);owner.onBossMeleeAttack(recursive);
        owner.onPendingPlayerDamageOutcome(denied);owner.onPendingPlayerDamageOutcome(recursive);
        check(recursive.cancelled&&owner.pendingPlayerDamageOutcome.outcome==denied,"protection callback borrowed a cancelled strike");
        var other=new EntityDamageByEntityEvent(new LivingEntity(),player);owner.onBossMeleeAttack(other);
        check(!other.cancelled&&other.damage==8,"unrelated mob was changed");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "BossNativeContactProbe"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
