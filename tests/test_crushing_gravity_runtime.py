"""Execute the production Crushing Gravity field adapter, not a mirrored policy.

The probe slices the real ``applyCrushingGravityPull`` / ``releaseCrushingGravityPull``
/ ``endCrushingGravityStateOnly`` bodies and drives them against a stub world that
records the velocities the plugin actually writes. That is the only way to prove
the field moves real arrows and real player motion instead of describing it.
"""
from pathlib import Path
import subprocess

from tests.test_wave7_named_trial_warning_audio_runtime import extract_method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def test_crushing_gravity_moves_real_players_and_arrows_and_restores(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    production = "\n".join(extract_method(source, signature) for signature in (
        "private void applyCrushingGravityPull(",
        "private boolean gravityAffectsEntity(",
        "private void releaseCrushingGravityPull(",
        "private void endCrushingGravityStateOnly(",
    ))
    production = (production.replace("org.bukkit.util.BoundingBox", "BoundingBox")
                  .replace("org.bukkit.entity.AbstractArrow", "AbstractArrow")
                  .replace("org.bukkit.entity.SpectralArrow", "SpectralArrow"))
    java = r"""
import java.util.*;
import me.copimine.endevent.domain.CrushingGravityPolicy;

public class CrushingGravityProbe {
    static final class Vector implements Cloneable {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        @Override public Vector clone(){return new Vector(x,y,z);}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        void setX(double value){x=value;}void setY(double value){y=value;}void setZ(double value){z=value;}
    }
    static final class Location {
        final World world;double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        @Override public Location clone(){return new Location(world,x,y,z);}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
    }
    static class Entity {
        final UUID id=UUID.randomUUID();final World world;
        boolean valid=true,dead,onGround;
        double x,y,z;Vector velocity=new Vector(0,0,0);
        Entity(World world){this.world=world;}
        UUID getUniqueId(){return id;}boolean isValid(){return valid&&!dead;}boolean isDead(){return dead;}
        boolean isOnGround(){return onGround;} World getWorld(){return world;}
        double getX(){return x;}double getY(){return y;}double getZ(){return z;}
        Location getLocation(){return new Location(world,x,y,z);}
        Vector getVelocity(){return velocity.clone();}
        void setVelocity(Vector value){velocity=value.clone();}
        void place(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
    }
    static final class Player extends Entity {Player(World w){super(w);}}
    static class AbstractArrow extends Entity {
        Player shooter;
        AbstractArrow(World w){super(w);}
        Player getShooter(){return shooter;}
        boolean isInBlock(){return onGround;}
    }
    static final class Arrow extends AbstractArrow {Arrow(World w){super(w);}}
    static final class SpectralArrow extends AbstractArrow {SpectralArrow(World w){super(w);}}
    record BoundingBox(double xMin,double yMin,double zMin,double xMax,double yMax,double zMax) {}
    static final class World {
        final List<Entity> entities=new ArrayList<>();
        List<Entity> getNearbyEntities(BoundingBox box,java.util.function.Predicate<Entity> filter){
            return entities.stream().filter(filter).toList();
        }
    }
    // --- production fields the sliced bodies touch ---------------------------
    CrushingGravityPolicy.Bounds crushingGravityBounds;
    World crushingGravityWorld;
    long crushingGravityGeneration=Long.MIN_VALUE;
    long crushingGravityStartMillis=Long.MIN_VALUE/2L;
    long crushingGravityReadyAtMillis;
    final Set<UUID> crushingGravityArrows=new HashSet<>();
    final Set<UUID> crushingGravityPlayers=new HashSet<>();
    final List<Player> players=new ArrayList<>();
    List<Player> activeLivingPlayers(){return players;}
    boolean isActiveBossParticipant(Player player){return players.contains(player);}
    boolean isArenaLocation(Location location){return location.getX() != 12;}

""" + production + r"""
    static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static void close(double actual,double expected,String what){
        if(Math.abs(actual-expected)>1.0E-6)throw new AssertionError(what+": expected "+expected+", got "+actual);
    }

    public static void main(String[] args){
        CrushingGravityProbe p=new CrushingGravityProbe();
        World world=new World();
        p.crushingGravityBounds=new CrushingGravityPolicy.Bounds(0,-10,0,30,30,30);
        p.crushingGravityWorld=world;
        p.crushingGravityGeneration=7L;
        p.crushingGravityStartMillis=1_000L;

        Player airborne=new Player(world);airborne.place(5,5,5);airborne.onGround=false;
        airborne.velocity=new Vector(0.30,0.10,0.20);
        Player jumping=new Player(world);jumping.place(6,5,6);jumping.onGround=true;
        jumping.velocity=new Vector(0.0,0.40,0.0);
        Player outside=new Player(world);outside.place(100,5,5);outside.velocity=new Vector(0,0.10,0);
        Arrow inArrow=new Arrow(world);inArrow.place(7,7,7);inArrow.velocity=new Vector(2.0,0.5,0.0);
        Arrow outArrow=new Arrow(world);outArrow.place(100,7,7);outArrow.velocity=new Vector(2.0,0.5,0.0);
        SpectralArrow spectral=new SpectralArrow(world);spectral.place(9,7,7);spectral.velocity=new Vector(2,0.5,0);
        inArrow.shooter=airborne; outArrow.shooter=airborne; spectral.shooter=airborne;
        Player shelter=new Player(world);shelter.place(12,5,12);shelter.velocity=new Vector(0,.1,0);
        p.players.addAll(List.of(airborne,jumping,outside,shelter));
        world.entities.addAll(List.of(inArrow,outArrow,spectral));

        p.applyCrushingGravityPull();
        close(airborne.velocity.getY(),0.10-CrushingGravityPolicy.PLAYER_DOWNWARD_ACCELERATION,
                "an airborne player is pulled down by the real field");
        close(jumping.velocity.getY(),0.40*CrushingGravityPolicy.JUMP_IMPULSE_SCALE,
                "a jump is weighed down, never removed");
        close(outside.velocity.getY(),0.10,"a player outside the frozen bounds is untouched");
        close(shelter.velocity.getY(),0.10,"a protected cell inside the AABB is untouched");
        close(inArrow.velocity.getY(),0.5-0.05*(CrushingGravityPolicy.ARROW_GRAVITY_MULTIPLIER-1.0D),
                "a real arrow inside the field falls harder but keeps flying");
        close(inArrow.velocity.getX(),2.0*(1.0D-CrushingGravityPolicy.ARROW_INFLIGHT_BLEED),
                "an in-flight arrow bleeds horizontal speed once");
        close(outArrow.velocity.getY(),0.5,"an arrow outside the bounds is untouched");
        require(p.crushingGravityPlayers.size()==2,"only in-bounds players enter the restore ledger");
        close(spectral.velocity.getY(),inArrow.velocity.getY(),"spectral arrows use the same real physics");
        require(p.crushingGravityArrows.size()==2,"only in-bounds arrows enter the restore ledger");

        p.applyCrushingGravityPull();
        close(inArrow.velocity.getX(),2.0*(1.0D-CrushingGravityPolicy.ARROW_INFLIGHT_BLEED),
                "the horizontal bleed is single-shot, not a per-tick drain");
        close(inArrow.velocity.getY(),0.5-2.0*0.05*(CrushingGravityPolicy.ARROW_GRAVITY_MULTIPLIER-1.0D),
                "the heavier fall keeps accelerating real arrows");

        p.releaseCrushingGravityPull();
        require(p.crushingGravityArrows.isEmpty()&&p.crushingGravityPlayers.isEmpty(),
                "release stops touching every tracked entity");

        p.endCrushingGravityStateOnly();
        require(p.crushingGravityBounds==null,"the frozen footprint is dropped on restore");
        require(p.crushingGravityWorld==null,"the frozen world is dropped on restore");
        require(p.crushingGravityGeneration==Long.MIN_VALUE,"a stale generation cannot revive the field");
        require(p.crushingGravityReadyAtMillis==0L,"a full reset clears the cooldown clock");
        System.out.println("PASS 23 Crushing Gravity: real player/arrow motion, one-shot bleed, clean restore");
    }
}
"""
    path = tmp_path / "CrushingGravityProbe.java"
    path.write_text(java, encoding="utf-8")
    compile_result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/BossPhase.java"),
         str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/CrushingGravityPolicy.java"),
         str(path)],
        text=True, capture_output=True, timeout=30, encoding="utf-8", errors="replace",
    )
    assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "CrushingGravityProbe"],
        text=True, capture_output=True, timeout=15, encoding="utf-8", errors="replace",
    )
    assert run.returncode == 0, run.stdout + run.stderr
