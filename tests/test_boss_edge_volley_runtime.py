"""Run real volley callbacks: cancellation must fence work even within one generation."""
from pathlib import Path
import subprocess
import pytest
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def volley_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("boss-edge-volley")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signatures = ["void startBossEdgeVolley(", "void cancelBossCastTask(", "void reconcileBossCastProjection("]
    if "void cancelBossCastForPhaseChange(" in source:
        signatures.append("void cancelBossCastForPhaseChange(")
    if "void finishBossEdgeVolley(" in source:
        signatures.append("void finishBossEdgeVolley(")
    methods = "\n".join(extract(source, signature) for signature in signatures)
    # Execute the actual post-impact state projection too: it used to end the cast after 20 ticks.
    opening = source.index("if (spell != EndRiftAiPolicy.BossSpell.FINAL_STRIKE", source.index("private void executeBossSpell("))
    post = extract(source, source[opening:source.index("{", opening)].strip())
    java = out / "BossEdgeVolleyRuntimeProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
public class BossEdgeVolleyRuntimeProbe {
    enum EventPhase {BOSS_ACTIVE,VICTORY_PROCESSING}
    static class Location {double distance(Location l){return 0;}Object getWorld(){return WORLD;}}
    static final Object WORLD=new Object();
    static class LivingEntity {
        final UUID id=UUID.randomUUID();boolean valid=true,dead;
        UUID getUniqueId(){return id;}boolean isValid(){return valid;}boolean isDead(){return dead;}
        void setInvulnerable(boolean value){}
        Location getLocation(){return new Location();}Object getWorld(){return WORLD;}
    }
    static class Player {boolean legal=true;Location getLocation(){return new Location();}}
    static class BukkitTask {final Runnable callback;final long tick;BukkitTask(Runnable r,long t){callback=r;tick=t;}void cancel(){}boolean isCancelled(){return false;}}
    static class Scheduler {
        final List<BukkitTask> tasks=new ArrayList<>();
        BukkitTask runTaskLater(Object plugin,Runnable r,long tick){var t=new BukkitTask(r,tick);tasks.add(t);return t;}
    }
    static class Bukkit {static Scheduler scheduler;static Scheduler getScheduler(){return scheduler;}}
    static class Registry {long generation=7;boolean owns(long g){return generation==g;}}
    final Registry taskRegistry=new Registry();
    final LivingEntity boss=new LivingEntity();final Player target=new Player();
    UUID bossUuid=boss.id;String eventId="event";long generation=7;
    long nextBossEdgeVolleyMillis,bossChestStrikeSequence,bossCastSequence,bossEdgeVolleySequence,bossCastDeadlineMillis,bossSpellPauseUntilMillis;
    boolean bossEdgeVolleyActive,bossChestStrikeActive,teleportAccepted=true,cornerAvailable=true;
    BukkitTask bossCastTask;BossPhase bossPhase=BossPhase.RIFT;
    EventPhase phase=EventPhase.BOSS_ACTIVE;BossAbilityState bossAbilityState=BossAbilityState.EXECUTING;
    static final double BOSS_PROJECTILE_DAMAGE=8;
    int salvos,saves;final List<Integer> volleyTimes=new ArrayList<>();int tick;
    boolean isCombatTarget(Player p){return p!=null&&p.legal;}
    boolean isActiveBossParticipant(Player p){return isCombatTarget(p);}
    boolean isTestBoss(LivingEntity b){return false;}
    Location farthestBossCorner(LivingEntity b){return cornerAvailable?new Location():null;}
    boolean teleportCombatEntity(LivingEntity b,Location l){return teleportAccepted;}
    String locationText(Location l){return "corner";}
    void registerEncounterTask(BukkitTask t){}
    void saveStateAsync(){saves++;}
    void releaseBossHazardReservation(String s){}
    void cancelBossFinalStrike(){}
    void clearArenaInferno(){}
    void clearVoidMarkZones(){}
    void aimBossCastAt(LivingEntity b,Location l){}
    void riftArrowVolley(LivingEntity b,Player p,String s,SkeletonCombatPolicy.ArrowProfile profile){salvos++;volleyTimes.add(tick);}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + methods + "\n void postImpact(LivingEntity boss,long callbackGeneration,EndRiftAiPolicy.BossSpell spell){" + post + r'''}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new BossEdgeVolleyRuntimeProbe();Bukkit.scheduler=new Scheduler();String s=args[0];
        if(s.equals("slow-telegraph")){
            p.bossAbilityState=BossAbilityState.TELEGRAPHING;p.bossCastTask=new BukkitTask(()->{},0);
            p.reconcileBossCastProjection(p.boss,System.currentTimeMillis()+100_000);
            check(p.bossAbilityState==BossAbilityState.TELEGRAPHING,"slow telegraph admitted another major cast");return;
        }
        p.teleportAccepted=!s.equals("failed-teleport");p.cornerAvailable=!s.equals("missing-corner");
        p.startBossEdgeVolley(p.boss,p.target);
        p.postImpact(p.boss,p.generation,EndRiftAiPolicy.BossSpell.RIFT_ARROWS);
        var tasks=new ArrayList<>(Bukkit.scheduler.tasks);
        if(s.equals("failed-teleport")||s.equals("missing-corner")){
            check(tasks.isEmpty()&&p.nextBossEdgeVolleyMillis==0,"failed reposition scheduled salvos or consumed cooldown");return;
        }
        check(tasks.size()==5,"legal volley must schedule exactly five salvos");
        check(p.bossAbilityState==BossAbilityState.EXECUTING,"generic impact recovery released the major cast before its five salvos");
        p.reconcileBossCastProjection(p.boss,System.currentTimeMillis()+100_000);
        check(p.bossAbilityState==BossAbilityState.EXECUTING,"low TPS let a wall-clock deadline overlap a second major spell");
        check(tasks.getFirst().tick>=20,"arrival aim window missing");
        for(int i=0;i<tasks.size();i++){
            if(i==1){
                if(s.equals("cancel"))p.cancelBossCastTask();
                if(s.equals("phase"))p.bossPhase=BossPhase.LAST_SEAL;
                if(s.equals("target"))p.target.legal=false;
                if(s.equals("generation")){p.generation++;p.taskRegistry.generation++;}
                if(s.equals("event"))p.eventId="new";
                if(s.equals("boss"))p.bossUuid=UUID.randomUUID();
                if(s.equals("death"))p.boss.dead=true;
            }
            p.tick=(int)tasks.get(i).tick;tasks.get(i).callback.run();
            if(i<4&&s.equals("complete"))check(p.bossAbilityState==BossAbilityState.EXECUTING,"major cast released early");
        }
        check(p.salvos==(s.equals("complete")?5:1),"cancelled/stale cast continued firing or legal cast lost a salvo");
        if(s.equals("complete")){
            check(!p.bossEdgeVolleyActive&&p.bossAbilityState==BossAbilityState.RECOVERY,"final salvo did not release to recovery");
            check(p.bossCastDeadlineMillis>System.currentTimeMillis(),"recovery missing");
        }
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"), "-d", str(out), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return out


@pytest.mark.parametrize("scenario", ["complete", "cancel", "phase", "target", "generation", "event", "boss", "death", "failed-teleport", "missing-corner", "slow-telegraph"])
def test_edge_volley_owns_its_whole_five_salvo_action(volley_probe, scenario):
    result = subprocess.run(["java", "-cp", str(volley_probe), "BossEdgeVolleyRuntimeProbe", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_edge_volley_needs_a_sustained_cluster_not_an_arrow_cast():
    """Spec 5.4(1): the trigger is a persistent majority cluster, not RIFT_ARROWS itself."""
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    opening = source.index("case RIFT_ARROWS ->", source.index("private void castBossSpell("))
    body = source[opening:source.index("case VOID_MARK ->", opening)]
    assert "BossEdgeVolleyPolicy.allowsPhase(bossPhase)" in body
    assert "bossEdgeVolleyClusterDwell.satisfied()" in body
    assert body.index("bossEdgeVolleyClusterDwell.satisfied()") < body.index("startBossEdgeVolley(boss, target)")
    # The plain arrow fallback must stay reachable when the party is not clustered.
    assert "else {" in body and "riftArrowVolley(boss, target," in body
    # The dwell must be sampled every tick, otherwise a 2-3 s window is unobservable.
    tick = source[source.index("private void tickCurrentBoss(LivingEntity boss)"):
                  source.index("private boolean pinBossToFinalSealCore", source.index("private void tickCurrentBoss(LivingEntity boss)"))]
    assert "trackBossEdgeVolleyClusterDwell(boss)" in tick
    assert "bossEdgeVolleyClusterDwell.reset()" in tick
