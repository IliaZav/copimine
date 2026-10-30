"""Execute all three real boss damage adapters with admitted and foreign sources."""
from pathlib import Path
import subprocess

import pytest

from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
SOURCES = ROOT / "copimine-end-event/src"


def _compile_probe(source_path, out):
    source = source_path.read_text(encoding="utf-8")
    signatures = [
        "private void handleCurrentBossDamage(",
        "private void handleCurrentBossProjectileDamage(",
        "private boolean applyCurrentBossProjectileDamage(",
        "private BossHealthCommit commitCurrentBossHealth(",
        "private Player playerDamageOwner(",
    ]
    if "private Player activeBossDamageOwner(" in source:
        signatures.append("private Player activeBossDamageOwner(")
    methods = "\n".join(_method(source, signature) for signature in signatures)
    methods = methods.replace("org.bukkit.entity.Projectile", "Projectile")
    java = out / "BossIncomingRosterProbe.java"
    java.write_text(r'''
import java.util.*;
import java.util.logging.*;
import me.copimine.endevent.domain.*;
public class BossIncomingRosterProbe {
    enum EventPhase {BOSS_ACTIVE,BOSS_FINISH}
    static class World {}
    static final World WORLD=new World();
    static class Entity {
        final UUID id=UUID.randomUUID(); World world=WORLD;
        UUID getUniqueId(){return id;} World getWorld(){return world;} String getType(){return "STUB";}
    }
    static class LivingEntity extends Entity {
        double hp=4500; boolean valid=true,dead,invulnerable;
        boolean isValid(){return valid;}boolean isDead(){return dead;}
        double getHealth(){return hp;}void setHealth(double h){hp=h;}
        boolean isInvulnerable(){return invulnerable;}void setInvulnerable(boolean value){invulnerable=value;}
    }
    static class Player extends LivingEntity {boolean admitted=true,online=true,spectator;}
    static class Projectile extends Entity {Entity shooter;Entity getShooter(){return shooter;}}
    static class AbstractArrow extends Projectile {double amount=6;double getDamage(){return amount;}}
    record DamageSource(Entity causingEntity){Entity getCausingEntity(){return causingEntity;}}
    static class EntityDamageEvent {
        boolean cancelled; double amount=6; DamageSource damageSource;
        boolean isCancelled(){return cancelled;}void setCancelled(boolean c){cancelled=c;}
        double getDamage(){return amount;}double getFinalDamage(){return amount;}
        DamageSource getDamageSource(){return damageSource;}
    }
    static class ProjectileHitEvent {
        boolean cancelled;boolean isCancelled(){return cancelled;}void setCancelled(boolean c){cancelled=c;}
    }
    static class Bukkit {static int getCurrentTick(){return 1;}}
    record BossHealthCommit(BossRealHealthDamagePolicy.Result result,double shieldBlockedDamage){}
    final LivingEntity currentBoss=new LivingEntity();
    EventPhase phase=EventPhase.BOSS_ACTIVE;
    BossPhase bossPhase=BossPhase.AWAKENING;
    BossAbilityState bossAbilityState=BossAbilityState.NONE;
    String eventId="official";
    UUID bossKillerUuid;
    boolean bossDefeatCinematicStarted,testCombatAiMode,localFallback,diagnostic;
    final Set<UUID> roster=new HashSet<>();
    int synchronizationCalls,feedback,lethalCalls;
    final Set<UUID> combatHelpers=new HashSet<>();
    final Map<EntityDamageEvent,Boolean> authoritativeCombatTraceEvents=new HashMap<>();
    boolean isCurrentBossCarrier(LivingEntity boss){return boss==currentBoss;}
    boolean isOfficialBoss(LivingEntity boss){return boss==currentBoss;}
    boolean isTestBoss(LivingEntity boss){return diagnostic;}
    boolean isOfficialAttempt(){return !diagnostic;}
    Set<UUID> authoritativeAttemptRoster(){return roster;}
    boolean isActiveBossParticipant(Player p){return p!=null&&(p.admitted||localFallback)&&p.online&&!p.dead&&!p.spectator&&p.world==WORLD;}
    double bossMaxHealth(LivingEntity boss){return 5000;}
    void synchronizeBossPhase(LivingEntity boss){
        synchronizationCalls++;
        bossPhase=BossPhasePolicy.transition(bossPhase,boss.getHealth(),bossMaxHealth(boss)).current();
    }
    BossPhasePolicy.DamageImmunityReason bossDamageImmunityReason(){return BossPhasePolicy.DamageImmunityReason.NONE;}
    boolean tentacleGuardianShielded(){return false;}
    int livingTentacleGuardianCount(){return 0;}
    boolean tentacleVulnerabilityOpen(){return false;}
    void releaseExpiredBossCastBeforeDamage(LivingEntity boss,long now){}
    void emitBossDamageDiagnostic(Object... args){}
    void applyBossHitFeedback(Object... args){feedback++;}
    void applyBossHitFeedbackAt(Object... args){feedback++;}
    Object bodyImpactContact(Object... args){return null;}
    Object guardianShieldImpactContact(Object... args){return null;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    void handleOfficialLethalHit(LivingEntity boss,Entity source,double before,double raw,double damage,boolean projectile){
        check(isActiveBossParticipant(playerDamageOwner(source)),"legitimate indirect lethal hit lost player attribution");
        lethalCalls++;
    }
''' + methods + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        String route=args[0],scenario=args[1];
        var p=new BossIncomingRosterProbe();var owner=new Player();var boss=p.currentBoss;
        var event=new EntityDamageEvent();var impact=new ProjectileHitEvent();var arrow=new AbstractArrow();
        arrow.shooter=owner;
        Entity source=owner;
        p.roster.add(owner.id);
        boolean shouldAccept=scenario.equals("member")||scenario.equals("diagnostic-member")||scenario.startsWith("attributed-");
        if(scenario.equals("outsider"))owner.admitted=false;
        if(scenario.equals("local-roster-fallback")){owner.admitted=false;p.localFallback=true;p.roster.clear();p.roster.add(UUID.randomUUID());}
        if(scenario.equals("diagnostic-member")){p.diagnostic=true;p.testCombatAiMode=true;p.roster.clear();}
        if(scenario.equals("offline"))owner.online=false;
        if(scenario.equals("dead"))owner.dead=true;
        if(scenario.equals("spectator"))owner.spectator=true;
        if(scenario.equals("other-world"))owner.world=new World();
        if(scenario.equals("ownerless")){source=new Entity();arrow.shooter=source;}
        if(scenario.equals("cancelled")){event.cancelled=true;impact.cancelled=true;}
        if(scenario.equals("stale-boss"))boss=new LivingEntity();
        if(scenario.equals("removed-boss"))boss.valid=false;
        if(scenario.equals("dead-boss"))boss.dead=true;
        if(scenario.startsWith("attributed-")||scenario.equals("foreign-causing-player")){
            source=scenario.contains("custom")?null:new Entity();
            event.damageSource=new DamageSource(owner);
            if(scenario.equals("foreign-causing-player"))owner.admitted=false;
            if(scenario.endsWith("lethal"))event.amount=10_000;
        }
        if(route.equals("event"))p.handleCurrentBossDamage(event,boss,source);
        else if(route.equals("proxy"))p.handleCurrentBossProjectileDamage(impact,boss,arrow);
        else p.applyCurrentBossProjectileDamage(boss,arrow);
        if(shouldAccept){
            if(scenario.endsWith("lethal"))check(p.lethalCalls==1,"legitimate lethal attribution was rejected");
            else check(boss.hp<4500&&p.feedback==1,"eligible hit stopped dealing real damage once");
            check(owner.id.equals(p.bossKillerUuid)&&p.combatHelpers.equals(Set.of(owner.id)),"eligible damage lost its responsible player");
        }else{
            check(boss.hp==4500,"unadmitted "+scenario+" changed real boss HP via "+route+": "+boss.hp);
            check(p.synchronizationCalls==0&&p.feedback==0&&p.lethalCalls==0
                    &&p.combatHelpers.isEmpty()&&p.bossKillerUuid==null,
                    "rejected source still changed boss phase, feedback, lethal or finisher credit via "+route);
        }
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(SOURCES),
                             "-d", str(out), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return out


@pytest.fixture(scope="module")
def incoming_boss_damage_probe(tmp_path_factory):
    return _compile_probe(SOURCE, tmp_path_factory.mktemp("boss-incoming-roster"))


@pytest.mark.parametrize("route", ["event", "proxy", "sweep"])
@pytest.mark.parametrize("scenario", [
    "member", "outsider", "offline", "dead", "spectator", "other-world", "ownerless",
    "stale-boss", "removed-boss", "dead-boss", "local-roster-fallback", "diagnostic-member",
])
def test_boss_incoming_damage_requires_current_admitted_player(incoming_boss_damage_probe, route, scenario):
    _run(incoming_boss_damage_probe, route, scenario)


@pytest.mark.parametrize("route", ["event", "proxy"])
def test_boss_incoming_cancelled_event_never_writes_hp(incoming_boss_damage_probe, route):
    _run(incoming_boss_damage_probe, route, "cancelled")


@pytest.mark.parametrize("scenario", [
    "attributed-tnt", "attributed-custom", "attributed-tnt-lethal", "attributed-custom-lethal", "foreign-causing-player",
])
def test_boss_incoming_indirect_damage_keeps_server_player_attribution(incoming_boss_damage_probe, scenario):
    _run(incoming_boss_damage_probe, "event", scenario)


def _run(out, route, scenario):
    result = subprocess.run(["java", "-cp", str(out), "BossIncomingRosterProbe", route, scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
