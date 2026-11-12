"""Execute the real Echo damage event adapter, including projectile ownership."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]

def test_real_echo_decoy_damage_admission(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void onEchoDecoyAttack(")
    harness = r"""
import java.util.*;
import me.copimine.endevent.domain.EchoDecoyPolicy;
public class EchoDamageProbe {
    static class Entity {}
    static class Attribute { static final Attribute GENERIC_ATTACK_DAMAGE = new Attribute(); }
    static class AttributeInstance { double getValue(){return 20;} }
    static class LivingEntity extends Entity { AttributeInstance getAttribute(Attribute key){return new AttributeInstance();} }
    static class Player extends LivingEntity { boolean admitted=true; }
    static class Projectile extends Entity { Object source; Projectile(Object source){this.source=source;} Object getShooter(){return source;} }
    static class EntityDamageByEntityEvent {
        Entity source,victim;boolean cancelled;double damage=9;
        EntityDamageByEntityEvent(Entity source,Entity victim){this.source=source;this.victim=victim;}
        Entity getDamager(){return source;}Entity getEntity(){return victim;}
        boolean isCancelled(){return cancelled;}void setCancelled(boolean value){cancelled=value;}
        void setDamage(double value){damage=value;}
    }
    enum BossPhase { RAGE, LAST_SEAL }
    BossPhase bossPhase=BossPhase.RAGE;long echoDecoyGeneration=4,generation=4;String eventId="event";
    LivingEntity decoy=new LivingEntity(),boss=new LivingEntity();boolean owns=true,active=true,tracked=true;Object keyKind;static final String EVENT_KIND_ECHO_DECOY="ECHO_DECOY";
    boolean isEchoDecoy(Entity source){return source==decoy&&tracked;}
    String readString(Entity source,Object key){return source==decoy?EVENT_KIND_ECHO_DECOY:"BOSS";}
    boolean ownedBySession(Entity source,String id,long generation){return owns;}
    LivingEntity liveBoss(){return boss;}
    boolean isBossCombatRuntimeActive(LivingEntity boss){return active;}
    boolean isActiveBossParticipant(Player victim){return victim.admitted;}
    record Profile(){double meleeDamageBonus(){return 4;}}
    Profile currentBossCombatProfile(){return new Profile();}
""" + method + r"""
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] ignored){
        var p=new EchoDamageProbe();var target=new Player();
        var melee=new EntityDamageByEntityEvent(p.decoy,target);p.onEchoDecoyAttack(melee);
        check(!melee.cancelled&&melee.damage==EchoDecoyPolicy.decoyDamage(24),"real reduced melee not wired");
        var arrow=new EntityDamageByEntityEvent(new Projectile(p.decoy),target);p.onEchoDecoyAttack(arrow);
        check(!arrow.cancelled&&arrow.damage==melee.damage,"projectile lost decoy attribution");
        var friendly=new EntityDamageByEntityEvent(new Projectile(p.decoy),p.boss);p.onEchoDecoyAttack(friendly);
        check(friendly.cancelled,"friendly event actor was damaged");
        target.admitted=false;var outsider=new EntityDamageByEntityEvent(p.decoy,target);p.onEchoDecoyAttack(outsider);
        check(outsider.cancelled,"outsider was hit");target.admitted=true;
        var cancelled=new EntityDamageByEntityEvent(p.decoy,target);cancelled.cancelled=true;p.onEchoDecoyAttack(cancelled);
        check(cancelled.cancelled&&cancelled.damage==9,"external cancellation changed");
        p.generation++;var stale=new EntityDamageByEntityEvent(new Projectile(p.decoy),target);p.onEchoDecoyAttack(stale);
        check(stale.cancelled,"stale projectile hit new generation");p.generation--;
        p.bossPhase=BossPhase.LAST_SEAL;var phase=new EntityDamageByEntityEvent(p.decoy,target);p.onEchoDecoyAttack(phase);
        check(phase.cancelled,"decoy hit after phase exit");p.bossPhase=BossPhase.RAGE;
        p.tracked=false;var retired=new EntityDamageByEntityEvent(new Projectile(p.decoy),target);p.onEchoDecoyAttack(retired);
        check(retired.cancelled,"removed decoy arrow remained dangerous");p.tracked=true;
        p.active=false;var inactive=new EntityDamageByEntityEvent(p.decoy,target);p.onEchoDecoyAttack(inactive);
        check(inactive.cancelled,"inactive encounter hit");p.active=true;p.owns=false;
        var foreign=new EntityDamageByEntityEvent(p.decoy,target);p.onEchoDecoyAttack(foreign);check(foreign.cancelled,"foreign ownership hit");
        var ordinary=new EntityDamageByEntityEvent(p.boss,target);p.onEchoDecoyAttack(ordinary);
        check(!ordinary.cancelled&&ordinary.damage==9,"real boss attack was changed");
    }
}
"""
    java=tmp_path/"EchoDamageProbe.java"
    java.write_text(harness,encoding="utf-8")
    result=subprocess.run(["javac","-encoding","UTF-8","-sourcepath",str(ROOT/"copimine-end-event/src"),"-d",str(tmp_path),str(java)],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
    result=subprocess.run(["java","-cp",str(tmp_path),"EchoDamageProbe"],capture_output=True,text=True)
    assert result.returncode==0,result.stdout+result.stderr
