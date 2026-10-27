"""Run the production crystal projectile adapter with a small simulated world."""

from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
SOURCE_ROOT = ROOT / "copimine-end-event/src"


def _block(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for cursor in range(opening, len(source)):
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        if depth == 0:
            return source[start : cursor + 1]
    raise AssertionError(f"unterminated block: {signature}")


def test_physical_crystal_projectiles_charge_only_on_confirmed_impact(tmp_path):
    source = SOURCE.read_text(encoding="utf-8")
    blocks = "\n".join(_block(source, signature) for signature in (
        "private static final class Wave6CrystalBarrage",
        "private boolean wave6AnchorCastValid(",
        "private void releaseWave6CrystalShot(",
        "private void clearWave6CrystalBarrage(",
        "public void onWave6CrystalBarrageDamage(",
        "public void onWave6CrystalBarrageImpact(",
        "private boolean handleWave6CrystalBarrageCollision(",
        "public boolean fireRiftBarrageIntoCrystal(",
        "public GravityAnchorBarrageProgress gravityAnchorBarrageProgress(",
    ))
    probe = tmp_path / "Wave6CrystalProjectileProbe.java"
    probe.write_text(r'''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.*;
import me.copimine.endevent.runtime.ritual.RitualSpellController;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.GravityAnchorCast;
import me.copimine.endevent.runtime.ritual.Wave6RitualRuntimeAdapter.GravityAnchorBarrageProgress;

public class Wave6CrystalProjectileProbe {
    enum Particle { PORTAL, SOUL_FIRE_FLAME }
    enum Sound { ENTITY_ENDER_DRAGON_SHOOT }
    enum SoundCategory { HOSTILE }
    static final class Vector implements Cloneable {
        double x, y, z;
        Vector(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
        Vector subtract(Vector other) { x-=other.x; y-=other.y; z-=other.z; return this; }
        double lengthSquared() { return x*x+y*y+z*z; }
        Vector normalize() { double n=Math.sqrt(lengthSquared()); x/=n; y/=n; z/=n; return this; }
        Vector multiply(double n) { x*=n; y*=n; z*=n; return this; }
        public Vector clone() { return new Vector(x,y,z); }
        Location toLocation(World world) { return new Location(world, x,y,z); }
    }
    static final class Location {
        World world; double x,y,z;
        Location(World world,double x,double y,double z) { this.world=world; this.x=x; this.y=y; this.z=z; }
        World getWorld() { return world; }
        Vector toVector() { return new Vector(x,y,z); }
    }
    static final class Box {
        final Vector center;
        Box(Vector center) { this.center=center; }
        Vector getCenter() { return center.clone(); }
    }
    static class Entity {
        final UUID id = UUID.randomUUID();
        final World world; Location location; boolean valid=true;
        String ownerEvent="event-one"; long ownerGeneration=7;
        final Set<String> tags=new HashSet<>();
        Entity(World world, Location location) { this.world=world; this.location=location; }
        UUID getUniqueId() { return id; }
        World getWorld() { return world; }
        Location getLocation() { return location; }
        boolean addScoreboardTag(String value) { return tags.add(value); }
        Set<String> getScoreboardTags() { return tags; }
    }
    static class LivingEntity extends Entity {
        LivingEntity(World world, Location location) { super(world,location); }
    }
    static final class Player extends LivingEntity {
        boolean online=true, dead=false, free=true;
        Player(World world,Location location) { super(world,location); }
        boolean isOnline() { return online; }
        boolean isDead() { return dead; }
    }
    static final class Bukkit {
        static final Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id) { return players.get(id); }
    }
    static class Projectile extends Entity {
        Projectile(World world, Location location) { super(world,location); }
    }
    static final class Snowball extends Projectile {
        boolean gravity=true, persistent=true; LivingEntity shooter; Vector velocity;
        Snowball(World world, Location location) { super(world,location); }
        void setGravity(boolean value) { gravity=value; }
        void setPersistent(boolean value) { persistent=value; }
        void setShooter(LivingEntity value) { shooter=value; }
        void setVelocity(Vector value) { velocity=value; }
    }
    static final class EnderCrystal extends Entity {
        EnderCrystal(World world, Location location) { super(world,location); }
        Box getBoundingBox() { return new Box(new Vector(location.x,location.y+1,location.z)); }
    }
    static final class World {
        int spawned; int failAt = -1;
        final List<Snowball> shots = new ArrayList<>();
        <T> T spawn(Location loc, Class<T> type) {
            if (++spawned == failAt) throw new IllegalStateException("synthetic spawn failure");
            Snowball shot = new Snowball(this, loc); shots.add(shot); return type.cast(shot);
        }
        void spawnParticle(Particle p, Location l, int count, double... options) { }
        void playSound(Location l, Sound s, SoundCategory c, float volume, float pitch) { }
    }
    static final class ProjectileHitEvent {
        final Projectile projectile; final Entity hit; final boolean cancelled;
        ProjectileHitEvent(Projectile p, Entity h, boolean c) { projectile=p; hit=h; cancelled=c; }
        Projectile getEntity() { return projectile; }
        Entity getHitEntity() { return hit; }
        boolean isCancelled() { return cancelled; }
    }
    static final class ProjectileCollideEvent {
        final Projectile projectile; final Entity target; boolean cancelled;
        ProjectileCollideEvent(Projectile p,Entity target) { projectile=p; this.target=target; }
        Projectile getEntity() { return projectile; }
        Entity getCollidedWith() { return target; }
        void setCancelled(boolean value) { cancelled=value; }
    }
    static final class EntityDamageByEntityEvent {
        final Entity damager; boolean cancelled;
        EntityDamageByEntityEvent(Entity damager) { this.damager=damager; }
        Entity getDamager() { return damager; }
        void setCancelled(boolean value) { cancelled=value; }
    }
    final World world = new World();
    final Location origin = new Location(world,0,70,0);
    final LivingEntity caster = new LivingEntity(world, origin);
    final Player target = new Player(world, origin);
    final EnderCrystal crystal = new EnderCrystal(world, new Location(world,0,70,10));
    final Map<UUID,Entity> ownedEntities = new HashMap<>();
    final Map<UUID,Wave6CrystalBarrage> wave6CrystalBarrages = new HashMap<>();
    final Map<UUID,Wave6CrystalBarrage> wave6CrystalBarrageShots = new HashMap<>();
    final WaveProjectileAdmissionController waveProjectileAdmission = new WaveProjectileAdmissionController();
    final GravityAnchorCast cast = new GravityAnchorCast(7, caster.id, target.id,
            new GravityAnchorPolicy.AnchorPoint(0,70,10), crystal.id, 2);
    final RitualSpellController ritualSpellController = new RitualSpellController(7);
    final String EVENT_KIND_PROJECTILE = "projectile";
    final String WAVE6_CRYSTAL_CHARGE_TAG = "copimine_wave6_crystal_charge";
    String eventId = "event-one"; long generation=7; boolean valid=true;
    Wave6CrystalProjectileProbe() {
        ownedEntities.put(caster.id,caster); ownedEntities.put(crystal.id,crystal); Bukkit.players.put(target.id,target);
        ritualSpellController.startGravityAnchor(7,caster.id,target.id,cast.fixedCrystalPoint(),2,
                true,true,true,true,0);
        ritualSpellController.gravityAnchorCrystalSpawned(7,crystal.id,0);
    }
    Location coreCombatAnchorLocation() { return origin; }
    Location ritualSphereCenter(Location ignored) { return origin; }
    EnderCrystal anchorCrystal(GravityAnchorCast ignored) { return crystal.valid ? crystal : null; }
    boolean ritualWaveActive() { return valid; }
    boolean isCurrentRitualCaster(Entity value) { return value == caster && value.valid; }
    boolean isArenaLocation(Location value) { return value != null; }
    boolean isFreeRitualTarget(Player value) { return value.free; }
    boolean hasProjectileBlockLineOfSight(Location from,Location to) { return true; }
    boolean reserveWaveProjectileGroup(UUID id,int count) { return waveProjectileAdmission.reserveGroup(id,2,count); }
    boolean trackWaveProjectile(Entity entity,UUID id) { return waveProjectileAdmission.trackProjectile(id,entity.id); }
    void tag(Entity entity,String kind,int wave,boolean official) { ownedEntities.put(entity.id,entity); }
    boolean isOfficialEntity(Entity ignored) { return true; }
    boolean isLiveOwnedEntity(UUID id) { return ownedEntities.containsKey(id) && ownedEntities.get(id).valid; }
    boolean ownedBySession(Entity e,String id,long owner) {
        return e.valid && owner==e.ownerGeneration && Objects.equals(e.ownerEvent,id);
    }
    void removeRitualEntity(UUID id) { Entity e=ownedEntities.remove(id); if (e!=null) e.valid=false; }
    Logger getLogger() { return Logger.getAnonymousLogger(); }
''' + blocks + r'''
    static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var p = new Wave6CrystalProjectileProbe();
        check(!p.fireRiftBarrageIntoCrystal(p.cast,3), "more than two shots were admitted");
        check(p.fireRiftBarrageIntoCrystal(p.cast,2) && p.world.shots.size()==2, "two physical shots not launched");
        var first=p.world.shots.get(0); var second=p.world.shots.get(1);
        check(first.getLocation()==p.origin && second.getLocation()==p.origin && !first.gravity && !first.persistent
                && first.shooter==p.caster && first.velocity.z>0
                && first.velocity.lengthSquared()<=Math.pow(RitualSphereProjectilePolicy.MAX_INITIAL_SPEED,2)+1e-10,
                "shots must start at sphere and use bounded, nonhoming velocity");
        var player=new LivingEntity(p.world,p.origin);
        var collision=new ProjectileCollideEvent(first,player);
        check(p.handleWave6CrystalBarrageCollision(collision) && collision.cancelled,
                "player or prisoner collision was not excluded");
        var damage=new EntityDamageByEntityEvent(first); p.onWave6CrystalBarrageDamage(damage);
        check(damage.cancelled, "charging projectile must not deal incidental damage");
        collision=new ProjectileCollideEvent(first,p.crystal);
        check(p.handleWave6CrystalBarrageCollision(collision) && !collision.cancelled,
                "the owning crystal must remain a legal collision");
        p.onWave6CrystalBarrageImpact(new ProjectileHitEvent(first,p.crystal,false));
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.PENDING,
                "one of two impacts must not finish charging");
        p.onWave6CrystalBarrageImpact(new ProjectileHitEvent(second,p.crystal,false));
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.CHARGED
                && p.waveProjectileAdmission.activeProjectileCount()==0,
                "confirmed impacts did not charge or release the physical shots");
        p.clearWave6CrystalBarrage(p.crystal.id);
        check(p.wave6CrystalBarrages.isEmpty(), "completed cast retained its resources");
        damage=new EntityDamageByEntityEvent(second); p.onWave6CrystalBarrageDamage(damage);
        check(damage.cancelled, "a delayed damage event escaped after cast cleanup");

        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,2);
        p.onWave6CrystalBarrageImpact(new ProjectileHitEvent(p.world.shots.get(0),null,false));
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "block impact was treated as a charge");
        p.clearWave6CrystalBarrage(p.crystal.id);
        check(p.waveProjectileAdmission.activeProjectileCount()==0 && p.wave6CrystalBarrageShots.isEmpty(),
                "failed cast leaked a physical child or reservation");

        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,1); p.valid=false;
        p.onWave6CrystalBarrageImpact(new ProjectileHitEvent(p.world.shots.get(0),p.crystal,false));
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "invalid caster, target or crystal still charged");
        p.clearWave6CrystalBarrage(p.crystal.id);

        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,1); p.target.online=false;
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "offline target remained valid");
        p.clearWave6CrystalBarrage(p.crystal.id);
        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,1); p.caster.valid=false;
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "dead or removed caster remained valid");
        p.clearWave6CrystalBarrage(p.crystal.id);
        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,1); p.crystal.valid=false;
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "removed crystal remained valid");
        p.clearWave6CrystalBarrage(p.crystal.id);
        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,1); p.eventId="other-event";
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "a different event inherited the cast");
        p.clearWave6CrystalBarrage(p.crystal.id);
        p=new Wave6CrystalProjectileProbe(); p.fireRiftBarrageIntoCrystal(p.cast,1); p.generation++;
        check(p.gravityAnchorBarrageProgress(p.cast)==GravityAnchorBarrageProgress.FAILED,
                "a different generation inherited the cast");
        p.clearWave6CrystalBarrage(p.crystal.id);

        p=new Wave6CrystalProjectileProbe(); p.world.failAt=2;
        check(!p.fireRiftBarrageIntoCrystal(p.cast,2) && p.wave6CrystalBarrageShots.isEmpty()
                && p.waveProjectileAdmission.activeProjectileCount()==0,
                "partial spawn failure leaked an owned shot or reservation");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(SOURCE_ROOT),
                             "-d", str(tmp_path), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave6CrystalProjectileProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
