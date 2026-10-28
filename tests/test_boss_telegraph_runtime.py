"""Execute the production scheduler, rather than merely checking marker constants."""
from pathlib import Path
import subprocess
import pytest
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def telegraph_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("boss-telegraph")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(extract(source, sig) for sig in ["boolean telegraphBossSpell(", "int spellTelegraphTicks("])
    java = out / "BossTelegraphRuntimeProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.*;
public class BossTelegraphRuntimeProbe {
    enum EventPhase {BOSS_ACTIVE,VICTORY_PROCESSING}
    enum Particle {DRAGON_BREATH,CRIT,REVERSE_PORTAL}
    static class Location {public Location clone(){return new Location();}float getYaw(){return 0;}Location add(double x,double y,double z){return this;}}
    static class LivingEntity {final UUID id=UUID.randomUUID();UUID getUniqueId(){return id;}boolean isValid(){return true;}boolean isDead(){return false;}Location getLocation(){return new Location();}}
    static class Player {final UUID id=UUID.randomUUID();UUID getUniqueId(){return id;}Location getLocation(){return new Location();}}
    static class BukkitTask {boolean cancelled;final Runnable callback;final long delay,period;
        BukkitTask(Runnable r,long d,long p){callback=r;delay=d;period=p;}void cancel(){cancelled=true;}boolean isCancelled(){return cancelled;}}
    static class Scheduler {int tick;BukkitTask task;
        BukkitTask runTaskTimer(Object plugin,Runnable r,long delay,long period){task=new BukkitTask(r,delay,period);return task;}
        void advance(){tick++;long first=Math.max(1,task.delay);if(!task.cancelled&&tick>=first&&(tick-first)%task.period==0)task.callback.run();}}
    static class Bukkit {static Scheduler scheduler;static Scheduler getScheduler(){return scheduler;}}
    static class Registry {boolean owns(long g){return g==7;}}
    record Config(){int bossSpellTelegraphTicks(){return 20;}}
    final Config config=new Config();final Registry taskRegistry=new Registry();
    final LivingEntity boss=new LivingEntity();final Player target=new Player();
    UUID bossUuid=boss.id;String eventId="event";long generation=7,bossCastSequence,bossCastDeadlineMillis;
    EventPhase phase=EventPhase.BOSS_ACTIVE;BossAbilityState bossAbilityState=BossAbilityState.NONE;
    boolean testCombatAiMode;float bossCastLockedYaw;BukkitTask bossCastTask;int impacts,impactTick,particles;
    boolean isTestBoss(LivingEntity b){return false;}
    void releaseBossHazardReservation(String reason){}
    void holdBossCastPosition(LivingEntity b){}
    void aimBossCastAt(LivingEntity b,Location l){}
    String bossTelegraphAnimation(EndRiftAiPolicy.BossSpell s){return s.id();}
    String bossCueId(String s,BossVisualCuePolicy.CueStage stage){return s;}
    void sendBossAnimationVisualUpdate(LivingEntity b,String s){}
    void playBossVisualCue(LivingEntity b,String s,Location l,boolean f){}
    void spawnEventParticle(Object... args){particles++;}
    void renderSpellTelegraphVisual(LivingEntity b,Location l,String s,int tick,int duration){}
    void executeBossSpell(LivingEntity b,Player t,Location l,EndRiftAiPolicy.BossSpell s,boolean f,long g){impacts++;impactTick=Bukkit.scheduler.tick;}
    void registerEncounterTask(BukkitTask t){}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + methods + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new BossTelegraphRuntimeProbe();Bukkit.scheduler=new Scheduler();
        var spell=EndRiftAiPolicy.BossSpell.valueOf(args[0]);int marker=p.spellTelegraphTicks(spell);
        p.telegraphBossSpell(p.boss,p.target,spell,false);
        for(int tick=1;tick<marker;tick++){Bukkit.scheduler.advance();check(p.impacts==0,"attack released before its authored marker");}
        Bukkit.scheduler.advance();check(p.impacts==1&&p.impactTick==marker,"attack did not release on its exact authored marker");
        Bukkit.scheduler.task.callback.run();check(p.impacts==1,"duplicate completed callback released the same attack twice");
        check(p.particles<=(marker+4)/5,"precise contact timing multiplied warning particle traffic");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"), "-d", str(out), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return out


@pytest.mark.parametrize("spell", ["MELEE_SWIPE", "CHEST_STRIKE", "GROUND_SLAM", "RIFT_ARROWS"])
def test_boss_release_matches_the_actual_animation_marker(telegraph_probe, spell):
    result = subprocess.run(["java", "-cp", str(telegraph_probe), "BossTelegraphRuntimeProbe", spell], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
