"""Exercise the production cast/navigation owner, not only its spell policy."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_mobile_casts_and_contact_recovery_continue_navigation(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(extract(source, signature) for signature in (
        "boolean allowsBossCastLocomotion(", "boolean tickBossCastLocomotion("))
    tick = extract(source, "void tickCurrentBoss(")
    assert "tickBossCastLocomotion(boss, now, finalSealPinned)" in tick
    java = tmp_path / "BossCastNavigationProbe.java"
    java.write_text(r'''
import me.copimine.endevent.domain.BossCastMobilityPolicy;
import me.copimine.endevent.domain.EndRiftAiPolicy;
public class BossCastNavigationProbe {
    enum BossAbilityState { NONE, TELEGRAPHING, EXECUTING, RECOVERY }
    static class LivingEntity {} static class Mob extends LivingEntity {}
    BossAbilityState bossAbilityState=BossAbilityState.NONE;boolean bossEdgeVolleyActive;
    EndRiftAiPolicy.BossSpell spell=EndRiftAiPolicy.BossSpell.RIFT_PROJECTILE;
    int evaluated,restored,paths;
    EndRiftAiPolicy.BossSpell currentCastingSpell(){return spell;}
    void maintainBossTargetSelection(LivingEntity boss,long now){evaluated++;}
    void restoreBossPursuitTarget(Mob boss,long now){restored++;}
    void maintainBossPath(LivingEntity boss,long now){paths++;}
''' + methods + r'''
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(var phase:BossAbilityState.values())for(boolean edge:new boolean[]{false,true})
          for(boolean pinned:new boolean[]{false,true})for(var spell:EndRiftAiPolicy.BossSpell.values()){
            var owner=new BossCastNavigationProbe();owner.bossAbilityState=phase;
            owner.bossEdgeVolleyActive=edge;owner.spell=spell;
            boolean owns=owner.tickBossCastLocomotion(new Mob(),10_000L,pinned);
            check(owns==(phase!=BossAbilityState.NONE),"cast admission changed");
            boolean moves=phase!=BossAbilityState.NONE&&!edge&&!pinned
                &&(phase==BossAbilityState.RECOVERY||BossCastMobilityPolicy.allowsLocomotion(spell));
            check(owner.paths==(moves?1:0)&&owner.restored==owner.paths&&owner.evaluated==owner.paths,
                "missing or conflicting navigation: "+spell+"/"+phase+" edge="+edge+" pinned="+pinned);
            check(owner.bossAbilityState==phase,"movement ended/restarted the cast");
          }
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath",
        str(ROOT / "copimine-end-event/src"), "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "BossCastNavigationProbe"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr


def test_failed_boss_paths_share_the_successful_attempt_budget(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void maintainBossPath(")
    java = tmp_path / "BossPathBudgetProbe.java"
    java.write_text(r'''
import java.util.logging.Logger;
public class BossPathBudgetProbe {
    static class Location {}
    static class Player extends LivingEntity {}
    static class LivingEntity {
        Location getLocation(){return new Location();}
        java.util.UUID getUniqueId(){return new java.util.UUID(0,1);}
        Data getPersistentDataContainer(){return new Data();}
    }
    static class Mob extends LivingEntity {
        final Pathfinder path=new Pathfinder();Object getTarget(){return new Player();}
        Pathfinder getPathfinder(){return path;}Data getPersistentDataContainer(){return new Data();}
    }
    static class Pathfinder {int requests;boolean moveTo(Location p,double speed){requests++;return false;}void stopPathfinding(){}}
    static class Data {void set(Object key,Object type,String value){}}
    static class PersistentDataType {static final Object STRING=new Object();}
    enum BossPhase { HUNT }enum Tactic { PURSUE }
    static class CombatTacticsPolicy {
        record BossPlan(Tactic tactic,double preferredDistance){}
        static BossPlan bossPlan(Object phase,int cycle,double distance,boolean core){return new BossPlan(Tactic.PURSUE,2);}
    }
    static class BossPhasePolicy {record CombatProfile(double movementSpeed){}}
    static class GroundedBossMotionPolicy {record Resolution(Animation animation){}}
    record Animation(){String wireId(){return "run";}}
    record Config(){double bossRadius(){return 40;}}
    long lastBossPathRequestMillis,nextBossTacticMillis;int bossTacticCycle,planned,fallback;
    static final long BOSS_TACTIC_REFRESH_MILLIS=1000;
    static final double MIN_BOSS_CORE_DISTANCE_BLOCKS=3;
    final Config config=new Config();BossPhase bossPhase=BossPhase.HUNT;Object keyCombatTactic;boolean noFloor;
    boolean isCombatTarget(Player p){return true;}Location coreCombatAnchorLocation(){return new Location();}
    boolean isCoreBlockPosition(Location p){return false;}double horizontalDistanceSquared(Location a,Location b){return 25;}
    Location bossTacticalDestination(Location a,Mob b,Player p,CombatTacticsPolicy.BossPlan plan){planned++;return noFloor?null:new Location();}
    double boundedCombatRadius(double r){return r;}
    BossPhasePolicy.CombatProfile currentBossCombatProfile(){return new BossPhasePolicy.CombatProfile(1);}
    boolean requestBoundedCombatMovement(Mob m,Location d,double s,Location a,double r,double min,int room,String reason,boolean path){fallback++;return false;}
    GroundedBossMotionPolicy.Resolution applyGroundedBossPursuitMotion(Mob m,double s){return new GroundedBossMotionPolicy.Resolution(new Animation());}
    Logger getLogger(){return Logger.getAnonymousLogger();}String locationText(Location p){return "point";}
''' + method + r'''
    public static void main(String[] args){for(boolean noFloor:new boolean[]{false,true}){
        var owner=new BossPathBudgetProbe();owner.noFloor=noFloor;var boss=new Mob();
        for(long now:new long[]{10000,10001,10499,10500})owner.maintainBossPath(boss,now);
        if(owner.planned!=2||boss.path.requests!=(noFloor?0:2)||owner.fallback!=(noFloor?0:2))
            throw new AssertionError("refused destination/path was retried every tick");
    }}
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "BossPathBudgetProbe"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr


def test_invalid_focus_is_urgent_but_refused_selection_is_bounded(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void maintainBossTargetSelection(")
    java = tmp_path / "BossTargetClockProbe.java"
    java.write_text(r'''
import java.util.UUID;
public class BossTargetClockProbe {
    static class LivingEntity {}static class Mob extends LivingEntity {}
    static class Player {boolean active=true;}
    static class Bukkit {static Player player=new Player();static Player getPlayer(UUID id){return player;}}
    static class BossPhasePolicy {record CombatProfile(double targetRotationMultiplier){}}
    record Config(){int bossTargetMinSeconds(){return 10;}int bossTargetMaxSeconds(){return 12;}}
    UUID bossPursuitTarget;long nextTargetMillis;int rotations;boolean deny;Config config=new Config();
    boolean isActiveBossParticipant(Player player){return player!=null&&player.active;}
    void rotateBossTarget(Mob boss){rotations++;bossPursuitTarget=deny?null:new UUID(0,1);}
    BossPhasePolicy.CombatProfile currentBossCombatProfile(){return new BossPhasePolicy.CombatProfile(1);}
    int randomSeconds(int min,int max){return min;}
    long scaledBossDelayMillis(int seconds,double multiplier,long minimum){return seconds*1000L;}
''' + method + r'''
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        var owner=new BossTargetClockProbe();var boss=new Mob();owner.maintainBossTargetSelection(boss,10000);
        owner.maintainBossTargetSelection(boss,10001);check(owner.rotations==1,"stable focus rotated early");
        Bukkit.player.active=false;owner.deny=true;owner.maintainBossTargetSelection(boss,10002);
        check(owner.rotations==2&&owner.nextTargetMillis==10502,"invalid focus was retained until old timeout");
        for(long now:new long[]{10003,10100,10501})owner.maintainBossTargetSelection(boss,now);
        check(owner.rotations==2,"refused target was retried every tick");
        owner.maintainBossTargetSelection(boss,10502);check(owner.rotations==3,"bounded retry did not occur");
        Bukkit.player.active=true;owner.deny=false;owner.maintainBossTargetSelection(boss,11002);
        check(owner.rotations==4&&owner.bossPursuitTarget!=null,"focus did not recover after refusal");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "BossTargetClockProbe"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
