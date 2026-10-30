"""Execute the production Wave 7 named-trial warning renderer, not a mirrored policy."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def extract_method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for pos in range(opening, len(source)):
        depth += (source[pos] == "{") - (source[pos] == "}")
        if depth == 0:
            return source[start:pos + 1]
    raise AssertionError("Unclosed production Java method")


def test_named_trial_warning_sound_fires_once_but_visual_updates_each_tick(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    production = extract_method(source, "private void renderNamedTrialTelegraph(")
    java = r"""
import java.util.*;
public class Wave7WarningAudioProbe {
    enum Particle { CRIT, ELECTRIC_SPARK, HAPPY_VILLAGER, SOUL_FIRE_FLAME, END_ROD, PORTAL, DRAGON_BREATH }
    enum Sound { BLOCK_AMETHYST_BLOCK_CHIME, BLOCK_BEACON_POWER_SELECT,
        ENTITY_EVOKER_PREPARE_ATTACK, ENTITY_ENDER_DRAGON_GROWL,
        ENTITY_ENDERMAN_STARE, ENTITY_SKELETON_SHOOT }
    static class Wave7NamedTrialActorPolicy {
        enum Attack { MARKSMAN_QUICK_SHOT, MARKSMAN_HEAVY_SHOT, MARKSMAN_SPREAD_SHOT,
            MARKSMAN_SUPPORT, ARCHMAGE_FANGS, ARCHMAGE_SPEAR, ARCHMAGE_STEP,
            ARCHMAGE_BEAM, NONE }
    }
    static class World {
        int sounds=0;
        void playSound(Location at, Sound sound, float volume, float pitch) {
            sounds++;
            if (volume <= 0 || pitch <= 0) throw new AssertionError("Invalid sound cue");
        }
    }
    static class Vector implements Cloneable {
        double x,y,z;
        Vector(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        Vector cloneVector(){return new Vector(x,y,z);}
        @Override public Vector clone(){return cloneVector();}
        double getX(){return x;}double getZ(){return z;}
        Vector multiply(double f){x*=f;y*=f;z*=f;return this;}
    }
    static class Location implements Cloneable {
        final World world;double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        @Override public Location clone(){return new Location(world,x,y,z);}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
        Location add(Vector vector){return add(vector.x,vector.y,vector.z);}
    }
    static class LivingEntity {
        final World world;final Location location;
        LivingEntity(World world){this.world=world;this.location=new Location(world,0,70,0);}
        Location getLocation(){return location.clone();}
        Location getEyeLocation(){return location.clone().add(0,1.6,0);}
        World getWorld(){return world;}
    }
    static final class Player extends LivingEntity { Player(World w){super(w);} }
    boolean valid=true;int visualFrames=0;
    boolean isCurrentRealitySplitTrialTarget(LivingEntity actor,Player target){return valid;}
    Vector horizontalDirection(Location a,Location b){return new Vector(1,0,0);}
    void faceRealitySplitTrialMob(LivingEntity mob,Vector direction){}
    void renderRealitySplitTrialTelegraph(Location a,Vector dir,double len,double width,Particle p){
        visualFrames++;
    }
    void spawnEventParticle(Location where,Particle p,int count,double x,double y,double z,double velocity){
        visualFrames++;
    }
""" + production + r"""
    static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    void verify(Wave7NamedTrialActorPolicy.Attack attack) {
        visualFrames=0; // Verify this attack's visuals, not a previous cast's frames.
        World world = new World();
        LivingEntity actor = new LivingEntity(world);
        Player target = new Player(world);
        renderNamedTrialTelegraph(actor,target,attack,true);
        for(int tick=0;tick<4;tick++) renderNamedTrialTelegraph(actor,target,attack,false);
        require(world.sounds==1,"Expected one warning sound, got "+world.sounds+" for "+attack);
        require(visualFrames>0,"Expected visible warning frames for "+attack);
        valid=false;
        renderNamedTrialTelegraph(actor,target,attack,true);
        require(world.sounds==1,"Invalid target must not emit sound: "+attack);
        valid=true;
    }
    public static void main(String[]args) {
        var runtime = new Wave7WarningAudioProbe();
        for(var a : List.of(
                Wave7NamedTrialActorPolicy.Attack.MARKSMAN_QUICK_SHOT,
                Wave7NamedTrialActorPolicy.Attack.MARKSMAN_HEAVY_SHOT,
                Wave7NamedTrialActorPolicy.Attack.MARKSMAN_SPREAD_SHOT,
                Wave7NamedTrialActorPolicy.Attack.MARKSMAN_SUPPORT,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_FANGS,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_SPEAR,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_STEP,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_BEAM)) {
            runtime.verify(a);
        }
        System.out.println("PASS 8 named trial attacks: one sound on cast, repeated visual warning");
    }
}
"""
    path = tmp_path / "Wave7WarningAudioProbe.java"
    path.write_text(java, encoding="utf-8")
    compile = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(path)],
        text=True, capture_output=True, timeout=30, encoding="utf-8", errors="replace",
    )
    assert compile.returncode == 0, compile.stdout + compile.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "Wave7WarningAudioProbe"],
        text=True, capture_output=True, timeout=15, encoding="utf-8", errors="replace",
    )
    assert run.returncode == 0, run.stdout + run.stderr


def test_named_trial_audio_cue_is_tied_to_successful_cast_reservation():
    source = SOURCE.read_text(encoding="utf-8")
    tick = extract_method(source, "private boolean tickNamedTrialActor(")
    assert "if (!writeNamedTrialActorCheckpoint(actor, started)) return false;" in tick
    assert "renderNamedTrialTelegraph(actor, target, attack, true);" in tick
    assert "renderNamedTrialTelegraph(actor, target, checkpoint.attack(), false);" in tick
