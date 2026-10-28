"""Run the production ticker: 16-tick vents cannot work on a 20-tick timer."""
from pathlib import Path
import subprocess
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


def test_inferno_vents_get_full_warning_and_a_real_accepted_eruption(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    if "private void startArenaInfernoTicker(" in source:
        ticker = _method(source, "private void startArenaInfernoTicker(")
        ticker += "\n" + _method(source, "private void tickArenaInferno(")
    else:
        # Execute the existing scheduling block, without reproducing its timing logic.
        opening = source.index("final int[] elapsedTicks = {0};", source.index('"BOSS_SPELL_ARENA_INFERNO event="'))
        closing = source.index("    private void renderArenaInferno(", opening)
        ticker = "private void startArenaInfernoTicker(int durationTicks) {\n" + source[opening:closing]
    steam = _method(source, "private void spawnArenaInfernoSteamVents(")
    probe = tmp_path / "InfernoRuntimeCadenceProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.ArenaInfernoPolicy;
public class InfernoRuntimeCadenceProbe {
    enum Particle { CAMPFIRE_COSY_SMOKE, SMOKE, CLOUD }
    enum Sound { BLOCK_LAVA_AMBIENT, BLOCK_FIRE_EXTINGUISH }
    enum SoundCategory { HOSTILE }
    static class World {
        void spawnParticle(Object... args) { }
        void playSound(Object... args) { }
    }
    static class Location {
        Location add(double x,double y,double z) { return this; }
        double distanceSquared(Location other) { return 0; }
    }
    static class Block { Location getLocation(){return new Location();} }
    static class Vector { Vector setY(double y){return this;} }
    static class LivingEntity { World getWorld(){return WORLD;} }
    static class Player extends LivingEntity {
        double health=20; int hits,impulses; boolean cancelled;
        Location getLocation(){return new Location();} double getHealth(){return health;}
        void damage(double amount,LivingEntity boss){hits++;if(!cancelled)health-=amount;}
        Vector getVelocity(){return new Vector();} void setVelocity(Vector v){impulses++;}
    }
    static final World WORLD=new World();
    static class BukkitTask { }
    static class Scheduler {
        Runnable callback;long period;
        BukkitTask runTaskTimer(Object plugin,Runnable task,long delay,long period){
            this.callback=task;this.period=period;return new BukkitTask();
        }
    }
    static class Bukkit { static final Scheduler scheduler=new Scheduler();static Scheduler getScheduler(){return scheduler;} }
    final LivingEntity boss=new LivingEntity();final Player target=new Player();
    final List<Block> arenaInfernoBlocks=List.of(new Block());
    final long eventTickCounter=137;
    final long ARENA_INFERNO_VENT_PERIOD_TICKS=60;
    BukkitTask arenaInfernoTask;final Object taskRegistry=null;
    int floorCalls;boolean cleared,admitted=true;
    LivingEntity liveBoss(){return boss;}
    void clearArenaInferno(){cleared=true;}
    void renderArenaInferno(LivingEntity boss){floorCalls++;}
    void registerEncounterTask(BukkitTask task){ }
    List<Player> eventAudience(){return List.of(target);}
    List<Player> activeLivingPlayers(){return List.of(target);}
    boolean isActiveBossParticipant(Player p){return admitted;}
''' + ticker + "\n" + steam + r'''
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        var p=new InfernoRuntimeCadenceProbe();p.startArenaInfernoTicker(120);
        check(Bukkit.scheduler.period==1,"vent ERUPT at tick16 is unreachable from the 20-tick scheduler");
        for(int tick=0;tick<16;tick++)Bukkit.scheduler.callback.run();
        check(p.target.hits==0,"a cast inherited a global-clock eruption without its full warning");
        Bukkit.scheduler.callback.run();
        check(p.target.hits==1 && p.target.impulses==1,"the first warned eruption never hit");
        for(int tick=17;tick<60;tick++)Bukkit.scheduler.callback.run();
        check(p.target.hits==1 && p.floorCalls==3,"floor cadence or single-eruption receipt changed");
        p.target.cancelled=true;
        for(int tick=60;tick<=76;tick++)Bukkit.scheduler.callback.run();
        check(p.target.hits==2 && p.target.impulses==1,"cancelled steam damage still launched the player");
        for(int tick=77;tick<=120;tick++)Bukkit.scheduler.callback.run();
        check(p.cleared && p.floorCalls==6,"inferno lifetime was stretched or floor damage multiplied");
        var viewer=new InfernoRuntimeCadenceProbe();viewer.admitted=false;
        viewer.startArenaInfernoTicker(120);
        for(int tick=0;tick<=16;tick++)Bukkit.scheduler.callback.run();
        check(viewer.target.hits==0 && viewer.target.impulses==0,"a nearby nonparticipant was hit by an event vent");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"),
                             "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "InfernoRuntimeCadenceProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
