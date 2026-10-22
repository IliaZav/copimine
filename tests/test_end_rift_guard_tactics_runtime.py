"""Execute the real W6 guard adapters at detached Paper movement/visibility boundaries."""

from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"


def declaration(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@pytest.fixture(scope="module")
def guard_probe(tmp_path_factory):
    source = SOURCE.read_text(encoding="utf-8")
    methods = "\n".join(declaration(source, signature) for signature in (
        "private void tickRitualGuardPost(",
        "private void tickRitualGuardEndermanCombat(",
        "private void beginRitualGuardAbility(",
        "private boolean ritualGuardTargetAllowed(",
        "private boolean isRitualGuardAbilityCastValid(",
    ))
    directory = tmp_path_factory.mktemp("guard-tactics")
    fixture = directory / "GuardTacticsProbe.java"
    fixture.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.RitualGuardAggroPolicy;
import me.copimine.endevent.domain.RitualGuardAbilityPolicy;
public class GuardTacticsProbe {
    long generation=1, ritualGuardAbilityGeneration=1, eventTickCounter, ritualGuardAbilityResolveAtTick;
    String eventId="fixture";
    UUID ritualGuardAbilityGuard, ritualGuardAbilityTarget;
    RitualGuardAbilityPolicy.Profile ritualGuardAbilityProfile=RitualGuardAbilityPolicy.forEntityType("ENDERMAN");
    Location ritualGuardAbilityOrigin=new Location(0,0), ritualGuardAbilityImpact=new Location(2,0);
    static final long WAVE_PATH_REQUEST_INTERVAL_MILLIS=1000;
    static final long RITUAL_GUARD_RETURN_PATH_TIMEOUT_MILLIS=8000;
    static final int RITUAL_GUARD_RETURN_MAX_PATH_REQUESTS=3;
    static final String EVENT_KIND_RITUAL_GUARD="RITUAL_GUARD";
    Map<UUID,Long> nextWavePathRequestMillis=new HashMap<>(), ritualCasterAlertUntil=new HashMap<>(),
        wavePlayerAggroUntil=new HashMap<>();
    Map<UUID,UUID> ritualGuardPreferredTargets=new HashMap<>();
    Map<UUID,Integer> ritualGuardReturnPathAttempts=new HashMap<>();
    Map<UUID,Long> ritualGuardReturnPathDeadlineMillis=new HashMap<>();
    Map<UUID,Location> ritualGuardReturnPathDestinations=new HashMap<>();
    Map<UUID,Long> ritualGuardEndermanMeleeBurstUntilTick=new HashMap<>(),
        ritualGuardEndermanMeleeCooldownUntilTick=new HashMap<>();
    Map<UUID,UUID> ritualGuardCasters=new HashMap<>();
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    enum EntityType { ZOMBIE, SPIDER, ENDERMAN }
    enum Sound { BLOCK_BEACON_POWER_SELECT, BLOCK_AMETHYST_BLOCK_CHIME }
    enum SoundCategory { HOSTILE }
    static class World { void playSound(Location at,Sound sound,SoundCategory category,float volume,float pitch){} }
    static final World WORLD=new World();
    static class Vector {
        double x,z; Vector(){ } Vector(double x,double z){this.x=x;this.z=z;}
        Vector subtract(Vector v){x-=v.x;z-=v.z;return this;} Vector setY(double y){return this;}
    }
    static class Location {
        double x,y,z; Location(double x,double z){this(x,0,z);} Location(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Location clone(){return new Location(x,y,z);} Vector toVector(){return new Vector(x,z);}
        Location add(Vector v){x+=v.x;z+=v.z;return this;}
        double distanceSquared(Location other){double dx=x-other.x,dy=y-other.y,dz=z-other.z;return dx*dx+dy*dy+dz*dz;}
        double getY(){return y;}
        World getWorld(){return WORLD;}
    }
    static class Entity {
        UUID id=UUID.randomUUID(); Location pos;
        Entity(double x,double z){pos=new Location(x,z);}
        UUID getUniqueId(){return id;} Location getLocation(){return pos;} World getWorld(){return WORLD;}
        Location getEyeLocation(){return pos;}
    }
    static class Player extends Entity {
        boolean online=true, dead; Player(double x,double z){super(x,z);}
        Location getEyeLocation(){return pos;} boolean isOnline(){return online;}
        boolean isDead(){return dead;} double getHealth(){return dead?0:20;}
    }
    static class Path {
        int moves, stops; boolean active, moveAccepted=true;
        void stopPathfinding(){stops++;active=false;}
        boolean hasPath(){return active;}
        boolean moveTo(Location p,double speed){moves++;active=moveAccepted;return moveAccepted;}
        boolean moveTo(Player p,double speed){return moveTo(p.getLocation(),speed);}
    }
    static class Mob extends Entity {
        Player target; Path path=new Path(); boolean ai, aware, los=true; EntityType type=EntityType.ZOMBIE;
        Mob(double x,double z){super(x,z);} Player getTarget(){return target;}
        EntityType getType(){return type;}
        void setTarget(Player p){target=p;} Path getPathfinder(){return path;}
        void setAI(boolean value){ai=value;} void setAware(boolean value){aware=value;}
        void swingMainHand(){}
        void setVelocity(Vector v){ } boolean hasLineOfSight(Player p){return los;}
    }
    static class Skeleton extends Mob { Skeleton(double x,double z){super(x,z);} }
    static class Spider extends Mob { Spider(double x,double z){super(x,z);type=EntityType.SPIDER;} }
    static class Enderman extends Mob { Enderman(double x,double z){super(x,z);type=EntityType.ENDERMAN;} }
    static class Bukkit {
        static Map<UUID,Player> players=new HashMap<>();
        static Player getPlayer(UUID id){return players.get(id);}
    }
    Location postLocation=new Location(2.4,0);
    Location ritualGuardPostLocation(Mob guard,Mob caster,Location core){return postLocation.clone();}
    static double horizontalDistanceSquared(Location a,Location b){return (a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z);}
    void faceRitualCaster(Mob mob,Location target){ }
    void renderRitualGuardAbilityTelegraph(){ }
    static class Log { void info(String text){} void warning(String text){} }
    Log getLogger(){return new Log();}
    Player ritualNearestTarget(Location origin,double radius){return null;}
    void maintainSkeletonCombatPosture(Skeleton skeleton,Player target,String kind,long now){ }
    boolean isCurrentRitualGuard(Entity guard){return ritualGuardCasters.containsKey(guard.id);}
    boolean ritualTargetAllowed(Entity guard,Player player){return player.online&&!player.dead;}
    boolean isLiveOwnedEntity(UUID id){return ownedEntities.containsKey(id);}
    METHODS
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var f=new GuardTacticsProbe(); var guard=new Mob(2.4,0); var caster=new Mob(0,0);
        var target=new Player(2,0); f.ritualGuardCasters.put(guard.id,caster.id);
        f.ownedEntities.put(guard.id,guard);f.ownedEntities.put(caster.id,caster);
        switch(args[0]){
            case "leash" -> {
                f.ritualCasterAlertUntil.put(caster.id,Long.MAX_VALUE);
                target.pos=new Location(15,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"alarm must not send a posted guard after a player beyond its caster leash");
                target.pos=new Location(14,0);
                check(f.ritualGuardTargetAllowed(guard,target),"an attacking player at the leash boundary remains eligible");
            }
            case "hysteresis" -> {
                guard.target=target; target.pos=new Location(8,0);
                check(f.ritualGuardTargetAllowed(guard,target),"engaged guard must retain a target in the wider hysteresis band");
                target.pos=new Location(10.01,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"unprovoked interception ends outside the ten-block holding radius");
                guard.target=null; target.pos=new Location(5.9,0);
                check(f.ritualGuardTargetAllowed(guard,target),"an idle guard wakes inside the six-block threat radius");
                target.pos=new Location(6.1,0);
                check(!f.ritualGuardTargetAllowed(guard,target),"an idle guard does not chase beyond the inner radius");
            }
            case "return" -> {
                guard.pos=new Location(7,0);
                for(long now=0;now<=1000;now+=250)f.tickRitualGuardPost(guard,caster,caster.pos,null,now);
                check(guard.path.stops==0,"returning guard must not stop its home path on every encounter tick");
                check(guard.path.moves==1,"an active home path must not receive repeated destination requests");
                guard.pos=new Location(2.4,0);f.tickRitualGuardPost(guard,caster,caster.pos,null,1250);
                check(!guard.ai&&!guard.aware&&!guard.path.active,"arrived guard holds its post without native wandering");
            }
            case "return-unreachable" -> {
                guard.pos=new Location(7,0);guard.path.moveAccepted=false;
                for(long now=0;now<=9000;now+=250)f.tickRitualGuardPost(guard,caster,caster.pos,null,now);
                check(guard.path.moves==4,"an unreachable post receives a slow retry after the fast request budget");
                check(guard.ai&&guard.aware,"a failed route stays retryable without freezing the guard AI");
            }
            case "return-timeout" -> {
                guard.pos=new Location(7,0);
                for(long now=0;now<=33000;now+=1000)f.tickRitualGuardPost(guard,caster,caster.pos,null,now);
                check(guard.path.moves==5,"a path that never makes progress keeps receiving bounded slow retries");
                check(guard.path.active&&guard.ai&&guard.aware,"timed-out path recovery never leaves the guard frozen");
            }
            case "return-target-change" -> {
                guard.pos=new Location(7,0);
                f.tickRitualGuardPost(guard,caster,caster.pos,null,0);
                f.postLocation=new Location(5,0);
                f.tickRitualGuardPost(guard,caster,caster.pos,null,250);
                check(guard.path.stops==1&&guard.path.moves==2,
                    "a changed home post cancels the old route and requests its replacement immediately");
            }
            case "combat-return" -> {
                guard.pos=new Location(7,0);guard.target=target;guard.path.active=true;
                f.nextWavePathRequestMillis.put(guard.id,1000L);
                f.tickRitualGuardPost(guard,caster,caster.pos,null,250);
                check(guard.target==null&&guard.path.stops==1&&guard.path.moves==1,
                    "lost combat target must cancel the chase and start the home path immediately");
                f.tickRitualGuardPost(guard,caster,caster.pos,null,500);
                check(guard.path.stops==1&&guard.path.moves==1,"home path must survive the following tick");
            }
            case "visibility" -> {
                f.ritualGuardAbilityGuard=guard.id;f.ritualGuardAbilityTarget=target.id;
                Bukkit.players.put(target.id,target);
                check(f.isRitualGuardAbilityCastValid(),"visible nearby target retains its committed cast");
                guard.los=false;
                check(!f.isRitualGuardAbilityCastValid(),"guard must cancel its committed cast when the target takes cover");
                guard.los=true;f.generation=2;
                check(!f.isRitualGuardAbilityCastValid(),"stale generation must cancel its committed cast");
            }
            case "cast-retention" -> {
                target.pos=new Location(4.5,0); guard.target=target;
                Bukkit.players.put(target.id,target);
                check(f.ritualGuardTargetAllowed(guard,target),"acquired target remains eligible in the six-block retention band");
                f.beginRitualGuardAbility(guard,target,RitualGuardAbilityPolicy.forEntityType("ENDERMAN"));
                check(guard.target==null,"ability telegraph deliberately clears the native mob target");
                check(f.isRitualGuardAbilityCastValid(),"committed target remains valid after begin clears native target");
                target.pos=new Location(10.01,0);
                check(!f.isRitualGuardAbilityCastValid(),"committed target still expires beyond the ten-block hysteresis boundary");
            }
            case "spider-support" -> {
                var spider=new Spider(0,0); var postCaster=new Mob(0,0);
                var distant=new Player(5,0);
                f.tickRitualGuardPost(spider,postCaster,postCaster.pos,distant,0);
                check(spider.target==null,"web support spider must not keep a melee target outside bite range");
                check(!spider.ai&&!spider.aware,"web support spider holds position while preparing ranged support");
                check(spider.path.moves==0,"web support spider must not chase with melee pathfinding");
                var closeSpider=new Spider(0,0); var closeTarget=new Player(1.9,0);
                f.tickRitualGuardPost(closeSpider,postCaster,postCaster.pos,closeTarget,0);
                check(closeSpider.target==closeTarget&&closeSpider.ai&&closeSpider.aware,
                    "spider may use native melee only inside its two-block bite range");
                check(closeSpider.path.moves==0,"close spider lets vanilla attack reach resolve without a pursuit path");
            }
            case "enderman-burst" -> {
                var enderman=new Enderman(0,0); var ritualCaster=new Mob(0,0); var closeTarget=new Player(2,0);
                f.ritualGuardCasters.put(enderman.id,ritualCaster.id);
                f.ownedEntities.put(enderman.id,enderman);f.ownedEntities.put(ritualCaster.id,ritualCaster);
                f.eventTickCounter=100;
                check(f.ritualGuardTargetAllowed(enderman,closeTarget)
                        && closeTarget.getWorld().equals(enderman.getWorld())
                        && RitualGuardAbilityPolicy.mayBeginEndermanMelee(2.0D,100L,0L),
                    "test preconditions expose close, eligible, ready melee target allowed="
                    +f.ritualGuardTargetAllowed(enderman,closeTarget)+" worlds="
                    +closeTarget.getWorld().equals(enderman.getWorld())+" policy="
                    +RitualGuardAbilityPolicy.mayBeginEndermanMelee(2.0D,100L,0L));
                f.tickRitualGuardEndermanCombat(enderman,closeTarget);
                check(enderman.target==closeTarget&&enderman.ai&&enderman.aware,
                    "Enderman performs a native melee burst at close range target="+enderman.target
                    +" ai="+enderman.ai+" end="+f.ritualGuardEndermanMeleeBurstUntilTick.get(enderman.id)
                    +" cooldown="+f.ritualGuardEndermanMeleeCooldownUntilTick.get(enderman.id));
                check(f.ritualGuardEndermanMeleeBurstUntilTick.get(enderman.id)==140L
                        && f.ritualGuardEndermanMeleeCooldownUntilTick.get(enderman.id)==220L,
                    "Enderman melee burst and next-burst cooldown are bounded in server ticks");
                f.eventTickCounter=120;f.tickRitualGuardEndermanCombat(enderman,closeTarget);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman remains in melee through the second pinned vanilla attack window");
                f.eventTickCounter=139;f.tickRitualGuardEndermanCombat(enderman,closeTarget);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman does not end its two-attack opportunity before the burst deadline");
                f.eventTickCounter=140;f.tickRitualGuardEndermanCombat(enderman,closeTarget);
                check(enderman.target==null&&!enderman.ai&&!enderman.aware,
                    "Enderman stops melee after the burst and resumes its casting post");
                f.eventTickCounter=219;f.tickRitualGuardEndermanCombat(enderman,closeTarget);
                check(enderman.target==null&&!enderman.ai,
                    "Enderman does not restart melee during its cooldown");
                f.eventTickCounter=220;f.tickRitualGuardEndermanCombat(enderman,closeTarget);
                check(enderman.target==closeTarget&&enderman.ai,
                    "Enderman may begin the next melee burst when cooldown expires");
            }
        }
        System.out.println("GuardTacticsProbe OK "+args[0]);
    }
}
'''.replace("METHODS", methods), encoding="utf-8")
    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualGuardAggroPolicy.java"
    ability_policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RitualGuardAbilityPolicy.java"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(policy), str(ability_policy), str(fixture)],
                              capture_output=True, text=True, timeout=45)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["leash", "hysteresis", "return", "return-unreachable", "return-timeout", "return-target-change", "combat-return", "visibility", "cast-retention", "spider-support", "enderman-burst"])
def test_actual_guard_tactics(guard_probe, scenario):
    result = subprocess.run(["java", "-cp", str(guard_probe), "GuardTacticsProbe", scenario],
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, result.stdout + result.stderr


def test_over_leash_guard_returns_by_path_without_tactical_teleport():
    source = SOURCE.read_text(encoding="utf-8")
    groups = declaration(source, "private void tickRitualGuardGroups(")
    assert "teleportCombatEntity(guard, safe)" not in groups
    assert "tickRitualGuardPost(guard, caster, core, null, now)" in groups


def test_guard_return_path_failures_back_off_without_disabling_the_guard():
    source = SOURCE.read_text(encoding="utf-8")
    post = declaration(source, "private void tickRitualGuardPost(")
    assert "RitualGuardAggroPolicy.nextReturnPathAttempt(" in post
    assert "RitualGuardAggroPolicy.returnPathRetryDelayMillis(" in post
    assert "RitualGuardAggroPolicy.mayRequestReturnPath(" in post
    assert "boolean exhausted" not in post


def test_guard_spider_retains_its_separate_web_target_assignment():
    source = SOURCE.read_text(encoding="utf-8")
    groups = declaration(source, "private void tickRitualGuardGroups(")
    abilities = declaration(source, "private void tickRitualGuardAbilities(")
    assert "target = ritualNearestGuardTarget(guard, caster, now)" in groups
    assert "tickRitualGuardPost(guard, caster, core, target, now)" in groups
    assert "tickRitualGuardAbilities(now)" in groups
    assert "ritualNearestGuardTarget(guard, owner instanceof Mob caster ? caster : guard, now)" in abilities


def test_ranged_guard_targets_are_far_first_sticky_visible_and_range_bounded():
    source = SOURCE.read_text(encoding="utf-8")
    target_picker = declaration(source, "private Player ritualNearestGuardTarget(")
    target_validity = declaration(source, "private boolean ritualGuardTargetAllowed(")
    ability_tick = declaration(source, "private void tickRitualGuardAbilities(")

    assert "ritualGuardPreferredTargets.get(guardId)" in target_picker
    assert "ritualGuardPreferredTargets.put(guardId," in target_picker
    assert "RitualGuardAggroPolicy.preferredTargetIndex" in target_picker
    assert "guard.hasLineOfSight(player)" in target_picker
    assert "maximumRangeForGuardRole" in target_picker
    assert "ritualGuardPreferredTargets" in target_validity
    assert "RitualGuardAbilityPolicy.forGuardActionAtRange(" in ability_tick
    assert "roleSlot, actionOrdinal, distance)" in ability_tick
    assert "mayBeginSpiderWeb(distance)" in ability_tick


def test_wave6_zombie_tanks_keep_their_melee_knockback_profile_after_recovery():
    source = SOURCE.read_text(encoding="utf-8")
    configure = declaration(source, "private void configureRitualGuardCombatAttributes(")
    spawn = declaration(source, "private boolean startRitualSphereObjective(")
    reindex = declaration(source, "private void reindexRitualEntity(")

    assert "Attribute.GENERIC_KNOCKBACK_RESISTANCE" in configure
    assert "Attribute.GENERIC_ATTACK_KNOCKBACK" in configure
    assert "RitualGuardStatsPolicy.ZOMBIE_KNOCKBACK_RESISTANCE" in configure
    assert "RitualGuardStatsPolicy.ZOMBIE_ATTACK_KNOCKBACK" in configure
    assert "configureRitualGuardCombatAttributes(living)" in spawn
    assert "configureRitualGuardCombatAttributes(living)" in reindex


def test_wave6_roster_recovery_rejects_a_saved_entity_with_the_wrong_species():
    source = SOURCE.read_text(encoding="utf-8")
    recovery = declaration(source, "private boolean ensureRitualCasterRosterAvailableForRecovery(")

    assert "entityTypeForGuardRole(roleSlot)" in recovery
    assert "isGuardEntityTypeCompatible" in recovery
    assert "giantReplacement != isRiftGiant(guard)" in recovery


def test_guard_giants_share_one_persisted_wave_summon_budget():
    source = SOURCE.read_text(encoding="utf-8")
    summon = declaration(source, "private boolean tickRiftGiantSummon(")
    assert "sharedRiftGiantSummonsUsed(wave)" in summon
    assert "storeSharedRiftGiantSummonsUsed(giant, wave, totalUsed)" in summon

    count = declaration(source, "private int sharedRiftGiantSummonsUsed(")
    assert "ownedEntities.values()" in count
    assert "isRiftGiant(giant)" in count
    assert "readInt(giant, keyWave, -1) != wave" in count
    assert "RiftGiantPolicy.sharedSummonsUsed(counters)" in count

    store = declaration(source, "private void storeSharedRiftGiantSummonsUsed(")
    assert "ownedEntities.values()" in store
    assert ".getPersistentDataContainer().set(keyRiftGiantSummonsUsed" in store
