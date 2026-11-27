"""Execute the production Guardian V5.1 physical move resolvers.

The probe slices the real ``resolveGuardianFan`` / ``resolveGuardianHeavy`` and
``applyContactHit`` bodies so the hit/dodge/range/vertical verdicts are the ones
the server actually runs, not a second copy of the cone maths.
"""
from pathlib import Path
import subprocess

from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_guardian_fan_and_heavy_hit_only_the_committed_geometry(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    production = "\n".join(extract_method(source, signature) for signature in (
        "private int resolveGuardianFan(",
        "private int resolveGuardianHeavy(",
        "private boolean applyContactHit(",
    ))
    java = r"""
import java.util.*;
import me.copimine.endevent.domain.GuardianV5MeleePolicy;

public class GuardianMeleeProbe {
    static final class Vector implements Cloneable {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        @Override public Vector clone(){return new Vector(x,y,z);}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        void setY(double value){y=value;}
        Vector normalize(){double length=Math.sqrt(x*x+y*y+z*z);
            if(length<1.0E-8){x=0;y=0;z=0;}else{x/=length;y/=length;z/=length;}return this;}
        Vector multiply(double f){x*=f;y*=f;z*=f;return this;}
        double lengthSquared(){return x*x+y*y+z*z;}
    }
    static final class Location {
        final World world;double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        @Override public Location clone(){return new Location(world,x,y,z);}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        World getWorld(){return world;}
        Location add(double dx,double dy,double dz){return new Location(world,x+dx,y+dy,z+dz);}
    }
    enum Sound { ENTITY_IRON_GOLEM_ATTACK }
    enum SoundCategory { HOSTILE }
    enum EntityEffect { HURT }
    static final class World {
        int sounds;
        void playSound(Location at,Sound sound,SoundCategory category,float volume,float pitch){sounds++;}
    }
    static class LivingEntity {
        final UUID id = UUID.randomUUID();
        UUID getUniqueId(){return id;}
        final World world;double x,y,z;Vector velocity=new Vector(0,0,0);
        LivingEntity(World world){this.world=world;}
        World getWorld(){return world;}double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        Location getLocation(){return new Location(world,x,y,z);}
        Vector getVelocity(){return velocity.clone();}
        void setVelocity(Vector value){velocity=value.clone();}
        boolean hasLineOfSight(Object other){return true;}
        boolean isValid(){return true;}boolean isDead(){return false;}
    }
    static final class Player extends LivingEntity {
        int hurts; boolean covered;
        Player(World world){super(world);}
        void playEffect(EntityEffect effect){hurts++;}
    }
    static final class BossImpactGeometryPolicy {enum Kind { SWIPE, SLAM }}
    static final class EndRiftAiPolicy {enum BossSpell { MELEE_SWIPE }}
    static final class BossAnimationId {static String MELEE_SWIPE="MELEE_SWIPE";}

    // --- production seams and fields the sliced bodies touch -----------------
    final List<Player> players=new ArrayList<>();
    List<Player> activeLivingPlayers(){return players;}
    final String eventId="event-7";final long generation=7L;
    long guardianMoveEndAtMillis,nextGuardianPhysicalMillis;
    static final long GUARDIAN_INTER_MOVE_MILLIS=400L;
    boolean damageLanded=true;
    boolean guardianContactHasCoverClear(LivingEntity boss,Player player,Location origin){return !player.covered;}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("GuardianMeleeProbe");}
    boolean applyBossAbilityDamage(LivingEntity boss,Player player,double damage){return damageLanded;}
    void renderBossContactImpact(LivingEntity boss,Player player,EndRiftAiPolicy.BossSpell spell){}
    void publishSpellContact(String token,LivingEntity actor,UUID id,Location at,Vector axis,double radius,double reach,int ticks){}
    void playBossImpactPresentation(LivingEntity boss,BossImpactGeometryPolicy.Kind kind,
            Location origin,Vector facing,double range,boolean a,boolean b){}
    void renderGuardianArcBurst(Location origin,Vector facing,int bands){}
    void renderGuardianHeavyBurst(Location origin,Vector facing){}

""" + production + r"""
    static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}

    Player player(World world,double x,double y,double z){
        Player p=new Player(world);p.x=x;p.y=y;p.z=z;players.add(p);return p;
    }

    void fan(){
        players.clear();
        World world=new World();
        LivingEntity boss=new LivingEntity(world);
        Location origin=new Location(world,0,70,0);
        Vector facing=new Vector(0,0,1);
        Player ahead=player(world,0,70,4);
        Player edge=player(world,2.0,70,3.4);
        Player behind=player(world,0,70,-4);
        Player far=player(world,0,70,5.5);
        Player high=player(world,0,72.5,3);
        Player covered=player(world,1,70,3); covered.covered=true;
        int hits=resolveGuardianFan(boss,origin,facing);
        require(hits==2,"the 125-degree ground fan catches the near pair only, got "+hits);
        require(ahead.velocity.lengthSquared()>0.0,"a caught player receives a real push");
        require(edge.velocity.lengthSquared()>0.0,"the wide edge of the fan is still inside the arc");
        require(Math.abs(ahead.velocity.getY()-0.22D)<1.0E-9,"the fan push has a small lift");
        require(behind.velocity.lengthSquared()==0.0,"a player behind the frozen facing is never hit");
        require(far.velocity.lengthSquared()==0.0,"a player past the 5-block reach is never hit");
        require(high.velocity.lengthSquared()==0.0,"a player above the ground band is never hit");
        require(ahead.hurts==1&&behind.hurts==0,"the hurt flash follows accepted damage only");
    }

    void fanDodged(){
        players.clear();
        World world=new World();
        LivingEntity boss=new LivingEntity(world);
        Location origin=new Location(world,0,70,0);
        Vector facing=new Vector(0,0,1);
        Player ahead=player(world,0,70,4);
        damageLanded=false;
        int hits=resolveGuardianFan(boss,origin,facing);
        require(hits==0,"a refused damage event cannot count as a fan hit");
        require(ahead.velocity.lengthSquared()==0.0,"a refused damage event cannot move the player");
        require(ahead.hurts==0,"a refused damage event cannot flash hurt");
    }

    long heavy(boolean landed){
        players.clear();
        World world=new World();
        LivingEntity boss=new LivingEntity(world);
        Location origin=new Location(world,0,70,0);
        Vector facing=new Vector(0,0,1);
        Player ahead=player(world,0,70,3);
        Player side=player(world,2.2,70,1.5);
        Player far=player(world,0,70,5.5);
        Player behind=player(world,0,70,-2);
        guardianMoveEndAtMillis=0;nextGuardianPhysicalMillis=0;damageLanded=landed;
        int hits=resolveGuardianHeavy(boss,origin,facing);
        if(landed){
            require(hits==1,"the narrow heavy wedge catches only the frontal target, got "+hits);
            require(ahead.velocity.lengthSquared()>0.0,"a heavy hit pushes its victim");
            require(side.velocity.lengthSquared()==0.0,"the side of the heavy wedge is a real dodge");
            require(far.velocity.lengthSquared()==0.0,"the heavy cannot reach past 5 blocks");
            require(behind.velocity.lengthSquared()==0.0,"the heavy cannot hit behind the boss");
        }else{
            require(hits==0,"an immune heavy hit must not count");
            require(ahead.velocity.lengthSquared()==0.0,"an immune heavy hit must not shove");
        }
        require(nextGuardianPhysicalMillis>0,"a heavy always commits a recovery clock");
        return nextGuardianPhysicalMillis;
    }

    public static void main(String[] args){
        GuardianMeleeProbe probe=new GuardianMeleeProbe();
        switch(args[0]){
            case "fan" -> probe.fan();
            case "fan-dodged" -> probe.fanDodged();
            case "heavy" -> probe.heavy(true);
            case "heavy-dodged" -> probe.heavy(false);
            default -> throw new AssertionError("unknown scenario "+args[0]);
        }
        // Punish contract: a heavy that found nobody must open the long window.
        if(args[0].startsWith("heavy")){
            GuardianMeleeProbe miss=new GuardianMeleeProbe();
            GuardianMeleeProbe hit=new GuardianMeleeProbe();
            long missNext=miss.heavy(false);
            long hitNext=hit.heavy(true);
            require(missNext>hitNext,"a whiffed heavy must recover longer than a landed one");
            require(missNext-hitNext>=500L,
                    "the punish window must be a real, measurable delay");
        }
        System.out.println("PASS Guardian V5.1: fan arc, heavy wedge, vertical/range dodges, damage-gated push, punish window");
    }
}
"""
    path = tmp_path / "GuardianMeleeProbe.java"
    path.write_text(java, encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossPhase.java"),
         str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/GuardianV5MeleePolicy.java"),
         str(path)],
        text=True, capture_output=True, timeout=30, encoding="utf-8", errors="replace",
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    for scenario in ("fan", "fan-dodged", "heavy", "heavy-dodged"):
        run = subprocess.run(
            ["java", "-cp", str(tmp_path), "GuardianMeleeProbe", scenario],
            text=True, capture_output=True, timeout=15, encoding="utf-8", errors="replace",
        )
        assert run.returncode == 0, run.stdout + run.stderr
