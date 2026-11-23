"""Execute the production Warden V5.1 resolvers, not a mirrored policy.

The probe slices the real ``resolveWardenFrontalSweep``,
``applyWardenSweepStunAndSlow`` and ``finishWardenSlam`` bodies so the
hit/dodge, accepted-damage-gated crowd control and wall-piercing verdicts are
the ones the server actually runs.
"""
from pathlib import Path
import subprocess

from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_warden_sweep_echo_and_cc_are_the_committed_geometry(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    production = "\n".join(extract_method(source, signature) for signature in (
        "private void resolveWardenFrontalSweep(",
        "private void applyWardenSweepStunAndSlow(",
        "private void finishWardenSlam(",
    ))
    # Through-wall proof: the echo resolver must contain no cover test at all.
    echo_body = extract_method(source, "private void finishWardenSlam(")
    assert "hasLineOfSight" not in echo_body and "rayTrace" not in echo_body
    assert "WardenV5CombatPolicy.echoHits" in echo_body
    java = r"""
import java.util.*;
import me.copimine.endevent.domain.WardenV5CombatPolicy;

public class WardenV5RuntimeProbe {
    static final class Vector implements Cloneable {
        double x,y,z;
        Vector(){this(0,0,0);}
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        @Override public Vector clone(){return new Vector(x,y,z);}
        double getX(){return x;} double getY(){return y;} double getZ(){return z;}
        Vector setY(double v){y=v;return this;}
        Vector multiply(double f){x*=f;y*=f;z*=f;return this;}
        Vector normalize(){double l=Math.sqrt(x*x+y*y+z*z);
            if(l<1.0E-8){x=0;y=0;z=0;}else{x/=l;y/=l;z/=l;}return this;}
        double lengthSquared(){return x*x+y*y+z*z;}
        Vector subtract(Vector o){return new Vector(x-o.x,y-o.y,z-o.z);}
    }
    static final class Location implements Cloneable {
        final World world; double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        @Override public Location clone(){return new Location(world,x,y,z);}
        double getX(){return x;} double getY(){return y;} double getZ(){return z;}
        World getWorld(){return world;}
        Location add(double dx,double dy,double dz){return new Location(world,x+dx,y+dy,z+dz);}
        Vector toVector(){return new Vector(x,y,z);}
    }
    enum Sound { ENTITY_WARDEN_ATTACK_IMPACT, ENTITY_WARDEN_SONIC_BOOM }
    enum SoundCategory { HOSTILE }
    enum Particle { SOUL_FIRE_FLAME, SCULK_SOUL, SONIC_BOOM, EXPLOSION, SOUL }
    enum PotionEffectType { SLOWNESS }
    static final class World {
        int sounds;
        void playSound(Location at,Sound s,SoundCategory c,float v,float p){sounds++;}
    }
    static final class PotionEffect {
        final PotionEffectType type; final int ticks; final int amplifier;
        PotionEffect(PotionEffectType t,int ticks,int amplifier,boolean a,boolean b,boolean c){
            this.type=t;this.ticks=ticks;this.amplifier=amplifier;}
    }
    static final class BukkitTask {final Runnable callback;final long ticks;
        BukkitTask(Runnable r,long t){callback=r;ticks=t;} void cancel(){}}
    abstract static class BukkitRunnable implements Runnable {
        BukkitTask runTaskLater(Object plugin,long ticks){
            BukkitTask t=new BukkitTask(this,ticks); schedulerTasks.add(t); return t;}
    }
    static final class Scheduler {final List<BukkitTask> tasks=new ArrayList<>();}
    static class LivingEntity {
        final World world; final UUID id=UUID.randomUUID();
        double x,y,z; Vector velocity=new Vector(0,0,0);
        LivingEntity(World world){this.world=world;}
        World getWorld(){return world;}
        double getX(){return x;} double getY(){return y;} double getZ(){return z;}
        Location getLocation(){return new Location(world,x,y,z);}
        Vector getVelocity(){return velocity.clone();}
        void setVelocity(Vector v){velocity=v.clone();}
        UUID getUniqueId(){return id;}
        boolean isValid(){return true;} boolean isDead(){return false;}
    }
    static final class Player extends LivingEntity {
        final List<PotionEffect> effects=new ArrayList<>();
        Player(World world){super(world);}
        boolean isOnline(){return true;}
        @Override boolean isDead(){return false;}
        void addPotionEffect(PotionEffect e,boolean force){effects.add(e);}
        void spawnParticle(Particle p,Location at,int n,double a,double b,double c,double d){}
    }
    static final class RealitySplitTrialRuntimeState {
        Vector direction; UUID target; final Set<UUID> hitPlayers=new HashSet<>();
    }
    static final class Bukkit {
        static Player nextPlayer;
        static Player getPlayer(UUID id){return nextPlayer;}
    }

    // --- frozen server state the sliced bodies touch ------------------------
    final World world=new World();
    final List<Player> targets=new ArrayList<>();
    final Set<UUID> stale=new HashSet<>();
    boolean damageLanded=true;
    long generation=7L;
    String eventId="event-7";
    static final List<BukkitTask> schedulerTasks=new ArrayList<>();
    int audience;
    static final double WARDEN_SLAM_RADIUS=WardenV5CombatPolicy.ECHO_RADIUS_BLOCKS;
    static final double WARDEN_SLAM_DAMAGE=WardenV5CombatPolicy.ECHO_DAMAGE;

    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}

    List<Player> realitySplitTrialTargets(LivingEntity warden){return targets;}
    boolean isCurrentRealitySplitTrialTarget(LivingEntity warden,Player p){
        return !stale.contains(p.getUniqueId());}
    boolean applyWaveAbilityDamage(LivingEntity warden,Player p,double d){return damageLanded;}
    void registerEncounterTask(BukkitTask t){}
    void renderWave7AbilityEffect(LivingEntity warden,Player p,String id){}
    List<Player> eventAudience(){return new ArrayList<>();}
    boolean isEventParticleViewer(Player viewer,Location at){return true;}
    void spawnPatternRing(Player viewer,Location c,Vector a,Vector b,double r,int n,double rot,Particle p){}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("WardenV5RuntimeProbe");}

""" + production + r"""
    Player player(double x,double y,double z){
        Player p=new Player(world);p.x=x;p.y=y;p.z=z;targets.add(p);return p;
    }

    void sweep(boolean landed){
        targets.clear(); stale.clear(); schedulerTasks.clear(); damageLanded=landed;
        LivingEntity warden=new LivingEntity(world);warden.x=0;warden.y=70;warden.z=0;
        RealitySplitTrialRuntimeState state=new RealitySplitTrialRuntimeState();
        state.direction=new Vector(0,0,1);
        Player front=player(0,70,3);
        Player edge=player(2.4,70,2);
        Player past=player(0,70,4.5);
        Player wide=player(2.6,70,2);
        Player behind=player(0,70,-2);
        Player high=player(0,72.5,2);
        resolveWardenFrontalSweep(warden,state);
        if(!landed){
            require(state.hitPlayers.isEmpty(),"an immune sweep must accept nobody");
            require(schedulerTasks.isEmpty(),"an immune sweep must schedule no crowd control");
            for(Player p:targets) require(p.effects.isEmpty(),"an immune sweep must apply no effect");
            return;
        }
        require(state.hitPlayers.size()==2,"the committed 5x4 volume catches the near pair, got "+state.hitPlayers.size());
        require(state.hitPlayers.contains(front.getUniqueId()),"a straight-ahead target is inside the sweep");
        require(state.hitPlayers.contains(edge.getUniqueId()),"the 2.4-block lateral edge is inside the sweep");
        require(!state.hitPlayers.contains(past.getUniqueId()),"past four blocks is a range dodge");
        require(!state.hitPlayers.contains(wide.getUniqueId()),"past 2.5 blocks across is a lateral dodge");
        require(!state.hitPlayers.contains(behind.getUniqueId()),"behind the committed heading is never hit");
        require(!state.hitPlayers.contains(high.getUniqueId()),"2.5 blocks up is a vertical dodge");
        require(front.effects.size()==1&&front.effects.get(0).type==PotionEffectType.SLOWNESS
                &&front.effects.get(0).amplifier==WardenV5CombatPolicy.SWEEP_STUN_SLOW_AMPLIFIER
                &&front.effects.get(0).ticks==WardenV5CombatPolicy.SWEEP_STUN_TICKS,
                "an accepted hit locks movement for the 20-tick stun immediately");
        require(schedulerTasks.size()==2,"each accepted hit schedules exactly one slow tail");
        for(BukkitTask t:schedulerTasks)
            require(t.ticks==WardenV5CombatPolicy.SWEEP_STUN_TICKS,"the slow tail waits out the whole stun");
        for(BukkitTask t:schedulerTasks) t.callback.run();
        require(front.effects.size()==2,"the fenced tail applies the 60-tick slow after the stun");
        require(front.effects.get(1).amplifier==WardenV5CombatPolicy.SWEEP_SLOW_AMPLIFIER
                &&front.effects.get(1).ticks==WardenV5CombatPolicy.SWEEP_SLOW_TICKS,
                "the tail is the lighter 60-tick 2nd-level slow");
        for(Player p:new Player[]{past,wide,behind,high})
            require(p.effects.isEmpty(),"a dodged target receives no crowd control");
    }

    void sweepStaleTail(){
        targets.clear(); stale.clear(); schedulerTasks.clear(); damageLanded=true;
        LivingEntity warden=new LivingEntity(world);warden.x=0;warden.y=70;warden.z=0;
        RealitySplitTrialRuntimeState state=new RealitySplitTrialRuntimeState();
        state.direction=new Vector(0,0,1);
        Player front=player(0,70,3);
        resolveWardenFrontalSweep(warden,state);
        generation++;
        for(BukkitTask t:schedulerTasks) t.callback.run();
        require(front.effects.size()==1,"a tail from a dead generation must not debuff");
    }

    void echo(boolean landed){
        targets.clear(); stale.clear(); damageLanded=landed;
        LivingEntity warden=new LivingEntity(world);warden.x=0;warden.y=70;warden.z=0;
        RealitySplitTrialRuntimeState state=new RealitySplitTrialRuntimeState();
        state.direction=new Vector(0,0,1);
        Player inside=player(0,70,0);
        Player wall=player(5.9,70,0);
        Player outside=player(6.2,70,0);
        Player high=player(0,76.5,0);
        Player already=player(0,70,1);
        state.hitPlayers.add(already.getUniqueId());
        finishWardenSlam(warden,state);
        if(!landed){
            require(state.hitPlayers.size()==1,"an immune echo must add no hit");
            for(Player p:new Player[]{inside,wall,outside,high})
                require(p.getVelocity().lengthSquared()==0.0,"an immune echo must not shove");
            return;
        }
        require(state.hitPlayers.contains(inside.getUniqueId()),"a target on the centre is caught");
        require(state.hitPlayers.contains(wall.getUniqueId()),"5.9 blocks through cover is still inside the sphere");
        require(!state.hitPlayers.contains(outside.getUniqueId()),"past the 6-block sphere is a real dodge");
        require(!state.hitPlayers.contains(high.getUniqueId()),"6.5 blocks above the centre is a real dodge");
        require(state.hitPlayers.contains(already.getUniqueId())&&already.effects.isEmpty(),
                "a target already hit this cast is not hit twice");
        require(inside.getVelocity().lengthSquared()>0.0,"a caught target is shoved");
        require(outside.getVelocity().lengthSquared()==0.0,"a dodged target is not shoved");
    }

    public static void main(String[] args){
        WardenV5RuntimeProbe p=new WardenV5RuntimeProbe();
        switch(args[0]){
            case "sweep" -> p.sweep(true);
            case "sweep-dodged" -> p.sweep(false);
            case "sweep-stale-tail" -> p.sweepStaleTail();
            case "echo" -> p.echo(true);
            case "echo-dodged" -> p.echo(false);
            default -> throw new AssertionError("unknown scenario "+args[0]);
        }
        System.out.println("PASS Warden V5.1: committed 5x4 sweep, gated stun+slow, wall-piercing radius-6 echo");
    }
}
"""
    path = tmp_path / "WardenV5RuntimeProbe.java"
    path.write_text(java, encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WardenV5CombatPolicy.java"),
         str(path)],
        text=True, capture_output=True, timeout=30, encoding="utf-8", errors="replace",
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    for scenario in ("sweep", "sweep-dodged", "sweep-stale-tail", "echo", "echo-dodged"):
        run = subprocess.run(
            ["java", "-cp", str(tmp_path), "WardenV5RuntimeProbe", scenario],
            text=True, capture_output=True, timeout=15, encoding="utf-8", errors="replace",
        )
        assert run.returncode == 0, run.stdout + run.stderr
