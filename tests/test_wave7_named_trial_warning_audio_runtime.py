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
import me.copimine.endevent.domain.ArchmageV5CombatPolicy;
public class Wave7WarningAudioProbe {
    enum Particle { CRIT, ELECTRIC_SPARK, HAPPY_VILLAGER, SOUL_FIRE_FLAME, END_ROD, PORTAL, DRAGON_BREATH }
    enum Sound { BLOCK_AMETHYST_BLOCK_CHIME, BLOCK_BEACON_POWER_SELECT,
        ENTITY_EVOKER_PREPARE_ATTACK, ENTITY_ENDER_DRAGON_GROWL,
        ENTITY_ENDERMAN_STARE, ENTITY_SKELETON_SHOOT,
        BLOCK_RESPAWN_ANCHOR_CHARGE, ITEM_TRIDENT_RETURN }
    static class Wave7NamedTrialActorPolicy {
        enum Attack { MARKSMAN_QUICK_SHOT, MARKSMAN_HEAVY_SHOT, MARKSMAN_SPREAD_SHOT,
            MARKSMAN_SUPPORT, ARCHMAGE_FANGS, ARCHMAGE_SPEAR, ARCHMAGE_STEP,
            ARCHMAGE_BEAM, ARCHMAGE_CIRCLE, ARCHMAGE_CROWN, ARCHMAGE_REPULSE, NONE }
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
        Vector subtract(Vector o){return new Vector(x-o.x,y-o.y,z-o.z);}
        double lengthSquared(){return x*x+y*y+z*z;}
        Vector setY(double v){y=v;return this;}
        Vector normalize(){double l=Math.sqrt(x*x+y*y+z*z);
            if(l<1.0E-8D){x=0;y=0;z=0;}else{x/=l;y/=l;z/=l;}return this;}
    }
    static class Location implements Cloneable {
        final World world;double x,y,z;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        @Override public Location clone(){return new Location(world,x,y,z);}
        Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
        Location add(Vector vector){return add(vector.x,vector.y,vector.z);}
        void setY(double y){this.y=y;}
        double getY(){return y;}
        double getYaw(){return 0.0D;}
        Vector toVector(){return new Vector(x,y,z);}
        Vector getDirection(){return new Vector(0.0D,0.0D,1.0D);}
    }
    static class LivingEntity {
        final World world;final Location location;
        final UUID id=UUID.randomUUID();
        LivingEntity(World world){this.world=world;this.location=new Location(world,0,70,0);}
        Location getLocation(){return location.clone();}
        Location getEyeLocation(){return location.clone().add(0,1.6,0);}
        World getWorld(){return world;}
        UUID getUniqueId(){return id;}
    }
    static final class Player extends LivingEntity { Player(World w){super(w);} }
    // The frozen aim lease the production renderer resolves fang victims from.
    record TrialAimLock(long generation,String event,long cast,Location point,Location floor,Location casterOrigin,Map<UUID,Location> fangMarks) { }
    final String eventId="event-7";
    final long generation=7;
    final Map<UUID,TrialAimLock> trialAimLocks=new HashMap<>();
    boolean valid=true;int visualFrames=0;long eventTickCounter;
    int wave7FrozenRosterSize(){return 10;}
    boolean isCurrentRealitySplitTrialTarget(LivingEntity actor,Player target){return valid;}
    Vector horizontalDirection(Location a,Location b){return new Vector(1,0,0);}
    void faceRealitySplitTrialMob(LivingEntity mob,Vector direction){}
    // The arm helper mirrors lockNamedTrialAim's shape: the authored
    // ARCHMAGE_FANGS pass renders only the marks frozen at the telegraph.
    void armTrialAim(LivingEntity actor,Player target,boolean withFangs){
        Map<UUID,Location> marks=new LinkedHashMap<>();
        if(withFangs) marks.put(target.getUniqueId(),target.getLocation().clone());
        trialAimLocks.put(actor.getUniqueId(),
                new TrialAimLock(generation,eventId,1L,target.getEyeLocation().clone(),
                        target.getLocation(),actor.getLocation(),Map.copyOf(marks)));
    }
    List<Location> namedTrialCirclePoints(LivingEntity actor,TrialAimLock lock){
        return List.of(lock.floor().clone());
    }
    List<Location> namedTrialCrownPoints(LivingEntity actor,Location base,int step){
        return List.of(base.clone());
    }
    void publishTrialFangCast(LivingEntity actor,Location at,String variant,List<Location> jaws,
            int warning,int active){}
    Location namedTrialAim(LivingEntity actor,Player target){
        TrialAimLock lock=trialAimLocks.get(actor.getUniqueId());
        return lock!=null && lock.generation()==generation && Objects.equals(lock.event(),eventId)
                ? lock.point().clone() : target.getEyeLocation().clone();
    }
    void renderNamedTrialAim(LivingEntity actor,Location origin,Vector direction,
            double length,double width,Particle particle){
        Vector side=new Vector(-direction.getZ(),0,direction.getX());
        for(int step=1;step<=12;step++){
            Location center=origin.clone().add(direction.clone().multiply(length*step/12D));
            emitTrialParticle(actor,center.clone().add(side.clone().multiply(width)),particle,1,0,0,0,0);
        }
    }
    void emitTrialParticle(LivingEntity actor,Location at,Particle particle,int count,
            double x,double y,double z,double speed){
        visualFrames++;
    }
    // Sound routing stays on the one World counter the assertions read, so the
    // "fires once" claim still measures real playWarningSound gating.
    void playTrialSound(LivingEntity actor,Location at,Sound sound,float volume,float pitch){
        actor.getWorld().playSound(at,sound,volume,pitch);
    }
    // The V5.1 authored-cast cue is presentation-only, so the probe records it as
    // a no-op; the "one sound, many visual frames" claim still measures real
    // particle/sound gating inside the sliced production renderer.
    static long cueSeedFor(UUID id){return id==null?0L:id.getMostSignificantBits()^id.getLeastSignificantBits();}
    void broadcastSpellVisualCue(String token,long seed,Location anchor,Location reach,int colorRgb){}
""" + production + r"""
    static void require(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    void verify(Wave7NamedTrialActorPolicy.Attack attack) {
        visualFrames=0; // Verify this attack's visuals, not a previous cast's frames.
        World world = new World();
        LivingEntity actor = new LivingEntity(world);
        Player target = new Player(world);
        // The real flow arms the aim lock at the telegraph; the fang pass renders
        // only the marks frozen there, so an unarmed actor must draw nothing.
        armTrialAim(actor,target,attack==Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_FANGS);
        renderNamedTrialTelegraph(actor,target,attack,true);
        for(int tick=1;tick<=4;tick++){
            eventTickCounter=tick;
            renderNamedTrialTelegraph(actor,target,attack,false);
        }
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
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_BEAM,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_CIRCLE,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_CROWN,
                Wave7NamedTrialActorPolicy.Attack.ARCHMAGE_REPULSE)) {
            runtime.verify(a);
        }
        System.out.println("PASS 11 named trial attacks: one sound on cast, repeated visual warning");
    }
}
"""
    path = tmp_path / "Wave7WarningAudioProbe.java"
    path.write_text(java, encoding="utf-8")
    compile = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
         str(ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ArchmageV5CombatPolicy.java"),
         str(path)],
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
    # SUPERSEDED: the method's parameter list wraps onto a second line, so the
    # single-line signature stopped matching. Anchor on the name only.
    tick = extract_method(source, "private boolean tickNamedTrialActor(LivingEntity actor,")
    assert "if (!writeNamedTrialActorCheckpoint(actor, started)) return false;" in tick
    assert "renderNamedTrialTelegraph(actor, target, attack, true);" in tick
    assert "renderNamedTrialTelegraph(actor, target, checkpoint.attack(), false);" in tick
