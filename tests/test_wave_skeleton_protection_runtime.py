"""Execute the real normal-zombie protection adapter against detached Paper entities."""

from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/WaveSkeletonProtectionPolicy.java"


def declaration(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def protection_probe(tmp_path_factory):
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(declaration(source, signature) for signature in (
        "private void respondToWaveSkeletonDamage(",
        "private int activeWaveSkeletonProtectionCount(",
        "private Player activeWaveSkeletonProtectionTarget(",
        "private void releaseWaveSkeletonProtection(",
    ))
    directory = tmp_path_factory.mktemp("wave-skeleton-protection")
    fixture = directory / "WaveSkeletonProtectionProbe.java"
    fixture.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.WaveSkeletonProtectionPolicy;
public class WaveSkeletonProtectionProbe {
    static final String EVENT_KIND_WAVE_MOB = "WAVE_MOB";
    static final Object keyKind = new Object(), keyWave = new Object();
    String eventId = "attempt-a";
    long generation = 4;
    int activeWave = 3;
    Map<UUID,Entity> ownedEntities = new LinkedHashMap<>();
    Map<UUID,WaveSkeletonProtectionLease> waveSkeletonProtectionLeases = new HashMap<>();
    Map<UUID,Long> wavePlayerAggroUntil = new HashMap<>(), nextWavePathRequestMillis = new HashMap<>();
    Map<UUID,Location> waveLastPathDestinations = new HashMap<>();
    record WaveSkeletonProtectionLease(UUID skeletonId, UUID targetId, String eventId,
                                       long generation, int wave, long expiresAtMillis) { }
    static class World {
        final String id; World(String id){this.id=id;}
        public boolean equals(Object other){return other instanceof World w && id.equals(w.id);}
    }
    static class Location {
        double x,y,z; World world;
        Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
        World getWorld(){return world;}
        double distanceSquared(Location b){double dx=x-b.x,dy=y-b.y,dz=z-b.z;return dx*dx+dy*dy+dz*dz;}
        public Location clone(){return new Location(world,x,y,z);}
    }
    static class Entity {
        UUID id=UUID.randomUUID(); Location loc; String kind=EVENT_KIND_WAVE_MOB;
        int wave=3; long generation=4; String event="attempt-a";
        boolean live=true, giant, ritualGuard, ritualCaster;
        Entity(Location loc){this.loc=loc;}
        UUID getUniqueId(){return id;} Location getLocation(){return loc;} World getWorld(){return loc.world;}
    }
    static class Pathfinder {
        int stops; boolean active;
        boolean hasPath(){return active;}
        void stopPathfinding(){stops++;active=false;}
    }
    static class Player extends Entity {
        boolean eligible=true;
        Player(Location loc){super(loc);}
    }
    static class Mob extends Entity {
        Player target; Pathfinder path=new Pathfinder();
        Mob(Location loc){super(loc);}
        Player getTarget(){return target;} void setTarget(Player p){target=p;}
        Pathfinder getPathfinder(){return path;}
    }
    static class Zombie extends Mob { Zombie(Location loc){super(loc);} }
    static class Skeleton extends Mob { Skeleton(Location loc){super(loc);} }
    static class Logger { void info(String text){} }
    Logger getLogger(){return new Logger();}
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id){return players.get(id);}
    }
    String readString(Entity e,Object key){return e.kind;}
    int readInt(Entity e,Object key,int fallback){return e.wave==0?fallback:e.wave;}
    boolean isRiftGiant(Entity e){return e.giant;}
    boolean isCurrentRitualGuard(Entity e){return e.ritualGuard;}
    boolean isCurrentRitualCaster(Entity e){return e.ritualCaster;}
    boolean isLiveOwnedEntity(UUID id){Entity e=ownedEntities.get(id);return e!=null&&e.live;}
    boolean ownedBySession(Entity e,String expectedEvent,long expectedGeneration){
        return e!=null&&Objects.equals(e.event,expectedEvent)&&e.generation==expectedGeneration;
    }
    boolean isCombatTarget(Player p){return p!=null&&p.eligible;}
    boolean isWaveTargetAllowed(Entity e,Player p){return isCombatTarget(p)&&e.getWorld().equals(p.getWorld());}
    METHODS
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static World world=new World("world");
    static Location at(double x,double z){return new Location(world,x,64,z);}
    static Player player(double x,double z){return new Player(at(x,z));}
    static Zombie zombie(WaveSkeletonProtectionProbe f,double x,double z){Zombie zmb=new Zombie(at(x,z));f.ownedEntities.put(zmb.id,zmb);return zmb;}
    static void mainScenario(String scenario){
        var f=new WaveSkeletonProtectionProbe();
        UUID skeleton=UUID.randomUUID(); Location skeletonAt=at(0,0);
        Skeleton skeletonMob=new Skeleton(skeletonAt); skeletonMob.id=skeleton;
        f.ownedEntities.put(skeleton,skeletonMob);
        Player first=player(1,0), second=player(-1,0); Bukkit.players.put(first.id,first);Bukkit.players.put(second.id,second);
        Zombie near=zombie(f,2,0), near2=zombie(f,0,3), far=zombie(f,4,0), outside=zombie(f,9,0);
        switch(scenario){
            case "nearest-and-cap" -> {
                long before=System.currentTimeMillis();
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,2.0D);
                check(near.target==first&&near2.target==first,"two nearest zombies target the accepted hitter");
                check(far.target==null&&outside.target==null,"additional and out-of-range zombies stay unassigned");
                check(f.waveSkeletonProtectionLeases.size()==2,"response cap is exactly two");
                for(var lease:f.waveSkeletonProtectionLeases.values())
                    check(lease.expiresAtMillis()>=before+6_900&&lease.expiresAtMillis()<=before+7_100,
                        "response deadline is seven seconds from accepted damage");
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,second,2.0D);
                check(f.waveSkeletonProtectionLeases.size()==2&&near.target==first&&near2.target==first,
                    "later hits cannot swap or add responders during the seven-second commitment");
            }
            case "expires-and-resumes" -> {
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,1.0D);
                var lease=f.waveSkeletonProtectionLeases.get(near.id);
                check(f.activeWaveSkeletonProtectionTarget(near,lease.expiresAtMillis()-1)==first,
                    "responding zombie retains its assigned target before expiry");
                var expired=new WaveSkeletonProtectionLease(lease.skeletonId(),lease.targetId(),lease.eventId(),
                    lease.generation(),lease.wave(),1L);
                f.waveSkeletonProtectionLeases.put(near.id,expired);
                check(f.activeWaveSkeletonProtectionTarget(near,1L)==null&&near.target==null,
                    "target and lease clear exactly at expiry so normal squad selection resumes");
            }
            case "reject-stale-and-invalid" -> {
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,0.0D);
                check(f.waveSkeletonProtectionLeases.isEmpty(),"zero applied damage cannot trigger response");
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"old-attempt",4,first,1.0D);
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,2,"attempt-a",4,first,1.0D);
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",3,first,1.0D);
                second.eligible=false;
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,second,1.0D);
                check(f.waveSkeletonProtectionLeases.isEmpty(),"stale attempts and ineligible attackers fail closed");
            }
            case "dead-skeleton" -> {
                skeletonMob.live=false;
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,1.0D);
                check(f.waveSkeletonProtectionLeases.isEmpty(),"a lethal hit cannot summon defenders after the Skeleton dies");
            }
            case "unregistered-skeleton" -> {
                f.ownedEntities.remove(skeleton);
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,1.0D);
                check(f.waveSkeletonProtectionLeases.isEmpty(),"a removed Skeleton cannot trigger delayed protection");
            }
            case "exclude-role-and-attempt-mismatches" -> {
                near.giant=true; near2.kind="ELITE"; far.ritualGuard=true; outside.wave=2;
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,1.0D);
                check(f.waveSkeletonProtectionLeases.isEmpty(),"giants, elites, ritual guards and other waves are excluded");
                var wrongWorld=zombie(f,1,0);wrongWorld.loc=new Location(new World("other"),1,64,0);
                wrongWorld.generation=5;
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,1.0D);
                check(!f.waveSkeletonProtectionLeases.containsKey(wrongWorld.id),"foreign generation or world never receives a target");
            }
            case "lock-other-skeleton" -> {
                near.live=false;near2.live=false;far.live=false;outside.live=false;
                var other=zombie(f,2,0); var available=zombie(f,3,0);
                var otherSkeleton=UUID.randomUUID();
                f.waveSkeletonProtectionLeases.put(other.id,new WaveSkeletonProtectionLease(
                    otherSkeleton,second.id,"attempt-a",4,3,System.currentTimeMillis()+7000));
                f.respondToWaveSkeletonDamage(skeleton,skeletonAt,3,"attempt-a",4,first,1.0D);
                check(other.target==null&&available.target==first,
                    "a zombie committed to another skeleton cannot be stolen by this response");
            }
        }
    }
    public static void main(String[] args){mainScenario(args[0]);System.out.println("WaveSkeletonProtectionProbe OK "+args[0]);}
}
'''.replace("METHODS", methods), encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(directory), str(POLICY), str(fixture)],
        capture_output=True, text=True, timeout=45,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", [
    "nearest-and-cap", "expires-and-resumes", "reject-stale-and-invalid",
    "dead-skeleton", "unregistered-skeleton",
    "exclude-role-and-attempt-mismatches", "lock-other-skeleton",
])
def test_real_wave_skeleton_protection_adapter(protection_probe, scenario):
    result = subprocess.run(
        ["java", "-cp", str(protection_probe), "WaveSkeletonProtectionProbe", scenario],
        capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_protection_is_triggered_only_after_authoritative_real_health_damage():
    source = SOURCE.read_text(encoding="utf-8")
    handler = declaration(source, "public void onWaveMobDamagedByPlayer(")
    authoritative = declaration(source, "public void onWaveMobPlayerDamageAuthoritative(")

    assert "boolean acceptedAtDispatch = authoritative != null || !event.isCancelled()" in handler
    assert "victim instanceof Skeleton" in handler
    assert "delta > 0.0D" in handler
    assert "respondToWaveSkeletonDamage(victimId, victimLocation, victimWave" in handler
    assert handler.index("double delta =") < handler.index("respondToWaveSkeletonDamage(")
    assert "victim.setHealth(result.remainingHealth())" in authoritative
    assert "authoritativeEventMobDamage.put(event, result)" in authoritative


def test_wave_ai_honors_the_committed_protection_target_until_deadline():
    source = SOURCE.read_text(encoding="utf-8")
    ai_tick = declaration(source, "private void tickWaveMobAi(")
    unregister = declaration(source, "private Entity unregisterOwnedEntity(")

    assert "activeWaveSkeletonProtectionTarget(mob, now)" in ai_tick
    assert "if (protectionTarget != null)" in ai_tick
    assert "clearWaveSkeletonProtectionForEntity(entityId)" in unregister
